#!/usr/bin/env bash
# =====================================================================================
# Cuantas sesiones llegan al tope absoluto de 90 dias y cuando (spec 050 tramo E, T051; FR-028).
# SOLO LECTURA: cuenta, no cambia nada. Lo corre el DUENO ANTES de desplegar el tope, para saber cuanta
# gente va a tener que volver a entrar y avisarles. Un agente nunca lo corre contra produccion.
#
# Que cuenta. Spring Session guarda en Redis, por cada sesion, el instante en que se inicio (`creationTime`).
# El tope es esa fecha + 90 dias (`renaser.sesion.tope-absoluto`). Las sesiones que NO se usan en 30 dias ya se
# fueron solas (la inactividad, `spring.session.timeout`), asi que lo que queda en Redis son las que se
# usaron en el ultimo mes: de ellas, las de mas de 90 dias se cortarian el DIA UNO del despliegue y el resto
# se va cortando a medida que cumple los 90.
#
# Uso (desde tu maquina, con el perfil `prod`):
#   export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#   export RENASER_INSTANCIA=<id de la instancia EC2 del backend, i-...>
#   scripts/proteccion/sesiones-por-antiguedad.sh                  # el tope de 90 dias
#   scripts/proteccion/sesiones-por-antiguedad.sh --tope-dias 60   # para ver como quedaria con otro tope
#
# Ensayo en tu maquina, contra un Redis de desarrollo (sin AWS):
#   scripts/proteccion/sesiones-por-antiguedad.sh --local [--contenedor redis] [--namespace renaser:session:v2]
#   (si ese Redis pide clave: REDISCLI_AUTH=<clave> scripts/proteccion/sesiones-por-antiguedad.sh --local)
#
# Lee la clave de Redis, si existe (tramo A1), de Parameter Store con el rol de la instancia y la pasa a redis-cli
# por REDISCLI_AUTH (nunca por argumento): no se imprime ni queda en ningun archivo. Recorre las claves con SCAN
# (no bloquea a Redis) y manda todos los HGET por una sola conexion.
#
# Cuenta SESIONES, no personas: quien entro desde dos telefonos tiene dos. La salida dice cuantas sesiones se
# cortarian el dia uno y cuantas cumplen el tope en los proximos 7, 14, 30 y 60 dias.
# Reglas de FR-011 que cumple: perfil `prod` explicito y cuenta verificada, no cambia nada (no hay --aplicar: no
# hay nada que aplicar), nada de secretos en pantalla ni en el repositorio, y `set -euo pipefail`.
# =====================================================================================
set -euo pipefail

PERFIL=prod
REGION=${REGION:-${RENASER_REGION:-us-east-1}}
PREFIJO=${PREFIJO:-${RENASER_PREFIJO_PARAMETROS:-/renaser/prod/}}
CONTENEDOR=${CONTENEDOR:-redis}
NAMESPACE=${NAMESPACE:-renaser:session:v2}
TOPE_DIAS=${TOPE_DIAS:-90}
LOCAL=no

falla() { echo "ERROR: $*" >&2; exit 1; }

# ------------------------------------------------------------------------------------------------
# EL CONTEO (corre dentro de la instancia, o en tu maquina con --local). Solo lectura.
# ------------------------------------------------------------------------------------------------
contar() {
  local clave_de_redis=${REDISCLI_AUTH:-}
  if [ -z "$clave_de_redis" ] && [ "$LOCAL" = no ]; then
    # Si el tramo A1 ya se aplico, el parametro existe; si no, no hay clave y se sigue sin ella.
    clave_de_redis=$(aws ssm get-parameter --region "$REGION" --with-decryption --name "${PREFIJO}REDIS_PASSWORD" \
                       --query Parameter.Value --output text 2>/dev/null || true)
    case "$clave_de_redis" in None) clave_de_redis="" ;; esac
  fi
  rc() { ( if [ -n "$clave_de_redis" ]; then export REDISCLI_AUTH="$clave_de_redis"; docker exec -i -e REDISCLI_AUTH "$CONTENEDOR" redis-cli "$@"
           else unset REDISCLI_AUTH; docker exec -i "$CONTENEDOR" redis-cli "$@"; fi ); }

  local ahora_ms claves total
  ahora_ms=$(( ${AHORA_EPOCH:-$(date +%s)} * 1000 ))
  # Las sesiones son `<namespace>:sessions:<id>`; `...:sessions:expires:<id>` es otra cosa (su aviso de expiracion).
  claves=$(rc --scan --pattern "$NAMESPACE:sessions:*" | grep -v ":sessions:expires:" || true)
  total=$(printf '%s' "$claves" | grep -c . || true)
  echo "Redis ($CONTENEDOR), espacio $NAMESPACE: $total sesiones."
  if [ "$total" -eq 0 ]; then
    echo "No hay sesiones: o el espacio de nombres no es el de produccion (REDIS_SESSION_NAMESPACE) o Redis esta vacio."
    return 0
  fi
  # `creationTime` se guarda serializado (JSON con el tipo, p. ej. ["java.lang.Long",1762000000000]): de cada respuesta
  # se toma el numero de 13 cifras (milisegundos desde 2001). Una respuesta vacia es una sesion sin ese campo.
  printf '%s\n' "$claves" | sed 's/.*/HGET & creationTime/' | rc | grep -oE '[0-9]{13}' \
    | awk -v ahora="$ahora_ms" -v tope="$TOPE_DIAS" -v total="$total" '
        { edad = (ahora - $1) / 86400000; leidas++
          if (edad > maxEdad) maxEdad = edad
          falta = tope - edad
          if (falta < 0) ya++
          else if (falta < 7) s7++
          else if (falta < 14) s14++
          else if (falta < 30) s30++
          else if (falta < 60) s60++
          else mas++ }
        END {
          printf "Con fecha de inicio legible: %d de %d. La mas vieja tiene %.0f dias.\n", leidas, total, maxEdad
          printf "\nCon un tope de %d dias:\n", tope
          printf "  %6d  ya lo pasaron: se cortarian el DIA UNO del despliegue\n", ya
          printf "  %6d  lo cumplen en los proximos 7 dias\n", s7
          printf "  %6d  lo cumplen en 7 a 14 dias\n", s14
          printf "  %6d  lo cumplen en 14 a 30 dias\n", s30
          printf "  %6d  lo cumplen en 30 a 60 dias\n", s60
          printf "  %6d  lo cumplen dentro de mas de 60 dias\n", mas
          if (leidas < total) printf "\nAVISO: %d sesiones no traen creationTime legible; no se cuentan arriba.\n", total - leidas
        }' || true
  echo
  echo "Son sesiones, no personas. Quien vuelve a entrar (la app lo lleva al login con su cola de envios intacta) tiene otros 90 dias."
}

