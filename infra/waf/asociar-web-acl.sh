#!/usr/bin/env bash
# =====================================================================================
# Asocia el Web ACL a la distribucion de CloudFront (spec 050 tramo B: FR-005 y FR-011; decision D2).
# Lo corre el DUENO, desde su maquina, con el perfil `prod`. Nunca un agente ni el CD.
#
# En CloudFront un Web ACL no se asocia con `associate-web-acl` (eso es para recursos regionales): se pone su ARN
# en `WebACLId` de la configuracion de la distribucion. Este script lo hace con `get-distribution-config` ->
# parche -> `update-distribution` con el ETag, y espera a que la distribucion quede desplegada.
#
#   0. Variables:  export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#                  export RENASER_DISTRIBUCION=<id de la distribucion de CloudFront del backend>
#   1. Simulacro:  infra/waf/asociar-web-acl.sh
#   2. Asociar:    infra/waf/asociar-web-acl.sh --aplicar          # el Web ACL nace en Count: no bloquea nada
#
# VUELTA ATRAS (desasociar; el Web ACL sigue existiendo, borrarlo es crear-web-acl.sh --revertir):
#        infra/waf/asociar-web-acl.sh --revertir [--aplicar]
#   Quita SOLO el WebACLId: no restaura una copia completa de la distribucion, asi no pisa lo que se haya cambiado
#   despues (la cabecera de origen del tramo A2, por ejemplo). Antes de cada cambio se guarda la configuracion
#   completa con su ETag en $RESPALDOS (fuera del repositorio, permiso 600).
#
# Si la distribucion ya tiene OTRO Web ACL, se niega: --reemplazar lo cambia a sabiendas.
# =====================================================================================
set -euo pipefail

APLICAR=no; SIMULAR=no; REVERTIR=no; REEMPLAZAR=no
while [ $# -gt 0 ]; do
  case "$1" in
    --simular) SIMULAR=si ;;
    --aplicar) APLICAR=si ;;
    --revertir) REVERTIR=si ;;
    --reemplazar) REEMPLAZAR=si ;;
    -h|--help) sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    *) echo "Opcion desconocida: $1 (ver --help)" >&2; exit 2 ;;
  esac
  shift
done
[ "$SIMULAR" = si ] && APLICAR=no
# shellcheck source=infra/waf/comun.sh
source "$(dirname "$0")/comun.sh"
preparar
: "${RENASER_DISTRIBUCION:?exporta RENASER_DISTRIBUCION con el id de la distribucion de CloudFront}"
DIST=$RENASER_DISTRIBUCION

aws_prod cloudfront get-distribution-config --id "$DIST" > "$TMP/config.json"
ETAG=$(jq -r .ETag "$TMP/config.json")
ACTUAL=$(jq -r '.DistributionConfig.WebACLId // ""' "$TMP/config.json")

actualizar_y_esperar() {  # <archivo con el DistributionConfig nuevo>
  aws_prod cloudfront update-distribution --id "$DIST" --if-match "$ETAG" --distribution-config "file://$1" >/dev/null
  echo "Distribucion actualizada; esperando a que se despliegue (puede tardar unos minutos)..."
  aws_prod cloudfront wait distribution-deployed --id "$DIST"
  echo "Distribucion desplegada."
}

if [ "$REVERTIR" = si ]; then
  log "Desasociar el Web ACL de la distribucion $DIST"
  if [ -z "$ACTUAL" ]; then echo "La distribucion no tiene ningun Web ACL asociado: nada que quitar."; exit 0; fi
  if [ "$APLICAR" != si ]; then echo "Simulacro: se guardaria un respaldo y se quitaria WebACLId (hoy: $ACTUAL)."; exit 0; fi
  respaldar "cloudfront-$DIST-antes-de-desasociar" "$TMP/config.json"
  jq '.DistributionConfig | .WebACLId = ""' "$TMP/config.json" > "$TMP/nueva.json"
  actualizar_y_esperar "$TMP/nueva.json"
  echo "Listo: la distribucion ya no pasa por el WAF. Para borrar el Web ACL: infra/waf/crear-web-acl.sh --revertir --aplicar"
  exit 0
fi

log "Asociar el Web ACL $NOMBRE a la distribucion $DIST"
EXISTENTE=$(acl_existente || true)
[ -n "$EXISTENTE" ] || falla "el Web ACL $NOMBRE no existe: corre antes infra/waf/crear-web-acl.sh --aplicar."
ARN=$(jq -r .ARN <<< "$EXISTENTE")
aws_prod wafv2 get-web-acl --name "$NOMBRE" --scope "$ALCANCE" --id "$(jq -r .Id <<< "$EXISTENTE")" > "$TMP/acl.json"
echo "Reglas del Web ACL ahora:"; resumen_de_reglas "$TMP/acl.json"
if [ "$ACTUAL" = "$ARN" ]; then echo "La distribucion ya tiene este Web ACL asociado: nada que cambiar (idempotente)."; exit 0; fi
if [ -n "$ACTUAL" ] && [ "$REEMPLAZAR" != si ]; then
  falla "la distribucion ya tiene OTRO Web ACL asociado ($ACTUAL). No se pisa; --reemplazar lo cambia a sabiendas."
fi
if [ "$APLICAR" != si ]; then
  echo "Simulacro: se guardaria un respaldo con su ETag ($ETAG) y se pondria WebACLId = $ARN (update-distribution y espera a 'Deployed')."
  echo "Mientras todas las reglas esten en Count, el trafico no cambia: solo se cuenta."
  echo "Vuelta atras: infra/waf/asociar-web-acl.sh --revertir --aplicar"
  exit 0
fi
respaldar "cloudfront-$DIST-antes-de-asociar" "$TMP/config.json"
jq --arg acl "$ARN" '.DistributionConfig | .WebACLId = $acl' "$TMP/config.json" > "$TMP/nueva.json"
actualizar_y_esperar "$TMP/nueva.json"
echo
echo "Listo. En CloudWatch (us-east-1, espacio AWS/WAFV2, region Global) ya se cuentan los pedidos; el registro va a aws-waf-logs-renaser."
echo "Siguiente: 7 dias de metricas y muestras; despues infra/waf/pasar-regla-a-bloqueo.sh (simulacro) regla por regla."
