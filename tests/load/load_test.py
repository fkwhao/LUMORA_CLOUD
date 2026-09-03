#!/usr/bin/env python3
"""Dependency-free buffered invocation load test for Lumora Cloud."""

from __future__ import annotations

import argparse
import getpass
import http.client
import json
import math
import os
import statistics
import sys
import time
import urllib.parse
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from decimal import Decimal
from typing import Any


@dataclass(frozen=True)
class HttpResult:
    status: int
    elapsed_ms: float
    route_id: str
    provider_code: str
    trace_id: str
    error_code: str


def request_json(
    base_url: str,
    path: str,
    method: str = "GET",
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 30.0,
) -> tuple[int, dict[str, str], dict[str, Any]]:
    encoded = None if body is None else json.dumps(body, separators=(",", ":")).encode("utf-8")
    request_headers = {"Accept": "application/json", **(headers or {})}
    if encoded is not None:
        request_headers["Content-Type"] = "application/json"
    parsed = urllib.parse.urlsplit(base_url.rstrip("/") + path)
    if parsed.scheme not in {"http", "https"} or parsed.hostname is None:
        raise ValueError("base URL must use http or https")
    connection_type = (
        http.client.HTTPSConnection if parsed.scheme == "https" else http.client.HTTPConnection
    )
    connection = connection_type(parsed.hostname, parsed.port, timeout=timeout)
    target = parsed.path or "/"
    if parsed.query:
        target += "?" + parsed.query
    try:
        connection.request(method, target, body=encoded, headers=request_headers)
        response = connection.getresponse()
        raw = response.read()
        return response.status, dict(response.getheaders()), decode_json(raw)
    finally:
        connection.close()


def decode_json(raw: bytes) -> dict[str, Any]:
    if not raw:
        return {}
    try:
        value = json.loads(raw.decode("utf-8"))
        return value if isinstance(value, dict) else {"value": value}
    except (UnicodeDecodeError, json.JSONDecodeError):
        return {"raw": raw[:512].decode("utf-8", errors="replace")}


def login(args: argparse.Namespace) -> str:
    email = args.email or input("Lumora test account email: ").strip()
    password = args.password or getpass.getpass("Lumora test account password: ")
    status, _, body = request_json(
        args.base_url,
        "/api/app/auth/login",
        method="POST",
        body={
            "email": email,
            "password": password,
            "clientType": "WEB",
            "deviceId": f"load-{uuid.uuid4().hex[:24]}",
            "deviceName": "Local load test",
        },
        timeout=args.timeout,
    )
    token = body.get("accessToken")
    if status != 200 or not isinstance(token, str) or not token:
        raise RuntimeError(f"login failed: HTTP {status}, code={body.get('code', 'UNKNOWN')}")
    return token


def invoke_once(args: argparse.Namespace, token: str, run_id: str, index: int) -> HttpResult:
    started = time.perf_counter()
    status, headers, body = request_json(
        args.base_url,
        "/api/app/model/v1/invoke",
        method="POST",
        body={
            "protocolVersion": "1",
            "model": args.model,
            "stream": False,
            "messages": [{
                "role": "user",
                "content": [{"type": "text", "text": f"Load test {run_id} request {index}: reply OK"}],
            }],
            "tools": [],
            "generation": {"maxOutputTokens": args.max_output_tokens},
        },
        headers={
            "Authorization": f"Bearer {token}",
            "X-Lumora-Client-Request-Id": f"load:{run_id}:{index:06d}",
        },
        timeout=args.timeout,
    )
    elapsed_ms = (time.perf_counter() - started) * 1000
    lowered = {key.lower(): value for key, value in headers.items()}
    return HttpResult(
        status=status,
        elapsed_ms=elapsed_ms,
        route_id=lowered.get("x-lumora-route-id", "unresolved"),
        provider_code=lowered.get("x-lumora-provider-code", "unresolved"),
        trace_id=lowered.get("x-lumora-request-id", str(body.get("traceId") or "")),
        error_code=str(body.get("code") or ""),
    )


def billing_state(args: argparse.Namespace, token: str) -> dict[str, Any]:
    headers = {"Authorization": f"Bearer {token}"}
    overview_status, _, overview = request_json(
        args.base_url, "/api/app/billing/overview", headers=headers, timeout=args.timeout
    )
    history_status, _, history = request_json(
        args.base_url, "/api/app/billing/history", headers=headers, timeout=args.timeout
    )
    if overview_status != 200 or history_status != 200:
        raise RuntimeError(
            f"billing snapshot failed: overview={overview_status}, history={history_status}"
        )
    return {"overview": overview, "history": history}


def decimal(value: object) -> Decimal:
    return Decimal(str(value or 0))


def quota(snapshot: dict[str, Any], field: str) -> Decimal:
    quota_snapshot = snapshot["overview"].get("quota")
    if not isinstance(quota_snapshot, dict):
        raise RuntimeError("test account has no active quota bucket")
    return decimal(quota_snapshot.get(field))


def ids(snapshot: dict[str, Any], collection: str, key: str) -> set[str]:
    return {
        str(item.get(key))
        for item in snapshot["history"].get(collection, [])
        if item.get(key) is not None
    }


