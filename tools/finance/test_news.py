import contextlib
from datetime import datetime, timedelta
import io
import json
import sys
import types
import unittest
from unittest.mock import patch

import news


class NewsTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 10, 24, 12, 0, tzinfo=news.SOURCE_ZONE)

    def row(self, title="英伟达消息", date="2026-10-24 10:00:00", url="https://finance.eastmoney.com/a/1.html"):
        return {"标题": title, "摘要": "<p>公开摘要 &amp; 内容</p>", "发布时间": date, "链接": url}

    def test_sort_deduplicate_and_keep_publication_separate_from_fetch(self):
        rows = [self.row(), self.row(), self.row("更新", "2026-10-24 11:00:00", "https://finance.eastmoney.com/a/2.html")]
        result = news.normalize(rows, "", 3, 1, self.now)
        self.assertEqual(3, result["scannedCount"])
        self.assertEqual(2, result["matchedCount"])
        self.assertEqual(1, len(result["articles"]))
        article = result["articles"][0]
        self.assertEqual("更新", article["title"])
        self.assertEqual("2026-10-24T11:00:00+08:00", article["publishedAt"])
        self.assertEqual("2026-10-24T12:00:00+08:00", article["retrievedAt"])
        self.assertEqual("公开摘要 & 内容", article["summary"])

    def test_old_future_invalid_time_and_unsafe_links_are_not_news(self):
        rows = [self.row(date="2026-10-01 10:00:00"), self.row(date="2026-10-25 10:00:00"),
                self.row(date="11:00:00"), self.row(date="NaT"), self.row(url="javascript:alert(1)"),
                self.row(url="https://user:password@example.test/news")]
        self.assertEqual([], news.normalize(rows, "", 3, 5, self.now)["articles"])

    def test_keyword_schema_and_exact_boundary(self):
        row = {"新闻标题": "<em>纳斯达克</em>", "新闻内容": "a" * 500, "文章来源": "媒体", "发布时间": "2026-10-21 12:00:00",
               "新闻链接": "http://finance.eastmoney.com/a/3.html"}
        result = news.normalize([row], "纳斯达克", 3, 5, self.now)
        self.assertEqual("纳斯达克", result["articles"][0]["title"])
        self.assertEqual(400, len(result["articles"][0]["summary"]))
        self.assertIn("有限近期结果", result["coverage"])

    def test_offset_dates_are_compared_in_source_timezone(self):
        row = self.row(date="2026-10-24T02:00:00Z")
        self.assertEqual("2026-10-24T10:00:00+08:00", news.normalize([row], "", 1, 1, self.now)["articles"][0]["publishedAt"])

    def test_empty_results_and_missing_dates_are_not_filled_with_now(self):
        self.assertEqual([], news.normalize([], "BTC", 3, 5, self.now)["articles"])
        self.assertEqual([], news.normalize([self.row(date="")], "", 3, 5, self.now)["articles"])

    def test_fetch_uses_fixed_akshare_functions_and_does_not_pollute_stdout(self):
        class Frame:
            columns = ["标题", "发布时间", "链接"]
            def head(self, count): return self
            def to_dict(self, orient): return []
        def noisy():
            print("upstream progress")
            return Frame()
        fake = types.SimpleNamespace(stock_info_global_em=noisy)
        stdout = io.StringIO()
        with patch.dict(sys.modules, {"akshare": fake}), contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(io.StringIO()):
            result = news.fetch("", 3, 5)
        self.assertEqual("", stdout.getvalue())
        self.assertEqual([], result["articles"])

    def test_source_schema_change_is_an_error_instead_of_empty_success(self):
        fake = types.SimpleNamespace(stock_info_global_em=lambda: types.SimpleNamespace(columns=["changed"]))
        with patch.dict(sys.modules, {"akshare": fake}):
            self.assertRaises(ValueError, news.fetch, "", 3, 5)

    def test_cli_errors_are_structured_and_do_not_leak_exception_details(self):
        for error, code in [(ImportError("private/path"), "DEPENDENCY_MISSING"), (RuntimeError("secret URL"), "UPSTREAM_UNAVAILABLE")]:
            output = io.StringIO()
            with patch.object(sys, "argv", ["news.py"]), patch.object(news, "fetch", side_effect=error), contextlib.redirect_stdout(output):
                self.assertEqual(1, news.main())
            result = json.loads(output.getvalue())
            self.assertEqual(code, result["error"])
            self.assertNotIn("secret", output.getvalue())


if __name__ == "__main__":
    unittest.main()
