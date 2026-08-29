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

async function authRequest(path: string, init?: RequestInit): Promise<AuthResponse> {
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
  accessToken = auth.accessToken;
  return auth;
}

export async function login(email: string, password: string): Promise<UserProfile> {
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
    const rotate = () => authRequest("/api/app/auth/refresh", {
      method: "POST",
      body: "{}",
    });
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
    accessToken = null;
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit, retry = true): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: "include",
    headers: {
      ...init?.headers,
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
  });

  if (response.status === 401 && retry) {
    try {
      await refresh();
      return apiFetch<T>(path, init, false);
    } catch {
      accessToken = null;
    }
  }

  if (!response.ok) throw await readError(response);
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}
