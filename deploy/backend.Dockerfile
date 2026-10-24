FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src/main ./src/main
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM python:3.12-slim-bookworm AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY tools/finance/requirements.txt ./tools/finance/requirements.txt
RUN pip install --no-cache-dir -r tools/finance/requirements.txt
RUN useradd --uid 10001 --create-home wealthflow \
    && mkdir /app/data && chown wealthflow:wealthflow /app/data
COPY --from=build /build/target/WealthFlow-0.0.1-SNAPSHOT.jar ./wealthflow.jar
COPY deploy/extract-sqlite.py /tmp/extract-sqlite.py
RUN python /tmp/extract-sqlite.py && rm /tmp/extract-sqlite.py
COPY tools/finance/news.py ./tools/finance/news.py
COPY tools/finance/web_search.py ./tools/finance/web_search.py
COPY deploy/check-health.py ./check-health.py
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONUTF8=1 \
    OPENBLAS_NUM_THREADS=1 \
    OMP_NUM_THREADS=1 \
    HOME=/tmp
USER wealthflow
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 \
    CMD ["python", "/app/check-health.py"]
ENTRYPOINT ["java", "-Dorg.sqlite.lib.path=/app/native", "-jar", "/app/wealthflow.jar"]
