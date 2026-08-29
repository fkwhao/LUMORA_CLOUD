package com.lumora.cloud.billing.domain;

public final class BillingTypes {

    private BillingTypes() {
    }

    public enum PlanStatus { ACTIVE, INACTIVE, ARCHIVED }
    public enum PlanVersionStatus { DRAFT, PUBLISHED, RETIRED }
    public enum SubscriptionStatus { ACTIVE, EXPIRED, CANCELED }
    public enum SubscriptionSource { ADMIN_GRANT, PURCHASE }
    public enum LedgerEntryType { GRANT, RESERVE, SETTLE, RELEASE, ADJUSTMENT }
    public enum PurchaseOrderStatus { PENDING_PAYMENT, FULFILLED, CANCELED, EXPIRED }
    public enum PaymentAttemptStatus { SUCCEEDED, FAILED }
    public enum PaymentProvider { MOCK }
}
