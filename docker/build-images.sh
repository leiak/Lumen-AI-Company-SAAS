#!/usr/bin/env bash
# =============================================================================
# Build all 18 lumen service images from the per-module fat jars.
# Run from repo root:   bash docker/build-images.sh
#
# Each image is tagged `lumen/<service>:<tag>` where tag defaults to the value
# of LUMEN_IMAGE_TAG in docker/.env (fallback: dev). The build context is the
# repo root (`lumen-parent/`) because service.Dockerfile's COPY points at
# `lumen-X/target/lumen-X.jar` relative to that context.
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKER_DIR="${REPO_ROOT}/docker"
CONTEXT="${REPO_ROOT}/lumen-parent"

# Load tag from .env (best-effort; fall back to "dev").
TAG="dev"
if [ -f "${DOCKER_DIR}/.env" ]; then
    TAG="$(grep -E '^LUMEN_IMAGE_TAG=' "${DOCKER_DIR}/.env" | head -1 | cut -d= -f2 | tr -d '"' || echo dev)"
fi

# Map of "service directory" -> "fat jar filename".
# - lumen-gateway produces `lumen-gateway-app.jar` (Spring Boot finalName override).
# - All others use the default Maven finalName: `lumen-X.jar`.
declare -A JAR_FILES=(
  [lumen-gateway]=lumen-gateway-app.jar
  [lumen-auth]=lumen-auth.jar
  [lumen-platform]=lumen-platform.jar
  [lumen-system]=lumen-system.jar
  [lumen-org]=lumen-org.jar
  [lumen-workflow]=lumen-workflow.jar
  [lumen-file]=lumen-file.jar
  [lumen-message]=lumen-message.jar
  [lumen-hr]=lumen-hr.jar
  [lumen-finance]=lumen-finance.jar
  [lumen-assets]=lumen-assets.jar
  [lumen-contract]=lumen-contract.jar
  [lumen-procurement]=lumen-procurement.jar
  [lumen-sales]=lumen-sales.jar
  [lumen-payroll]=lumen-payroll.jar
  [lumen-inventory]=lumen-inventory.jar
  [lumen-bi]=lumen-bi.jar
  [lumen-mobile]=lumen-mobile.jar
)

# Pre-flight: every fat jar must exist. We don't rebuild jars here — that's
# done by `mvn package -pl X` (per the flux already in the project's CI doc).
MISSING=0
for svc in "${!JAR_FILES[@]}"; do
    jar="${CONTEXT}/${svc}/target/${JAR_FILES[$svc]}"
    if [ ! -f "$jar" ]; then
        printf '  ✗ missing jar: %s\n' "$jar"
        MISSING=$((MISSING+1))
    fi
done
if [ "$MISSING" -gt 0 ]; then
    printf '\n%d fat jar(s) missing — run `mvn package -Dmaven.test.skip=true` in lumen-parent/ first.\n' "$MISSING"
    exit 1
fi

# Use BuildKit for cache + parallel layer output.
export DOCKER_BUILDKIT=1

for svc in "${!JAR_FILES[@]}"; do
    jar="${JAR_FILES[$svc]}"
    img="lumen/${svc}:${TAG}"
    printf '→ building %-40s (%s)\n' "$svc" "$jar"
    docker build \
        --file "${DOCKER_DIR}/service.Dockerfile" \
        --build-arg "SERVICE=${svc}" \
        --build-arg "JAR_FILE=${jar}" \
        --tag "$img" \
        --label "lumen.service=${svc}" \
        --label "lumen.image.tag=${TAG}" \
        "$CONTEXT" > /tmp/docker-build-${svc}.log 2>&1 \
        && printf '  ✓ %s\n' "$img" \
        || { printf '  ✗ FAILED — see /tmp/docker-build-%s.log\n' "$svc"; exit 1; }
done

printf '\n✓ all 18 lumen images built with tag %s\n' "$TAG"
printf '  run: docker compose -f docker/docker-compose.yml up -d\n'
