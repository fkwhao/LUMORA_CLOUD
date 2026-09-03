package com.lumora.cloud.billing.job.wallet;

import com.lumora.cloud.billing.mapper.wallet.WalletTopupOrderMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WalletTopupExpiryJob {

    private final WalletTopupOrderMapper topupMapper;

    public WalletTopupExpiryJob(WalletTopupOrderMapper topupMapper) {
        this.topupMapper = topupMapper;
    }

    @Scheduled(
            fixedDelayString = "${lumora.billing.payment.expiry-scan-interval:PT1M}",
            initialDelayString = "${lumora.billing.payment.expiry-initial-delay:PT1M}"
    )
    public void expirePending() {
        topupMapper.expirePending(Instant.now(), 500);
    }
}
