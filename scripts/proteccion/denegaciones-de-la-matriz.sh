#!/usr/bin/env bash
# =====================================================================================
# Lo que la matriz de Mentor y Guia nego, o HABRIA negado en SOMBRA, por dia y por endpoint (spec 050 tramo H, FR-041; D12).
# SOLO LECTURA. Lo corre el DUENO con el perfil `prod`, durante la sombra, para decidir si el registro esta limpio antes de pasar la
# matriz a APLICAR (scripts/despliegue/permisos-modo.sh), y despues, para ver los 403 que la matriz da.
#
# DE DONDE SALE. Cada pedido de un Mentor o una Guia a un endpoint cuyo permiso no esta en su lista suma 1 a un campo del hash de
# Redis `proteccion:permisos:denegaciones:<AAAA-MM-DD>` (dia de Lima), con el campo
# `<ESTADO> <ROL> <PERMISO> <METODO> <RUTA>`: ESTADO es HABRIA_DENEGADO (sombra: el pedido paso) o DENEGADO (aplicar: dio 403) y la
# RUTA es la plantilla (`/api/v1/mentor/groups/{id}`), nunca la ruta concreta. NO guarda la cuenta ni ningun token. Vive 30 dias; el
# registro de Docker (`matriz-estricta HABRIA_DENEGADO ...`) solo vive desde el ultimo despliegue, esto si sobrevive.
#
# COMO LEERLO. Un registro LIMPIO es el que no tiene ninguna linea HABRIA_DENEGADO de un flujo legitimo despues de varios dias de uso
# real (y con todos los roles: Mentor y Guia). Cada linea es un endpoint que un Mentor o una Guia usa y cuyo permiso falta en la
# lista: o es un abuso, o falta un permiso en la tabla (que lo aprueba el dueño, regla 00).
#
#   0. Variables (las dos son obligatorias):
#        export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#        export RENASER_INSTANCIA=<id de la instancia EC2 del backend, i-...>
#   1. scripts/proteccion/denegaciones-de-la-matriz.sh [dias]     (sin dias: 7, contando hoy, hora de Lima; maximo 30)
#      Imprime `veces  estado  rol  permiso  metodo  ruta`, de mas a menos, y al final el total por permiso.
#
# Cumple FR-011: solo lee (no hay nada que simular ni que revertir), perfil `prod` explicito y cuenta verificada, sin secretos en
# pantalla (la contrasena de Redis, si existe, la lee la instancia de Parameter Store y entra por REDISCLI_AUTH) y `set -euo pipefail`.
# =====================================================================================
set -euo pipefail

PERFIL=prod
REGION=${REGION:-${RENASER_REGION:-us-east-1}}
PREFIJO=${PREFIJO:-${RENASER_PREFIJO_PARAMETROS:-/renaser/prod/}}
CONTENEDOR=${CONTENEDOR:-redis}
CLAVES="proteccion:permisos:denegaciones:"

