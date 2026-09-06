#!/usr/bin/env bash
#
# Crea, UNA SOLA VEZ, la infraestructura del panel de solicitudes de cuenta.
# Es idempotente: si algo ya existe lo reutiliza, asi que se puede volver a correr.
#
#   bash admin-panel/scripts/crear-infra.sh
#
# Para actualizar solo el codigo despues, usar `desplegar.sh`.

set -euo pipefail

# En Git Bash sobre Windows, MSYS convierte cualquier argumento que parezca ruta ("/renaser/...")
# a una ruta de Windows y la CLI responde "Parameter name must be a fully qualified name".
export MSYS_NO_PATHCONV=1

PERFIL="renaser"
REGION="us-east-1"
CUENTA_ESPERADA="302277511407"

VPC="vpc-0025b53ddda33cdf3"
SUBRED_A="subnet-0522b2bad1681744f"   # us-east-1c, la misma AZ que la EC2 del backend
SUBRED_B="subnet-0af424a3e509c0374"   # us-east-1a, para que la Lambda no dependa de una sola AZ
SG_BACKEND="sg-0ba472486a99c47a6"
BACKEND_IP_PRIVADA="172.31.26.17"
PUERTO_BACKEND="8080"

FUNCION="renaser-admin-panel"
ROL="renaser-admin-panel-lambda"
SG_PANEL_NOMBRE="renaser-admin-panel-lambda"
PARAM_CLAVE="/renaser/prod/ADMIN_PANEL_CLAVE"

# En estilo Windows (C:/...), no en estilo MSYS (/c/...): con MSYS_NO_PATHCONV=1 activo,
# `aws.exe` y `node.exe` reciben la ruta tal cual y no saben abrir `/c/Users/...`.
RAIZ="$(cygpath -m "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" 2>/dev/null \
  || (cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd))"
ZIP="$RAIZ/panel.zip"

aws() { command aws --profile "$PERFIL" --region "$REGION" "$@"; }

echo "==> Verificando que estamos en la cuenta correcta"
CUENTA="$(aws sts get-caller-identity --query Account --output text)"
if [ "$CUENTA" != "$CUENTA_ESPERADA" ]; then
  echo "ERROR: la CLI resolvio la cuenta $CUENTA, no $CUENTA_ESPERADA." >&2
  echo "       Revisa que no haya variables de entorno AWS_* pisando el perfil '$PERFIL'." >&2
  exit 1
fi
echo "    cuenta $CUENTA — ok"

# ---------------------------------------------------------------------------
# 1. Security group de la Lambda, y el permiso para llegar al backend
# ---------------------------------------------------------------------------
echo "==> Security group de la Lambda"
SG_PANEL="$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=$SG_PANEL_NOMBRE" "Name=vpc-id,Values=$VPC" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")"

if [ "$SG_PANEL" = "None" ] || [ -z "$SG_PANEL" ]; then
  SG_PANEL="$(aws ec2 create-security-group \
    --group-name "$SG_PANEL_NOMBRE" --vpc-id "$VPC" \
    --description "Panel de solicitudes de cuenta (Lambda). Solo egress hacia el backend." \
    --query GroupId --output text)"
  echo "    creado $SG_PANEL"
else
  echo "    ya existia $SG_PANEL"
fi

echo "==> Abriendo el $PUERTO_BACKEND del backend SOLO para este security group"
# No se toca la regla que ya existe para la IP del dueno, y NO se abre a internet:
# la fuente es el SG de la Lambda, no un CIDR.
aws ec2 authorize-security-group-ingress \
  --group-id "$SG_BACKEND" \
  --ip-permissions "IpProtocol=tcp,FromPort=$PUERTO_BACKEND,ToPort=$PUERTO_BACKEND,UserIdGroupPairs=[{GroupId=$SG_PANEL,Description='Panel admin (Lambda)'}]" \
  >/dev/null 2>&1 && echo "    regla agregada" || echo "    la regla ya existia"

# ---------------------------------------------------------------------------
# 2. Rol de ejecucion
# ---------------------------------------------------------------------------
echo "==> Rol de ejecucion"
if aws iam get-role --role-name "$ROL" >/dev/null 2>&1; then
  echo "    ya existia"
else
  aws iam create-role --role-name "$ROL" \
    --description "Ejecucion del panel de solicitudes de cuenta. Sin permisos de datos: la Lambda solo habla HTTP con el backend." \
    --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}' \
    >/dev/null
  echo "    creado"
fi
# Unica politica: ENIs en la VPC + logs. Nada de SSM, S3 ni base de datos.
aws iam attach-role-policy --role-name "$ROL" \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole
ARN_ROL="arn:aws:iam::$CUENTA:role/$ROL"

# ---------------------------------------------------------------------------
# 3. Clave del panel -> Parameter Store, y su verificador
# ---------------------------------------------------------------------------
echo "==> Clave del panel"
if aws ssm get-parameter --name "$PARAM_CLAVE" >/dev/null 2>&1; then
  echo "    $PARAM_CLAVE ya existe, se reutiliza"
