#!/usr/bin/env bash
# =====================================================================================
# Ensayo LOCAL de infra/redis/contrasena.sh (spec 050 T005): corre el mismo codigo que corre en la
# instancia contra un Redis de juguete en Docker, sin tocar AWS ni nada de produccion.
#
#   infra/redis/ensayo-local.sh
#
# Necesita Docker y las imagenes redis:7-alpine y nginx (se bajan solas). Crea la red `ensayo050-red` y
# los contenedores `ensayo050-*`, y los borra al terminar. Un `aws` de mentira (en un directorio
# temporal) contesta lo que la instancia le pediria a Parameter Store. NO hace falta ningun secreto: la
# clave se genera aqui y vive en un archivo temporal.
#
# Lo que comprueba, en orden: el simulacro no cambia nada; exigir la clave deja NOAUTH sin ella y PONG con
# ella, las sesiones intactas, el AOF encendido, `notify-keyspace-events` con Egx, el redis.conf con permiso
# 600 y ningun puerto publicado; repetirlo no cambia nada; la vuelta atras deja Redis sin clave y con las
# sesiones de ahora; y se puede exigir otra vez.
# =====================================================================================
set -euo pipefail

AQUI=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SCRIPT="$AQUI/contrasena.sh"
IMAGEN_REDIS=${IMAGEN_REDIS:-redis:7-alpine}
IMAGEN_HTTP=${IMAGEN_HTTP:-public.ecr.aws/nginx/nginx:1.28-alpine}
RED=ensayo050-red
REDIS=ensayo050-redis
BACKEND=ensayo050-backend
PUERTO_SALUD=${PUERTO_SALUD:-18095}
TMP=$(mktemp -d)
FALLOS=0

limpiar() {
  docker rm -f "$REDIS" "$REDIS-previo" "$REDIS-con-clave" "$REDIS-conversion" "$BACKEND" >/dev/null 2>&1 || true
  docker network rm "$RED" >/dev/null 2>&1 || true
  docker run --rm --user 0 -v "$TMP:/d" "$IMAGEN_REDIS" sh -c 'rm -rf /d/* /d/.[!.]*' >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap limpiar EXIT

ok() { echo "  OK   $1"; }
mal() { echo "  MAL  $1"; FALLOS=$((FALLOS + 1)); }
comprobar() {  # comprobar "que" valor_esperado valor_real
  if [ "$2" = "$3" ]; then ok "$1 ($3)"; else mal "$1: esperaba '$2' y fue '$3'"; fi
}

mkdir -p "$TMP/bin" "$TMP/parametros" "$TMP/instancia"
openssl rand -hex 32 > "$TMP/parametros/REDIS_PASSWORD"
printf 'default' > "$TMP/parametros/REDIS_USERNAME"
touch -d '-120 seconds' "$TMP/parametros/REDIS_PASSWORD" "$TMP/parametros/REDIS_USERNAME"
CLAVE=$(cat "$TMP/parametros/REDIS_PASSWORD")

# Un `aws` de mentira: solo lo que contrasena.sh le pregunta a Parameter Store desde la instancia.
cat > "$TMP/bin/aws" <<'FIN'
#!/usr/bin/env bash
nombre=""; consulta=""
while [ $# -gt 0 ]; do
  case "$1" in --name) nombre=${2##*/}; shift ;; --query) consulta=$2; shift ;; esac
  shift
done
[ -f "$PARAMETROS/$nombre" ] || exit 254
case "$consulta" in
  Parameter.Value) cat "$PARAMETROS/$nombre" ;;
  Parameter.LastModifiedDate) date -d "@$(stat -c %Y "$PARAMETROS/$nombre")" --iso-8601=seconds ;;
esac
FIN
chmod +x "$TMP/bin/aws"

correr() {  # correr <accion> <aplicar>
  PATH="$TMP/bin:$PATH" PARAMETROS="$TMP/parametros" EN_LA_INSTANCIA="$1" APLICAR="$2" \
    CONTENEDOR="$REDIS" BACKEND="$BACKEND" RED="$RED" DIR_REDIS="$TMP/instancia" \
    SALUD_URL="http://127.0.0.1:$PUERTO_SALUD/actuator/health" \
    PLANTILLA_B64="$(base64 -w0 "$AQUI/redis.conf.plantilla")" \
    bash "$SCRIPT"
}
sin_clave() { docker run --rm --network "$RED" "$IMAGEN_REDIS" redis-cli -h "$REDIS" "$@" 2>&1 | tr -d '\r'; }
con_clave() { docker run --rm --network "$RED" -e REDISCLI_AUTH="$CLAVE" "$IMAGEN_REDIS" redis-cli -h "$REDIS" "$@" 2>&1 | tr -d '\r'; }

