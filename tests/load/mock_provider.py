#!/usr/bin/env python3
"""Dependency-free local model provider used by Lumora Cloud load tests."""

from __future__ import annotations

import argparse
import json
import re
import threading
import time
import uuid
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


SUPPORTED_PATHS = ("/chat/completions", "/messages", "/responses")


def scenario(model: str, timeout_delay: float) -> tuple[int, float]:
    normalized = model.strip().lower()
    status_match = re.fullmatch(r"mock-(400|401|403|408|429|500|503)", normalized)
    if status_match:
        return int(status_match.group(1)), 0.0
    slow_match = re.fullmatch(r"mock-slow-(\d{1,6})", normalized)
    if slow_match:
        return 200, int(slow_match.group(1)) / 1000.0
    if normalized == "mock-timeout":
        return 200, timeout_delay
    return 200, 0.0


class ProviderState:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._counts: Counter[str] = Counter()

    def record(self, model: str) -> None:
        with self._lock:
            self._counts[model] += 1

    def snapshot(self) -> dict[str, int]:
        with self._lock:
            return dict(self._counts)

    def reset(self) -> None:
        with self._lock:
            self._counts.clear()


class MockProviderServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address: tuple[str, int], timeout_delay: float, verbose: bool) -> None:
        super().__init__(address, MockProviderHandler)
        self.state = ProviderState()
        self.timeout_delay = timeout_delay
        self.verbose = verbose


class MockProviderHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server: MockProviderServer

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            self._json(200, {"status": "UP"})
            return
        if self.path == "/stats":
            self._json(200, {"requestsByModel": self.server.state.snapshot()})
            return
        self._json(404, {"error": {"message": "not found"}})

    def do_POST(self) -> None:  # noqa: N802
        if self.path == "/reset":
            self.server.state.reset()
            self._json(200, {"reset": True})
            return
        if not self.path.endswith(SUPPORTED_PATHS):
            self._json(404, {"error": {"message": "unsupported provider endpoint"}})
            return

        try:
            payload = self._request_json()
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError):
            self._json(400, {"error": {"message": "invalid JSON"}})
            return

        model = str(payload.get("model") or "mock-ok")
        self.server.state.record(model)
        status, delay = scenario(model, self.server.timeout_delay)
        if delay > 0:
            time.sleep(delay)
        if status != 200:
            self._json(status, {"error": {"message": f"mock upstream HTTP {status}"}})
            return

        if bool(payload.get("stream")):
            self._stream(model)
        else:
            self._buffered(model)

    def log_message(self, format_string: str, *args: object) -> None:
        if self.server.verbose:
            super().log_message(format_string, *args)

    def _request_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > 16 * 1024 * 1024:
            raise ValueError("invalid body length")
        value = json.loads(self.rfile.read(length).decode("utf-8"))
        if not isinstance(value, dict):
            raise ValueError("body must be an object")
        return value

    def _buffered(self, model: str) -> None:
        if self.path.endswith("/messages"):
            body = {
                "id": f"msg_{uuid.uuid4().hex}",
                "type": "message",
                "role": "assistant",
                "model": model,
                "content": [{"type": "text", "text": "OK"}],
                "stop_reason": "end_turn",
                "usage": {"input_tokens": 120, "output_tokens": 20},
            }
        elif self.path.endswith("/responses"):
            body = {
                "id": f"resp_{uuid.uuid4().hex}",
                "object": "response",
                "status": "completed",
                "model": model,
                "output": [{
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "output_text", "text": "OK", "annotations": []}],
                }],
                "usage": {"input_tokens": 120, "output_tokens": 20, "total_tokens": 140},
            }
        else:
            body = {
                "id": f"chatcmpl_{uuid.uuid4().hex}",
                "object": "chat.completion",
                "model": model,
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": "OK"},
                    "finish_reason": "stop",
                }],
                "usage": {"prompt_tokens": 120, "completion_tokens": 20, "total_tokens": 140},
            }
        self._json(200, body)

    def _stream(self, model: str) -> None:
        if self.path.endswith("/messages"):
            events = [
                {"type": "message_start", "message": {"model": model, "usage": {"input_tokens": 120}}},
                {"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}},
                {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": "OK"}},
                {"type": "content_block_stop", "index": 0},
                {"type": "message_delta", "delta": {"stop_reason": "end_turn"}, "usage": {"output_tokens": 20}},
                {"type": "message_stop"},
            ]
        elif self.path.endswith("/responses"):
            response_id = f"resp_{uuid.uuid4().hex}"
            events = [
                {"type": "response.created", "response": {"id": response_id, "model": model}},
                {"type": "response.output_text.delta", "delta": "OK"},
                {
                    "type": "response.completed",
                    "response": {
                        "id": response_id,
                        "model": model,
                        "usage": {"input_tokens": 120, "output_tokens": 20, "total_tokens": 140},
                    },
                },
            ]
        else:
            response_id = f"chatcmpl_{uuid.uuid4().hex}"
            events = [
                {
                    "id": response_id,
                    "object": "chat.completion.chunk",
                    "model": model,
                    "choices": [{"index": 0, "delta": {"content": "OK"}, "finish_reason": None}],
                },
                {
                    "id": response_id,
                    "object": "chat.completion.chunk",
                    "model": model,
                    "choices": [],
                    "usage": {"prompt_tokens": 120, "completion_tokens": 20, "total_tokens": 140},
                },
            ]

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.send_header("x-request-id", f"mock-{uuid.uuid4()}")
        self.end_headers()
        try:
            for event in events:
                self.wfile.write(f"data: {json.dumps(event, separators=(',', ':'))}\n\n".encode("utf-8"))
                self.wfile.flush()
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass
        self.close_connection = True

    def _json(self, status: int, body: dict[str, Any]) -> None:
        encoded = json.dumps(body, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("x-request-id", f"mock-{uuid.uuid4()}")
        self.end_headers()
        try:
            self.wfile.write(encoded)
        except (BrokenPipeError, ConnectionResetError):
            pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the local Lumora model-provider mock")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=19090)
    parser.add_argument("--timeout-delay", type=float, default=15.0)
    parser.add_argument("--verbose", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    server = MockProviderServer((args.host, args.port), args.timeout_delay, args.verbose)
    print(f"Lumora mock provider listening on http://{args.host}:{args.port}/v1", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
