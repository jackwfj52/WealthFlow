"""Opt-in public web search. Returns snippets and links, not page bodies."""
from __future__ import annotations

import argparse
import contextlib
from datetime import datetime, timezone
import hashlib
from html import unescape
from html.parser import HTMLParser
import ipaddress
import json
import os
import sys
from urllib.parse import urlsplit

TIME_RANGES = {"any": None, "day": "d", "week": "w", "month": "m", "year": "y"}
COVERAGE = "仅为公开搜索引擎返回的有限标题、链接和摘要，未读取网页全文；时间筛选不保证页面发布日期，不能视为实时行情或完整全网检索。"


class PlainText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []

    def handle_data(self, data):
        self.parts.append(data)


def clean(value, limit):
    parser = PlainText()
    parser.feed(str(value or ""))
    return " ".join(unescape(" ".join(parser.parts)).split())[:limit]


def public_url(value):
    value = str(value or "").strip()
    try:
        url = urlsplit(value)
        host = (url.hostname or "").rstrip(".").lower()
        if (url.scheme not in {"http", "https"} or not host or url.username or url.password
                or len(value) > 2048 or any(c.isspace() or ord(c) < 32 for c in value)
                or host == "localhost" or host.endswith((".localhost", ".local", ".internal"))):
            return ""
        try:
            if not ipaddress.ip_address(host).is_global:
                return ""
        except ValueError:
            if "." not in host or host.replace(".", "").isdigit():
                return ""
        return value
    except ValueError:
        return ""


def normalize(rows, query, time_range, limit, now=None):
    now = (now or datetime.now(timezone.utc)).isoformat(timespec="seconds")
    sources, seen = [], set()
    for row in rows:
        url = public_url(row.get("href", row.get("url")))
        title = clean(row.get("title"), 180)
        if not url or not title or url in seen:
            continue
        seen.add(url)
        sources.append({
            "id": "web-" + hashlib.sha256(url.encode("utf-8")).hexdigest()[:16],
            "title": title, "snippet": clean(row.get("body", row.get("snippet")), 800),
            "url": url, "siteName": urlsplit(url).hostname,
            "publishedAt": None, "retrievedAt": now,
        })
        if len(sources) >= limit:
            break
    return {"provider": "DDGS", "query": query, "timeRange": time_range,
            "retrievedAt": now, "coverage": COVERAGE, "sources": sources}


def search(query, time_range, limit):
    with contextlib.redirect_stdout(sys.stderr):
        from ddgs import DDGS
        with DDGS(timeout=8) as client:
            rows = client.text(query, max_results=limit, timelimit=TIME_RANGES[time_range],
                               backend=os.environ.get("WEALTHFLOW_WEB_SEARCH_BACKEND", "bing,brave,duckduckgo"))
    return normalize(rows, query, time_range, limit)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--query", required=True)
    parser.add_argument("--time-range", default="any")
    parser.add_argument("--limit", type=int, default=5)
    args = parser.parse_args()
    query = args.query.strip()
    if os.environ.get("WEALTHFLOW_WEB_SEARCH_ENABLED", "false").lower() != "true":
        result = {"error": "DISABLED", "message": "联网搜索默认关闭，请在确认第三方搜索服务后启用"}
    elif (not query or len(query) > 200 or any(ord(c) < 32 or ord(c) == 127 for c in query)
            or args.time_range not in TIME_RANGES or not 1 <= args.limit <= 8):
        result = {"error": "INVALID_ARGUMENTS", "message": "联网搜索参数无效"}
    else:
        try:
            result = search(query, args.time_range, args.limit)
        except ImportError:
            result = {"error": "DEPENDENCY_MISSING", "message": "联网搜索依赖未安装，请安装 tools/finance/requirements.txt"}
        except Exception:
            result = {"error": "SEARCH_UNAVAILABLE", "message": "搜索引擎暂时不可用、连接超时或触发限流，请稍后重试"}
    print(json.dumps(result, ensure_ascii=False, allow_nan=False))
    return 1 if "error" in result else 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
