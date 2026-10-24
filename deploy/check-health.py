"""Health probe uses the standard library, no external model/news dependency."""
import json
from urllib.request import urlopen

with urlopen("http://127.0.0.1:8080/api/v1/system/health", timeout=3) as response:
    if response.status != 200 or json.load(response).get("status") != "UP":
        raise SystemExit(1)
