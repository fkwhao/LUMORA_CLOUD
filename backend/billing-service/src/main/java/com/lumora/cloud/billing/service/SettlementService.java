package com.lumora.cloud.billing.service;

import com.lumora.cloud.api.billing.BillingContracts.PendingRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReservationStatus;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettlementResponse;
import com.lumora.cloud.api.billing.BillingContracts.UsageStatus;
import com.lumora.cloud.billing.domain.BillingTypes.LedgerEntryType;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.persistence.entity.QuotaBucketEntity;
import com.lumora.cloud.billing.persistence.entity.QuotaLedgerEntity;
import com.lumora.cloud.billing.persistence.entity.ReservationEntity;
import com.lumora.cloud.billing.persistence.entity.SubscriptionEntity;
import com.lumora.cloud.billing.persistence.entity.UsageRecordEntity;
import com.lumora.cloud.billing.persistence.mapper.QuotaBucketMapper;
import com.lumora.cloud.billing.persistence.mapper.QuotaLedgerMapper;
import com.lumora.cloud.billing.persistence.mapper.ReservationMapper;
import com.lumora.cloud.billing.persistence.mapper.UsageRecordMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class SettlementService {

    private static final Duration DEFAULT_RESERVATION_TTL = Duration.ofMinutes(15);
    private static final Duration MAX_RESERVATION_TTL = Duration.ofHours(2);

    private final SubscriptionService subscriptionService;
    private final QuotaBucketService bucketService;
    private final QuotaBucketMapper bucketMapper;
    private final ReservationMapper reservationMapper;
    private final UsageRecordMapper usageMapper;
    private final QuotaLedgerMapper ledgerMapper;
    private final PlanModelAccessService planModelAccess;

    public SettlementService(
            SubscriptionService subscriptionService,
            QuotaBucketService bucketService,
            QuotaBucketMapper bucketMapper,
            ReservationMapper reservationMapper,
            UsageRecordMapper usageMapper,
            QuotaLedgerMapper ledgerMapper,
            PlanModelAccessService planModelAccess
    ) {
        this.subscriptionService = subscriptionService;
        this.bucketService = bucketService;
        this.bucketMapper = bucketMapper;
        this.reservationMapper = reservationMapper;
        this.usageMapper = usageMapper;
        this.ledgerMapper = ledgerMapper;
        this.planModelAccess = planModelAccess;
    }

    @Transactional
    public ReservationResponse reserve(ReserveRequest request) {
        validateReserve(request);
        Instant now = Instant.now();
        BigDecimal maximumQuota = BillingAmounts.positive(request.maximumQuota(), "maximumQuota");
        Instant requestedExpiry = reservationExpiry(request.expiresAt(), now);
        ReservationEntity processing = ReservationEntity.processing(
                UUID.randomUUID().toString(), request.requestId().trim(), request.clientRequestId().trim(),
                request.userId(), request.modelCode().trim(), request.pricingVersion().trim(), maximumQuota,
                request.pricingAt(), BillingAmounts.positive(request.quotaMultiplier(), "quotaMultiplier"),
                trimToNull(request.pricingRuleName()),
                requestedExpiry
        );
        reservationMapper.insertIdempotent(processing);
        ReservationEntity persisted = reservationMapper.findByRequestIdForUpdate(processing.getRequestId());
        if (persisted == null) {
            persisted = reservationMapper.findByClientRequestForUpdate(
                    processing.getUserId(), processing.getClientRequestId()
            );
        }
        if (persisted == null) {
            throw new IllegalStateException("Inserted reservation could not be read back");
        }
        // MySQL may report one affected row for a no-op ON DUPLICATE KEY UPDATE,
        // so idempotency must be decided by the persisted identifier instead of update count.
        if (!persisted.getId().equals(processing.getId())) {
            return existingReservation(request, maximumQuota);
        }
        processing = persisted;

        SubscriptionEntity subscription = subscriptionService.activeForUpdate(request.userId(), now);
        planModelAccess.requireAllowed(subscription.getPlanVersionId(), processing.getModelCode());
        QuotaBucketEntity bucket = bucketService.currentForUpdate(subscription, now);
        if (bucketMapper.reserve(bucket.getId(), maximumQuota) != 1) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_QUOTA", "当前套餐额度不足");
        }
        Instant effectiveExpiry = requestedExpiry.isBefore(bucket.getEndsAt()) ? requestedExpiry : bucket.getEndsAt();
        if (!effectiveExpiry.isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "QUOTA_PERIOD_ENDING", "当前额度周期已经结束");
        }
        if (reservationMapper.activate(processing.getId(), bucket.getId(), effectiveExpiry) != 1) {
            throw new IllegalStateException("Reservation could not be activated");
        }
        ledgerMapper.insert(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), request.userId(), bucket.getId(), processing.getId(),
                LedgerEntryType.RESERVE.name(), "MODEL_REQUEST", processing.getRequestId(),
                BillingAmounts.zero(), maximumQuota, BillingAmounts.zero(),
                "为模型 " + processing.getModelCode() + " 预占额度"
        ));
        return reservationResponse(
                reservationMapper.findByRequestIdForUpdate(processing.getRequestId()),
                bucketMapper.findByIdForUpdate(bucket.getId()),
                false
        );
    }

    @Transactional
    public SettlementResponse settle(String requestId, SettleRequest request) {
        requireText(requestId, "requestId", 64);
        validateSettle(request);
        BigDecimal billedQuota = BillingAmounts.positive(request.billedQuota(), "billedQuota");
        ReservationEntity reservation = requireReservation(requestId);
        ReservationStatus currentStatus = ReservationStatus.valueOf(reservation.getStatus());
        if (currentStatus == ReservationStatus.SETTLED || currentStatus == ReservationStatus.PENDING_RECONCILIATION) {
            return existingSettlement(reservation, request);
        }
        if (currentStatus != ReservationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_SETTLEABLE",
                    "当前预占状态不能结算: " + currentStatus);
        }
        if (!reservation.getPricingVersion().equals(request.pricingVersion().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRICING_VERSION_MISMATCH",
                    "结算价格版本与预占时锁定的版本不一致");
        }

        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        UsageRecordEntity usage = UsageRecordEntity.processing(
                UUID.randomUUID().toString(), reservation, request, occurredAt
        );
        usageMapper.insertIdempotent(usage);
        UsageRecordEntity persistedUsage = usageMapper.findByUsageIdForUpdate(usage.getUsageId());
        if (persistedUsage == null) {
            persistedUsage = usageMapper.findByReservationIdForUpdate(reservation.getId());
        }
        if (persistedUsage == null) {
            throw new IllegalStateException("Inserted usage record could not be read back");
        }
        if (!persistedUsage.getId().equals(usage.getId())) {
            return existingSettlement(reservation, request);
        }
        usage = persistedUsage;
        QuotaBucketEntity bucket = requireBucket(reservation);

        if (billedQuota.compareTo(reservation.getRequestedQuota()) > 0) {
            String reason = "实际费用超过最大预占额度";
            usageMapper.markPending(usage.getId());
            reservationMapper.markPending(reservation.getId(), reason);
            return new SettlementResponse(
                    usage.getUsageId(), reservation.getRequestId(),
                    ReservationStatus.PENDING_RECONCILIATION, UsageStatus.PENDING_RECONCILIATION,
                    reservation.getRequestedQuota(), billedQuota, BillingAmounts.zero(), bucket.availableQuota()
            );
        }

        if (bucketMapper.settle(bucket.getId(), reservation.getRequestedQuota(), billedQuota) != 1) {
            throw new IllegalStateException("Quota bucket could not be settled");
        }
        Instant settledAt = Instant.now();
        if (reservationMapper.markSettled(reservation.getId(), billedQuota, settledAt) != 1
                || usageMapper.markCompleted(usage.getId()) != 1) {
            throw new IllegalStateException("Settlement state could not be completed");
        }
        ledgerMapper.insert(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), reservation.getUserId(), bucket.getId(), reservation.getId(),
                LedgerEntryType.SETTLE.name(), "USAGE", usage.getUsageId(),
                BillingAmounts.zero(), reservation.getRequestedQuota().negate(), billedQuota,
                "结算模型 " + reservation.getModelCode() + " 的权威用量"
        ));
        QuotaBucketEntity updated = bucketMapper.findByIdForUpdate(bucket.getId());
        return new SettlementResponse(
                usage.getUsageId(), reservation.getRequestId(), ReservationStatus.SETTLED, UsageStatus.COMPLETED,
                reservation.getRequestedQuota(), billedQuota,
                reservation.getRequestedQuota().subtract(billedQuota), updated.availableQuota()
        );
    }

    @Transactional
    public ReservationResponse release(String requestId, ReleaseRequest request) {
        requireText(requestId, "requestId", 64);
        ReservationEntity reservation = requireReservation(requestId);
        ReservationStatus status = ReservationStatus.valueOf(reservation.getStatus());
        if (status == ReservationStatus.RELEASED) {
            return reservationResponse(reservation, requireBucket(reservation), true);
        }
        if (status != ReservationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_RELEASABLE",
                    "当前预占状态不能释放: " + status);
        }
        QuotaBucketEntity bucket = requireBucket(reservation);
        if (bucketMapper.release(bucket.getId(), reservation.getRequestedQuota()) != 1) {
            throw new IllegalStateException("Quota bucket could not release reservation");
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "模型调用未产生费用"
                : truncate(request.reason().trim(), 255);
        if (reservationMapper.markReleased(reservation.getId(), reason, Instant.now()) != 1) {
            throw new IllegalStateException("Reservation could not be released");
        }
        ledgerMapper.insert(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), reservation.getUserId(), bucket.getId(), reservation.getId(),
                LedgerEntryType.RELEASE.name(), "MODEL_REQUEST", reservation.getRequestId(),
                BillingAmounts.zero(), reservation.getRequestedQuota().negate(), BillingAmounts.zero(), reason
        ));
        return reservationResponse(
                reservationMapper.findByRequestIdForUpdate(requestId),
                bucketMapper.findByIdForUpdate(bucket.getId()),
                false
        );
    }

    @Transactional
    public ReservationResponse markPending(String requestId, PendingRequest request) {
        requireText(requestId, "requestId", 64);
        ReservationEntity reservation = requireReservation(requestId);
        ReservationStatus status = ReservationStatus.valueOf(reservation.getStatus());
        if (status == ReservationStatus.PENDING_RECONCILIATION) {
            return reservationResponse(reservation, requireBucket(reservation), true);
        }
        if (status != ReservationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_PENDING",
                    "当前预占状态不能转为待对账: " + status);
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "供应商是否计费暂时无法确认"
                : truncate(request.reason().trim(), 255);
        if (reservationMapper.markPending(reservation.getId(), reason) != 1) {
            throw new IllegalStateException("Reservation could not be marked pending");
        }
        return reservationResponse(
                reservationMapper.findByRequestIdForUpdate(requestId), requireBucket(reservation), false
        );
    }

    private ReservationResponse existingReservation(ReserveRequest request, BigDecimal maximumQuota) {
        ReservationEntity existing = reservationMapper.findByRequestIdForUpdate(request.requestId().trim());
        if (existing == null) {
            existing = reservationMapper.findByClientRequestForUpdate(request.userId(), request.clientRequestId().trim());
        }
        if (existing == null) {
            throw new IllegalStateException("Idempotent reservation disappeared");
        }
        if (!existing.getRequestId().equals(request.requestId().trim())
                || !existing.getClientRequestId().equals(request.clientRequestId().trim())
                || !existing.getUserId().equals(request.userId())
                || !existing.getModelCode().equals(request.modelCode().trim())
                || !existing.getPricingVersion().equals(request.pricingVersion().trim())
                || !existing.getPricingAt().equals(request.pricingAt())
                || existing.getQuotaMultiplier().compareTo(request.quotaMultiplier()) != 0
                || !java.util.Objects.equals(existing.getPricingRuleName(), trimToNull(request.pricingRuleName()))
                || existing.getRequestedQuota().compareTo(maximumQuota) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_IDEMPOTENCY_CONFLICT",
                    "相同幂等键对应了不同预占参数");
        }
        if (ReservationStatus.PROCESSING.name().equals(existing.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_PROCESSING", "预占请求仍在处理中，请稍后重试");
        }
        return reservationResponse(existing, requireBucket(existing), true);
    }

    private SettlementResponse existingSettlement(ReservationEntity reservation, SettleRequest request) {
        UsageRecordEntity usage = usageMapper.findByUsageIdForUpdate(request.usageId().trim());
        if (usage == null) {
            usage = usageMapper.findByReservationIdForUpdate(reservation.getId());
        }
        if (usage == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SETTLEMENT_STATE_INCOMPLETE", "结算状态不完整，需要人工对账");
        }
        if (!usageMatches(usage, reservation, request)) {
            throw new ApiException(HttpStatus.CONFLICT, "USAGE_IDEMPOTENCY_CONFLICT",
                    "相同用量标识对应了不同结算参数");
        }
        QuotaBucketEntity bucket = requireBucket(reservation);
        ReservationStatus reservationStatus = ReservationStatus.valueOf(reservation.getStatus());
        UsageStatus usageStatus = UsageStatus.valueOf(usage.getStatus());
        BigDecimal released = reservationStatus == ReservationStatus.SETTLED
                ? reservation.getRequestedQuota().subtract(usage.getBilledQuota())
                : BillingAmounts.zero();
        return new SettlementResponse(
                usage.getUsageId(), reservation.getRequestId(), reservationStatus, usageStatus,
                reservation.getRequestedQuota(), usage.getBilledQuota(), released, bucket.availableQuota()
        );
    }

    private boolean usageMatches(
            UsageRecordEntity usage,
            ReservationEntity reservation,
            SettleRequest request
    ) {
        return usage.getUsageId().equals(request.usageId().trim())
                && usage.getReservationId().equals(reservation.getId())
                && usage.getRequestId().equals(reservation.getRequestId())
                && usage.getPricingVersion().equals(request.pricingVersion().trim())
                && usage.getInputTokens() == request.inputTokens()
                && usage.getOutputTokens() == request.outputTokens()
                && usage.getReasoningTokens() == request.reasoningTokens()
                && usage.getCacheReadTokens() == request.cacheReadTokens()
                && usage.getCacheWriteTokens() == request.cacheWriteTokens()
                && usage.getBilledQuota().compareTo(
                        BillingAmounts.positive(request.billedQuota(), "billedQuota")
                ) == 0;
    }

    private ReservationResponse reservationResponse(
            ReservationEntity reservation,
            QuotaBucketEntity bucket,
            boolean idempotentReplay
    ) {
        return new ReservationResponse(
                reservation.getId(), reservation.getRequestId(), reservation.getUserId(), reservation.getModelCode(),
                reservation.getPricingVersion(),
                ReservationStatus.valueOf(reservation.getStatus()), reservation.getRequestedQuota(),
                reservation.getSettledQuota(), bucket.availableQuota(), reservation.getPricingAt(),
                reservation.getQuotaMultiplier(), reservation.getPricingRuleName(),
                reservation.getExpiresAt(), idempotentReplay
        );
    }

    private ReservationEntity requireReservation(String requestId) {
        ReservationEntity reservation = reservationMapper.findByRequestIdForUpdate(requestId.trim());
        if (reservation == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "预占记录不存在");
        }
        return reservation;
    }

    private QuotaBucketEntity requireBucket(ReservationEntity reservation) {
        if (reservation.getQuotaBucketId() == null) {
            throw new IllegalStateException("Reservation has no quota bucket");
        }
        QuotaBucketEntity bucket = bucketMapper.findByIdForUpdate(reservation.getQuotaBucketId());
        if (bucket == null) {
            throw new IllegalStateException("Reservation references a missing quota bucket");
        }
        return bucket;
    }

    private Instant reservationExpiry(Instant requested, Instant now) {
        Instant expiry = requested == null ? now.plus(DEFAULT_RESERVATION_TTL) : requested;
        if (!expiry.isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_EXPIRY", "预占过期时间必须晚于当前时间");
        }
        if (expiry.isAfter(now.plus(MAX_RESERVATION_TTL))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESERVATION_TTL_TOO_LONG", "预占最长不能超过 2 小时");
        }
        return expiry;
    }

    private void validateReserve(ReserveRequest request) {
        if (request == null || request.userId() == null || request.userId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "userId 不正确");
        }
        requireText(request.requestId(), "requestId", 64);
        requireText(request.clientRequestId(), "clientRequestId", 128);
        requireText(request.modelCode(), "modelCode", 128);
        requireText(request.pricingVersion(), "pricingVersion", 64);
        if (request.pricingAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICING_AT", "pricingAt 不能为空");
        }
        BillingAmounts.positive(request.quotaMultiplier(), "quotaMultiplier");
        if (request.pricingRuleName() != null && request.pricingRuleName().trim().length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICING_RULE_NAME",
                    "pricingRuleName 长度不能超过 80");
        }
    }

    private void validateSettle(SettleRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SETTLEMENT", "结算请求不能为空");
        }
        requireText(request.usageId(), "usageId", 64);
        requireText(request.pricingVersion(), "pricingVersion", 64);
        if (request.inputTokens() < 0 || request.outputTokens() < 0 || request.reasoningTokens() < 0
                || request.cacheReadTokens() < 0 || request.cacheWriteTokens() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN_USAGE", "Token 用量不能为负数");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_" + field.toUpperCase(),
                    field + " 不能为空且长度不能超过 " + maxLength);
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
