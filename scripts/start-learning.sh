#!/usr/bin/env bash
set -euo pipefail
quest_mode=${1:-off}
case "$quest_mode" in off|process|docker) ;; *) echo 'Usage: bash scripts/start-learning.sh off|process|docker' >&2; exit 2;; esac
quest_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
quest_api_jar="$quest_root/backend/target/java-quest-api-0.1.0.jar"
quest_judge_jar="$quest_root/backend/target/java-quest-api-0.1.0-judge.jar"
if [[ ! -f "$quest_api_jar" || ( "$quest_mode" != off && ! -f "$quest_judge_jar" ) ]]; then
  echo 'Build first: mvn -f backend/pom.xml verify' >&2; exit 1
fi
quest_api_pid=''; quest_judge_pid=''
quest_cleanup() {
  for quest_pid in "$quest_api_pid" "$quest_judge_pid"; do
    if [[ -n "$quest_pid" ]] && kill -0 "$quest_pid" 2>/dev/null; then kill "$quest_pid" 2>/dev/null || true; wait "$quest_pid" 2>/dev/null || true; fi
  done
}
trap quest_cleanup EXIT
trap 'exit 130' INT TERM
quest_api_port=${PORT:-8080}
export JUDGE_PORT=${JUDGE_PORT:-8091}
quest_ports=("$quest_api_port")
[[ "$quest_mode" != off ]] && quest_ports+=("$JUDGE_PORT")
for quest_port in "${quest_ports[@]}"; do
  if curl -sS --connect-timeout 1 "http://127.0.0.1:$quest_port/" -o /dev/null 2>/dev/null; then
    echo "Port $quest_port is occupied; stop the previous instance before switching modes." >&2; exit 1
  fi
done
export JUDGE_MODE="$quest_mode" JUDGE_ENABLED=true JUDGE_IMAGE=eclipse-temurin:21-jdk
[[ "$quest_mode" == off ]] && export JUDGE_MODE=docker JUDGE_ENABLED=false
export JUDGE_URL="http://127.0.0.1:$JUDGE_PORT"
export JUDGE_TOKEN=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
quest_wait() {
  local quest_pid=$1 quest_url=$2
  for ((quest_attempt=0;quest_attempt<120;quest_attempt++)); do
    kill -0 "$quest_pid" 2>/dev/null || { echo 'Owned service exited; see console.' >&2; exit 1; }
    if curl --max-time 2 -fsS "$quest_url" >/dev/null 2>&1; then return; fi
    sleep .25
  done
  echo "Startup timed out: $quest_url" >&2; exit 1
}
if [[ "$quest_mode" != off ]]; then
  quest_args=("--mode=$quest_mode")
  if [[ "$quest_mode" == process ]]; then
    echo 'TRUSTED LOCAL CODE ONLY: file/network/CPU/process permissions are NOT isolated.' >&2
    quest_args+=(--allow-local-process)
  fi
  java -jar "$quest_judge_jar" "${quest_args[@]}" & quest_judge_pid=$!
  quest_wait "$quest_judge_pid" "$JUDGE_URL/health"
fi
java -jar "$quest_api_jar" --spring.profiles.active=local "--server.port=$quest_api_port" & quest_api_pid=$!
quest_wait "$quest_api_pid" "http://127.0.0.1:$quest_api_port/api/health"
echo "Mode=$quest_mode. Start frontend separately: npm run dev --prefix frontend"
while kill -0 "$quest_api_pid" 2>/dev/null; do
  if [[ -n "$quest_judge_pid" ]] && ! kill -0 "$quest_judge_pid" 2>/dev/null; then echo 'Judge stopped; no fallback.' >&2; exit 1; fi
  sleep .5
done