echo "== Preparando: red, un Redis SIN clave con sesiones, y un backend de mentira que dice UP =="
docker network create "$RED" >/dev/null
docker run -d --name "$REDIS" --network "$RED" --restart unless-stopped "$IMAGEN_REDIS" >/dev/null
sleep 1
for id in a b c; do docker exec "$REDIS" redis-cli SET "renaser:session:v2:sessions:$id" "{}" EX 86400 >/dev/null; done
docker exec "$REDIS" redis-cli CONFIG SET notify-keyspace-events Egx >/dev/null   # lo que hace Spring Session al arrancar
printf 'server { listen 80; location = /actuator/health { default_type application/json; return 200 %s; } }\n' "'{\"status\":\"UP\"}'" > "$TMP/salud.conf"
docker run -d --name "$BACKEND" --network "$RED" -p "127.0.0.1:$PUERTO_SALUD:80" \
  -v "$TMP/salud.conf:/etc/nginx/conf.d/default.conf:ro" "$IMAGEN_HTTP" >/dev/null
for _ in $(seq 1 20); do curl -fsS "http://127.0.0.1:$PUERTO_SALUD/actuator/health" >/dev/null 2>&1 && break; sleep 0.5; done
comprobar "antes: PING sin clave" PONG "$(sin_clave PING)"

echo "== 1. Simulacro: no cambia nada =="
correr exigir no | tail -n 14
comprobar "tras el simulacro: PING sin clave" PONG "$(sin_clave PING)"
comprobar "tras el simulacro: no hay Redis previo" "" "$(docker ps -aq --filter "name=^/$REDIS-previo$")"

echo "== 2. Exigir la clave =="
correr exigir si | tail -n 30
case "$(sin_clave PING)" in *NOAUTH*) ok "PING sin clave: NOAUTH" ;; *) mal "PING sin clave no dio NOAUTH" ;; esac
comprobar "PING con clave" PONG "$(con_clave PING)"
comprobar "sesiones intactas" 3 "$(con_clave --scan --pattern 'renaser:session:v2:*' | wc -l | tr -d ' ')"
case "$(con_clave INFO persistence)" in *aof_enabled:1*) ok "AOF encendido" ;; *) mal "AOF apagado" ;; esac
comprobar "notify-keyspace-events" gxE "$(con_clave CONFIG GET notify-keyspace-events | tail -n 1)"
comprobar "puertos publicados" "" "$(docker port "$REDIS")"
comprobar "permiso del redis.conf" "600 999" "$(stat -c '%a %u' "$TMP/instancia/conf/redis.conf" 2>/dev/null || docker run --rm --user 0 -v "$TMP/instancia:/d" "$IMAGEN_REDIS" sh -c 'echo "$(stat -c %a /d/conf/redis.conf) $(stat -c %u /d/conf/redis.conf)"')"
comprobar "el Redis viejo queda parado" exited "$(docker inspect -f '{{.State.Status}}' "$REDIS-previo")"
case "$(cat "$TMP/instancia/respaldo/previo.txt" 2>/dev/null)" in *imagen=*) ok "respaldo de lo anterior guardado" ;; *) mal "falta el respaldo" ;; esac
con_clave SET "renaser:session:v2:sessions:nueva" "{}" EX 86400 >/dev/null

echo "== 3. Repetirlo no cambia nada =="
correr exigir si | tail -n 12
comprobar "sigue exigiendo clave" PONG "$(con_clave PING)"

echo "== 4. Vuelta atras =="
correr revertir si | tail -n 8
comprobar "Redis vuelve sin clave" PONG "$(sin_clave PING)"
comprobar "las sesiones de ahora pasaron al Redis viejo" 4 "$(sin_clave --scan --pattern 'renaser:session:v2:*' | wc -l | tr -d ' ')"
comprobar "el Redis con clave queda parado" exited "$(docker inspect -f '{{.State.Status}}' "$REDIS-con-clave")"

echo "== 5. Se puede exigir otra vez =="
# En la instancia el script corre como root y puede reescribir lo que es de `redis`; aqui no: se le devuelve la carpeta al usuario.
docker run --rm --user 0 -v "$TMP/instancia:/d" "$IMAGEN_REDIS" chown -R "$(id -u):$(id -g)" /d
correr exigir si | tail -n 6
case "$(sin_clave PING)" in *NOAUTH*) ok "PING sin clave: NOAUTH" ;; *) mal "PING sin clave no dio NOAUTH" ;; esac
comprobar "sesiones tras la segunda vez" 4 "$(con_clave --scan --pattern 'renaser:session:v2:*' | wc -l | tr -d ' ')"

echo
if [ "$FALLOS" -eq 0 ]; then echo "ENSAYO OK: todo lo comprobado paso."; else echo "ENSAYO CON FALLOS: $FALLOS."; exit 1; fi
