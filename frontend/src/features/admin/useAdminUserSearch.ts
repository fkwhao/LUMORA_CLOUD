import { useCallback, useEffect, useRef, useState } from "react";

import { ApiClientError } from "../../api/auth";
import { searchAdminUsers, type AdminUser } from "../../api/users";

export const ADMIN_USER_SEARCH_MIN_LENGTH = 2;
export const ADMIN_USER_SEARCH_DEBOUNCE_MS = 300;

export function useAdminUserSearch(pageSize = 20) {
  const [query, setQuery] = useState("");
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestSequence = useRef(0);
  const debounceTimer = useRef<number | null>(null);

  const normalizedQuery = query.trim();
  const queryIsValid = normalizedQuery.length === 0
    || normalizedQuery.length >= ADMIN_USER_SEARCH_MIN_LENGTH;

  const runSearch = useCallback(async (targetQuery: string, cursor: number | null, append: boolean) => {
    const requestId = ++requestSequence.current;
    if (append) setLoadingMore(true); else setLoading(true);
    setError(null);
    try {
      const page = await searchAdminUsers(targetQuery, cursor, pageSize);
      if (requestId !== requestSequence.current) return;
      setUsers((current) => append ? mergeUsers(current, page.items) : page.items);
      setNextCursor(page.nextCursor);
      setHasMore(page.hasMore);
    } catch (reason) {
      if (requestId !== requestSequence.current) return;
      setError(message(reason));
      if (!append) {
        setUsers([]);
        setNextCursor(null);
        setHasMore(false);
      }
    } finally {
      if (requestId === requestSequence.current) {
        if (append) setLoadingMore(false); else setLoading(false);
      }
    }
  }, [pageSize]);

  useEffect(() => {
    requestSequence.current += 1;
    setNextCursor(null);
    setHasMore(false);
    setLoadingMore(false);
    setError(null);
    if (!queryIsValid) {
      setUsers([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    debounceTimer.current = window.setTimeout(
      () => void runSearch(normalizedQuery, null, false),
      ADMIN_USER_SEARCH_DEBOUNCE_MS,
    );
    return () => {
      if (debounceTimer.current != null) window.clearTimeout(debounceTimer.current);
    };
  }, [normalizedQuery, queryIsValid, runSearch]);

  const reload = useCallback(() => {
    if (!queryIsValid) return Promise.resolve();
    if (debounceTimer.current != null) window.clearTimeout(debounceTimer.current);
    return runSearch(normalizedQuery, null, false);
  }, [normalizedQuery, queryIsValid, runSearch]);

  const loadMore = useCallback(() => {
    if (!queryIsValid || !hasMore || nextCursor == null || loading || loadingMore) return Promise.resolve();
    return runSearch(normalizedQuery, nextCursor, true);
  }, [hasMore, loading, loadingMore, nextCursor, normalizedQuery, queryIsValid, runSearch]);

  const updateUser = useCallback((updated: AdminUser) => {
    setUsers((current) => current.map((user) => user.id === updated.id ? updated : user));
  }, []);

  return {
    query,
    setQuery,
    users,
    loading,
    loadingMore,
    error,
    hasMore,
    queryIsValid,
    reload,
    loadMore,
    updateUser,
  };
}

function mergeUsers(current: AdminUser[], incoming: AdminUser[]) {
  const knownIds = new Set(current.map((user) => user.id));
  return [...current, ...incoming.filter((user) => !knownIds.has(user.id))];
}

function message(reason: unknown) {
  return reason instanceof ApiClientError ? reason.message : "无法连接云端服务，请稍后重试";
}
