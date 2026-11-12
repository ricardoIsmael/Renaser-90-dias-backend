#!/usr/bin/env bash
# =====================================================================================
# Quien habria sido bloqueado por el limite por cuenta en un grupo y un dia (spec 050 tramo C, FR-019; D12). SOLO LECTURA.
# Lo corre el DUENO con el perfil `prod`, durante las dos semanas de medicion, para revisar una por una las cuentas que pasaron su
# tope (abuso, o un tope mal puesto) antes de pasar el grupo a APLICAR.
#
# DE DONDE SALE. Cada pedido que un grupo en REGISTRAR habria bloqueado suma 1 a un campo del hash de Redis
# `proteccion:habria:<grupo>:<AAAA-MM-DD>` (dia de Lima): el campo es el id de la cuenta; un pedido sin sesion suma a `sin_cuenta`
# (nunca guarda una IP). Vive 30 dias; el registro de Docker solo vive desde el ultimo despliegue, esto si sobrevive.
#
#   0. Variables (las dos son obligatorias):
#        export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#        export RENASER_INSTANCIA=<id de la instancia EC2 del backend, i-...>
#   1. scripts/proteccion/cuentas-que-habrian-bloqueado.sh <grupo> [AAAA-MM-DD]     (sin dia: hoy, hora de Lima)
#      Imprime `veces  id-de-la-cuenta`, de mas a menos (las 50 primeras).
#   Grupos: lectura escritura muro chat subidas ia-fuera-de-ser ser exportaciones admin webhook-whatsapp
#
# Cumple FR-011: solo lee (no hay nada que simular ni que revertir), perfil `prod` explicito y cuenta verificada, sin secretos en
# pantalla (la contrasena de Redis, si existe, la lee la instancia de Parameter Store y entra por REDISCLI_AUTH) y `set -euo pipefail`.
# =====================================================================================
set -euo pipefail

PERFIL=prod
REGION=${REGION:-${RENASER_REGION:-us-east-1}}
PREFIJO=${PREFIJO:-${RENASER_PREFIJO_PARAMETROS:-/renaser/prod/}}
CONTENEDOR=${CONTENEDOR:-redis}
GRUPOS="lectura escritura muro chat subidas ia-fuera-de-ser ser exportaciones admin webhook-whatsapp"

falla() { echo "ERROR: $*" >&2; exit 1; }
en_lista() { case " $2 " in *" $1 "*) return 0 ;; esac; return 1; }

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE DENTRO DE LA INSTANCIA (EN_LA_INSTANCIA=leer). Lo manda el script local por SSM.
# ------------------------------------------------------------------------------------------------
if [ -n "${EN_LA_INSTANCIA:-}" ]; then
  en_lista "${GRUPO:-}" "$GRUPOS" || { echo "Grupo desconocido." >&2; exit 2; }
  [[ "${DIA:-}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "Dia desconocido." >&2; exit 2; }
  clave=$(aws ssm get-parameter --region "$REGION" --with-decryption --name "${PREFIJO}REDIS_PASSWORD" \
            --query Parameter.Value --output text 2>/dev/null || true)
  if [ -n "$clave" ] && [ "$clave" != None ]; then
    leer() { ( export REDISCLI_AUTH="$clave"; docker exec -i -e REDISCLI_AUTH "$CONTENEDOR" redis-cli "$@" ); }
  else
    leer() { docker exec -i "$CONTENEDOR" redis-cli "$@"; }
  fi
  echo "Grupo $GRUPO, dia $DIA (veces, cuenta):"
  leer HGETALL "proteccion:habria:$GRUPO:$DIA" | tr -d '\r' | paste - - | awk -F'\t' 'NF == 2 { print $2 "\t" $1 }' | sort -rn | head -50
  exit 0
fi

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE EN LA MAQUINA DEL DUENO
# ------------------------------------------------------------------------------------------------
uso() { sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; }

GRUPO=${1:-}
case "$GRUPO" in -h|--help) uso; exit 0 ;; esac
[ -n "$GRUPO" ] || { uso >&2; exit 2; }
en_lista "$GRUPO" "$GRUPOS" || { echo "El grupo debe ser uno de: $GRUPOS" >&2; exit 2; }
DIA=${2:-$(TZ=America/Lima date +%F)}
[[ "$DIA" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "El dia va como AAAA-MM-DD." >&2; exit 2; }

for h in aws jq base64; do command -v "$h" >/dev/null || falla "falta $h en esta maquina."; done
: "${RENASER_CUENTA_AWS:?exporta RENASER_CUENTA_AWS con el id de 12 digitos de la cuenta de produccion}"
: "${RENASER_INSTANCIA:?exporta RENASER_INSTANCIA con el id de la instancia EC2 del backend}"
CUENTA=$(aws sts get-caller-identity --profile "$PERFIL" --query Account --output text)
[ "$CUENTA" = "$RENASER_CUENTA_AWS" ] || falla "el perfil $PERFIL apunta a la cuenta $CUENTA, no a la esperada ($RENASER_CUENTA_AWS)."
echo "Cuenta verificada ($CUENTA, perfil $PERFIL). Solo lectura."

parametros=$(mktemp); trap 'rm -f "$parametros"' EXIT
jq -n --arg b64 "$(base64 -w0 "$0")" --arg prefijo "$PREFIJO" --arg region "$REGION" --arg contenedor "$CONTENEDOR" \
      --arg grupo "$GRUPO" --arg dia "$DIA" \
  '{commands: [
     "set -eu",
     "S=$(mktemp /tmp/habrian-bloqueado.XXXXXX)",
     "trap \"rm -f $S\" EXIT",
     ("echo " + $b64 + " | base64 -d > \"$S\""),
     ("EN_LA_INSTANCIA=leer PREFIJO=" + $prefijo + " REGION=" + $region + " CONTENEDOR=" + $contenedor + " GRUPO=" + $grupo + " DIA=" + $dia + " bash \"$S\"")
   ]}' > "$parametros"
cid=$(aws ssm send-command --profile "$PERFIL" --region "$REGION" --instance-ids "$RENASER_INSTANCIA" \
        --document-name AWS-RunShellScript --comment "cuentas que habrian bloqueado: $GRUPO $DIA" \
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
