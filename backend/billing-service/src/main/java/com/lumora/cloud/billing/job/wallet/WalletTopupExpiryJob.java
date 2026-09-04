package com.lumora.cloud.billing.job.wallet;

import com.lumora.cloud.billing.mapper.wallet.WalletTopupOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class WalletTopupExpiryJob {

    private final WalletTopupOrderMapper topupMapper;

    @Scheduled(
            fixedDelayString = "${lumora.billing.payment.expiry-scan-interval:PT1M}",
            initialDelayString = "${lumora.billing.payment.expiry-initial-delay:PT1M}"
    )
    public void expirePending() {
        topupMapper.expirePending(Instant.now(), 500);
    }
}
