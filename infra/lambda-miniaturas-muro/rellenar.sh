#!/usr/bin/env bash
# Arma la miniatura de las fotos del Muro que ya existían antes de la Lambda (spec 049). Invoca la función una vez por foto,
# de forma asíncrona, con el mismo evento que mandaría S3. Se puede correr de nuevo sin problema: rehace las que ya estén.
set -euo pipefail
A="aws --profile prod --region us-east-1"
BUCKET=renaser90dias-prod
NOMBRE=renaser-miniaturas-muro
CLAVES=$($A s3api list-objects-v2 --bucket "$BUCKET" --prefix muro/fotos/ --query 'Contents[].Key' --output text | tr '\t' '\n' | grep -v '^None$' || true)
TOTAL=$(echo "$CLAVES" | grep -c . || true)
echo "Fotos del Muro: $TOTAL"
read -rp "¿Armar sus miniaturas? escribe si: " r; [[ $r == si ]] || exit 1
n=0
while read -r clave; do
  [[ -z $clave ]] && continue
  evento=$(python3 -c 'import json,sys;print(json.dumps({"Records":[{"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":sys.argv[1]},"object":{"key":sys.argv[2]}}}]}))' "$BUCKET" "$clave")
  $A lambda invoke --function-name "$NOMBRE" --invocation-type Event --cli-binary-format raw-in-base64-out --payload "$evento" /dev/null >/dev/null
  n=$((n+1)); (( n % 50 == 0 )) && echo "  $n / $TOTAL"
done <<<"$CLAVES"
echo "Pedidas $n miniaturas. En unos minutos están en muro/miniaturas/ (logs: /aws/lambda/$NOMBRE)."
