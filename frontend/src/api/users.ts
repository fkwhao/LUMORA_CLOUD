import { apiFetch } from "./auth";

export interface AdminUser {
  id: number;
  email: string;
  displayName: string;
  status: "ACTIVE" | "DISABLED";
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

export function searchAdminUsers(query = ""): Promise<AdminUser[]> {
  return apiFetch<AdminUser[]>(`/api/admin/users?query=${encodeURIComponent(query)}`);
}

export function getUserStatistics(): Promise<UserStatistics> {
  return apiFetch<UserStatistics>("/api/admin/users/statistics");
}
