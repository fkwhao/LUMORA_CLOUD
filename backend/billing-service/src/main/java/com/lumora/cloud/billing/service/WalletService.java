package com.lumora.cloud.billing.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.billing.config.PaymentProperties;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.messaging.WalletTopupExpiryScheduledEvent;
import com.lumora.cloud.billing.persistence.entity.WalletAccountEntity;
import com.lumora.cloud.billing.persistence.entity.WalletLedgerEntity;
import com.lumora.cloud.billing.persistence.entity.WalletTopupOrderEntity;
import com.lumora.cloud.billing.persistence.mapper.WalletAccountMapper;
import com.lumora.cloud.billing.persistence.mapper.WalletLedgerMapper;
import com.lumora.cloud.billing.persistence.mapper.WalletTopupOrderMapper;
import com.lumora.cloud.billing.web.BillingWebContracts.AdminWalletAdjustmentRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.CreateWalletTopupRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.WalletAccountResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.WalletAdjustmentResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.WalletLedgerEntryResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.WalletOverviewResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.WalletTopupOrderResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WalletService {

    private static final String DEFAULT_CURRENCY = "CNY";
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final WalletAccountMapper accountMapper;
    private final WalletTopupOrderMapper topupMapper;
    private final WalletLedgerMapper ledgerMapper;
    private final PaymentProperties paymentProperties;
    private final ApplicationEventPublisher events;

    public WalletService(
            WalletAccountMapper accountMapper,
            WalletTopupOrderMapper topupMapper,
            WalletLedgerMapper ledgerMapper,
            PaymentProperties paymentProperties,
            ApplicationEventPublisher events
    ) {
        this.accountMapper = accountMapper;
        this.topupMapper = topupMapper;
        this.ledgerMapper = ledgerMapper;
        this.paymentProperties = paymentProperties;
        this.events = events;
    }

    @Transactional
    public WalletOverviewResponse overview(Long userId) {
        topupMapper.expireUserOrders(userId, Instant.now());
        List<WalletAccountEntity> accounts = accountMapper.findByUserId(userId);
        if (accounts.isEmpty()) {
            accountMapper.ensureExists(userId, DEFAULT_CURRENCY);
            accounts = accountMapper.findByUserId(userId);
        }
        return overviewResponse(userId, accounts);
    }

    @Transactional
    public WalletTopupOrderResponse createTopup(
            Long userId, String idempotencyKey, CreateWalletTopupRequest request
    ) {
        String currency = currency(request.currency());
        String normalizedKey = idempotencyKey.trim();
        accountMapper.ensureExists(userId, currency);
        WalletAccountEntity account = accountMapper.findForUpdate(userId, currency);
        if (account == null) {
            throw new IllegalStateException("Wallet account was not created");
        }
        Instant now = Instant.now();
        WalletTopupOrderEntity pending = WalletTopupOrderEntity.pending(
                UUID.randomUUID().toString(), topupOrderNumber(now), account.getId(), userId,
                request.amountMinor(), currency, normalizedKey, now.plus(paymentProperties.orderTtl())
        );
        topupMapper.insertPendingIgnore(pending);
        WalletTopupOrderEntity persisted = topupMapper.findByIdempotencyForUpdate(userId, normalizedKey);
        if (persisted == null) {
            throw new IllegalStateException("Wallet top-up order was not created");
        }
        if (persisted.getAmountMinor() != request.amountMinor() || !persisted.getCurrency().equals(currency)) {
            throw new ApiException(HttpStatus.CONFLICT, "TOPUP_IDEMPOTENCY_CONFLICT",
                    "相同幂等键对应了不同充值参数");
        }
        if ("PENDING_PAYMENT".equals(persisted.getStatus())) {
            events.publishEvent(new WalletTopupExpiryScheduledEvent(
                    persisted.getOrderNo(), persisted.getExpiresAt()
            ));
        }
        return topupResponse(persisted);
    }

    @Transactional
    public WalletTopupOrderResponse mockPayTopup(Long userId, String orderNo) {
        if (!paymentProperties.mockEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MOCK_PAYMENT_DISABLED", "本地测试支付渠道未启用");
        }
        WalletTopupOrderEntity order = requireTopupForUpdate(userId, orderNo);
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return topupResponse(order);
        }
        Instant now = Instant.now();
        if (!order.getExpiresAt().isAfter(now)) {
            topupMapper.expirePendingOrder(orderNo, now);
            return topupResponse(topupMapper.selectById(order.getId()));
        }

        WalletAccountEntity account = accountMapper.findForUpdate(userId, order.getCurrency());
        long balanceAfter = checkedAdd(account.getAvailableMinor(), order.getAmountMinor());
        if (accountMapper.credit(account.getId(), order.getAmountMinor()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "WALLET_STATE_CONFLICT", "钱包余额已被其他操作修改");
        }
        ledgerMapper.insert(WalletLedgerEntity.create(
                UUID.randomUUID().toString(), account.getId(), userId, order.getCurrency(),
                "TOPUP", "TOPUP_ORDER", orderNo, order.getAmountMinor(), balanceAfter,
                "MOCK 充值到账", userId
        ));
        if (topupMapper.markPaid(order.getId(), now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "TOPUP_STATE_CONFLICT", "充值订单状态已被其他操作修改");
        }
        return topupResponse(topupMapper.selectById(order.getId()));
    }

    @Transactional
    public WalletTopupOrderResponse cancelTopup(Long userId, String orderNo) {
        WalletTopupOrderEntity order = requireTopupForUpdate(userId, orderNo);
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return topupResponse(order);
        }
        Instant now = Instant.now();
        if (order.getExpiresAt().isAfter(now)) {
            topupMapper.markCanceled(order.getId());
        } else {
            topupMapper.expirePendingOrder(orderNo, now);
        }
        return topupResponse(topupMapper.selectById(order.getId()));
    }

    @Transactional
    public boolean expireTopup(String orderNo, Instant now) {
        return topupMapper.expirePendingOrder(orderNo, now) == 1;
    }

    @Transactional
    public WalletAdjustmentResponse adjust(
            Long actorUserId, String idempotencyKey, AdminWalletAdjustmentRequest request
    ) {
        if (request.amountDelta() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZERO_WALLET_ADJUSTMENT", "钱包调整金额不能为 0");
        }
        if (request.amountDelta() == Long.MIN_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_ADJUSTMENT", "钱包调整金额超出系统范围");
        }
        String currency = currency(request.currency());
        String referenceId = actorUserId + ":" + idempotencyKey.trim();
        accountMapper.ensureExists(request.userId(), currency);
        WalletAccountEntity account = accountMapper.findForUpdate(request.userId(), currency);
        WalletLedgerEntity existing = ledgerMapper.findByReference("ADMIN_ADJUSTMENT", "ADMIN_REQUEST", referenceId);
        if (existing != null) {
            if (!existing.getAmountDelta().equals(request.amountDelta())
                    || !existing.getCurrency().equals(currency)
                    || !existing.getUserId().equals(request.userId())) {
                throw new ApiException(HttpStatus.CONFLICT, "WALLET_ADJUSTMENT_IDEMPOTENCY_CONFLICT",
                        "相同幂等键对应了不同钱包调整参数");
            }
            return new WalletAdjustmentResponse(accountResponse(account), ledgerResponse(existing));
        }

        long balanceAfter;
        if (request.amountDelta() > 0) {
            balanceAfter = checkedAdd(account.getAvailableMinor(), request.amountDelta());
            if (accountMapper.credit(account.getId(), request.amountDelta()) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "WALLET_STATE_CONFLICT", "钱包余额已被其他操作修改");
            }
        } else {
            long debit = Math.negateExact(request.amountDelta());
            if (accountMapper.debit(account.getId(), debit) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_WALLET_BALANCE", "钱包余额不足");
            }
            balanceAfter = account.getAvailableMinor() - debit;
        }
        WalletLedgerEntity ledger = WalletLedgerEntity.create(
                UUID.randomUUID().toString(), account.getId(), request.userId(), currency,
                "ADMIN_ADJUSTMENT", "ADMIN_REQUEST", referenceId, request.amountDelta(), balanceAfter,
                request.reason().trim(), actorUserId
        );
        ledgerMapper.insert(ledger);
        return new WalletAdjustmentResponse(
                accountResponse(accountMapper.selectById(account.getId())),
                ledgerResponse(ledgerMapper.selectById(ledger.getId()))
        );
    }

    @Transactional
    public WalletOverviewResponse adminOverview(Long userId) {
        return overview(userId);
    }

    void debitPurchase(Long userId, String currency, long amountMinor, String orderNo) {
        WalletAccountEntity account = accountMapper.findForUpdate(userId, currency(currency));
        if (account == null || accountMapper.debit(account.getId(), amountMinor) != 1) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_WALLET_BALANCE", "钱包余额不足");
        }
        ledgerMapper.insert(WalletLedgerEntity.create(
                UUID.randomUUID().toString(), account.getId(), userId, account.getCurrency(),
                "PURCHASE", "PURCHASE_ORDER", orderNo, -amountMinor,
                account.getAvailableMinor() - amountMinor, "购买云端套餐", userId
        ));
    }

    private WalletOverviewResponse overviewResponse(Long userId, List<WalletAccountEntity> accounts) {
        List<WalletTopupOrderResponse> topups = topupMapper.selectList(
                        Wrappers.<WalletTopupOrderEntity>lambdaQuery()
                                .eq(WalletTopupOrderEntity::getUserId, userId)
                                .orderByDesc(WalletTopupOrderEntity::getCreatedAt).last("LIMIT 50"))
                .stream().map(this::topupResponse).toList();
        List<WalletLedgerEntryResponse> ledger = ledgerMapper.selectList(
                        Wrappers.<WalletLedgerEntity>lambdaQuery()
                                .eq(WalletLedgerEntity::getUserId, userId)
                                .orderByDesc(WalletLedgerEntity::getCreatedAt).last("LIMIT 100"))
                .stream().map(this::ledgerResponse).toList();
        return new WalletOverviewResponse(
                userId, accounts.stream().map(this::accountResponse).toList(), topups, ledger
        );
    }

    private WalletTopupOrderEntity requireTopupForUpdate(Long userId, String orderNo) {
        WalletTopupOrderEntity order = topupMapper.findByOrderNoForUpdate(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "WALLET_TOPUP_ORDER_NOT_FOUND", "充值订单不存在");
        }
        return order;
    }

    private WalletAccountResponse accountResponse(WalletAccountEntity account) {
        return new WalletAccountResponse(
                account.getId(), account.getUserId(), account.getCurrency(), account.getAvailableMinor(),
                account.getVersion(), account.getUpdatedAt()
        );
    }

    private WalletTopupOrderResponse topupResponse(WalletTopupOrderEntity order) {
        return new WalletTopupOrderResponse(
                order.getOrderNo(), order.getUserId(), order.getAmountMinor(), order.getCurrency(),
                order.getStatus(), order.getExpiresAt(), order.getPaidAt(), paymentProperties.mockEnabled(),
                order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    private WalletLedgerEntryResponse ledgerResponse(WalletLedgerEntity ledger) {
        return new WalletLedgerEntryResponse(
                ledger.getId(), ledger.getUserId(), ledger.getCurrency(), ledger.getEntryType(),
                ledger.getReferenceType(), ledger.getReferenceId(), ledger.getAmountDelta(),
                ledger.getBalanceAfter(), ledger.getDescription(), ledger.getActorUserId(), ledger.getCreatedAt()
        );
    }

    private String currency(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private long checkedAdd(long balance, long amount) {
        try {
            return Math.addExact(balance, amount);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "WALLET_BALANCE_OVERFLOW", "钱包余额超出系统上限");
        }
    }

    private String topupOrderNumber(Instant now) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "WU" + ORDER_TIME.format(now) + random;
    }
}
