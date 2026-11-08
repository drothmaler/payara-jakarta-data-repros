#!/usr/bin/env bash
# Builds the reproduction WAR, runs it on a clean Payara Server 7.2026.10 in Docker and prints every case.
# Requirements: JDK 21, Maven 3.9+, Docker. No database to install (Payara's default embedded data source).
# Usage: ./run.sh            run once, remove the container afterwards
#        KEEP=1 ./run.sh     keep the container (http://localhost:18080/repro/api/probe), remove it with: docker rm -f payara-jd-repro
#        PAYARA_IMAGE=payara/server-full:7.2026.11 ./run.sh      try another Payara version
set -euo pipefail
cd "$(dirname "$0")"
IMAGE="${PAYARA_IMAGE:-payara/server-full:7.2026.10}"
PORT="${PORT:-18080}"
NAME=payara-jd-repro

echo "== build"
mvn -B -q clean package
echo "== start $IMAGE"
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" -p "$PORT:8080" -v "$PWD/target/repro.war:/opt/payara/deployments/repro.war:ro" "$IMAGE" >/dev/null
trap '[ "${KEEP:-0}" = 1 ] || docker rm -f "$NAME" >/dev/null 2>&1' EXIT

echo -n "== waiting for the application"
for _ in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/repro/api/probe" || true)
  [ "$code" = 200 ] && break
  printf '.'
  sleep 3
done
echo
docker logs "$NAME" 2>&1 | grep -m1 -oE "Payara Server[^|]*[0-9]+\.[0-9]+\.[0-9]+[^|]*" | head -1 || true
echo "== results (the cases that FAIL are the reproductions)"
curl -s "http://localhost:$PORT/repro/api/probe"
