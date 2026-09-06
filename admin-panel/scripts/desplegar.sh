#!/usr/bin/env bash
#
# Actualiza SOLO el codigo del panel (src/index.mjs). No toca la VPC, ni el rol, ni la clave.
#
#   bash admin-panel/scripts/desplegar.sh
#
# Si cambio la clave del panel en Parameter Store, correr esto con RECARGAR_CLAVE=1 para que
# la Lambda vuelva a calcular el verificador:
#
#   RECARGAR_CLAVE=1 bash admin-panel/scripts/desplegar.sh

set -euo pipefail
export MSYS_NO_PATHCONV=1

PERFIL="renaser"
REGION="us-east-1"
FUNCION="renaser-admin-panel"
PARAM_CLAVE="/renaser/prod/ADMIN_PANEL_CLAVE"

# Ver la nota de crear-infra.sh: con MSYS_NO_PATHCONV=1 la ruta tiene que ir en estilo Windows.
RAIZ="$(cygpath -m "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" 2>/dev/null \
  || (cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd))"
ZIP="$RAIZ/panel.zip"

aws() { command aws --profile "$PERFIL" --region "$REGION" "$@"; }

node "$RAIZ/scripts/empaquetar.mjs" "$ZIP" "$RAIZ/src/index.mjs"

aws lambda update-function-code --function-name "$FUNCION" --zip-file "fileb://$ZIP" \
  --query 'LastModified' --output text
aws lambda wait function-updated --function-name "$FUNCION"

if [ "${RECARGAR_CLAVE:-0}" = "1" ]; then
  CLAVE="$(aws ssm get-parameter --name "$PARAM_CLAVE" --with-decryption --query Parameter.Value --output text)"
  VERIFICADOR="$(printf '%s' "$CLAVE" | node "$RAIZ/scripts/verificador.mjs")"
  unset CLAVE
  ACTUAL="$(aws lambda get-function-configuration --function-name "$FUNCION" \
    --query 'Environment.Variables.BACKEND_BASE_URL' --output text)"
  aws lambda update-function-configuration --function-name "$FUNCION" \
    --environment "Variables={BACKEND_BASE_URL=$ACTUAL,CLAVE_PANEL_VERIFICADOR=$VERIFICADOR}" >/dev/null
  aws lambda wait function-updated --function-name "$FUNCION"
  echo "verificador de la clave actualizado"
fi

rm -f "$ZIP"
aws lambda get-function-url-config --function-name "$FUNCION" --query FunctionUrl --output text
