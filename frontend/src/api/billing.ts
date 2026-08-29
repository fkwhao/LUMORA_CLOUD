import { apiFetch } from "./auth";

export interface BillingPlan {
  planId: number;
  code: string;
  name: string;
  description?: string;
  planVersionId: number;
  versionNo: number;
  monthlyPriceMinor: number;
  currency: string;
  weeklyQuota: number;
}

export interface BillingSubscription {
  subscriptionId: string;
  userId: number;
  planVersionId: number;
  status: "ACTIVE" | "EXPIRED" | "CANCELED";
  source: "ADMIN_GRANT" | "PURCHASE";
  sourceReference?: string;
  startsAt: string;
  endsAt: string;
  createdAt: string;
}

export interface CreatePlanInput {
  code: string;
  name: string;
  description: string;
  monthlyPriceMinor: number;
  currency: string;
  weeklyQuota: number;
}

export interface CreatePlanVersionInput {
  monthlyPriceMinor: number;
  currency: string;
  weeklyQuota: number;
}

export interface GrantSubscriptionInput {
  userId: number;
  planVersionId: number;
  sourceReference: string;
  startsAt: string;
  endsAt: string;
}

export interface BillingQuota {
  bucketId: string;
  periodNo: number;
  startsAt: string;
  endsAt: string;
  granted: number;
  reserved: number;
  consumed: number;
  remaining: number;
}

export interface BillingOverview {
  hasActiveSubscription: boolean;
  plan?: BillingPlan;
  subscription?: BillingSubscription;
  quota?: BillingQuota;
}

export interface BillingLedgerEntry {
  id: string;
  entryType: "GRANT" | "RESERVE" | "SETTLE" | "RELEASE" | "ADJUSTMENT";
  referenceType: string;
  referenceId: string;
  grantedDelta: number;
  reservedDelta: number;
  consumedDelta: number;
  description?: string;
  createdAt: string;
}

export interface BillingUsage {
  usageId: string;
  requestId: string;
  modelCode: string;
  pricingVersion: string;
  inputTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  billedQuota: number;
  status: "PROCESSING" | "COMPLETED" | "PENDING_RECONCILIATION" | "FAILED";
  occurredAt: string;
}

export interface BillingHistory {
  ledger: BillingLedgerEntry[];
  usage: BillingUsage[];
}

export interface PurchaseOrder {
  orderNo: string;
  userId: number;
  planVersionId: number;
  planCode: string;
  planName: string;
  amountMinor: number;
  currency: string;
  status: "PENDING_PAYMENT" | "FULFILLED" | "CANCELED" | "EXPIRED";
  expiresAt: string;
  paidAt?: string;
  fulfilledAt?: string;
  subscriptionId?: string;
  mockPaymentEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentCapabilities {
  availableMethods: Array<"MOCK">;
}

export interface CurrencyRevenue {
  currency: string;
  amountMinor: number;
  orderCount: number;
}

export interface BillingStatistics {
  publishedPlans: number;
  activeSubscriptions: number;
  pendingOrders: number;
  fulfilledOrdersThisMonth: number;
  revenueThisMonth: CurrencyRevenue[];
  modelRequestsToday: number;
  completedModelRequestsToday: number;
  pendingReconciliationToday: number;
  billedQuotaToday: number;
  reportingZone: string;
  generatedAt: string;
}

export function listPublishedPlans(): Promise<BillingPlan[]> {
  return apiFetch<BillingPlan[]>("/api/app/billing/plans");
}

export function getBillingOverview(): Promise<BillingOverview> {
  return apiFetch<BillingOverview>("/api/app/billing/overview");
}

export function getBillingHistory(): Promise<BillingHistory> {
  return apiFetch<BillingHistory>("/api/app/billing/history");
}

export function getPaymentCapabilities(): Promise<PaymentCapabilities> {
  return apiFetch<PaymentCapabilities>("/api/app/billing/payment-capabilities");
}

export function listPurchaseOrders(): Promise<PurchaseOrder[]> {
  return apiFetch<PurchaseOrder[]>("/api/app/billing/orders");
}

export function getPurchaseOrder(orderNo: string): Promise<PurchaseOrder> {
  return apiFetch<PurchaseOrder>(`/api/app/billing/orders/${encodeURIComponent(orderNo)}`);
}

export function createPurchaseOrder(planVersionId: number, idempotencyKey: string): Promise<PurchaseOrder> {
  return apiFetch<PurchaseOrder>("/api/app/billing/orders", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ planVersionId }),
  });
}

export function completeMockPayment(orderNo: string): Promise<PurchaseOrder> {
  return apiFetch<PurchaseOrder>(`/api/app/billing/orders/${encodeURIComponent(orderNo)}/payments/mock`, {
    method: "POST",
  });
}

export function cancelPurchaseOrder(orderNo: string): Promise<PurchaseOrder> {
  return apiFetch<PurchaseOrder>(`/api/app/billing/orders/${encodeURIComponent(orderNo)}/cancel`, {
    method: "POST",
  });
}

export function listAdminPlans(): Promise<BillingPlan[]> {
  return apiFetch<BillingPlan[]>("/api/admin/billing/plans");
}

export function createBillingPlan(input: CreatePlanInput): Promise<BillingPlan> {
  return apiFetch<BillingPlan>("/api/admin/billing/plans", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function listPlanVersions(planId: number): Promise<BillingPlan[]> {
  return apiFetch<BillingPlan[]>(`/api/admin/billing/plans/${planId}/versions`);
}

export function publishPlanVersion(
  planId: number,
  input: CreatePlanVersionInput,
): Promise<BillingPlan> {
  return apiFetch<BillingPlan>(`/api/admin/billing/plans/${planId}/versions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function listAdminSubscriptions(userId?: number): Promise<BillingSubscription[]> {
  const query = userId ? `?userId=${encodeURIComponent(userId)}` : "";
  return apiFetch<BillingSubscription[]>(`/api/admin/billing/subscriptions${query}`);
}

export function grantSubscription(input: GrantSubscriptionInput): Promise<BillingSubscription> {
  return apiFetch<BillingSubscription>("/api/admin/billing/subscriptions/grant", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function listAdminOrders(): Promise<PurchaseOrder[]> {
  return apiFetch<PurchaseOrder[]>("/api/admin/billing/orders");
}

export function getBillingStatistics(): Promise<BillingStatistics> {
  return apiFetch<BillingStatistics>("/api/admin/billing/statistics");
}
