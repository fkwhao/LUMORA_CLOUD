package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.domain.dto.subscription.GrantSubscriptionRequest;
import com.lumora.cloud.billing.domain.entity.subscription.SubscriptionEntity;
import com.lumora.cloud.billing.domain.vo.overview.BillingOverviewResponse;
import com.lumora.cloud.billing.domain.vo.subscription.SubscriptionResponse;

import java.time.Instant;
import java.util.List;

public interface ISubscriptionService {

    SubscriptionResponse grant(GrantSubscriptionRequest request);

    BillingOverviewResponse overview(Long userId);

    List<SubscriptionResponse> listRecent(Long userId);

    SubscriptionResponse purchase(Long userId, Long planVersionId, String orderNo, Instant now);

    SubscriptionEntity activeForUpdate(Long userId, Instant now);
}
