package com.lumora.cloud.modelgateway.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.billing.BillingClient;
import com.lumora.cloud.api.billing.BillingContracts.*;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DurableRecoveryJournalTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private RecoveryCommand command() {
        return new RecoveryCommand(RecoveryOperation.SETTLE, "request-1",
                new SettleRequest("usage-1", "v1", 100, 20, 0, 0, 0, new BigDecimal("0.00012"),
                        Instant.parse("2026-09-01T00:00:00Z")), null, 0, Instant.now());
    }
    private ModelGatewayProperties properties() {
        var value = mock(ModelGatewayProperties.class, RETURNS_DEEP_STUBS);
        when(value.recovery().scanInterval()).thenReturn(Duration.ofSeconds(1));
        when(value.recovery().retention()).thenReturn(Duration.ofDays(7));
        when(value.recovery().batchSize()).thenReturn(100);
        return value;
    }
    @SuppressWarnings("unchecked")
    private ReactiveStringRedisTemplate unavailableRedis() {
        ReactiveZSetOperations<String, String> zset = mock(ReactiveZSetOperations.class,
                invocation -> Flux.error(new IllegalStateException("Redis unavailable")));
        return mock(ReactiveStringRedisTemplate.class, invocation -> switch (invocation.getMethod().getName()) {
            case "execute" -> Flux.error(new IllegalStateException("Redis unavailable"));
            case "opsForZSet" -> zset;
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }
    @Test void billingAndRedisOutagePreservesFullEvidenceAndRestartsWithoutRedis() throws Exception {
        var client = mock(BillingClient.class);
        when(client.settle(anyString(), any())).thenThrow(new IllegalStateException("Billing unavailable"));
        var props = properties();
        var redis = unavailableRedis();
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 10)) {
            var store = new RecoveryStore(journal, redis, mapper, props);
            var service = new BillingRecoveryService(client, store, props);
            assertThat(service.settle("request-1", command().settlement()).block(Duration.ofSeconds(5))).isFalse();
            assertThat(journal.size()).isEqualTo(1);
            var due = journal.claimDue(Instant.now().plusSeconds(600), Instant.now(), 10);
            assertThat(due).singleElement().satisfies(row -> assertThat(row.settlement().inputTokens()).isEqualTo(100));
            journal.schedule(due.getFirst(), Instant.EPOCH);
        }
        reset(client);
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 10)) {
            var store = new RecoveryStore(journal, redis, mapper, props);
            var service = new BillingRecoveryService(client, store, props);
            assertThat(store.due(Instant.now()).collectList().block()).hasSize(1);
            var due = journal.claimDue(Instant.now().plusSeconds(600), Instant.now(), 10).getFirst();
            journal.schedule(due, Instant.EPOCH);
            service.retryDueCommands();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(journal.size()).isZero());
            verify(client, times(1)).settle(eq("request-1"), argThat(usage ->
                    usage.usageId().equals("usage-1") && usage.billedQuota().compareTo(new BigDecimal("0.00012")) == 0));
        }
    }
    @Test void replayAfterLostAcknowledgementKeepsSameUsageId() throws Exception {
        var value = command();
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 10)) {
            journal.schedule(value, Instant.EPOCH);
            assertThat(journal.claimDue(Instant.now(), Instant.now().plusSeconds(30), 10)).containsExactly(value);
            assertThat(journal.claimDue(Instant.now(), Instant.now().plusSeconds(30), 10)).isEmpty();
        }
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 10)) {
            assertThat(journal.claimDue(Instant.now(), Instant.now().plusSeconds(30), 10)).containsExactly(value);
            journal.complete(value.nextAttempt());
            assertThat(journal.size()).isZero();
        }
    }
    @Test void fullJournalRejectsNewWorkButAllowsRetryOfExistingEvidence() throws Exception {
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 1)) {
            var value = command();
            journal.schedule(value, Instant.now());
            assertThatThrownBy(journal::checkWritable).hasMessageContaining("capacity");
            journal.schedule(value.nextAttempt(), Instant.EPOCH);
            assertThat(journal.size()).isEqualTo(1);
            var conflicting = new RecoveryCommand(RecoveryOperation.RELEASE, value.requestId(), null, "different", 1, value.createdAt());
            assertThatThrownBy(() -> journal.schedule(conflicting, Instant.now())).hasMessageContaining("Conflicting");
            journal.complete(conflicting);
            assertThat(journal.size()).isEqualTo(1);
        }
    }
    @Test void exhaustedRetriesRemainDurableAndAdminCanResumeOriginalEvidence() throws Exception {
        var value = command();
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 10)) {
            var store = new RecoveryStore(journal, unavailableRedis(), mapper, properties());
            var client = mock(BillingClient.class);
            when(client.settle(anyString(), any())).thenThrow(new IllegalStateException("still unavailable"));
            var service = new BillingRecoveryService(client, store, properties());
            org.springframework.test.util.ReflectionTestUtils.setField(service, "maxAttempts", 1);
            assertThat(service.settle(value.requestId(), value.settlement()).block(Duration.ofSeconds(5))).isFalse();
            assertThat(journal.claimDue(Instant.now().plusSeconds(99999), Instant.now(), 10)).isEmpty();
            assertThat(journal.page(0, 10).getFirst().blockedReason()).contains("重试上限");
        }
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 10)) {
            assertThat(journal.page(0, 10)).hasSize(1);
            journal.resume(value.requestId(), "42", "TEST-002 Billing restored");
            var restored = journal.claimDue(Instant.now().plusSeconds(1), Instant.now(), 10).getFirst();
            assertThat(restored.attempts()).isZero();
            assertThat(restored.settlement()).isEqualTo(value.settlement());
            assertThat(journal.page(0, 10).getFirst().retryNote()).contains("#42", "TEST-002");
        }
    }
    @Test void corruptEvidenceFailsStartupWithoutRemovingIt() throws Exception {
        Path damaged = directory.resolve("corrupt.json");
        Files.writeString(damaged, "{broken");
        assertThatThrownBy(() -> new DurableRecoveryJournal(mapper, directory.toString(), 10)).isInstanceOf(java.io.IOException.class);
        assertThat(Files.readString(damaged)).isEqualTo("{broken");
    }
    @Test void directoryHasSingleOwnerAndCanBeReopenedAfterShutdown() throws Exception {
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 10)) {
            assertThatThrownBy(() -> new DurableRecoveryJournal(mapper, directory.toString(), 10))
                    .isInstanceOf(java.nio.channels.OverlappingFileLockException.class);
        }
        try (var journal = new DurableRecoveryJournal(mapper, directory.toString(), 10)) { journal.checkWritable(); }
    }
}