if [ -n "${EN_LA_INSTANCIA:-}" ]; then
  contar
  exit 0
fi

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE EN LA MAQUINA DEL DUENO
# ------------------------------------------------------------------------------------------------

uso() { sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --local) LOCAL=si ;;
    --contenedor) CONTENEDOR=${2:?--contenedor necesita el nombre del contenedor de Redis}; shift ;;
    --namespace) NAMESPACE=${2:?--namespace necesita el espacio de nombres de las sesiones}; shift ;;
    --tope-dias) TOPE_DIAS=${2:?--tope-dias necesita un numero de dias}; shift ;;
    -h|--help) uso; exit 0 ;;
    *) echo "Opcion desconocida: $1" >&2; uso >&2; exit 2 ;;
  esac
  shift
done
[[ "$TOPE_DIAS" =~ ^[0-9]+$ ]] && [ "$TOPE_DIAS" -ge 1 ] || { echo "--tope-dias debe ser un numero entero de dias, 1 o mas." >&2; exit 2; }
[[ "$NAMESPACE" =~ ^[A-Za-z0-9:._-]+$ ]] || { echo "El espacio de nombres solo lleva letras, numeros y : . _ -" >&2; exit 2; }
[[ "$CONTENEDOR" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "El nombre del contenedor no es valido." >&2; exit 2; }

if [ "$LOCAL" = si ]; then
  contar
  exit 0
fi

for h in aws jq base64; do command -v "$h" >/dev/null || falla "falta $h en esta maquina."; done
: "${RENASER_CUENTA_AWS:?exporta RENASER_CUENTA_AWS con el id de 12 digitos de la cuenta de produccion}"
: "${RENASER_INSTANCIA:?exporta RENASER_INSTANCIA con el id de la instancia EC2 del backend}"

CUENTA=$(aws sts get-caller-identity --profile "$PERFIL" --query Account --output text)
if [ "$CUENTA" != "$RENASER_CUENTA_AWS" ]; then
  falla "el perfil $PERFIL apunta a la cuenta $CUENTA, no a la esperada ($RENASER_CUENTA_AWS). No se hizo nada."
fi
echo "Cuenta verificada ($CUENTA, perfil $PERFIL). Solo lectura."

en_la_instancia() {  # manda ESTE script por SSM, con las variables por delante (patron de infra/redis/contrasena.sh)
  local parametros cid estado desde
  parametros=$(mktemp); trap "rm -f '$parametros'" EXIT
  jq -n --arg b64 "$(base64 -w0 "$0")" --arg ns "$NAMESPACE" --arg cont "$CONTENEDOR" --arg tope "$TOPE_DIAS" \
        --arg prefijo "$PREFIJO" --arg region "$REGION" \
    '{commands: [
       "set -eu",
       "S=$(mktemp /tmp/sesiones-por-antiguedad.XXXXXX)",
       "trap \"rm -f $S\" EXIT",
       ("echo " + $b64 + " | base64 -d > \"$S\""),
       ("EN_LA_INSTANCIA=si NAMESPACE=" + $ns + " CONTENEDOR=" + $cont + " TOPE_DIAS=" + $tope + " PREFIJO=" + $prefijo + " REGION=" + $region + " bash \"$S\"")
     ]}' > "$parametros"
  cid=$(aws ssm send-command --profile "$PERFIL" --region "$REGION" --instance-ids "$RENASER_INSTANCIA" \
          --document-name AWS-RunShellScript --comment "sesiones por antiguedad (solo lectura)" \
          --parameters "file://$parametros" --query Command.CommandId --output text)
  echo "Comando SSM $cid enviado; esperando el resultado..."
  desde=$SECONDS
  while true; do
    estado=$(aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
               --instance-id "$RENASER_INSTANCIA" --query Status --output text 2>/dev/null || echo Pending)
    case "$estado" in Success|Failed|Cancelled|TimedOut) break ;; esac
    [ $(( SECONDS - desde )) -ge 300 ] && { estado=SinRespuesta; break; }
    sleep 3
  done
  echo "----- salida de la instancia -----"
  aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
    --instance-id "$RENASER_INSTANCIA" --query StandardOutputContent --output text || true
  if [ "$estado" != Success ]; then
    aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
      --instance-id "$RENASER_INSTANCIA" --query StandardErrorContent --output text || true
    falla "la instancia termino con estado $estado."
  fi
}

en_la_instancia
