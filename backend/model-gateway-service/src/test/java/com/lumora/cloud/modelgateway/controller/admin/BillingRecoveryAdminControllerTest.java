package com.lumora.cloud.modelgateway.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.modelgateway.recovery.*;
import com.lumora.cloud.modelgateway.security.ModelGatewayAccess;
import com.lumora.cloud.modelgateway.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.nio.file.Path;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class BillingRecoveryAdminControllerTest {
    @TempDir Path directory;
    private HttpHeaders headers(String role) {
        var headers = new HttpHeaders();
        headers.set(AuthHeaders.INTERNAL_TOKEN, "isolated-token");
        headers.set(AuthHeaders.SERVICE_ID, "lumora-cloud-gateway");
        headers.set(AuthHeaders.USER_ID, "42");
        headers.set(AuthHeaders.ROLES, role);
        return headers;
    }

    @Test void deniesUntrustedAndOrdinaryUsersForReadAndRetry() throws Exception {
        try (var journal = new DurableRecoveryJournal(new ObjectMapper().findAndRegisterModules(), directory.toString(), 10)) {
            var controller = new BillingRecoveryAdminController(new ModelGatewayAccess("isolated-token"), journal);
            for (var headers : java.util.List.of(new HttpHeaders(), headers("USER"))) {
                var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").headers(headers).build());
                assertThatThrownBy(() -> controller.list(exchange, 1)).isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> controller.retry(exchange, "request-1",
                        new BillingRecoveryAdminController.ResumeRequest("verified")))
                        .isInstanceOf(ApiException.class);
            }
            assertThat(journal.size()).isZero();
        }
    }

    @Test void adminSeesEvidenceAndRetryReturnsNoContentWithAudit() throws Exception {
        try (var journal = new DurableRecoveryJournal(new ObjectMapper().findAndRegisterModules(), directory.toString(), 10)) {
            var command = new RecoveryCommand(RecoveryOperation.PENDING, "request-1", null, "missing usage", 0, Instant.now());
            journal.schedule(command, Instant.now());
            journal.park(command, "retry limit reached");
            var controller = new BillingRecoveryAdminController(new ModelGatewayAccess("isolated-token"), journal);
            var client = WebTestClient.bindToController(controller).build();
            client.get().uri("/api/admin/model-gateway/recovery").headers(h -> h.addAll(headers("ADMIN")))
                    .exchange().expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(1)
                    .jsonPath("$.items[0].command.requestId").isEqualTo("request-1");
            client.post().uri("/api/admin/model-gateway/recovery/request-1/retry")
                    .headers(h -> h.addAll(headers("ADMIN"))).bodyValue(java.util.Map.of("reason", " "))
                    .exchange().expectStatus().isBadRequest();
            assertThat(journal.page(0, 10).getFirst().blockedReason()).isNotNull();
            client.post().uri("/api/admin/model-gateway/recovery/request-1/retry")
                    .headers(h -> h.addAll(headers("ADMIN"))).bodyValue(java.util.Map.of("reason", "TEST-003 verified"))
                    .exchange().expectStatus().isNoContent().expectBody().isEmpty();
            assertThat(journal.page(0, 10).getFirst().retryNote()).contains("#42", "TEST-003");
            assertThat(journal.page(0, 10).getFirst().blockedReason()).isNull();
        }
    }
}
