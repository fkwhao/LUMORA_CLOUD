package com.lumora.cloud.billing.messaging.order;

import com.lumora.cloud.billing.config.BillingMessagingConfiguration;
import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.billing.domain.entity.order.PurchaseOrderEntity;
import com.lumora.cloud.billing.mapper.order.PurchaseOrderMapper;
import com.lumora.cloud.billing.service.IBillingCatalogService;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.rabbitmq.listener.simple.retry.multiplier=2",
                "spring.rabbitmq.listener.simple.retry.max-interval=5000ms",
                "logging.level.com.lumora.cloud.billing.messaging=debug",
                "lumora.billing.reservation-expiry.enabled=false"
        }
)
@EnabledIfEnvironmentVariable(named = "LUMORA_RUN_RABBIT_TESTS", matches = "true")
class OrderExpiryRabbitIntegrationTest {

    @MockitoBean
    private CatalogClient catalogClient;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private IBillingCatalogService catalogService;

    @Autowired
    private PurchaseOrderMapper orderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deadLettersExpiredMessageAndExpiresPendingOrder() throws InterruptedException {
        org.mockito.Mockito.when(catalogClient.publishedModelReferences())
                .thenReturn(List.of(new PublishedModelReference("test-model", "Test Model")));
        assertThat(listenerRegistry.getListenerContainers())
                .isNotEmpty()
                .allSatisfy(container -> assertThat(container.isRunning()).isTrue());

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String planCode = "rabbit-it-" + suffix;
        String orderId = UUID.randomUUID().toString();
        String orderNo = "RIT-" + suffix;
        String delayQueueName = "lumora.billing.order.expiry.test." + suffix + ".q";
        String readyQueueName = "lumora.billing.order.expiry.test." + suffix + ".ready.q";
        String delayRoutingKey = "order.expire.test." + suffix;

        var plan = catalogService.create(new CreatePlanRequest(
                planCode, "RabbitMQ 订单过期测试", "自动化测试数据", 1L, "CNY", BigDecimal.ONE,
                List.of("test-model")
        ));
        Queue delayQueue = QueueBuilder.nonDurable(delayQueueName)
                .autoDelete()
                .deadLetterExchange(BillingMessagingConfiguration.ORDER_EXCHANGE)
                .deadLetterRoutingKey(BillingMessagingConfiguration.ORDER_EXPIRY_ROUTING_KEY)
                .build();
        Queue readyQueue = QueueBuilder.nonDurable(readyQueueName).autoDelete().build();

        try {
            amqpAdmin.declareQueue(delayQueue);
            amqpAdmin.declareQueue(readyQueue);
            amqpAdmin.declareBinding(BindingBuilder.bind(delayQueue)
                    .to(new DirectExchange(BillingMessagingConfiguration.ORDER_EXCHANGE))
                    .with(delayRoutingKey));
            amqpAdmin.declareBinding(BindingBuilder.bind(readyQueue)
                    .to(new DirectExchange(BillingMessagingConfiguration.ORDER_EXCHANGE))
                    .with(BillingMessagingConfiguration.ORDER_EXPIRY_ROUTING_KEY));

            Instant expiresAt = Instant.now().plusSeconds(2);
            orderMapper.insertPendingIgnore(PurchaseOrderEntity.pending(
                    orderId, orderNo, 9_900_000_001L, plan.planVersionId(), plan.code(), plan.name(),
                    plan.monthlyPriceMinor(), plan.currency(), "rabbit-it-" + suffix, expiresAt
            ));
            rabbitTemplate.convertAndSend(
                    BillingMessagingConfiguration.ORDER_EXCHANGE,
                    delayRoutingKey,
                    orderNo,
                    message -> {
                        message.getMessageProperties().setExpiration("3000");
                        return message;
                    }
            );
            assertThat(amqpAdmin.getQueueInfo(delayQueueName))
                    .isNotNull()
                    .extracting(info -> info.getMessageCount())
                    .isEqualTo(1);

            assertThat(rabbitTemplate.receiveAndConvert(readyQueueName, 10_000))
                    .isEqualTo(orderNo);
            Instant deadline = Instant.now().plusSeconds(15);
            PurchaseOrderEntity order;
            do {
                order = orderMapper.selectById(orderId);
                if (order != null && "EXPIRED".equals(order.getStatus())) {
                    break;
                }
                Thread.sleep(100);
            } while (Instant.now().isBefore(deadline));

            assertThat(order).isNotNull();
            assertThat(order.getStatus()).isEqualTo("EXPIRED");
        } finally {
            amqpAdmin.deleteQueue(delayQueueName);
            amqpAdmin.deleteQueue(readyQueueName);
            orderMapper.deleteById(orderId);
            jdbcTemplate.update("""
                    DELETE scope
                    FROM billing_plan_version_model scope
                    INNER JOIN billing_plan_version version ON version.id = scope.plan_version_id
                    WHERE version.plan_id = ?
                    """, plan.planId());
            jdbcTemplate.update("DELETE FROM billing_plan_version WHERE plan_id = ?", plan.planId());
            jdbcTemplate.update("DELETE FROM billing_plan WHERE id = ?", plan.planId());
        }
    }
}
