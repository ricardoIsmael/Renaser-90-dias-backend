#!/usr/bin/env bash
# =====================================================================================
# Crea el PRIMER administrador de un entorno nuevo. Se corre UNA sola vez, a mano.
#
# POR QUE EXISTE ESTE SCRIPT Y NO UN CASO DE USO
#
# Dar de alta un usuario exige pasar por `ApproveAccountRequestUseCase` o
# `InviteAndCreateUserUseCase`, y los dos exigen un admin que apruebe. En una base recien
# creada no hay ninguno: es el problema del huevo y la gallina que tiene todo sistema con
# altas controladas. La unica salida es un alta directa, hecha una vez, por quien administra
# la base — no por la aplicacion.
#
# A partir de aca, TODO lo demas pasa por los casos de uso. Esta es la excepcion, y es una sola.
#
# LA CONTRASENA NUNCA SE IMPRIME
#
# Se genera dentro de la instancia, se guarda cifrada en Parameter Store, y se lee despues con
# un comando aparte. No aparece en la salida de este script, ni en el historial de la terminal,
# ni en los registros de SSM.
#
# USO:
#   bash scripts/crear-primer-admin.sh "correo@ejemplo.com" "Nombre Completo"
# =====================================================================================
set -euo pipefail

CORREO="${1:?Falta el correo. Uso: $0 <correo> <nombre completo>}"
NOMBRE="${2:?Falta el nombre. Uso: $0 <correo> <nombre completo>}"

PERFIL=renaser
INSTANCIA=i-0ea00f555c5fe8028
REGION=us-east-1
RDS=renaser-prod.cm7cywwku4wf.us-east-1.rds.amazonaws.com

# En Git Bash sobre Windows, sin esto la CLI recibe "/renaser/prod/..." convertido a una ruta
# de Windows y falla con "Parameter name must be a fully qualified name".
export MSYS_NO_PATHCONV=1

echo "Cuenta destino:"
aws sts get-caller-identity --profile "$PERFIL" --query "[Account,Arn]" --output text
echo "Tiene que decir 302277511407. Si dice otra cosa, cortá acá (Ctrl+C)."
read -r -p "Enter para continuar... " _

CMD=$(aws ssm send-command --profile "$PERFIL" --region "$REGION" \
  --instance-ids "$INSTANCIA" --document-name AWS-RunShellScript \
  --parameters "commands=[
    \"set -e\",
    \"P=\\\$(aws ssm get-parameter --name /renaser/prod/DB_PASSWORD --with-decryption --query Parameter.Value --output text --region $REGION)\",
    \"CLAVE=\\\$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)\",
    \"HASH=\\\$(docker run --rm httpd:alpine htpasswd -bnBC 10 '' \\\"\\\$CLAVE\\\" | tr -d ':\\\\n')\",
    \"aws ssm put-parameter --region $REGION --name /renaser/prod/BOOTSTRAP_ADMIN_PASSWORD --type SecureString --overwrite --value \\\"\\\$CLAVE\\\" >/dev/null\",
    \"docker run --rm -e PGPASSWORD=\\\"\\\$P\\\" -e H=\\\"{bcrypt}\\\$HASH\\\" postgres:16-alpine psql -h $RDS -U renaser -d renaser -v ON_ERROR_STOP=1 -c \\\"INSERT INTO renaser.usuarios (id,email,nombre_completo,rol,estado,hash_contrasena,contrasena_actualizada_en) VALUES (gen_random_uuid(),'$CORREO','$NOMBRE','ALQUIMISTA','ACTIVO',current_setting('H',true),now()) ON CONFLICT (email) DO NOTHING\\\"\",
    \"docker run --rm -e PGPASSWORD=\\\"\\\$P\\\" postgres:16-alpine psql -h $RDS -U renaser -d renaser -t -A -c \\\"SELECT email, rol, estado, (hash_contrasena IS NOT NULL) AS tiene_clave FROM renaser.usuarios\\\"\"
  ]" --query "Command.CommandId" --output text)

echo "Ejecutando en la instancia (unos 40 segundos)..."
sleep 45
aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" \
  --command-id "$CMD" --instance-id "$INSTANCIA" \
  --query "[Status,StandardOutputContent,StandardErrorContent]" --output text | tail -8

cat <<FIN

---------------------------------------------------------------
Para ver la contrasena (una sola vez, y despues cambiala desde la app):

  aws ssm get-parameter --profile $PERFIL --region $REGION \\
      --name "/renaser/prod/BOOTSTRAP_ADMIN_PASSWORD" \\
      --with-decryption --query Parameter.Value --output text

Cuando ya entraste y le pusiste una contrasena propia, borrala:

  aws ssm delete-parameter --profile $PERFIL --region $REGION \\
      --name "/renaser/prod/BOOTSTRAP_ADMIN_PASSWORD"

Y quitale a la instancia el permiso temporal de escritura:

  aws iam delete-role-policy --profile $PERFIL \\
      --role-name renaser-backend-ec2 --policy-name bootstrap-primer-admin
---------------------------------------------------------------
FIN
