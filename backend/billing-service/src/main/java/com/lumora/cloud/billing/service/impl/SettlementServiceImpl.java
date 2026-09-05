package com.lumora.cloud.billing.service.impl;

import com.lumora.cloud.api.billing.BillingContracts.PendingRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReservationStatus;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettlementResponse;
import com.lumora.cloud.api.billing.BillingContracts.UsageStatus;
import com.lumora.cloud.billing.domain.enums.LedgerEntryType;
import com.lumora.cloud.billing.domain.dto.reconciliation.ReconciliationRequest;
import com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationCase;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.domain.entity.quota.QuotaBucketEntity;
import com.lumora.cloud.billing.domain.entity.quota.QuotaLedgerEntity;
import com.lumora.cloud.billing.domain.entity.quota.ReservationEntity;
import com.lumora.cloud.billing.domain.entity.subscription.SubscriptionEntity;
import com.lumora.cloud.billing.domain.entity.usage.UsageRecordEntity;
import com.lumora.cloud.billing.mapper.quota.QuotaBucketMapper;
import com.lumora.cloud.billing.mapper.quota.ReservationMapper;
import com.lumora.cloud.billing.mapper.usage.UsageDailySummaryMapper;
import com.lumora.cloud.billing.mapper.usage.UsageRecordMapper;
import com.lumora.cloud.billing.service.ISettlementService;
import com.lumora.cloud.billing.service.ISubscriptionService;
import com.lumora.cloud.billing.support.PlanModelAccessService;
import com.lumora.cloud.billing.support.QuotaBucketService;
import com.lumora.cloud.billing.support.QuotaLedgerWriter;
import com.lumora.cloud.billing.utils.BillingAmounts;
import com.lumora.cloud.billing.utils.BillingReportingPeriods;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements ISettlementService {

    private static final Duration DEFAULT_RESERVATION_TTL = Duration.ofMinutes(15);
    private static final Duration MAX_RESERVATION_TTL = Duration.ofHours(2);

    private final ISubscriptionService subscriptionService;
    private final QuotaBucketService bucketService;
    private final QuotaBucketMapper bucketMapper;
    private final ReservationMapper reservationMapper;
    private final UsageRecordMapper usageMapper;
    private final UsageDailySummaryMapper usageDailySummaryMapper;
    private final QuotaLedgerWriter ledgerWriter;
    private final PlanModelAccessService planModelAccess;

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
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_QUOTA",
                    "本次请求需预占 " + maximumQuota.toPlainString() + " 额度，可用 "
                            + bucket.availableQuota().toPlainString() + "；可缩短输入、降低最大输出或选择低费率模型");
        }
        // The quota period selects the bucket; the request TTL governs in-flight work.
        Instant effectiveExpiry = requestedExpiry;
        if (!effectiveExpiry.isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "QUOTA_PERIOD_ENDING", "当前额度周期已经结束");
        }
        if (reservationMapper.activate(processing.getId(), bucket.getId(), effectiveExpiry) != 1) {
            throw new IllegalStateException("Reservation could not be activated");
        }
        ledgerWriter.append(QuotaLedgerEntity.create(
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
        return settleLocked(requestId, request, false);
    }

    private SettlementResponse settleLocked(String requestId, SettleRequest request, boolean approveOverage) {
        requireText(requestId, "requestId", 64);
        validateSettle(request);
        BigDecimal billedQuota = BillingAmounts.nonNegative(request.billedQuota(), "billedQuota");
        ReservationEntity reservation = requireReservation(requestId);
        ReservationStatus currentStatus = ReservationStatus.valueOf(reservation.getStatus());
        if (currentStatus == ReservationStatus.SETTLED) {
            return existingSettlement(reservation, request);
        }
        if (currentStatus != ReservationStatus.ACTIVE && currentStatus != ReservationStatus.PENDING_RECONCILIATION) {
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
            if (currentStatus != ReservationStatus.PENDING_RECONCILIATION
                    || !UsageStatus.PENDING_RECONCILIATION.name().equals(persistedUsage.getStatus())
                    || !usageMatches(persistedUsage, reservation, request)) {
                return existingSettlement(reservation, request);
            }
        }
        usage = persistedUsage;
        boolean alreadySummarized = UsageStatus.PENDING_RECONCILIATION.name().equals(usage.getStatus());
        QuotaBucketEntity bucket = requireBucket(reservation);

        BigDecimal held = heldQuota(reservation);
        boolean needsReview = !approveOverage && billedQuota.compareTo(reservation.getRequestedQuota()) > 0;
        int settled = needsReview ? 0 : approveOverage || reservation.isHoldReleased()
                ? bucketMapper.settleReconciled(bucket.getId(), held, billedQuota)
                : bucketMapper.settle(bucket.getId(), held, billedQuota);
        if (settled != 1) {
            String reason = needsReview ? "实际费用超过预占，已保存用量等待核对" : "原周期可用额度不足，已保存用量等待核对";
            usageMapper.markPending(usage.getId());
            reservationMapper.markPending(reservation.getId(), reason);
            if (!alreadySummarized) addUsageSummary(usage, UsageStatus.PENDING_RECONCILIATION);
            return new SettlementResponse(
                    usage.getUsageId(), reservation.getRequestId(),
                    ReservationStatus.PENDING_RECONCILIATION, UsageStatus.PENDING_RECONCILIATION,
                    reservation.getRequestedQuota(), billedQuota, BillingAmounts.zero(), bucket.availableQuota()
            );
        }
        Instant settledAt = Instant.now();
        if (reservationMapper.markSettled(reservation.getId(), billedQuota, settledAt) != 1
                || usageMapper.markCompleted(usage.getId()) != 1) {
            throw new IllegalStateException("Settlement state could not be completed");
        }
        if (alreadySummarized) {
            if (usageDailySummaryMapper.resolvePending(usage, "COMPLETED",
                    BillingReportingPeriods.localDate(usage.getOccurredAt())) != 1) {
                throw new IllegalStateException("Pending usage summary could not be completed");
            }
        } else {
            addUsageSummary(usage, UsageStatus.COMPLETED);
        }
        ledgerWriter.append(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), reservation.getUserId(), bucket.getId(), reservation.getId(),
                LedgerEntryType.SETTLE.name(), "USAGE", usage.getUsageId(),
                BillingAmounts.zero(), held.negate(), billedQuota,
                "结算模型 " + reservation.getModelCode() + " 的权威用量"
        ));
        QuotaBucketEntity updated = bucketMapper.findByIdForUpdate(bucket.getId());
        return new SettlementResponse(
                usage.getUsageId(), reservation.getRequestId(), ReservationStatus.SETTLED, UsageStatus.COMPLETED,
                reservation.getRequestedQuota(), billedQuota,
                reservation.getRequestedQuota().subtract(billedQuota).max(BillingAmounts.zero()), updated.availableQuota()
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
        if (status != ReservationStatus.ACTIVE
                && !(status == ReservationStatus.PENDING_RECONCILIATION && reservation.isHoldReleased()
                && usageMapper.findByReservationIdForUpdate(reservation.getId()) == null)) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_RELEASABLE",
                    "当前预占状态不能释放: " + status);
        }
        QuotaBucketEntity bucket = requireBucket(reservation);
        if (bucketMapper.release(bucket.getId(), heldQuota(reservation)) != 1) {
            throw new IllegalStateException("Quota bucket could not release reservation");
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "模型调用未产生费用"
                : truncate(request.reason().trim(), 255);
        if (reservationMapper.markReleased(reservation.getId(), reason, Instant.now()) != 1) {
            throw new IllegalStateException("Reservation could not be released");
        }
        ledgerWriter.append(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), reservation.getUserId(), bucket.getId(), reservation.getId(),
                LedgerEntryType.RELEASE.name(), reservation.isHoldReleased() ? "RECONCILIATION_RELEASE" : "MODEL_REQUEST", reservation.getRequestId(),
                BillingAmounts.zero(), heldQuota(reservation).negate(), BillingAmounts.zero(), reason
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

    @Override
    @Transactional(readOnly = true)
    public ReconciliationCase reconciliation(String requestId) {
        requireText(requestId, "requestId", 64);
        ReservationEntity row = reservationMapper.findByRequestId(requestId.trim());
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "预占记录不存在");
        return new ReconciliationCase(row, usageMapper.findByReservationId(row.getId()), ledgerWriter.history(row.getId()));
    }

    @Override
    @Transactional
    public ReconciliationCase reconcile(Long actorUserId, String requestId, ReconciliationRequest request) {
        requireText(requestId, "requestId", 64);
        if (actorUserId == null || actorUserId <= 0 || request == null || request.action() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECONCILIATION", "对账操作无效");
        }
        requireText(request.reason(), "reason", 160);
        if (request.action() == ReconciliationRequest.Action.RELEASE && request.settlement() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECONCILIATION", "释放操作不能同时提交结算");
        }
        ReservationEntity row = requireReservation(requestId);
        ReservationStatus status = ReservationStatus.valueOf(row.getStatus());
        if (status == ReservationStatus.SETTLED && request.action() == ReconciliationRequest.Action.SETTLE) {
            validateSettle(request.settlement());
            existingSettlement(row, request.settlement());
            return reconciliation(requestId);
        }
        if (status == ReservationStatus.RELEASED && request.action() == ReconciliationRequest.Action.RELEASE) {
            return reconciliation(requestId);
        }
        if (status != ReservationStatus.PENDING_RECONCILIATION) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_PENDING", "仅待对账记录可以人工处理");
        }
        UsageRecordEntity prior = usageMapper.findByReservationIdForUpdate(row.getId());
        BigDecimal priorQuota = prior == null ? BillingAmounts.zero() : prior.getBilledQuota();
        if (request.action() == ReconciliationRequest.Action.SETTLE) {
            SettlementResponse settled = settleLocked(requestId, request.settlement(), true);
            if (settled.reservationStatus() != ReservationStatus.SETTLED
                    || settled.usageStatus() != UsageStatus.COMPLETED) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_RECONCILIATION_QUOTA",
                        "原周期额度不足或用量状态异常，记录保留待对账");
            }
        } else {
            if (prior != null && !UsageStatus.PENDING_RECONCILIATION.name().equals(prior.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "SETTLEMENT_STATE_INCOMPLETE", "已有用量状态异常，请先核实记录");
            }
            QuotaBucketEntity bucket = requireBucket(row);
            String reason = "管理员 #" + actorUserId + " 核实释放：" + request.reason().trim();
            if (bucketMapper.release(bucket.getId(), heldQuota(row)) != 1
                    || reservationMapper.markReleased(row.getId(), reason, Instant.now()) != 1) {
                throw new IllegalStateException("Pending reservation could not be released");
            }
            if (prior != null) {
                if (usageDailySummaryMapper.resolvePending(prior, "FAILED",
                        BillingReportingPeriods.localDate(prior.getOccurredAt())) != 1
                        || usageMapper.markWaived(prior.getId()) != 1) {
                    throw new IllegalStateException("Pending usage could not be closed");
                }
            }
            ledgerWriter.append(QuotaLedgerEntity.create(
                    UUID.randomUUID().toString(), row.getUserId(), bucket.getId(), row.getId(),
                    LedgerEntryType.RELEASE.name(), row.isHoldReleased() ? "RECONCILIATION_RELEASE" : "MODEL_REQUEST", row.getRequestId(),
                    BillingAmounts.zero(), heldQuota(row).negate(), BillingAmounts.zero(), reason
            ));
        }
        // One immutable audit entry per reservation, in the same transaction as the terminal state.
        ledgerWriter.append(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), row.getUserId(), row.getQuotaBucketId(), row.getId(),
                LedgerEntryType.ADJUSTMENT.name(), "RECONCILIATION", row.getRequestId(),
                BillingAmounts.zero(), BillingAmounts.zero(), BillingAmounts.zero(),
                "管理员 #" + actorUserId + " " + request.action() + "；原记录费用="
                        + priorQuota.toPlainString() + "；依据：" + request.reason().trim()
        ));
        return reconciliation(requestId);
    }

    @Override
    @Transactional
    public void expire(String requestId) {
        ReservationEntity row = requireReservation(requestId);
        if (row.isHoldReleased() || row.getExpiresAt().isAfter(Instant.now())
                || !(row.getStatus().equals("ACTIVE") || row.getStatus().equals("PENDING_RECONCILIATION"))) return;
        QuotaBucketEntity bucket = requireBucket(row);
        if (bucketMapper.release(bucket.getId(), heldQuota(row)) != 1
                || reservationMapper.expireHold(row.getId()) != 1) {
            throw new IllegalStateException("Expired hold could not be returned");
        }
        ledgerWriter.append(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), row.getUserId(), bucket.getId(), row.getId(),
                LedgerEntryType.RELEASE.name(), "RESERVATION_EXPIRY", row.getRequestId(),
                BillingAmounts.zero(), heldQuota(row).negate(), BillingAmounts.zero(),
                "预占超时返还额度；保留请求和用量证据，等待核对"
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationPage listReconciliation(
            String status, String requestId, Long userId, int page, int pageSize) {
        if (status != null && !java.util.Set.of("PENDING_RECONCILIATION", "SETTLED", "RELEASED").contains(status))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "不支持的对账状态");
        if (page < 1 || pageSize < 1 || pageSize > 100 || userId != null && userId <= 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "分页或用户参数无效");
        String query = trimToNull(requestId);
        if (query != null) requireText(query, "requestId", 64);
        var rows = reservationMapper.listReconciliation(status, query, userId, (long) (page - 1) * pageSize, pageSize);
        return new com.lumora.cloud.billing.domain.vo.reconciliation.ReconciliationPage(
                rows.stream().map(row -> new ReconciliationCase(row, usageMapper.findByReservationId(row.getId()))).toList(),
                reservationMapper.countReconciliation(status, query, userId), page, pageSize);
    }

    @Override
    @Transactional
    public ReconciliationCase autoReconcile(String requestId) {
        ReservationEntity row = requireReservation(requestId);
        if (!row.getStatus().equals("PENDING_RECONCILIATION")) return reconciliation(requestId);
        UsageRecordEntity usage = usageMapper.findByReservationIdForUpdate(row.getId());
        Instant now = Instant.now();
        String note = "缺少可靠用量，等待供应商依据或管理员核对";
        if (usage != null && UsageStatus.PENDING_RECONCILIATION.name().equals(usage.getStatus())) {
            SettleRequest request = new SettleRequest(usage.getUsageId(), usage.getPricingVersion(),
                    usage.getInputTokens(), usage.getOutputTokens(), usage.getReasoningTokens(),
                    usage.getCacheReadTokens(), usage.getCacheWriteTokens(), usage.getBilledQuota(), usage.getOccurredAt());
            // Only use stored authoritative usage. Never derive a charge from the reservation.
            var result = settleLocked(requestId, request, true);
            if (result.reservationStatus() == ReservationStatus.SETTLED) {
                reservationMapper.recordReconciliationSuccess(row.getId(), now);
                ledgerWriter.append(QuotaLedgerEntity.create(UUID.randomUUID().toString(),
                        row.getUserId(), row.getQuotaBucketId(), row.getId(),
                        LedgerEntryType.ADJUSTMENT.name(), "RECONCILIATION", row.getRequestId(),
                        BillingAmounts.zero(), BillingAmounts.zero(), BillingAmounts.zero(),
                        "系统自动结算；依据：已记录用量 " + usage.getUsageId()
                                + "；费用=" + usage.getBilledQuota().toPlainString()));
                return reconciliation(requestId);
            }
            note = "原周期额度不足，等待管理员核对；其他请求预占保持受保护";
        } else if (usage != null) {
            note = "用量状态异常，需管理员核对";
        }
        reservationMapper.recordReconciliationAttempt(row.getId(), now, now.plusSeconds(300), note);
        return reconciliation(requestId);
    }

    @Override
    @Transactional
    public void recordReconciliationFailure(String requestId, String reason) {
        ReservationEntity row = requireReservation(requestId);
        Instant now = Instant.now();
        reservationMapper.recordReconciliationAttempt(row.getId(), now, now.plusSeconds(300),
                truncate(reason == null ? "自动核对失败" : reason, 255));
    }

    private BigDecimal heldQuota(ReservationEntity row) {
        return row.isHoldReleased() ? BillingAmounts.zero() : row.getRequestedQuota();
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
                ? reservation.getRequestedQuota().subtract(usage.getBilledQuota()).max(BillingAmounts.zero())
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
                        BillingAmounts.nonNegative(request.billedQuota(), "billedQuota")
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

    private void addUsageSummary(UsageRecordEntity usage, UsageStatus status) {
        if (usageDailySummaryMapper.addUsage(
                usage,
                status.name(),
                BillingReportingPeriods.localDate(usage.getOccurredAt())
        ) <= 0) {
            throw new IllegalStateException("Usage daily summary could not be updated");
        }
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