falla() { echo "ERROR: $*" >&2; exit 1; }

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE DENTRO DE LA INSTANCIA (EN_LA_INSTANCIA=leer). Lo manda el script local por SSM.
# ------------------------------------------------------------------------------------------------
if [ -n "${EN_LA_INSTANCIA:-}" ]; then
  [[ "${DIAS:-}" =~ ^[0-9]+$ ]] && [ "$DIAS" -ge 1 ] && [ "$DIAS" -le 30 ] || { echo "Dias desconocidos." >&2; exit 2; }
  clave=$(aws ssm get-parameter --region "$REGION" --with-decryption --name "${PREFIJO}REDIS_PASSWORD" \
            --query Parameter.Value --output text 2>/dev/null || true)
  if [ -n "$clave" ] && [ "$clave" != None ]; then
    leer() { ( export REDISCLI_AUTH="$clave"; docker exec -i -e REDISCLI_AUTH "$CONTENEDOR" redis-cli "$@" ); }
  else
    leer() { docker exec -i "$CONTENEDOR" redis-cli "$@"; }
  fi
  TODO=$(mktemp); trap 'rm -f "$TODO"' EXIT
  for ((i = 0; i < DIAS; i++)); do
    dia=$(TZ=America/Lima date -d "-$i day" +%F)
    # El hash alterna campo y valor, una linea cada uno; el campo lleva espacios y el valor es el numero de veces.
    leer HGETALL "${CLAVES}${dia}" | tr -d '\r' | paste - - | awk -F'\t' 'NF == 2 { print $2 "\t" $1 }' >> "$TODO"
  done
  echo "Ultimos $DIAS dias (veces, estado, rol, permiso, metodo, ruta):"
  awk -F'\t' '{ n[$2] += $1 } END { for (c in n) print n[c] "\t" c }' "$TODO" | sort -rn | head -100
  echo
  echo "Total por permiso (veces, estado, permiso):"
  awk -F'\t' '{ split($2, c, " "); n[c[1] " " c[3]] += $1 } END { for (k in n) print n[k] "\t" k }' "$TODO" | sort -rn | head -50
  exit 0
fi

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE EN LA MAQUINA DEL DUENO
# ------------------------------------------------------------------------------------------------
uso() { sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; }

DIAS=${1:-7}
case "$DIAS" in -h|--help) uso; exit 0 ;; esac
[[ "$DIAS" =~ ^[0-9]+$ ]] && [ "$DIAS" -ge 1 ] && [ "$DIAS" -le 30 ] || { echo "Los dias van como un numero de 1 a 30." >&2; exit 2; }

for h in aws jq base64; do command -v "$h" >/dev/null || falla "falta $h en esta maquina."; done
: "${RENASER_CUENTA_AWS:?exporta RENASER_CUENTA_AWS con el id de 12 digitos de la cuenta de produccion}"
: "${RENASER_INSTANCIA:?exporta RENASER_INSTANCIA con el id de la instancia EC2 del backend}"
CUENTA=$(aws sts get-caller-identity --profile "$PERFIL" --query Account --output text)
[ "$CUENTA" = "$RENASER_CUENTA_AWS" ] || falla "el perfil $PERFIL apunta a la cuenta $CUENTA, no a la esperada ($RENASER_CUENTA_AWS)."
echo "Cuenta verificada ($CUENTA, perfil $PERFIL). Solo lectura."

parametros=$(mktemp); trap 'rm -f "$parametros"' EXIT
jq -n --arg b64 "$(base64 -w0 "$0")" --arg prefijo "$PREFIJO" --arg region "$REGION" --arg contenedor "$CONTENEDOR" --arg dias "$DIAS" \
  '{commands: [
     "set -eu",
     "S=$(mktemp /tmp/denegaciones-matriz.XXXXXX)",
     "trap \"rm -f $S\" EXIT",
     ("echo " + $b64 + " | base64 -d > \"$S\""),
     ("EN_LA_INSTANCIA=leer PREFIJO=" + $prefijo + " REGION=" + $region + " CONTENEDOR=" + $contenedor + " DIAS=" + $dias + " bash \"$S\"")
   ]}' > "$parametros"
cid=$(aws ssm send-command --profile "$PERFIL" --region "$REGION" --instance-ids "$RENASER_INSTANCIA" \
        --document-name AWS-RunShellScript --comment "denegaciones de la matriz de mentores: $DIAS dias" \
        --parameters "file://$parametros" --query Command.CommandId --output text)
desde=$SECONDS
while true; do
  estado=$(aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
             --instance-id "$RENASER_INSTANCIA" --query Status --output text 2>/dev/null || echo Pending)
  case "$estado" in Success|Failed|Cancelled|TimedOut) break ;; esac
  [ $(( SECONDS - desde )) -ge 300 ] && { estado=SinRespuesta; break; }
  sleep 3
done
aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
  --instance-id "$RENASER_INSTANCIA" --query StandardOutputContent --output text || true
[ "$estado" = Success ] || falla "la instancia termino con estado $estado."
