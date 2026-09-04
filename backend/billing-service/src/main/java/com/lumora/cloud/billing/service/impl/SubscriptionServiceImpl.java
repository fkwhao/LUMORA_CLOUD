package com.lumora.cloud.billing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.billing.domain.enums.SubscriptionSource;
import com.lumora.cloud.billing.config.PaymentProperties;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.domain.entity.account.BillingAccountEntity;
import com.lumora.cloud.billing.domain.entity.quota.QuotaBucketEntity;
import com.lumora.cloud.billing.domain.entity.subscription.SubscriptionEntity;
import com.lumora.cloud.billing.mapper.account.BillingAccountMapper;
import com.lumora.cloud.billing.mapper.subscription.SubscriptionMapper;
import com.lumora.cloud.billing.domain.vo.overview.BillingOverviewResponse;
import com.lumora.cloud.billing.domain.dto.subscription.GrantSubscriptionRequest;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import com.lumora.cloud.billing.domain.vo.quota.QuotaResponse;
import com.lumora.cloud.billing.domain.vo.subscription.SubscriptionResponse;
import com.lumora.cloud.billing.service.IBillingCatalogService;
import com.lumora.cloud.billing.service.ISubscriptionService;
import com.lumora.cloud.billing.support.QuotaBucketService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements ISubscriptionService {

    private final BillingAccountMapper accountMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final IBillingCatalogService catalogService;
    private final QuotaBucketService bucketService;
    private final PaymentProperties paymentProperties;

    @Transactional
    public SubscriptionResponse grant(GrantSubscriptionRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBSCRIPTION_TIME", "订阅结束时间必须晚于开始时间");
        }
        PlanResponse plan = catalogService.publishedVersion(request.planVersionId());
        SubscriptionEntity sameReference = subscriptionMapper.findBySourceReferenceForUpdate(
                SubscriptionSource.ADMIN_GRANT.name(), request.sourceReference().trim()
        );
        if (sameReference != null) {
            ensureSameGrant(sameReference, request);
            return response(sameReference);
        }

        accountMapper.ensureExists(request.userId());
        BillingAccountEntity account = accountMapper.findByUserIdForUpdate(request.userId());
        if (account == null) {
            throw new IllegalStateException("Billing account was not created");
        }
        SubscriptionEntity overlapping = subscriptionMapper.findOverlappingForUpdate(
                request.userId(), request.startsAt(), request.endsAt()
        );
        if (overlapping != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_SUBSCRIPTION_EXISTS", "该用户在此时间段已有有效套餐");
        }

        SubscriptionEntity subscription = SubscriptionEntity.grant(
                UUID.randomUUID().toString(), account.getId(), request.userId(), plan.planVersionId(),
                request.sourceReference().trim(), request.startsAt(), request.endsAt()
        );
        try {
            subscriptionMapper.insert(subscription);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBSCRIPTION_GRANT_CONFLICT", "套餐发放请求已经处理");
        }

        Instant now = Instant.now();
        if (!now.isBefore(subscription.getStartsAt()) && now.isBefore(subscription.getEndsAt())) {
            bucketService.currentForUpdate(subscription, now);
        }
        return response(subscriptionMapper.selectById(subscription.getId()));
    }

    @Transactional
    public BillingOverviewResponse overview(Long userId) {
        Instant now = Instant.now();
        SubscriptionEntity subscription = subscriptionMapper.findActiveForUpdate(userId, now);
        if (subscription == null) {
            return new BillingOverviewResponse(false, null, null, null);
        }
        PlanResponse plan = catalogService.publishedVersion(subscription.getPlanVersionId());
        QuotaBucketEntity bucket = bucketService.currentForUpdate(subscription, now);
        return new BillingOverviewResponse(true, plan, response(subscription), quota(bucket));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listRecent(Long userId) {
        var query = Wrappers.<SubscriptionEntity>lambdaQuery()
                .orderByDesc(SubscriptionEntity::getCreatedAt)
                .last("LIMIT 100");
        if (userId != null) {
            query.eq(SubscriptionEntity::getUserId, userId);
        }
        return subscriptionMapper.selectList(query).stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public SubscriptionResponse purchase(Long userId, Long planVersionId, String orderNo, Instant now) {
        accountMapper.ensureExists(userId);
        BillingAccountEntity account = accountMapper.findByUserIdForUpdate(userId);
        if (account == null) {
            throw new IllegalStateException("Billing account was not created");
        }

        SubscriptionEntity latest = subscriptionMapper.findLatestEndingForUpdate(userId, now);
        Instant startsAt = latest != null && latest.getEndsAt().isAfter(now) ? latest.getEndsAt() : now;
        Instant endsAt = startsAt.plus(paymentProperties.subscriptionDuration());
        SubscriptionEntity subscription = SubscriptionEntity.purchase(
                UUID.randomUUID().toString(), account.getId(), userId, planVersionId,
                orderNo, startsAt, endsAt
        );
        try {
            subscriptionMapper.insert(subscription);
        } catch (DuplicateKeyException exception) {
            SubscriptionEntity existing = subscriptionMapper.findBySourceReferenceForUpdate(
                    SubscriptionSource.PURCHASE.name(), orderNo
            );
            if (existing == null) {
                throw new ApiException(HttpStatus.CONFLICT, "PURCHASE_SUBSCRIPTION_CONFLICT",
                        "支付订单开通订阅时发生并发冲突");
            }
            ensureSamePurchase(existing, userId, planVersionId);
            return response(existing);
        }

        if (!now.isBefore(startsAt) && now.isBefore(endsAt)) {
            bucketService.currentForUpdate(subscription, now);
        }
        return response(subscriptionMapper.selectById(subscription.getId()));
    }

    @Override
    public SubscriptionEntity activeForUpdate(Long userId, Instant now) {
        SubscriptionEntity subscription = subscriptionMapper.findActiveForUpdate(userId, now);
        if (subscription == null) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "NO_ACTIVE_SUBSCRIPTION", "当前没有可用套餐");
        }
        return subscription;
    }

    QuotaResponse quota(QuotaBucketEntity bucket) {
        return new QuotaResponse(
                bucket.getId(), bucket.getPeriodNo(), bucket.getStartsAt(), bucket.getEndsAt(),
                bucket.getGrantedQuota(), bucket.getReservedQuota(), bucket.getConsumedQuota(), bucket.availableQuota()
        );
    }

    private SubscriptionResponse response(SubscriptionEntity subscription) {
        return new SubscriptionResponse(
                subscription.getId(), subscription.getUserId(), subscription.getPlanVersionId(),
                subscription.getStatus(), subscription.getSource(), subscription.getSourceReference(),
                subscription.getStartsAt(), subscription.getEndsAt(), subscription.getCreatedAt()
        );
    }

    private void ensureSameGrant(SubscriptionEntity existing, GrantSubscriptionRequest request) {
        if (!existing.getUserId().equals(request.userId())
                || !existing.getPlanVersionId().equals(request.planVersionId())
                || !existing.getStartsAt().equals(request.startsAt())
                || !existing.getEndsAt().equals(request.endsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "GRANT_IDEMPOTENCY_CONFLICT",
                    "相同发放引用对应了不同请求参数");
        }
    }

    private void ensureSamePurchase(SubscriptionEntity existing, Long userId, Long planVersionId) {
        if (!existing.getUserId().equals(userId) || !existing.getPlanVersionId().equals(planVersionId)) {
            throw new ApiException(HttpStatus.CONFLICT, "PURCHASE_REFERENCE_CONFLICT",
                    "支付订单已经对应其他订阅");
        }
    }
}
