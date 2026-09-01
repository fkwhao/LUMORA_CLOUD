package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.config.PaymentProperties;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.persistence.entity.WalletAccountEntity;
import com.lumora.cloud.billing.persistence.entity.WalletLedgerEntity;
import com.lumora.cloud.billing.persistence.entity.WalletTopupOrderEntity;
import com.lumora.cloud.billing.persistence.mapper.WalletAccountMapper;
import com.lumora.cloud.billing.persistence.mapper.WalletLedgerMapper;
import com.lumora.cloud.billing.persistence.mapper.WalletTopupOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletServiceTest {

    private final WalletAccountMapper accountMapper = mock(WalletAccountMapper.class);
    private final WalletTopupOrderMapper topupMapper = mock(WalletTopupOrderMapper.class);
    private final WalletLedgerMapper ledgerMapper = mock(WalletLedgerMapper.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final WalletService service = new WalletService(
            accountMapper, topupMapper, ledgerMapper,
            new PaymentProperties(true, Duration.ofMinutes(30), Duration.ofDays(30)), events
    );

    @Test
    void mockTopupCreditsLockedAccountAndCreatesImmutableLedger() {
        Instant now = Instant.now();
        WalletTopupOrderEntity pending = WalletTopupOrderEntity.pending(
                "topup-id", "WU20260901090000ABCDEFGHIJKL", 3L, 7L,
                10_000L, "CNY", "retry-1", now.plusSeconds(600)
        );
        WalletAccountEntity account = mock(WalletAccountEntity.class);
        when(account.getId()).thenReturn(3L);
        when(account.getAvailableMinor()).thenReturn(2_000L);
        when(topupMapper.findByOrderNoForUpdate(pending.getOrderNo())).thenReturn(pending);
        when(accountMapper.findForUpdate(7L, "CNY")).thenReturn(account);
        when(accountMapper.credit(3L, 10_000L)).thenReturn(1);
        when(topupMapper.markPaid(any(), any())).thenReturn(1);
        AtomicReference<WalletLedgerEntity> ledger = new AtomicReference<>();
        when(ledgerMapper.insert(any(WalletLedgerEntity.class))).thenAnswer(invocation -> {
            ledger.set(invocation.getArgument(0));
            return 1;
        });
        WalletTopupOrderEntity paid = mock(WalletTopupOrderEntity.class);
        when(paid.getOrderNo()).thenReturn(pending.getOrderNo());
        when(paid.getUserId()).thenReturn(7L);
        when(paid.getAmountMinor()).thenReturn(10_000L);
        when(paid.getCurrency()).thenReturn("CNY");
        when(paid.getStatus()).thenReturn("PAID");
        when(paid.getExpiresAt()).thenReturn(pending.getExpiresAt());
        when(topupMapper.selectById("topup-id")).thenReturn(paid);

        var response = service.mockPayTopup(7L, pending.getOrderNo());

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(ledger.get().getAmountDelta()).isEqualTo(10_000L);
        assertThat(ledger.get().getBalanceAfter()).isEqualTo(12_000L);
        verify(accountMapper).credit(3L, 10_000L);
    }

    @Test
    void purchaseDebitNeverAllowsNegativeBalance() {
        WalletAccountEntity account = mock(WalletAccountEntity.class);
        when(account.getId()).thenReturn(3L);
        when(accountMapper.findForUpdate(7L, "CNY")).thenReturn(account);
        when(accountMapper.debit(3L, 5_000L)).thenReturn(0);

        assertThatThrownBy(() -> service.debitPurchase(7L, "CNY", 5_000L, "LU-order"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("INSUFFICIENT_WALLET_BALANCE");
    }
}
