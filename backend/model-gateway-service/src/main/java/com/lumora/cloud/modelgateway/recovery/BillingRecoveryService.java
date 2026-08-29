package com.lumora.cloud.modelgateway.recovery;

import com.lumora.cloud.api.billing.BillingClient;
import com.lumora.cloud.api.billing.BillingContracts.PendingRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;

@Component
public class BillingRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(BillingRecoveryService.class);

    private final BillingClient billingClient;
    private final RecoveryStore store;
    private final ModelGatewayProperties properties;

    public BillingRecoveryService(
            BillingClient billingClient,
            RecoveryStore store,
            ModelGatewayProperties properties
    ) {
        this.billingClient = billingClient;
        this.store = store;
        this.properties = properties;
    }

    public Mono<Boolean> settle(String requestId, SettleRequest request) {
        return scheduleAndExecute(new RecoveryCommand(
                RecoveryOperation.SETTLE, requestId, request, null, 0, Instant.now()
        ));
    }

    public Mono<Boolean> release(String requestId, String reason) {
        return scheduleAndExecute(new RecoveryCommand(
                RecoveryOperation.RELEASE, requestId, null, reason, 0, Instant.now()
        ));
    }

    public Mono<Boolean> pending(String requestId, String reason) {
        return scheduleAndExecute(new RecoveryCommand(
                RecoveryOperation.PENDING, requestId, null, reason, 0, Instant.now()
        ));
    }

    private Mono<Boolean> scheduleAndExecute(RecoveryCommand command) {
        long safetyDelayMillis = Math.max(30_000L, properties.recovery().scanInterval().toMillis() * 3L);
        return store.schedule(command, Instant.now().plusMillis(safetyDelayMillis))
                .onErrorResume(exception -> {
                    log.error("Could not persist billing recovery command for request {}", command.requestId(), exception);
                    return Mono.empty();
                })
                .then(execute(command));
    }

    private Mono<Boolean> execute(RecoveryCommand command) {
        return Mono.fromCallable(() -> {
                    switch (command.operation()) {
                        case SETTLE -> billingClient.settle(command.requestId(), command.settlement());
                        case RELEASE -> billingClient.release(command.requestId(), new ReleaseRequest(command.reason()));
                        case PENDING -> billingClient.markPending(command.requestId(), new PendingRequest(command.reason()));
                    }
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ignored -> store.complete(command.requestId())
                        .onErrorResume(exception -> Mono.empty())
                        .thenReturn(true))
                .onErrorResume(exception -> {
                    RecoveryCommand next = command.nextAttempt();
                    Duration delay = Duration.ofSeconds(Math.min(300L, 5L << Math.min(next.attempts(), 5)));
                    log.warn("Billing recovery {} failed for request {}; retry in {} seconds",
                            command.operation(), command.requestId(), delay.toSeconds());
                    return store.schedule(next, Instant.now().plus(delay))
                            .onErrorResume(storeError -> {
                                log.error("Could not reschedule billing recovery for request {}",
                                        command.requestId(), storeError);
                                return Mono.empty();
                            })
                            .thenReturn(false);
                });
    }

    @Scheduled(
            fixedDelayString = "${lumora.model-gateway.recovery.scan-interval:PT10S}",
            initialDelayString = "${lumora.model-gateway.recovery.scan-interval:PT10S}"
    )
    public void retryDueCommands() {
        store.due(Instant.now())
                .flatMap(this::execute, 4)
                .onErrorContinue((exception, command) ->
                        log.error("Billing recovery scan failed for {}", command, exception))
                .subscribe();
    }
}
