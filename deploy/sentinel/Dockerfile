ARG JAVA_BASE_IMAGE=nacos/nacos-server:v3.0.3
FROM ${JAVA_BASE_IMAGE}

ARG SENTINEL_VERSION=1.8.9

LABEL org.opencontainers.image.title="Lumora Sentinel Dashboard" \
      org.opencontainers.image.description="Sentinel Dashboard image for Lumora Cloud development" \
      org.opencontainers.image.source="https://github.com/alibaba/Sentinel"

USER root
WORKDIR /opt/sentinel

RUN curl -fSL --retry 5 --retry-all-errors --retry-delay 2 \
      --output sentinel-dashboard.jar \
      "https://github.com/alibaba/Sentinel/releases/download/${SENTINEL_VERSION}/sentinel-dashboard-${SENTINEL_VERSION}.jar" \
    && test -s sentinel-dashboard.jar \
    && if command -v useradd >/dev/null 2>&1; then \
         groupadd --system sentinel \
         && useradd --system --gid sentinel --home-dir /opt/sentinel --no-create-home sentinel; \
       else \
         addgroup -S sentinel \
         && adduser -S -D -H -h /opt/sentinel -G sentinel sentinel; \
       fi \
    && chown -R sentinel:sentinel /opt/sentinel

ENV HOME=/opt/sentinel

USER sentinel
EXPOSE 8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-Dserver.port=8080", "-Dcsp.sentinel.dashboard.server=localhost:8080", "-Dproject.name=sentinel-dashboard", "-jar", "/opt/sentinel/sentinel-dashboard.jar"]
