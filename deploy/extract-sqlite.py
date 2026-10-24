"""Install the JDBC JAR's own matching native library in the read-only image."""
from io import BytesIO
from pathlib import Path
import platform
from zipfile import ZipFile

architecture = {"x86_64": "x86_64", "aarch64": "aarch64"}[platform.machine()]
with ZipFile("/app/wealthflow.jar") as application:
    jars = [name for name in application.namelist() if name.startswith("BOOT-INF/lib/sqlite-jdbc-") and name.endswith(".jar")]
    if len(jars) != 1:
        raise RuntimeError("Expected exactly one SQLite JDBC dependency")
    with ZipFile(BytesIO(application.read(jars[0]))) as jdbc:
        target = Path("/app/native/libsqlitejdbc.so")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(jdbc.read(f"org/sqlite/native/Linux/{architecture}/libsqlitejdbc.so"))
        target.chmod(0o555)
