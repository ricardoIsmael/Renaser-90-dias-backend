#!/usr/bin/env bash
# =====================================================================================
# Pasa UNA regla del Web ACL de Count a Block, o la devuelve a Count (spec 050 tramo B: FR-002, FR-003, FR-011).
# Lo corre el DUENO, desde su maquina, con el perfil `prod`. Nunca un agente ni el CD.
#
# El paso de Count a Block es el unico que puede cortar a gente legitima, asi que tiene su propia bandera:
# `--aplicar-bloqueo`. `--aplicar` no lo hace (este script no la acepta). Sin bandera es un simulacro que muestra,
# ademas, cuantos pedidos conto la regla en los ultimos 7 dias (CloudWatch): eso es lo que se mira antes de bloquear.
#
#   0. Variables:  export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#   1. Simulacro:  infra/waf/pasar-regla-a-bloqueo.sh --regla limite-auth
#   2. Bloquear:   infra/waf/pasar-regla-a-bloqueo.sh --regla limite-auth --aplicar-bloqueo
#   3. VUELTA ATRAS (a Count, una regla):
#                  infra/waf/pasar-regla-a-bloqueo.sh --regla limite-auth --volver-a-count
#
# ORDEN RECOMENDADO (D2): despues de 7 dias de metricas, primero las dos reglas por tasa (`limite-auth`,
# `limite-global`) y `reputacion-de-ip`; los otros dos grupos (`entradas-conocidas-como-malas`, `reglas-comunes`)
# solo si no hubo falsos positivos con trafico de la app: si una regla de un grupo acierta mal, se agrega a
# `Renaser.Excepciones` de la plantilla con su motivo y fecha y se corre crear-web-acl.sh --actualizar; el grupo
# entero nunca se apaga (FR-003).
#
# Una regla por tasa en Block responde 429 con Retry-After y {"message":"Espera un momento y vuelve a intentarlo.",
# "codigo":"DEMASIADOS_PEDIDOS"} (FR-002). Un grupo administrado en Block usa las acciones propias de cada una de sus
# reglas (normalmente Block con 403).
# =====================================================================================
set -euo pipefail

APLICAR=no; REGLA=""; ACCION=bloqueo
while [ $# -gt 0 ]; do
  case "$1" in
    --regla) REGLA=${2:?--regla necesita el nombre de la regla}; shift ;;
    --aplicar-bloqueo) APLICAR=si; ACCION=bloqueo ;;
    --volver-a-count) APLICAR=si; ACCION=count ;;
    --simular) APLICAR=no ;;
    -h|--help) sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    *) echo "Opcion desconocida: $1 (ver --help; el bloqueo se pide con --aplicar-bloqueo)" >&2; exit 2 ;;
  esac
  shift
done
[ -n "$REGLA" ] || { echo "Falta --regla <nombre> (ver --help)." >&2; exit 2; }
# shellcheck source=infra/waf/comun.sh
source "$(dirname "$0")/comun.sh"
preparar

EXISTENTE=$(acl_existente || true)
[ -n "$EXISTENTE" ] || falla "el Web ACL $NOMBRE no existe: corre antes infra/waf/crear-web-acl.sh --aplicar."
ID=$(jq -r .Id <<< "$EXISTENTE")
aws_prod wafv2 get-web-acl --name "$NOMBRE" --scope "$ALCANCE" --id "$ID" > "$TMP/vivo.json"
jq -e --arg r "$REGLA" '.WebACL.Rules[] | select(.Name == $r)' "$TMP/vivo.json" >/dev/null \
  || falla "no hay una regla llamada $REGLA. Las que hay: $(jq -r '[.WebACL.Rules[].Name] | join(", ")' "$TMP/vivo.json")"

log "Regla $REGLA"
resumen_de_reglas "$TMP/vivo.json" | grep "  $REGLA  " || true
TASA=$(jq -r --arg r "$REGLA" '.WebACL.Rules[] | select(.Name == $r) | if .Statement.RateBasedStatement then "si" else "no" end' "$TMP/vivo.json")

log "Lo que conto en los ultimos 7 dias (CloudWatch, solo lectura)"
aws_prod cloudwatch get-metric-statistics --namespace AWS/WAFV2 --metric-name CountedRequests \
  --dimensions "Name=WebACL,Value=$NOMBRE" "Name=Rule,Value=$REGLA" "Name=Region,Value=Global" \
  --start-time "$(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%SZ)" --end-time "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --period 86400 --statistics Sum --output json \
  | jq -r '.Datapoints | sort_by(.Timestamp) | if length == 0 then "  sin datos: la regla no conto nada (o el Web ACL no esta asociado todavia)" else .[] | "  \(.Timestamp[0:10])  \(.Sum | floor) pedidos contados" end'
echo "  (en una regla por tasa, lo contado son los pedidos de IP que pasaron del limite; en un grupo, los que el grupo habria actuado)"

# La accion de una regla por tasa y la de un grupo administrado son campos distintos (Action / OverrideAction).
if [ "$TASA" = si ]; then CAMPO=Action; else CAMPO=OverrideAction; fi
if [ "$ACCION" = bloqueo ]; then
  if [ "$TASA" = si ]; then NUEVA=$(jq -c .Renaser.AccionDeBloqueoPorTasa "$PLANTILLA"); else NUEVA='{"None":{}}'; fi
else
  NUEVA='{"Count":{}}'
fi

jq --arg r "$REGLA" --arg campo "$CAMPO" --argjson nueva "$NUEVA" \
   '.Rules |= map(if .Name == $r then .[$campo] = $nueva else . end)' <(entrada_de_actualizacion < "$TMP/vivo.json") > "$TMP/actualizacion.json"
log "Como quedaria"
resumen_de_reglas "$TMP/actualizacion.json" | grep "  $REGLA  " || true

if [ "$APLICAR" != si ]; then
  echo
  echo "SIMULACRO: no se cambio nada. Para bloquear (solo esta regla):"
  echo "  infra/waf/pasar-regla-a-bloqueo.sh --regla $REGLA --aplicar-bloqueo"
  exit 0
fi
respaldar "web-acl-$NOMBRE-antes-de-$ACCION-$REGLA" "$TMP/vivo.json"
aws_prod wafv2 update-web-acl --cli-input-json "file://$TMP/actualizacion.json" >/dev/null
if [ "$ACCION" = bloqueo ]; then
  echo "Listo: $REGLA ahora BLOQUEA. Mira la metrica BlockedRequests de esa regla."
  echo "Vuelta atras (solo esta regla): infra/waf/pasar-regla-a-bloqueo.sh --regla $REGLA --volver-a-count"
else
  echo "Listo: $REGLA vuelve a Count (solo cuenta)."
fi
