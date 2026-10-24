import contextlib
from datetime import datetime, timezone
import io
import json
import os
import sys
import types
import unittest
from unittest.mock import patch

import web_search


class WebSearchTests(unittest.TestCase):
    def test_normalize_deduplicates_and_does_not_invent_publication_time(self):
        row = {"title": "<b>Company announcement</b>", "body": "<p>Public &amp; information</p>",
               "href": "https://example.com/release"}
        now = datetime(2026, 10, 25, tzinfo=timezone.utc)
        result = web_search.normalize([row, row], "company", "week", 5, now)
        self.assertEqual(1, len(result["sources"]))
        source = result["sources"][0]
        self.assertEqual("Company announcement", source["title"])
        self.assertEqual("Public & information", source["snippet"])
        self.assertIsNone(source["publishedAt"])
        self.assertEqual("2026-10-25T00:00:00+00:00", source["retrievedAt"])
        self.assertEqual("example.com", source["siteName"])
        self.assertRegex(source["id"], r"^web-[a-f0-9]{16}$")

    def test_unsafe_links_are_not_exposed(self):
        for url in ("javascript:alert(1)", "file:///etc/passwd", "https://user:pass@example.com",
                    "http://localhost/x", "http://127.0.0.1", "http://10.1.2.3", "http://[::1]", "http://service.internal"):
            self.assertEqual("", web_search.public_url(url), url)

    def test_limit_and_snippet_cap(self):
        rows = [{"title": "x" * 300, "body": "x" * 2000, "href": f"https://example.com/{i}"} for i in range(10)]
        sources = web_search.normalize(rows, "test", "any", 2)["sources"]
        self.assertEqual(2, len(sources))
        self.assertEqual(180, len(sources[0]["title"]))
        self.assertEqual(800, len(sources[0]["snippet"]))

    def test_search_uses_only_query_and_bounded_options(self):
        observed = {}

        class FakeClient:
            def __init__(self, **kwargs): observed["client"] = kwargs
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def text(self, query, **kwargs):
                observed["query"] = query
                observed["options"] = kwargs
                print("engine progress")
                return []

        stdout = io.StringIO()
        with patch.dict(sys.modules, {"ddgs": types.SimpleNamespace(DDGS=FakeClient)}), \
                contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(io.StringIO()):
            web_search.search("public company", "week", 3)
        self.assertEqual("", stdout.getvalue())
        self.assertEqual("public company", observed["query"])
        self.assertEqual(3, observed["options"]["max_results"])
        self.assertEqual("w", observed["options"]["timelimit"])

    def test_disabled_by_default_without_any_search_call(self):
        output = io.StringIO()
        with patch.dict(os.environ, {}, clear=True), patch.object(sys, "argv", ["web_search.py", "--query=test"]), \
                patch.object(web_search, "search") as search, contextlib.redirect_stdout(output):
            self.assertEqual(1, web_search.main())
            search.assert_not_called()
        self.assertEqual("DISABLED", json.loads(output.getvalue())["error"])

    def test_invalid_arguments_never_search(self):
        for args in (["--query=", "--limit=5"], ["--query=test", "--limit=9"], ["--query=test", "--time-range=custom"]):
            with patch.dict(os.environ, {"WEALTHFLOW_WEB_SEARCH_ENABLED": "true"}), \
                    patch.object(sys, "argv", ["web_search.py", *args]), patch.object(web_search, "search") as search, \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, web_search.main())
                search.assert_not_called()

    def test_errors_do_not_leak_exception_details(self):
        for error, code in ((ImportError("private path"), "DEPENDENCY_MISSING"), (RuntimeError("secret URL"), "SEARCH_UNAVAILABLE")):
            output = io.StringIO()
            with patch.dict(os.environ, {"WEALTHFLOW_WEB_SEARCH_ENABLED": "true"}), \
                    patch.object(sys, "argv", ["web_search.py", "--query=test"]), \
                    patch.object(web_search, "search", side_effect=error), contextlib.redirect_stdout(output):
                self.assertEqual(1, web_search.main())
            self.assertEqual(code, json.loads(output.getvalue())["error"])
            self.assertNotIn("secret", output.getvalue())


if __name__ == "__main__":
    unittest.main()
