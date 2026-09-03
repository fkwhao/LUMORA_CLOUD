import http.client
import json
import threading
import unittest

from mock_provider import MockProviderServer, scenario


class MockProviderScenarioTest(unittest.TestCase):

    def test_status_scenarios(self) -> None:
        self.assertEqual((401, 0.0), scenario("mock-401", 15.0))
        self.assertEqual((503, 0.0), scenario("mock-503", 15.0))

    def test_slow_and_timeout_scenarios(self) -> None:
        self.assertEqual((200, 1.25), scenario("mock-slow-1250", 15.0))
        self.assertEqual((200, 15.0), scenario("mock-timeout", 15.0))

    def test_unknown_model_is_successful(self) -> None:
        self.assertEqual((200, 0.0), scenario("mock-ok", 15.0))


class MockProviderHttpTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.server = MockProviderServer(("127.0.0.1", 0), 0.01, False)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base_url = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def test_openai_buffered_response_contains_authoritative_usage(self) -> None:
        status, body = self.post("/v1/chat/completions", "mock-ok")

        self.assertEqual(200, status)
        self.assertEqual(120, body["usage"]["prompt_tokens"])
        self.assertEqual(20, body["usage"]["completion_tokens"])

    def test_configured_error_status_is_returned(self) -> None:
        status, body = self.post("/v1/chat/completions", "mock-429")

        self.assertEqual(429, status)
        self.assertIn("429", body["error"]["message"])

    def post(self, path: str, model: str) -> tuple[int, dict]:
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=2)
        try:
            connection.request(
                "POST",
                path,
                body=json.dumps({"model": model, "stream": False}).encode("utf-8"),
                headers={"Content-Type": "application/json"},
            )
            response = connection.getresponse()
            return response.status, json.loads(response.read().decode("utf-8"))
        finally:
            connection.close()


if __name__ == "__main__":
    unittest.main()
