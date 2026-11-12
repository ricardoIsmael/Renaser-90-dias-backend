#!/usr/bin/env bash
# =====================================================================================
# Le da al rol del CI (`renaser-github-actions`, el mismo del CD) el permiso MINIMO para actualizar la Lambda de miniaturas del
# Muro desde `.github/workflows/lambda-miniaturas.yml`. Lo corre el DUENO, desde su maquina, con el perfil `prod`. Nunca un agente
# ni el CD. Se corre UNA vez, antes del primer despliegue automatico.
#
# Pone una politica EN LINEA en el rol, `actualizar-lambda-miniaturas`, con lo que dice `permiso-del-ci.plantilla.json`:
#   lambda:UpdateFunctionCode, lambda:GetFunction, lambda:GetFunctionConfiguration y lambda:InvokeFunction
#   SOLO sobre arn:aws:lambda:<region>:<cuenta>:function:renaser-miniaturas-muro   (la cuenta sale de AWS, no del repositorio)
# GetFunction lo pide `aws lambda wait function-updated-v2`; InvokeFunction, la invocacion de prueba que no toca el bucket.
# Nada de UpdateFunctionConfiguration, CreateFunction, DeleteFunction, iam:PassRole ni S3: el rol no puede cambiar el rol de la
# funcion, ni su disparador, ni leer o escribir fotos. Las otras dos politicas del rol (desplegar-por-ssm, publicar-en-ecr) no se tocan.
#
#   0. Variable:   export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#   1. Simulacro:  infra/lambda-miniaturas-muro/permiso-del-ci.sh            # por omision: muestra y no cambia nada
#   2. Aplicar:    infra/lambda-miniaturas-muro/permiso-del-ci.sh --aplicar
#   3. Comprobar (opcional, de solo lectura):
#        aws iam simulate-principal-policy --profile prod --policy-source-arn arn:aws:iam::<cuenta>:role/renaser-github-actions \
#          --action-names lambda:UpdateFunctionCode lambda:InvokeFunction --resource-arns <arn de la funcion> \
#          --query 'EvaluationResults[].[EvalActionName,EvalDecision]' --output text
#
# Es idempotente: si la politica ya es la misma, no hace nada; si difiere, guarda la anterior en $RESPALDOS (fuera del
# repositorio, permiso 600) y la reemplaza.
#
# VUELTA ATRAS (borra SOLO esa politica en linea; el CD y el resto del rol siguen igual; el workflow vuelve a fallar con AccessDenied):
#        infra/lambda-miniaturas-muro/permiso-del-ci.sh --revertir [--aplicar]
# =====================================================================================
set -euo pipefail

APLICAR=no; SIMULAR=no; REVERTIR=no
while [ $# -gt 0 ]; do
  case "$1" in
    --simular) SIMULAR=si ;;
    --aplicar) APLICAR=si ;;
    --revertir) REVERTIR=si ;;
    -h|--help) sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    *) echo "Opcion desconocida: $1 (ver --help)" >&2; exit 2 ;;
  esac
  shift
done
[ "$SIMULAR" = si ] && APLICAR=no

PERFIL=prod
REGION=us-east-1
ROL=renaser-github-actions
POLITICA=actualizar-lambda-miniaturas
FUNCION=renaser-miniaturas-muro
ACCIONES='["lambda:GetFunction","lambda:GetFunctionConfiguration","lambda:InvokeFunction","lambda:UpdateFunctionCode"]'
AQUI=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PLANTILLA="$AQUI/permiso-del-ci.plantilla.json"
RESPALDOS=${RENASER_RESPALDOS:-$HOME/.renaser/respaldos}

log() { echo "== $* =="; }
falla() { echo "ERROR: $*" >&2; exit 1; }
# Toda llamada a AWS pasa por aqui: perfil y region explicitos, nunca los de por defecto.
aws_prod() { aws "$@" --profile "$PERFIL" --region "$REGION"; }

for h in aws jq; do command -v "$h" >/dev/null || falla "falta $h en esta maquina."; done
: "${RENASER_CUENTA_AWS:?exporta RENASER_CUENTA_AWS con el id de 12 digitos de la cuenta de produccion}"
[[ $RENASER_CUENTA_AWS =~ ^[0-9]{12}$ ]] || falla "RENASER_CUENTA_AWS no son 12 digitos."
CUENTA=$(aws_prod sts get-caller-identity --query Account --output text)
[ "$CUENTA" = "$RENASER_CUENTA_AWS" ] || falla "el perfil $PERFIL apunta a la cuenta $CUENTA, no a la esperada ($RENASER_CUENTA_AWS). No se cambio nada."
echo "Cuenta verificada ($CUENTA, perfil $PERFIL, region $REGION). Modo: $([ "$APLICAR" = si ] && echo APLICAR || echo SIMULACRO)."

TMP=$(mktemp -d)
# Comillas dobles A PROPOSITO: la ruta se fija ahora; con comillas simples `$TMP` se expandiria al salir (E-679).
# shellcheck disable=SC2064
trap "rm -rf '$TMP'" EXIT
umask 077

