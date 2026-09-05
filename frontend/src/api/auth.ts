export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  status: string;
  roles: string[];
}

interface AuthResponse {
  tokenType: "Bearer";
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken?: string;
  sessionExpiresAt: string;
  user: UserProfile;
}

interface ApiErrorBody {
  code?: string;
  message?: string;
  traceId?: string;
}

export class ApiClientError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly traceId?: string,
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

let accessToken: string | null = null;
let sessionUserId: string | null = null;
let authGeneration = 0;
const invalidationListeners = new Set<() => void>();

export function onSessionInvalidated(listener: () => void): () => void {
  invalidationListeners.add(listener);
  return () => { invalidationListeners.delete(listener); };
}

function sessionChanged(): ApiClientError {
  return new ApiClientError(401, "SESSION_CHANGED", "登录身份已变化，请刷新页面确认账号后重新操作");
}

function invalidateSession(): void {
  accessToken = null;
  sessionUserId = null;
  authGeneration += 1;
  for (const listener of invalidationListeners) listener();
}
let refreshRequest: Promise<AuthResponse> | null = null;

function deviceId(): string {
  const storageKey = "lumora.web.device-id";
  const existing = window.localStorage.getItem(storageKey);
  if (existing) return existing;

  const value = crypto.randomUUID();
  window.localStorage.setItem(storageKey, value);
  return value;
}

async function readError(response: Response): Promise<ApiClientError> {
  let body: ApiErrorBody = {};
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    // Non-JSON failures are still represented as one consistent client error.
  }

  return new ApiClientError(
    response.status,
    body.code ?? "REQUEST_FAILED",
    body.message ?? "请求失败，请稍后重试",
    body.traceId,
  );
}

async function authRequest(path: string, init?: RequestInit, expectedUserId?: string | null): Promise<AuthResponse> {
  const generation = authGeneration;
  const response = await fetch(path, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  if (!response.ok) throw await readError(response);

  const auth = (await response.json()) as AuthResponse;
  if (generation !== authGeneration) throw sessionChanged();
  if (expectedUserId && auth.user.id !== expectedUserId) {
    invalidateSession();
    throw sessionChanged();
  }
  accessToken = auth.accessToken;
  sessionUserId = auth.user.id;
  return auth;
}

export async function login(email: string, password: string): Promise<UserProfile> {
  authGeneration += 1;
  accessToken = null;
  sessionUserId = null;
  const auth = await authRequest("/api/app/auth/login", {
    method: "POST",
    body: JSON.stringify({
      email,
      password,
      clientType: "WEB",
      deviceId: deviceId(),
      deviceName: `${navigator.platform || "Web"} Browser`,
    }),
  });
  return auth.user;
}

async function refresh(): Promise<AuthResponse> {
  if (!refreshRequest) {
    const expectedUserId = sessionUserId;
    const rotate = () => authRequest("/api/app/auth/refresh", {
      method: "POST",
      body: "{}",
    }, expectedUserId);
    // Refresh cookies are shared by browser tabs. Serialize rotation so two tabs
    // cannot reuse the same one-time token and revoke the whole session.
    refreshRequest = (navigator.locks
      ? navigator.locks.request("lumora.auth.refresh", rotate)
      : rotate()
    ).finally(() => {
        refreshRequest = null;
      });
  }
  return refreshRequest;
}

export async function restoreSession(): Promise<UserProfile | null> {
  try {
    return (await refresh()).user;
  } catch (error) {
    accessToken = null;
    sessionUserId = null;
    if (error instanceof ApiClientError && (error.status === 400 || error.status === 401)) {
      return null;
    }
    throw error;
  }
}

export async function logout(): Promise<void> {
  try {
    await fetch("/api/app/auth/logout", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: "{}",
    });
  } finally {
    invalidateSession();
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit, retry = true): Promise<T> {
  const requestUserId = sessionUserId;
  if (!requestUserId) throw new ApiClientError(401, "AUTHENTICATION_REQUIRED", "请先登录");
  const response = await fetch(path, {
    ...init,
    credentials: "include",
    headers: {
      ...init?.headers,
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
  });

  if (requestUserId !== sessionUserId) throw sessionChanged();
  if (response.status === 401 && retry) {
    try {
      await refresh();
    } catch (error) {
      if (requestUserId === sessionUserId) invalidateSession();
      throw error;
    }
    if (requestUserId !== sessionUserId) throw sessionChanged();
    return apiFetch<T>(path, init, false);
  }

  if (!response.ok) throw await readError(response);
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}
