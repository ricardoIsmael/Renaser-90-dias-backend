#!/usr/bin/env bash
# Abre un tunel desde tu maquina hasta la base de produccion (RDS `renaser-prod`), pasando por la
# EC2 del backend con Session Manager de SSM. Es la unica forma de llegar a la base desde afuera:
# desde el 2026-11-25 la RDS no tiene IP publica (D-370).
#
#   scripts/base-prod-tunel.sh            # deja la base en localhost:15432 hasta que hagas Ctrl+C
#   PUERTO_LOCAL=5433 scripts/base-prod-tunel.sh
#
# Con el tunel abierto, en otra terminal (usuario y clave en Parameter Store, /renaser/prod/DB_USERNAME y DB_PASSWORD):
#
#   pg_dump -h localhost -p 15432 -U <usuario> -d renaser -Fc -f renaser-prod.dump
#   pg_restore -h localhost -p 5432 -U postgres -d <base-local-clonada> --no-owner renaser-prod.dump
#
# Solo para LEER o CLONAR: nunca se prueba contra produccion (AGENTS.md, regla 4).
#
# Requisitos: AWS CLI con el perfil `prod` y el session-manager-plugin
# (https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html).
# No hace falta abrir ningun puerto ni grupo de seguridad: el tunel va por el agente de SSM de la EC2,
# que ya llega a la base porque el SG de la RDS acepta al SG del backend.
set -euo pipefail

PERFIL=${AWS_PROFILE:-prod}
INSTANCIA=${INSTANCIA:-i-0ea00f555c5fe8028}   # renaser-backend
BASE=${BASE:-renaser-prod.cm7cywwku4wf.us-east-1.rds.amazonaws.com}
PUERTO_LOCAL=${PUERTO_LOCAL:-15432}

if ! command -v session-manager-plugin >/dev/null 2>&1; then
  echo "Falta el session-manager-plugin de AWS. Instalalo (ver la cabecera de este script) y vuelve a correrlo." >&2
  exit 1
fi

cuenta=$(aws sts get-caller-identity --profile "$PERFIL" --query Account --output text)
if [ "$cuenta" != 302277511407 ]; then
  echo "El perfil '$PERFIL' apunta a la cuenta $cuenta, no a la de produccion (302277511407). Usa AWS_PROFILE=prod." >&2
  exit 1
fi

echo "Base de produccion en localhost:${PUERTO_LOCAL} (Ctrl+C para cerrar el tunel)."
exec aws ssm start-session --profile "$PERFIL" \
  --target "$INSTANCIA" \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters "{\"host\":[\"$BASE\"],\"portNumber\":[\"5432\"],\"localPortNumber\":[\"$PUERTO_LOCAL\"]}"
