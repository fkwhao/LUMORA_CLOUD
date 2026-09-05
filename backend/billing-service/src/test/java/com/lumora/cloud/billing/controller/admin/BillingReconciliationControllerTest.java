package com.lumora.cloud.billing.controller.admin;

import com.lumora.cloud.api.UserContext;
import com.lumora.cloud.api.UserContextHolder;
import com.lumora.cloud.billing.domain.dto.reconciliation.ReconciliationRequest;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.security.BillingAccess;
import com.lumora.cloud.billing.service.ISettlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BillingReconciliationControllerTest {
    private final ISettlementService settlements = mock(ISettlementService.class);
    private final BillingReconciliationController controller =
            new BillingReconciliationController(new BillingAccess(), settlements);
    private final ReconciliationRequest release = new ReconciliationRequest(
            ReconciliationRequest.Action.RELEASE, "supplier invoice checked", null);

    @AfterEach void clearContext() { UserContextHolder.clear(); }

    private void context(Set<String> roles) {
        UserContextHolder.set(new UserContext("42", "test-session", "test-device", roles, "WEB", "test-request"));
    }

    private void denied(String code) {
        assertThatThrownBy(() -> controller.get("request-1")).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode()).isEqualTo(code);
        assertThatThrownBy(() -> controller.resolve("request-1", release)).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode()).isEqualTo(code);
        assertThatThrownBy(() -> controller.list("PENDING_RECONCILIATION", null, null, 1, 20))
                .isInstanceOf(ApiException.class).extracting(error -> ((ApiException) error).getCode()).isEqualTo(code);
        assertThatThrownBy(() -> controller.batch(new com.lumora.cloud.billing.domain.dto.reconciliation.ReconciliationBatchRequest(
                java.util.List.of("request-1"), ReconciliationRequest.Action.RELEASE, "checked")))
                .isInstanceOf(ApiException.class).extracting(error -> ((ApiException) error).getCode()).isEqualTo(code);
        verifyNoInteractions(settlements);
    }

    @Test void rejectsUnauthenticatedReadsAndWrites() { denied("AUTHENTICATION_REQUIRED"); }

    @Test void rejectsOrdinaryUserReadsAndWrites() {
        context(Set.of("USER"));
        denied("ADMIN_REQUIRED");
    }

    @Test void passesAuthenticatedAdminIdentityForAudit() {
        context(Set.of("ADMIN"));
        controller.get("request-1");
        controller.resolve("request-1", release);
        verify(settlements).reconciliation("request-1");
        verify(settlements).reconcile(42L, "request-1", release);
        verifyNoMoreInteractions(settlements);
    }
}