ARN_FUNCION="arn:aws:lambda:$REGION:$CUENTA:function:$FUNCION"
# El documento tal como lo recibe AWS: la plantilla con la region y la cuenta puestas, con las llaves en orden para poder compararlo.
jq -S --arg region "$REGION" --arg cuenta "$CUENTA" \
  '.Statement |= map(.Resource |= gsub("<REGION>"; $region) | .Resource |= gsub("<CUENTA>"; $cuenta))' "$PLANTILLA" > "$TMP/deseada.json"

# Defensa extra: si alguien edita la plantilla y le suma una accion o un comodin, el script se niega antes de hablar con IAM.
jq -e --argjson acciones "$ACCIONES" --arg arn "$ARN_FUNCION" '
  (.Statement | length) == 1
  and (.Statement[0].Effect == "Allow")
  and ([.Statement[0].Action] | flatten | sort) == $acciones
  and (.Statement[0].Resource == $arn)' "$TMP/deseada.json" >/dev/null \
  || falla "la plantilla ($PLANTILLA) no es la politica minima esperada: solo $ACCIONES sobre $ARN_FUNCION."

respaldar() {  # <nombre del respaldo> <archivo>: copia con permiso 600 fuera del repositorio
  mkdir -p "$RESPALDOS"; chmod 700 "$RESPALDOS"
  local destino
  destino="$RESPALDOS/$1-$(date -u +%Y%m%dT%H%M%SZ).json"
  cp "$2" "$destino"; chmod 600 "$destino"
  echo "Respaldo en $destino"
}

aws_prod iam get-role --role-name "$ROL" --query Role.Arn --output text >/dev/null 2>"$TMP/error" \
  || falla "no pude leer el rol $ROL: $(cat "$TMP/error")"

# La politica que el rol tiene hoy con ese nombre (ausente = NoSuchEntity; cualquier otro error corta: no se adivina).
HAY=no
if aws_prod iam get-role-policy --role-name "$ROL" --policy-name "$POLITICA" --query PolicyDocument --output json >"$TMP/actual.raw" 2>"$TMP/error"; then
  HAY=si
  jq -S . "$TMP/actual.raw" > "$TMP/actual.json"
elif ! grep -q NoSuchEntity "$TMP/error"; then
  falla "no pude leer la politica $POLITICA del rol $ROL: $(cat "$TMP/error")"
fi

if [ "$REVERTIR" = si ]; then
  log "Quitar la politica $POLITICA del rol $ROL"
  if [ "$HAY" = no ]; then echo "El rol no tiene la politica $POLITICA: nada que quitar."; exit 0; fi
  if [ "$APLICAR" != si ]; then echo "Simulacro: se guardaria un respaldo y se borraria la politica $POLITICA (--aplicar para hacerlo)."; exit 0; fi
  respaldar "iam-$ROL-$POLITICA-antes-de-quitar" "$TMP/actual.json"
  aws_prod iam delete-role-policy --role-name "$ROL" --policy-name "$POLITICA"
  echo "Listo: el rol ya no puede tocar la Lambda. El workflow lambda-miniaturas.yml fallara con AccessDenied hasta que se vuelva a aplicar."
  exit 0
fi

log "Dar al rol $ROL el permiso de actualizar $FUNCION"
if aws_prod lambda get-function --function-name "$FUNCION" --query Configuration.FunctionArn --output text >"$TMP/funcion" 2>"$TMP/error"; then
  [ "$(cat "$TMP/funcion")" = "$ARN_FUNCION" ] || echo "AVISO: la funcion real es $(cat "$TMP/funcion") y la politica apunta a $ARN_FUNCION."
else
  echo "AVISO: no pude leer la funcion $FUNCION en $REGION ($(head -c 200 "$TMP/error")). La politica se puede poner igual."
fi
echo "Politica que se pone (politica en linea $POLITICA):"
jq . "$TMP/deseada.json"

if [ "$HAY" = si ] && cmp -s "$TMP/actual.json" "$TMP/deseada.json"; then
  echo "La politica $POLITICA ya esta aplicada y es la misma: nada que hacer."
  exit 0
fi
if [ "$HAY" = si ]; then
  echo "El rol ya tiene una politica $POLITICA DISTINTA. Diferencias (- hoy, + la que se pondria):"
  diff -u "$TMP/actual.json" "$TMP/deseada.json" || true
fi
if [ "$APLICAR" != si ]; then
  echo "Simulacro: no se cambio nada. Para aplicarla: $0 --aplicar"
  exit 0
fi

if [ "$HAY" = si ]; then respaldar "iam-$ROL-$POLITICA-antes-de-reemplazar" "$TMP/actual.json"; fi
aws_prod iam put-role-policy --role-name "$ROL" --policy-name "$POLITICA" --policy-document "file://$TMP/deseada.json"
# Se vuelve a leer: lo que quedo en IAM tiene que ser lo que se queria.
aws_prod iam get-role-policy --role-name "$ROL" --policy-name "$POLITICA" --query PolicyDocument --output json | jq -S . > "$TMP/quedo.json"
cmp -s "$TMP/quedo.json" "$TMP/deseada.json" || falla "IAM guardo una politica distinta de la pedida. Revisala con: aws iam get-role-policy --profile $PERFIL --role-name $ROL --policy-name $POLITICA"
echo "Listo: $ROL puede actualizar $FUNCION. Prueba: Actions → «Lambda de miniaturas» → Run workflow (desde master)."
echo "Para quitarlo: $0 --revertir --aplicar"