def consistency_report(
    before: dict[str, Any],
    after: dict[str, Any],
    successful_requests: int,
) -> dict[str, Any]:
    old_usage_ids = ids(before, "usage", "usageId")
    old_ledger_ids = ids(before, "ledger", "id")
    new_usage = [
        item for item in after["history"].get("usage", [])
        if str(item.get("usageId")) not in old_usage_ids
    ]
    new_ledger = [
        item for item in after["history"].get("ledger", [])
        if str(item.get("id")) not in old_ledger_ids
    ]
    usage_charge = sum((decimal(item.get("billedQuota")) for item in new_usage), Decimal("0"))
    settlement_charge = sum(
        (decimal(item.get("consumedDelta")) for item in new_ledger if item.get("entryType") == "SETTLE"),
        Decimal("0"),
    )
    bucket_consumed_delta = quota(after, "consumed") - quota(before, "consumed")
    reserved_delta = quota(after, "reserved") - quota(before, "reserved")
    checks = {
        "successfulRequestsMatchUsage": len(new_usage) == successful_requests,
        "usageMatchesSettlementLedger": usage_charge == settlement_charge,
        "usageMatchesQuotaBucket": usage_charge == bucket_consumed_delta,
        "noReservationLeak": reserved_delta == Decimal("0"),
    }
    return {
        "passed": all(checks.values()),
        "checks": checks,
        "newUsageRecords": len(new_usage),
        "newLedgerRecords": len(new_ledger),
        "usageBilledQuota": str(usage_charge),
        "settlementConsumedDelta": str(settlement_charge),
        "bucketConsumedDelta": str(bucket_consumed_delta),
        "bucketReservedDelta": str(reserved_delta),
    }


def percentile(values: list[float], quantile: float) -> float:
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * quantile) - 1)
    return ordered[index]


def parse_statuses(value: str) -> set[int]:
    try:
        statuses = {int(item.strip()) for item in value.split(",") if item.strip()}
    except ValueError as error:
        raise argparse.ArgumentTypeError("statuses must be comma-separated integers") from error
    if not statuses:
        raise argparse.ArgumentTypeError("at least one expected status is required")
    return statuses


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Load test Lumora Cloud against a local mock model")
    parser.add_argument("--base-url", default=os.getenv("LUMORA_LOAD_BASE_URL", "http://127.0.0.1:46100"))
    parser.add_argument("--email", default=os.getenv("LUMORA_LOAD_EMAIL"))
    parser.add_argument("--password", default=os.getenv("LUMORA_LOAD_PASSWORD"))
    parser.add_argument("--model", default="lumora-load-mock")
    parser.add_argument("--requests", type=int, default=30)
    parser.add_argument("--concurrency", type=int, default=3)
    parser.add_argument("--max-output-tokens", type=int, default=32)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--expected-statuses", type=parse_statuses, default={200})
    parser.add_argument("--skip-billing-check", action="store_true")
    parser.add_argument("--allow-non-mock-model", action="store_true")
    args = parser.parse_args()
    if args.requests < 1 or args.requests > 10_000:
        parser.error("--requests must be between 1 and 10000")
    if args.concurrency < 1 or args.concurrency > 1_000:
        parser.error("--concurrency must be between 1 and 1000")
    if args.max_output_tokens < 1:
        parser.error("--max-output-tokens must be positive")
    if not args.skip_billing_check and args.requests > 40:
        parser.error(
            "billing history only exposes the latest 100 rows; use at most 40 requests "
            "or pass --skip-billing-check"
        )
    if "mock" not in args.model.lower() and not args.allow_non_mock_model:
        parser.error("refusing to load test a non-mock model; pass --allow-non-mock-model explicitly")
    return args


def main() -> int:
    args = parse_args()
    token = login(args)
    before = None if args.skip_billing_check else billing_state(args, token)
    run_id = f"{int(time.time())}-{uuid.uuid4().hex[:8]}"
    started = time.perf_counter()
    results: list[HttpResult] = []
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(invoke_once, args, token, run_id, index) for index in range(args.requests)]
        for future in as_completed(futures):
            results.append(future.result())
    wall_seconds = time.perf_counter() - started

    latencies = [result.elapsed_ms for result in results]
    status_counts = Counter(result.status for result in results)
    successful = status_counts.get(200, 0)
    unexpected = sum(count for status, count in status_counts.items() if status not in args.expected_statuses)
    summary: dict[str, Any] = {
        "runId": run_id,
        "model": args.model,
        "requests": len(results),
        "concurrency": args.concurrency,
        "wallSeconds": round(wall_seconds, 3),
        "throughputRps": round(len(results) / wall_seconds, 2),
        "latencyMs": {
            "min": round(min(latencies), 2),
            "mean": round(statistics.fmean(latencies), 2),
            "p50": round(percentile(latencies, 0.50), 2),
            "p95": round(percentile(latencies, 0.95), 2),
            "p99": round(percentile(latencies, 0.99), 2),
            "max": round(max(latencies), 2),
        },
        "statuses": dict(sorted(status_counts.items())),
        "errorCodes": dict(sorted(Counter(result.error_code for result in results if result.error_code).items())),
        "routes": dict(sorted(Counter(result.route_id for result in results).items())),
        "providers": dict(sorted(Counter(result.provider_code for result in results).items())),
    }

    consistency_ok = True
    if before is not None:
        after = billing_state(args, token)
        for _ in range(20):
            if quota(after, "reserved") == quota(before, "reserved"):
                break
            time.sleep(0.25)
            after = billing_state(args, token)
        consistency = consistency_report(before, after, successful)
        consistency_ok = bool(consistency["passed"])
        summary["billingConsistency"] = consistency

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if unexpected == 0 and consistency_ok else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError) as error:
        print(f"load test failed: {error}", file=sys.stderr)
        raise SystemExit(2) from error