else
  CLAVE_NUEVA="$(node -e "console.log(require('node:crypto').randomBytes(24).toString('base64url'))")"
  aws ssm put-parameter --name "$PARAM_CLAVE" --type SecureString \
    --description "Clave de acceso al panel de solicitudes de cuenta. Se pide en el ingreso, ANTES del correo y la contrasena." \
    --value "$CLAVE_NUEVA" >/dev/null
  echo "    creada y guardada en Parameter Store"
fi

CLAVE="$(aws ssm get-parameter --name "$PARAM_CLAVE" --with-decryption --query Parameter.Value --output text)"
VERIFICADOR="$(printf '%s' "$CLAVE" | node "$RAIZ/scripts/verificador.mjs")"
unset CLAVE

# ---------------------------------------------------------------------------
# 4. La funcion
# ---------------------------------------------------------------------------
echo "==> Empaquetando"
node "$RAIZ/scripts/empaquetar.mjs" "$ZIP" "$RAIZ/src/index.mjs"

echo "==> Funcion Lambda"
if aws lambda get-function --function-name "$FUNCION" >/dev/null 2>&1; then
  echo "    ya existia; actualizando codigo y configuracion"
  aws lambda update-function-code --function-name "$FUNCION" --zip-file "fileb://$ZIP" >/dev/null
  aws lambda wait function-updated --function-name "$FUNCION"
  aws lambda update-function-configuration --function-name "$FUNCION" \
    --environment "Variables={BACKEND_BASE_URL=http://$BACKEND_IP_PRIVADA:$PUERTO_BACKEND,CLAVE_PANEL_VERIFICADOR=$VERIFICADOR}" >/dev/null
else
  # El rol recien creado tarda unos segundos en propagarse en IAM; se reintenta.
  for intento in 1 2 3 4 5 6; do
    if aws lambda create-function --function-name "$FUNCION" \
      --runtime nodejs22.x --architectures arm64 --handler index.handler \
      --role "$ARN_ROL" --zip-file "fileb://$ZIP" \
      --timeout 15 --memory-size 256 \
      --description "Panel minimo de solicitudes de cuenta de Renaser OS" \
      --vpc-config "SubnetIds=$SUBRED_A,$SUBRED_B,SecurityGroupIds=$SG_PANEL" \
      --environment "Variables={BACKEND_BASE_URL=http://$BACKEND_IP_PRIVADA:$PUERTO_BACKEND,CLAVE_PANEL_VERIFICADOR=$VERIFICADOR}" \
      >/dev/null 2>&1; then
      echo "    creada"
      break
    fi
    echo "    esperando a que IAM propague el rol (intento $intento)"
    sleep 10
  done
fi
aws lambda wait function-updated --function-name "$FUNCION"

# ---------------------------------------------------------------------------
# 5. Function URL (HTTPS gratis) — AuthType AWS_IAM
#
# Se probo primero con `NONE` y la URL respondia 403 con AccessDeniedException aunque la
# politica de recurso fuera la correcta. La causa es **Lambda Block Public Access**: en las
# funciones nuevas viene en denegar por defecto, y bloquea el acceso publico sin importar lo
# que diga la politica. Ver docs/BITACORA_ERRORES.md E-130.
#
# Se eligio AWS_IAM en vez de desactivar esa proteccion: deja la URL fuera del alcance de
# internet (sin credenciales de esta cuenta no responde) y la incomodidad de firmar SigV4 la
# resuelve `scripts/abrir-panel.py` del lado del navegador.
# ---------------------------------------------------------------------------
echo "==> Function URL"
if aws lambda get-function-url-config --function-name "$FUNCION" >/dev/null 2>&1; then
  aws lambda update-function-url-config --function-name "$FUNCION" --auth-type AWS_IAM >/dev/null
else
  aws lambda create-function-url-config --function-name "$FUNCION" --auth-type AWS_IAM >/dev/null
fi
URL="$(aws lambda get-function-url-config --function-name "$FUNCION" --query FunctionUrl --output text)"

rm -f "$ZIP"

echo
echo "=========================================================================="
echo " Panel listo:  $URL   (AWS_IAM: no responde sin credenciales de la cuenta)"
echo
echo " Para abrirlo en el navegador:"
echo "   python admin-panel/scripts/abrir-panel.py   ->   http://127.0.0.1:8788"
echo
echo " Adentro hacen falta TRES cosas:"
echo "   1. la clave del panel  ->  aws ssm get-parameter --name $PARAM_CLAVE \\"
echo "                                 --with-decryption --profile $PERFIL --region $REGION \\"
echo "                                 --query Parameter.Value --output text"
echo "   2. el correo de una cuenta Renaser con rol ADMIN o ALCHEMIST"
echo "   3. su contrasena"
echo "=========================================================================="
