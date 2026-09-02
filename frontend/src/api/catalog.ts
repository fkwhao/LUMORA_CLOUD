import { apiFetch } from "./auth";

export interface ProviderCredentialStatus {
  storageType: "ENCRYPTED_DATABASE" | "ENVIRONMENT_REFERENCE";
  managed: boolean;
  maskedValue: string;
  fingerprint?: string;
  revision: number;
  rotatedAt?: string;
}

export interface ModelProvider {
  id: number;
  code: string;
  name: string;
  protocolType: string;
  baseUrl: string;
  maxConcurrency?: number | null;
  requestsPerMinute?: number | null;
  tokensPerMinute?: number | null;
  credential: ProviderCredentialStatus;
  status: "ACTIVE" | "DISABLED";
  revision: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProviderInput {
  code: string;
  name: string;
  protocolType: string;
  baseUrl: string;
  apiKey: string;
  maxConcurrency?: number;
  requestsPerMinute?: number;
  tokensPerMinute?: number;
}

export interface UpdateProviderInput {
  name: string;
  protocolType: string;
  baseUrl: string;
  maxConcurrency?: number;
  requestsPerMinute?: number;
  tokensPerMinute?: number;
  status: ModelProvider["status"];
}

export interface ModelCapabilities {
  contextWindow: number;
  maxOutputTokens: number;
  reasoning: boolean;
  tools: boolean;
  vision: boolean;
  json: boolean;
  webSearch: boolean;
}

export interface PublicModel {
  code: string;
  displayName: string;
  description?: string;
  pricingVersion: string;
  providerCode: string;
  capabilities: ModelCapabilities;
  publishedAt: string;
}

export interface ModelRates {
  uncachedInputPerMillion: number;
  cachedInputPerMillion: number;
  cacheCreationInputPerMillion: number;
  outputPerMillion: number;
}

export interface ModelQuotaRates extends ModelRates {
  minimumRequestQuota: number;
}

export type PricingDay = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export interface CostTimePricingRule {
  name: string;
  daysOfWeek: PricingDay[];
  startTime: string;
  endTime: string;
  costRates: ModelRates;
}

export interface CostTimePricingPolicy {
  zoneId: string;
  rules: CostTimePricingRule[];
}

export interface QuotaTimePricingRule {
  name: string;
  daysOfWeek: PricingDay[];
  startTime: string;
  endTime: string;
  quotaMultiplier: number;
}

export interface QuotaTimePricingPolicy {
  zoneId: string;
  defaultQuotaMultiplier: number;
  rules: QuotaTimePricingRule[];
}

export interface ModelVersion {
  id: string;
  pricingVersion: string;
  versionNo: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  revision: number;
  providerId: number;
  displayName: string;
  description?: string;
  upstreamModel: string;
  protocolType: string;
  baseUrl: string;
  credentialReference: string;
  capabilities: ModelCapabilities;
  costCurrency: string;
  costRates: ModelRates;
  costTimePricingPolicy?: CostTimePricingPolicy | null;
  quotaRates: ModelQuotaRates;
  quotaTimePricingPolicy?: QuotaTimePricingPolicy | null;
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
  routes: ModelRoute[];
}

export interface ModelRoute {
  id: string;
  routeName: string;
  providerId: number;
  providerCode: string;
  providerName: string;
  protocolType: string;
  baseUrl: string;
  upstreamModel: string;
  priority: number;
  weight: number;
  maxConcurrency?: number | null;
  requestsPerMinute?: number | null;
  tokensPerMinute?: number | null;
  accountMaxConcurrency?: number | null;
  accountRequestsPerMinute?: number | null;
  accountTokensPerMinute?: number | null;
  failoverEnabled: boolean;
  circuitBreakerEnabled: boolean;
  status: "ACTIVE" | "DISABLED";
  primary: boolean;
  costCurrency: string;
  costRates: ModelRates;
  costTimePricingPolicy?: CostTimePricingPolicy | null;
  revision: number;
  createdAt: string;
  updatedAt: string;
}

export interface ModelRouteInput {
  routeName: string;
  providerId: number;
  upstreamModel: string;
  priority: number;
  weight: number;
  maxConcurrency?: number;
  requestsPerMinute?: number;
  tokensPerMinute?: number;
  failoverEnabled: boolean;
  circuitBreakerEnabled: boolean;
  status: ModelRoute["status"];
  costCurrency: string;
  uncachedInputCostPerMillion: number;
  cachedInputCostPerMillion: number;
  cacheCreationInputCostPerMillion?: number;
  outputCostPerMillion: number;
  costTimePricingPolicy?: CostTimePricingPolicy;
}

export interface AdminModel {
  modelId: number;
  code: string;
  status: "ACTIVE" | "DISABLED";
  revision: number;
  draft?: ModelVersion;
  published?: ModelVersion;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogStatistics {
  totalProviders: number;
  activeProviders: number;
  totalModels: number;
  publicModels: number;
  draftModels: number;
  totalVersions: number;
  generatedAt: string;
}

export interface ModelVersionInput {
  displayName: string;
  description: string;
  upstreamModel: string;
  contextWindow: number;
  maxOutputTokens: number;
  supportsReasoning: boolean;
  supportsTools: boolean;
  supportsVision: boolean;
  supportsJson: boolean;
  supportsWebSearch: boolean;
  costCurrency: string;
  uncachedInputCostPerMillion: number;
  cachedInputCostPerMillion: number;
  cacheCreationInputCostPerMillion?: number;
  outputCostPerMillion: number;
  costTimePricingPolicy?: CostTimePricingPolicy;
  uncachedInputQuotaPerMillion: number;
  cachedInputQuotaPerMillion: number;
  cacheCreationInputQuotaPerMillion?: number;
  outputQuotaPerMillion: number;
  minimumRequestQuota: number;
  quotaTimePricingPolicy?: QuotaTimePricingPolicy;
}

export interface CreateModelInput {
  code: string;
  providerId: number;
  version: ModelVersionInput;
}

export function listProviders(): Promise<ModelProvider[]> {
  return apiFetch<ModelProvider[]>("/api/admin/catalog/providers");
}

export function getCatalogStatistics(): Promise<CatalogStatistics> {
  return apiFetch<CatalogStatistics>("/api/admin/catalog/statistics");
}

export function createProvider(input: CreateProviderInput): Promise<ModelProvider> {
  return apiFetch<ModelProvider>("/api/admin/catalog/providers", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function updateProvider(provider: ModelProvider, input: UpdateProviderInput): Promise<ModelProvider> {
  return apiFetch<ModelProvider>(`/api/admin/catalog/providers/${provider.id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: provider.revision, ...input }),
  });
}

export function rotateProviderCredential(provider: ModelProvider, apiKey: string): Promise<ModelProvider> {
  return apiFetch<ModelProvider>(`/api/admin/catalog/providers/${provider.id}/credential`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: provider.revision, apiKey }),
  });
}

export function listModels(): Promise<AdminModel[]> {
  return apiFetch<AdminModel[]>("/api/admin/catalog/models");
}

export function listPublicModels(): Promise<PublicModel[]> {
  return apiFetch<PublicModel[]>("/api/app/catalog/models");
}

export function listModelVersions(modelId: number): Promise<ModelVersion[]> {
  return apiFetch<ModelVersion[]>(`/api/admin/catalog/models/${modelId}/versions`);
}

export function createModel(input: CreateModelInput): Promise<AdminModel> {
  return apiFetch<AdminModel>("/api/admin/catalog/models", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function createModelDraft(modelId: number): Promise<AdminModel> {
  return apiFetch<AdminModel>(`/api/admin/catalog/models/${modelId}/drafts`, {
    method: "POST",
  });
}

export function updateModelDraft(
  model: AdminModel,
  providerId: number,
  version: ModelVersionInput,
): Promise<AdminModel> {
  if (!model.draft) throw new Error("模型草稿不存在");
  return apiFetch<AdminModel>(`/api/admin/catalog/models/${model.modelId}/draft`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: model.draft.revision, providerId, version }),
  });
}

export function discardModelDraft(model: AdminModel): Promise<void> {
  if (!model.draft) throw new Error("模型草稿不存在");
  const revision = encodeURIComponent(String(model.draft.revision));
  return apiFetch<void>(`/api/admin/catalog/models/${model.modelId}/draft?expectedRevision=${revision}`, {
    method: "DELETE",
  });
}

export function publishModelDraft(model: AdminModel): Promise<AdminModel> {
  if (!model.draft) throw new Error("模型草稿不存在");
  return apiFetch<AdminModel>(`/api/admin/catalog/models/${model.modelId}/draft/publish`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: model.draft.revision }),
  });
}

export function updateModelStatus(
  model: AdminModel,
  status: AdminModel["status"],
): Promise<AdminModel> {
  return apiFetch<AdminModel>(`/api/admin/catalog/models/${model.modelId}/status`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: model.revision, status }),
  });
}

export function createModelRoute(modelId: number, route: ModelRouteInput): Promise<ModelRoute> {
  return apiFetch<ModelRoute>(`/api/admin/catalog/models/${modelId}/draft/routes`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ route }),
  });
}

export function updateModelRoute(modelId: number, current: ModelRoute, route: ModelRouteInput): Promise<ModelRoute> {
  return apiFetch<ModelRoute>(`/api/admin/catalog/models/${modelId}/draft/routes/${current.id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: current.revision, route }),
  });
}

export function deleteModelRoute(modelId: number, route: ModelRoute): Promise<void> {
  return apiFetch<void>(
    `/api/admin/catalog/models/${modelId}/draft/routes/${route.id}?expectedRevision=${route.revision}`,
    { method: "DELETE" },
  );
}
