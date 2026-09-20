# =============================================================================
# Lumen service image — multi-service template.
#
# Usage:
#   docker build -f docker/service.Dockerfile \
#       --build-arg SERVICE=lumen-auth \
#       --build-arg JAR_FILE=lumen-auth.jar \
#       -t lumen/lumen-auth:dev lumen-parent/
#
# Or via the helper:
#   bash docker/build-images.sh
# =============================================================================
#
# Why a single template for 18 services?
#   * BuildKit layer cache for the JRE base image is reused across services.
#   * Only the COPY of the fat jar changes per service — keeps the docker/
#     directory terse (1 Dockerfile instead of 18).
#   * Each service still ends up as a discrete `lumen/lumen-X:dev` image so
#     compose can pull/start/scale them independently.
#
# The fat-jar naming is intentionally explicit (ARG JAR_FILE) because
# lumen-gateway produces `lumen-gateway-app.jar` while the rest produce
# `lumen-X.jar` — see docker/build-images.sh for the mapping.
# =============================================================================
FROM eclipse-temurin:17-jre-alpine

ARG SERVICE
ARG JAR_FILE
ENV SERVICE=${SERVICE}

# Non-root user so the container doesn't run as uid 0.
# alpine's `adduser -D` is BusyBox; UID 10001 maps to nobody in most distros.
RUN addgroup -g 10001 -S lumen && adduser -u 10001 -S -G lumen lumen

WORKDIR /app

# Pre-create the directory logback writes to (./logs/lumen/{info,error}.log).
# Owned by `lumen` so the non-root user can write rolling logs there.
RUN mkdir -p /app/logs/lumen && chown -R lumen:lumen /app/logs

# Fat jar layout is already a complete Spring Boot app — no need to expand it.
COPY --chown=lumen:lumen ${SERVICE}/target/${JAR_FILE} /app/app.jar

USER lumen

# Default port — actual port is set via SERVER_PORT env in docker-compose.
EXPOSE 9200

# JVM flags: container-aware ergonomics + sane UTF-8 default for log aggregation.
ENV JAVA_TOOL_OPTIONS="\
-XX:+UseG1GC \
-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError \
-Dfile.encoding=UTF-8 \
-Duser.timezone=Asia/Shanghai \
-Djava.security.egd=file:/dev/./urandom"

# Liveness probe — TCP connect to the service port. We can't use HTTP
# probes because none of the lumen services ship spring-boot-starter-actuator
# and the GlobalExceptionHandler turns any unknown path into a 500 (which
# makes wget/curl exit non-zero even though the server is alive). Port-bound
# is "up enough" for compose scheduling purposes. `nc` is part of busybox so
# always available on eclipse-temurin:17-jre-alpine (bash is not).
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
    CMD nc -z 127.0.0.1 ${SERVER_PORT:-9200} || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
