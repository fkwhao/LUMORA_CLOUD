import { apiFetch } from "./auth";

export interface SettlementEvidence {
  usageId: string;
  pricingVersion: string;
  inputTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  billedQuota: number;
  occurredAt: string;
}
export interface ReconciliationCase {
  reservation: {
    requestId: string; userId: number; modelCode: string; pricingVersion: string;
    requestedQuota: number; settledQuota?: number; status: string; failureReason?: string;
    holdReleased: boolean; expiresAt: string; createdAt: string;
    reconciliationAttempts: number; reconciliationCheckedAt?: string;
    reconciliationNextAt?: string; reconciliationNote?: string;
  };
  usage?: SettlementEvidence & { status: string };
  history: Array<{ id: string; createdAt: string; description: string; referenceType: string }>;
}
export interface ReconciliationPage { items: ReconciliationCase[]; total: number; page: number; pageSize: number }
export interface BatchResult { requestId: string; completed: boolean; status?: string; code?: string; message: string }
export type Resolution = { action: "SETTLE" | "RELEASE"; reason: string; settlement?: SettlementEvidence };
const base = "/api/admin/billing/reconciliation";
export function listReconciliation(status: string, requestId: string, userId: string, page: number) {
  const params = new URLSearchParams({ status, page: String(page), pageSize: "20" });
  if (requestId.trim()) params.set("requestId", requestId.trim());
  if (userId.trim()) params.set("userId", userId.trim());
  return apiFetch<ReconciliationPage>(`${base}?${params}`);
}
export function getReconciliation(id: string) { return apiFetch<ReconciliationCase>(`${base}/${encodeURIComponent(id)}`); }
export function resolveReconciliation(id: string, body: Resolution) {
  return apiFetch<ReconciliationCase>(`${base}/${encodeURIComponent(id)}`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
}
export function batchReconciliation(requestIds: string[], action: Resolution["action"], reason: string) {
  return apiFetch<BatchResult[]>(`${base}/batch`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ requestIds, action, reason }),
  });
}
