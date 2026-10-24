"""AKShare news adapter. One JSON response on stdout; no trading or account access."""
from __future__ import annotations

import argparse
import contextlib
from datetime import datetime, timedelta, timezone
import hashlib
from html import unescape
from html.parser import HTMLParser
import json
import sys
from urllib.parse import urlsplit

SOURCE_ZONE = timezone(timedelta(hours=8))
COVERAGE = "仅覆盖东方财富接口本次返回的有限近期结果；关键词接口可能按相关性返回。并非完整历史新闻或全网检索，无结果不代表没有相关事件。"


class PlainText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []

    def handle_data(self, data):
        self.parts.append(data)


def clean(value, maximum):
    if value is None:
        return ""
    text = str(value)
    if text.lower() in {"nan", "nat", "none"}:
        return ""
    parser = PlainText()
    parser.feed(text)
    return " ".join(unescape(" ".join(parser.parts)).split())[:maximum]


def source_url(value):
    text = str(value or "").strip()
    try:
        parsed = urlsplit(text)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
            return ""
        if len(text) > 2048 or any(ord(c) < 32 for c in text):
            return ""
        return text
    except ValueError:
        return ""


def publication_time(value):
    # Eastmoney publishes naive China-local timestamps. Never substitute retrieval time.
    text = str(value or "").strip()
    try:
        result = datetime.fromisoformat(text.replace("Z", "+00:00"))
        if result.tzinfo is None:
            result = result.replace(tzinfo=SOURCE_ZONE)
        return result.astimezone(SOURCE_ZONE)
    except (ValueError, TypeError):
        return None


def normalize(rows, query, days, limit, now=None):
    now = now or datetime.now(SOURCE_ZONE)
    now = now.astimezone(SOURCE_ZONE)
    cutoff = now - timedelta(days=days)
    articles = []
    seen = set()
    skipped = 0
    for row in rows:
        title = clean(row.get("新闻标题", row.get("标题")), 180)
        url = source_url(row.get("新闻链接", row.get("链接")))
        published = publication_time(row.get("发布时间"))
        if not title or not url or published is None:
            skipped += 1
            continue
        if published < cutoff or published > now:
            continue
        if url in seen:
            continue
        seen.add(url)
        articles.append({
            "id": hashlib.sha256(url.encode("utf-8")).hexdigest()[:16],
            "title": title,
            "summary": clean(row.get("新闻内容", row.get("摘要")), 400),
            "publisher": clean(row.get("文章来源"), 100) or "东方财富",
            "publishedAt": published.isoformat(timespec="seconds"),
            "retrievedAt": now.isoformat(timespec="seconds"),
            "url": url,
        })
    articles.sort(key=lambda item: item["publishedAt"], reverse=True)
    return {
        "provider": "AKShare / 东方财富", "query": query, "days": days,
        "retrievedAt": now.isoformat(timespec="seconds"), "timeZone": "Asia/Shanghai",
        "coverage": COVERAGE, "scannedCount": len(rows), "matchedCount": len(articles),
        "skippedInvalidCount": skipped, "articles": articles[:limit],
    }


def fetch(query, days, limit):
    # AKShare and its upstream clients can print progress. Keep the JSON channel clean.
    with contextlib.redirect_stdout(sys.stderr):
        import akshare as ak
        frame = ak.stock_news_em(symbol=query) if query else ak.stock_info_global_em()
        required = {"新闻标题", "发布时间", "新闻链接"} if query else {"标题", "发布时间", "链接"}
        if not required.issubset(frame.columns):
            raise ValueError("upstream schema changed")
        rows = frame.head(200).to_dict(orient="records")
    return normalize(rows, query, days, limit)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--query", default="")
    parser.add_argument("--days", type=int, default=3)
    parser.add_argument("--limit", type=int, default=5)
    args = parser.parse_args()
    query = args.query.strip()
    if len(query) > 80 or any(ord(c) < 32 for c in query) or not 1 <= args.days <= 30 or not 1 <= args.limit <= 10:
        print(json.dumps({"error": "INVALID_ARGUMENTS", "message": "查询参数无效"}, ensure_ascii=False))
        return 2
    try:
        result = fetch(query, args.days, args.limit)
    except ImportError:
        result = {"error": "DEPENDENCY_MISSING", "message": "财经查询依赖未安装或无法加载，请安装 tools/finance/requirements.txt 并配置Python路径"}
    except Exception:
        result = {"error": "UPSTREAM_UNAVAILABLE", "message": "财经数据源暂时不可用或返回格式发生变化，请稍后重试"}
    print(json.dumps(result, ensure_ascii=False, allow_nan=False))
    return 0 if "error" not in result else 1


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
