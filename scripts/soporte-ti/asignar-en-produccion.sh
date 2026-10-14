#!/usr/bin/env bash
# =====================================================================================
# Corre asignar.sql contra la base de PRODUCCIÓN, por SSM, desde la instancia del backend
# (la base no se publica a internet). Lo corre el dueño a mano; nunca un agente ni la app.
#
#   bash scripts/soporte-ti/asignar-en-produccion.sh tu-otro-correo@dominio.com
#
# La cuenta tiene que existir, estar ACTIVA y tener contraseña (una que entra solo con Google o
# Apple no puede desbloquear la consola). Decisión del dueño del 2026-10-14 (P7): Soporte TI va
# en una cuenta APARTE de la suya, que sigue como Admin o Alquimista; ponerle Soporte TI a su
# cuenta de siempre le quitaría Administración.
#
# Usa el perfil `prod` (cuenta 302277511407): sin él, la CLI cae en otra cuenta sin avisar.
# La contraseña de la base se lee dentro de la instancia y no sale en ninguna salida.
# =====================================================================================
set -euo pipefail

CORREO="${1:?Falta el correo. Uso: $0 <correo de la cuenta Soporte TI>}"
if ! [[ "$CORREO" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]]; then
  echo "«$CORREO» no parece un correo. No se cambió nada." >&2
  exit 2
fi

PERFIL=prod
REGION=us-east-1
INSTANCIA=i-0ea00f555c5fe8028
RDS=renaser-prod.cm7cywwku4wf.us-east-1.rds.amazonaws.com
SQL="$(dirname "$0")/asignar.sql"
export MSYS_NO_PATHCONV=1

CUENTA=$(aws sts get-caller-identity --profile "$PERFIL" --query Account --output text)
if [ "$CUENTA" != "302277511407" ]; then
  echo "El perfil $PERFIL apunta a la cuenta $CUENTA, no a la de producción (302277511407). No se cambió nada." >&2
  exit 2
fi
echo "Producción (cuenta $CUENTA). Se pasa a Soporte TI la cuenta: $CORREO"
read -r -p "Enter para continuar, Ctrl+C para cortar... " _

B64=$(base64 -w0 "$SQL")
PARAMS=$(mktemp)
trap 'rm -f "$PARAMS"' EXIT
python3 - "$PARAMS" "$B64" "$CORREO" "$RDS" "$REGION" <<'PY'
import json, sys
destino, b64, correo, rds, region = sys.argv[1:]
psql = f'docker run --rm -e PGPASSWORD="$P" -v /tmp/asignar-soporte-ti.sql:/asignar.sql:ro postgres:16-alpine psql -h {rds} -U renaser -d renaser'
json.dump({"commands": [
    "set -e",
    f"P=$(aws ssm get-parameter --name /renaser/prod/DB_PASSWORD --with-decryption --query Parameter.Value --output text --region {region})",
    f"echo {b64} | base64 -d > /tmp/asignar-soporte-ti.sql",
    f"{psql} -v ON_ERROR_STOP=1 -v correo='{correo}' -f /asignar.sql || {{ rm -f /tmp/asignar-soporte-ti.sql; exit 1; }}",
    f"{psql} -tA -c \"SELECT email, rol, estado FROM renaser.usuarios WHERE lower(email) = lower('{correo}')\"",
    "rm -f /tmp/asignar-soporte-ti.sql",
]}, open(destino, "w"))
PY

CMD=$(aws ssm send-command --profile "$PERFIL" --region "$REGION" --instance-ids "$INSTANCIA" \
  --document-name AWS-RunShellScript --parameters "file://$PARAMS" --query Command.CommandId --output text)
echo "Ejecutando en la instancia..."
aws ssm wait command-executed --profile "$PERFIL" --region "$REGION" --command-id "$CMD" --instance-id "$INSTANCIA" || true
aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$CMD" --instance-id "$INSTANCIA" \
  --query "[Status,StandardOutputContent,StandardErrorContent]" --output text
