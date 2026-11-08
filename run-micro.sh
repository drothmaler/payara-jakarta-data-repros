#!/usr/bin/env bash
# Same reproduction without Docker: runs the WAR on Payara Micro (the same Jakarta Data code as Payara Server).
# Requirements: JDK 21 on the PATH (or JAVA_HOME), Maven 3.9+, curl. Downloads ~100 MB (payara-micro) from Maven Central once.
# Usage: ./run-micro.sh                       Payara Micro 7.2026.10
#        PAYARA_VERSION=7.2026.9 ./run-micro.sh
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${PAYARA_VERSION:-7.2026.10}"
PORT="${PORT:-18081}"
JAR="target/payara-micro-$VERSION.jar"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

echo "== build"
mvn -B -q clean package
if [ ! -f "$JAR" ]; then
  echo "== download payara-micro $VERSION"
  curl -fsSL -o "$JAR" "https://repo1.maven.org/maven2/fish/payara/extras/payara-micro/$VERSION/payara-micro-$VERSION.jar"
fi
echo "== start Payara Micro $VERSION on port $PORT (no clustering)"
"$JAVA" -jar "$JAR" --nocluster --port "$PORT" --deploy target/repro.war > target/micro.log 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT
echo -n "== waiting for the application"
for _ in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/repro/api/probe" || true)
  [ "$code" = 200 ] && break
  printf '.'
  sleep 3
done
echo
echo "== results (the cases that FAIL are the reproductions)"
curl -s "http://localhost:$PORT/repro/api/probe"
