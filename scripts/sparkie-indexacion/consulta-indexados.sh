#!/usr/bin/env bash
# SOLO LECTURA. Pregunta a la base de PRODUCCION que hay ya indexado en renaser.base_conocimiento y
# genera el archivo de salteo para indexar.py (consulta/saltear.txt) y un resumen (consulta/informe.txt).
#
# Como llega a la base (igual que los otros scripts de produccion):
#   1. Verifica que el perfil AWS "prod" sea la cuenta 302277511407. Si no, no hace nada.
#   2. Por SSM le pide a la instancia i-0ea00f555c5fe8028 que lea las credenciales de la base de
#      Parameter Store (/renaser/prod/DB_*) y corra psql en un contenedor descartable.
#   3. Las credenciales nunca salen de la instancia ni se imprimen.
#
# Por que es seguro: la sesion de Postgres se abre con default_transaction_read_only=on y la consulta
# va dentro de BEGIN READ ONLY ... ROLLBACK. Solo hay SELECT. Si alguien agregara un INSERT/UPDATE/
# DELETE, Postgres lo rechazaria ("cannot execute ... in a read-only transaction").
#
# Uso:  ./consulta-indexados.sh            (necesita aws cli con el perfil "prod" y python3)
set -euo pipefail

export AWS_PROFILE=prod
CUENTA_PROD=302277511407
INSTANCIA=i-0ea00f555c5fe8028
REGION=us-east-1
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DESTINO="$AQUI/consulta"

CUENTA=$(aws sts get-caller-identity --query Account --output text 2>/dev/null || true)
if [ "$CUENTA" != "$CUENTA_PROD" ]; then
  echo "La cuenta de AWS es '$CUENTA', no produccion ($CUENTA_PROD). No se consulto nada." >&2
  exit 3
fi
echo "Cuenta de AWS: $CUENTA (produccion). Consulta de solo lectura."

# Lo que corre EN la instancia. Comillas simples: nada se expande aca, todo alla.
IFS= read -r -d '' REMOTO <<'FIN_REMOTO' || true
set -e
P=/renaser/prod
g(){ aws ssm get-parameter --region us-east-1 --name $P/$1 --with-decryption --query Parameter.Value --output text; }
URL=$(g DB_URL); U=$(g DB_USERNAME); export PGPASSWORD=$(g DB_PASSWORD)
HP=${URL#jdbc:postgresql://}; HP=${HP%%\?*}; H=${HP%%/*}; DB=${HP#*/}; HOST=${H%%:*}; PORT=${H#*:}; [ "$PORT" = "$H" ] && PORT=5432
export PGOPTIONS='-c default_transaction_read_only=on'
docker run --rm -i -e PGPASSWORD -e PGOPTIONS postgres:16-alpine \
  psql -X -q -A -t -F "$(printf '\t')" -v ON_ERROR_STOP=1 -h "$HOST" -p "$PORT" -U "$U" -d "$DB" <<'SQL'
BEGIN READ ONLY;
SET LOCAL search_path TO renaser;
WITH b AS (
  SELECT coalesce(leccion_id, documento_id) AS clave, tipo_fuente,
         CASE WHEN metadatos->>'parte' ~ '^[0-9]+/[0-9]+$'
              THEN split_part(metadatos->>'parte', '/', 1)::int END AS k,
         CASE WHEN metadatos->>'parte' ~ '^[0-9]+/[0-9]+$'
              THEN split_part(metadatos->>'parte', '/', 2)::int END AS n
  FROM base_conocimiento
), g AS (
  SELECT clave, min(tipo_fuente) AS tipo, count(*) AS filas, count(DISTINCT k) AS distintas,
         coalesce(max(n), 0) AS n, count(DISTINCT n) AS totales,
         count(*) FILTER (WHERE k IS NULL) AS sin_parte,
         string_agg(DISTINCT k::text, ',') AS partes
  FROM b GROUP BY clave
)
SELECT 'FILA', coalesce(clave, '(sin clave)'), tipo, filas, distintas, n, totales, sin_parte,
       CASE WHEN filas = n AND distintas = n AND sin_parte = 0 THEN '' ELSE coalesce(partes, '') END
FROM g ORDER BY 2;
SELECT 'FIN', count(*) FROM (SELECT 1 FROM base_conocimiento GROUP BY coalesce(leccion_id, documento_id)) x;
SELECT 'TIPO', tipo_fuente, coalesce(clase, '-'), count(*), count(DISTINCT coalesce(leccion_id, documento_id))
FROM base_conocimiento GROUP BY 2, 3 ORDER BY 2, 3;
ROLLBACK;
SQL
FIN_REMOTO

B64=$(printf '%s\n' "$REMOTO" | base64 -w0)
CID=$(aws ssm send-command --region "$REGION" --instance-ids "$INSTANCIA" \
  --document-name AWS-RunShellScript \
  --parameters "commands=[\"echo $B64 | base64 -d | bash\"]" \
  --query Command.CommandId --output text)
echo "Consultando produccion (comando $CID)..."
ESTADO=""
for _ in $(seq 1 45); do
  ESTADO=$(aws ssm get-command-invocation --region "$REGION" --command-id "$CID" \
    --instance-id "$INSTANCIA" --query Status --output text 2>/dev/null || true)
  case "$ESTADO" in Success|Failed|TimedOut|Cancelled) break ;; esac
  sleep 4
done
SALIDA=$(aws ssm get-command-invocation --region "$REGION" --command-id "$CID" \
  --instance-id "$INSTANCIA" --query StandardOutputContent --output text)
if [ "$ESTADO" != "Success" ]; then
  echo "La consulta termino en estado '$ESTADO'. Error:" >&2
  aws ssm get-command-invocation --region "$REGION" --command-id "$CID" \
    --instance-id "$INSTANCIA" --query StandardErrorContent --output text >&2
  exit 4
fi

echo
echo "Por tipo de fuente (tipo, clase, filas, documentos):"
printf '%s\n' "$SALIDA" | grep '^TIPO' | cut -f2- | sed 's/^/  /' || true
echo
# procesar_consulta.py verifica la linea FIN: si SSM corto la salida (tope de 24.000 caracteres),
# no genera un salteo parcial que haria reenviar lo que ya esta.
printf '%s\n' "$SALIDA" | python3 "$AQUI/procesar_consulta.py" "$DESTINO"
echo
echo "Archivos: $DESTINO/saltear.txt (para --saltear-archivo) y $DESTINO/informe.txt"
