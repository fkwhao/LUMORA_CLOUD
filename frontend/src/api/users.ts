import { apiFetch } from "./auth";

export interface AdminUser {
  id: number;
  email: string;
  displayName: string;
  status: "ACTIVE" | "DISABLED";
  roles: string[];
  activeSessions: number;
  createdAt: string;
}

export interface AdminUserPage {
  items: AdminUser[];
  nextCursor: number | null;
  hasMore: boolean;
}

export interface AdminRole {
  code: string;
  name: string;
}

export interface AdminUserSession {
  id: string;
  clientType: string;
  deviceId: string;
  deviceName?: string;
  ipAddress?: string;
  userAgent?: string;
  status: "ACTIVE" | "REVOKED" | "EXPIRED";
  expiresAt: string;
  lastSeenAt?: string;
  revokedAt?: string;
  createdAt: string;
}

export interface UserStatistics {
  totalUsers: number;
  activeUsers: number;
  disabledUsers: number;
  createdThisMonth: number;
  activeSessions: number;
  reportingZone: string;
  generatedAt: string;
}

export function searchAdminUsers(query = "", cursor?: number | null, limit = 20): Promise<AdminUserPage> {
  const parameters = new URLSearchParams({ query, limit: String(limit) });
  if (cursor != null) parameters.set("cursor", String(cursor));
  return apiFetch<AdminUserPage>(`/api/admin/users?${parameters.toString()}`);
}

export function getUserStatistics(): Promise<UserStatistics> {
  return apiFetch<UserStatistics>("/api/admin/users/statistics");
}

export function listAdminRoles(): Promise<AdminRole[]> {
  return apiFetch<AdminRole[]>("/api/admin/users/roles");
}

export function listAdminUserSessions(userId: number): Promise<AdminUserSession[]> {
  return apiFetch<AdminUserSession[]>(`/api/admin/users/${userId}/sessions`);
}

export function updateAdminUserRoles(userId: number, roles: string[]): Promise<AdminUser> {
  return apiFetch<AdminUser>(`/api/admin/users/${userId}/roles`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ roles }),
  });
}

export function updateAdminUserStatus(userId: number, status: AdminUser["status"]): Promise<AdminUser> {
  return apiFetch<AdminUser>(`/api/admin/users/${userId}/status`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
}

export function revokeAdminUserSession(userId: number, sessionId: string): Promise<AdminUserSession> {
  return apiFetch<AdminUserSession>(`/api/admin/users/${userId}/sessions/${sessionId}/revoke`, {
    method: "POST",
  });
}
