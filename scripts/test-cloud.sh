#!/usr/bin/env bash
# Maven/Java locales; todos los contenedores de prueba en Testcontainers Cloud.
set -euo pipefail
umask 077

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
agent=${TCC_AGENT_BIN:-"$HOME/.local/bin/testcontainers-cloud-agent"}
token_file=${TCC_TOKEN_FILE:-"$HOME/.config/renaser/testcontainers-cloud.token"}
log_dir=${XDG_CACHE_HOME:-"$HOME/.cache"}/renaser-testcontainers

if [[ ! -x "$agent" ]]; then
  echo "Falta el agente de Testcontainers Cloud. Ver docs/PRUEBAS_EN_CLOUD.md." >&2
  exit 1
fi
if [[ -z "${TC_CLOUD_TOKEN:-}" && -s "$token_file" ]]; then
  TC_CLOUD_TOKEN=$(<"$token_file")
fi
if [[ -z "${TC_CLOUD_TOKEN:-}" ]]; then
  echo "Falta el token. Guardalo en $token_file o configura TC_CLOUD_TOKEN." >&2
  echo "No se iniciaron Maven ni contenedores locales." >&2
  exit 1
fi
export TC_CLOUD_TOKEN
# Un solo worker: este proyecto no necesita Turbo ni varios contenedores de Postgres por fork.
export TC_CLOUD_CONCURRENCY=1
mkdir -p "$log_dir"
agent_log=$(mktemp "$log_dir/agent.XXXXXX.log")
"$agent" --terminate >"$agent_log" 2>&1 &
agent_pid=$!
cleanup() {
  kill "$agent_pid" 2>/dev/null || true
  wait "$agent_pid" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
if ! "$agent" wait --timeout-seconds 45; then
  echo "No se pudo conectar a Testcontainers Cloud. Log: $agent_log" >&2
  exit 1
fi
if ! kill -0 "$agent_pid" 2>/dev/null; then
  echo "El agente termino antes de las pruebas. Log: $agent_log" >&2
  exit 1
fi
cd "$repo_dir"
if [[ $# -eq 0 ]]; then
  set -- clean verify
fi
# El guard de TestcontainersConfiguration verifica el runtime ANTES de crear Postgres o Redis.
# No hay fallback silencioso a Docker local si el agente falla.
./mvnw -B -ntp "$@" -Drenaser.tests.cloud-required=true
