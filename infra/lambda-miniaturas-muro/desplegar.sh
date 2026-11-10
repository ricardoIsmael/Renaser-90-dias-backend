#!/usr/bin/env bash
# Crea o actualiza en AWS producción la Lambda de miniaturas del Muro (spec 049, D-339). Lo corre el dueño:
#   bash infra/lambda-miniaturas-muro/construir.sh && bash infra/lambda-miniaturas-muro/desplegar.sh
#
# Qué crea (todo con nombre renaser-miniaturas-muro):
#   - un rol con permiso SOLO para leer muro/fotos/*, escribir y borrar muro/miniaturas/* del bucket, y escribir sus logs;
#   - la función (Node.js 22, arm64, 512 MB, 30 s), fuera de la VPC (solo habla con S3);
#   - el aviso del bucket: al crear o borrar algo bajo muro/fotos/ se invoca la función. Se SUMA a los avisos que ya tenga
#     el bucket (put-bucket-notification-configuration reemplaza todo: por eso se lee, se agrega y se vuelve a escribir).
# Costo: dentro de la capa gratuita para el volumen del Muro.
set -euo pipefail
cd "$(dirname "$0")"
A="aws --profile prod --region us-east-1"
BUCKET=renaser90dias-prod
NOMBRE=renaser-miniaturas-muro
CUENTA=$($A sts get-caller-identity --query Account --output text)
echo "Cuenta: $CUENTA  (debe ser 302277511407) · bucket $BUCKET"
[[ -f lambda.zip ]] || { echo "Falta lambda.zip: corre primero construir.sh"; exit 1; }
read -rp "¿Seguir? escribe si: " r; [[ $r == si ]] || exit 1

if ! $A iam get-role --role-name "$NOMBRE" >/dev/null 2>&1; then
  $A iam create-role --role-name "$NOMBRE" --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}' >/dev/null
  sleep 10
fi
$A iam put-role-policy --role-name "$NOMBRE" --policy-name miniaturas --policy-document '{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":"s3:GetObject","Resource":"arn:aws:s3:::'"$BUCKET"'/muro/fotos/*"},
 {"Effect":"Allow","Action":["s3:PutObject","s3:DeleteObject"],"Resource":"arn:aws:s3:::'"$BUCKET"'/muro/miniaturas/*"},
 {"Effect":"Allow","Action":["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"],"Resource":"arn:aws:logs:us-east-1:'"$CUENTA"':*"}]}'
echo "1 rol listo"

ARN_ROL="arn:aws:iam::$CUENTA:role/$NOMBRE"
if $A lambda get-function --function-name "$NOMBRE" >/dev/null 2>&1; then
  $A lambda update-function-code --function-name "$NOMBRE" --zip-file fileb://lambda.zip >/dev/null
else
  for i in 1 2 3 4 5; do   # el rol recién creado tarda en estar disponible para Lambda
    $A lambda create-function --function-name "$NOMBRE" --runtime nodejs22.x --architectures arm64 --handler index.handler \
      --role "$ARN_ROL" --zip-file fileb://lambda.zip --memory-size 512 --timeout 30 \
      --description "Miniaturas del Muro (spec 049)" >/dev/null && break || sleep 8
  done
fi
$A lambda wait function-active-v2 --function-name "$NOMBRE"
echo "2 función lista"

$A lambda add-permission --function-name "$NOMBRE" --statement-id s3-invoca --action lambda:InvokeFunction \
  --principal s3.amazonaws.com --source-arn "arn:aws:s3:::$BUCKET" --source-account "$CUENTA" >/dev/null 2>&1 || true
ARN_FUNCION=$($A lambda get-function --function-name "$NOMBRE" --query Configuration.FunctionArn --output text)

ACTUAL=$($A s3api get-bucket-notification-configuration --bucket "$BUCKET" --output json)
NUEVA=$(python3 - "$ACTUAL" "$ARN_FUNCION" <<'PY'
import json, sys
conf = json.loads(sys.argv[1] or '{}'); arn = sys.argv[2]
conf.pop('ResponseMetadata', None)
lista = [c for c in conf.get('LambdaFunctionConfigurations', []) if c.get('Id') != 'miniaturas-muro']
lista.append({'Id': 'miniaturas-muro', 'LambdaFunctionArn': arn, 'Events': ['s3:ObjectCreated:*', 's3:ObjectRemoved:*'],
              'Filter': {'Key': {'FilterRules': [{'Name': 'prefix', 'Value': 'muro/fotos/'}]}}})
conf['LambdaFunctionConfigurations'] = lista
print(json.dumps(conf))
PY
)
$A s3api put-bucket-notification-configuration --bucket "$BUCKET" --notification-configuration "$NUEVA"
echo "3 aviso del bucket listo (muro/fotos/ → $NOMBRE)"
echo "Listo. Para las fotos que ya existían: bash $(dirname "$0")/rellenar.sh"
