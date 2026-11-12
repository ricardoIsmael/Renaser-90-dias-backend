#!/usr/bin/env bash
# =====================================================================================
# Ensayo LOCAL de los scripts del WAF (spec 050 tramo B): corre el MISMO codigo de produccion contra un AWS de
# mentira, sin tocar ninguna cuenta.
#
#   infra/waf/ensayo-local.sh
#
# Un `aws` de mentira (en un directorio temporal) hace de AWS WAF, CloudWatch Logs, CloudWatch y CloudFront: el estado
# son archivos. NO es AWS: no valida las reglas como las valida el servicio (eso lo hace `check-capacity` en el
# simulacro de crear-web-acl.sh, con la cuenta real). Lo que SI comprueba es lo que los scripts arman y mandan:
#   - la forma de cada llamada (campos obligatorios, una sola de Action/OverrideAction por regla, la respuesta 429
#     referenciada existe, nada de lo que es solo de Renaser sale hacia AWS, el LockToken correcto);
#   - el orden y la idempotencia: simulacro sin cambios, crear dos veces, asociar dos veces, bloquear solo una regla,
#     volver a Count, actualizar sin perder los bloqueos, y borrar solo si esta desasociado;
#   - los respaldos (permiso 600) y que ningun secreto salga por pantalla (aqui no hay: el WAF no maneja secretos).
# No necesita Docker. Si pasa, lo que falta por verificar con AWS de verdad es solo el esquema (check-capacity).
# =====================================================================================
set -euo pipefail

AQUI=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
TMP=$(mktemp -d)
FALLOS=0
SALIDA="$TMP/salida-de-todo.txt"
: > "$SALIDA"
limpiar() { rm -rf "$TMP"; }
trap limpiar EXIT

ok() { echo "  OK   $1"; }
mal() { echo "  MAL  $1"; FALLOS=$((FALLOS + 1)); }
comprobar() { if [ "$2" = "$3" ]; then ok "$1 ($3)"; else mal "$1: esperaba '$2' y fue '$3'"; fi; }

mkdir -p "$TMP/bin" "$TMP/waf" "$TMP/cf" "$TMP/respaldos" "$TMP/copia"
# Una copia de los scripts y la plantilla: el ensayo de las excepciones (FR-003) modifica SU plantilla, no la del repositorio.
cp "$AQUI"/comun.sh "$AQUI"/crear-web-acl.sh "$AQUI"/asociar-web-acl.sh "$AQUI"/pasar-regla-a-bloqueo.sh "$AQUI"/web-acl.plantilla.json "$TMP/copia/"

cat > "$TMP/bin/aws" <<'FIN'
#!/usr/bin/env bash
set -u
args=("$@"); limpio=(); i=0
while [ $i -lt ${#args[@]} ]; do
  case "${args[$i]}" in --profile|--region|--output) i=$((i + 2)); continue ;; esac
  limpio+=("${args[$i]}"); i=$((i + 1))
done
servicio=${limpio[0]:-}; operacion=${limpio[1]:-}
nombre=""; consulta=""; id=""; etag=""; config=""; json=""; reglas=""; lock=""; arn=""; prefijo=""; dias=""; grupo=""; registro=""
rest=("${limpio[@]:2}"); j=0
while [ $j -lt ${#rest[@]} ]; do
  case "${rest[$j]}" in
    --name) nombre=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --query) consulta=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --id) id=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --if-match) etag=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --distribution-config) config=${rest[$((j + 1))]#file://}; j=$((j + 1)) ;;
    --cli-input-json) json=${rest[$((j + 1))]#file://}; j=$((j + 1)) ;;
    --rules) reglas=${rest[$((j + 1))]#file://}; j=$((j + 1)) ;;
    --lock-token) lock=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --resource-arn|--web-acl-id) arn=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --log-group-name-prefix) prefijo=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --log-group-name) grupo=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --retention-in-days) dias=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --logging-configuration) registro=${rest[$((j + 1))]#file://}; j=$((j + 1)) ;;
    --scope|--namespace|--metric-name|--dimensions|--start-time|--end-time|--period|--statistics) j=$((j + 1)) ;;
  esac
  j=$((j + 1))
done
rechazo() { echo "An error occurred ($1) when calling the $2 operation: $3" >&2; exit 254; }
VALIDAS='["Name","Scope","DefaultAction","Description","Rules","VisibilityConfig","Tags","CustomResponseBodies","CaptchaConfig","ChallengeConfig","TokenDomains","AssociationConfig","Id","LockToken"]'
# AWS valida cada regla: nombre, prioridad, sentencia, visibilidad y UNA sola accion del tipo que toca.
validar_reglas() {  # <archivo con el array de reglas> <archivo con los cuerpos de respuesta o vacio>
  jq -e '
    (type == "array" and length > 0)
    and ([.[].Priority] | length == (unique | length))
    and all(.[]; has("Name") and has("Priority") and has("Statement") and has("VisibilityConfig")
        and ((has("Action") and (has("OverrideAction") | not)) or ((has("Action") | not) and has("OverrideAction"))))
    and all(.[]; if .Statement.ManagedRuleGroupStatement then has("OverrideAction") else has("Action") end)
    and all(.[]; .VisibilityConfig.MetricName == .Name)' "$1" >/dev/null || rechazo WAFInvalidParameterException "$2" "reglas invalidas"
  if [ -n "${3:-}" ]; then
    jq -e --slurpfile cuerpos "$3" '[.[] | .Action.Block.CustomResponse.CustomResponseBodyKey // empty] | all(. as $k | $cuerpos[0] | has($k))' "$1" >/dev/null \
      || rechazo WAFInvalidParameterException "$2" "una regla pide una respuesta personalizada que no esta definida"
  fi
}
case "$servicio $operacion" in
  "sts get-caller-identity") echo "$FAKE_CUENTA" ;;
  "wafv2 check-capacity") validar_reglas "$reglas" CheckCapacity; echo 934 ;;
  "wafv2 list-web-acls")
    if [ -f "$FAKE_WAF/acl.json" ]; then jq '{WebACLs: [{Name: .WebACL.Name, Id: .WebACL.Id, ARN: .WebACL.ARN, LockToken: .LockToken}]}' "$FAKE_WAF/acl.json"
    else echo '{"WebACLs": []}'; fi ;;
  "wafv2 create-web-acl")
    jq -e --argjson v "$VALIDAS" 'keys | all(. as $k | $v | index($k))' "$json" >/dev/null || rechazo WAFInvalidParameterException CreateWebACL "parametro desconocido: $(jq -r --argjson v "$VALIDAS" '[keys[] | select(. as $k | $v | index($k) | not)] | join(",")' "$json")"
    jq -e 'has("Name") and has("Scope") and has("DefaultAction") and has("VisibilityConfig") and has("Rules")' "$json" >/dev/null || rechazo WAFInvalidParameterException CreateWebACL "faltan campos"
    jq '.Rules' "$json" > "$FAKE_WAF/r.json"; jq '.CustomResponseBodies // {}' "$json" > "$FAKE_WAF/c.json"
    validar_reglas "$FAKE_WAF/r.json" CreateWebACL "$FAKE_WAF/c.json"
    [ ! -f "$FAKE_WAF/acl.json" ] || rechazo WAFDuplicateItemException CreateWebACL "ya existe"
    nuevo=abcd1234-0000-4000-8000-000000000001
    jq --arg id "$nuevo" --arg arn "arn:aws:wafv2:us-east-1:$FAKE_CUENTA:global/webacl/$(jq -r .Name "$json")/$nuevo" \
       '{WebACL: (del(.Scope) + {Id: $id, ARN: $arn, Capacity: 934}), LockToken: "lock-1"}' "$json" > "$FAKE_WAF/acl.json"
    jq '{Summary: {Name: .WebACL.Name, Id: .WebACL.Id, ARN: .WebACL.ARN, LockToken: .LockToken}}' "$FAKE_WAF/acl.json" ;;
  "wafv2 get-web-acl") [ -f "$FAKE_WAF/acl.json" ] || rechazo WAFNonexistentItemException GetWebACL "no existe"; cat "$FAKE_WAF/acl.json" ;;
  "wafv2 update-web-acl")
    jq -e --argjson v "$VALIDAS" 'keys | all(. as $k | $v | index($k))' "$json" >/dev/null || rechazo WAFInvalidParameterException UpdateWebACL "parametro desconocido"
    jq -e 'has("Name") and has("Scope") and has("Id") and has("LockToken") and has("DefaultAction") and has("VisibilityConfig") and has("Rules")' "$json" >/dev/null || rechazo WAFInvalidParameterException UpdateWebACL "faltan campos"
    [ "$(jq -r .LockToken "$json")" = "$(jq -r .LockToken "$FAKE_WAF/acl.json")" ] || rechazo WAFOptimisticLockException UpdateWebACL "LockToken viejo"
    jq '.Rules' "$json" > "$FAKE_WAF/r.json"; jq '.CustomResponseBodies // {}' "$json" > "$FAKE_WAF/c.json"
    validar_reglas "$FAKE_WAF/r.json" UpdateWebACL "$FAKE_WAF/c.json"
    n=$(( $(jq -r .LockToken "$FAKE_WAF/acl.json" | sed 's/lock-//') + 1 ))
    jq --slurpfile nuevo "$json" --arg l "lock-$n" '.WebACL = (.WebACL + ($nuevo[0] | del(.Scope, .LockToken))) | .LockToken = $l' "$FAKE_WAF/acl.json" > "$FAKE_WAF/acl.nuevo" && mv "$FAKE_WAF/acl.nuevo" "$FAKE_WAF/acl.json" ;;
  "wafv2 delete-web-acl")
    [ "$lock" = "$(jq -r .LockToken "$FAKE_WAF/acl.json")" ] || rechazo WAFOptimisticLockException DeleteWebACL "LockToken viejo"
    rm -f "$FAKE_WAF/acl.json" "$FAKE_WAF/registro.json" ;;
  "wafv2 put-logging-configuration")
    jq -e --slurpfile acl "$FAKE_WAF/acl.json" '.LoggingConfiguration | .ResourceArn == $acl[0].WebACL.ARN
        and (.LogDestinationConfigs | length == 1 and (.[0] | test("^arn:aws:logs:us-east-1:[0-9]+:log-group:aws-waf-logs-")))
        and (.RedactedFields | length > 0 and all(.[]; .SingleHeader.Name == (.SingleHeader.Name | ascii_downcase)))' "$json" >/dev/null \
      || rechazo WAFInvalidParameterException PutLoggingConfiguration "configuracion de registro invalida"
    cp "$json" "$FAKE_WAF/registro.json" ;;
  "wafv2 delete-logging-configuration") [ -f "$FAKE_WAF/registro.json" ] || rechazo WAFNonexistentItemException DeleteLoggingConfiguration "no hay"; rm -f "$FAKE_WAF/registro.json" ;;
  "logs describe-log-groups") [ -f "$FAKE_WAF/grupo-$prefijo" ] && echo 1 || echo 0 ;;
  "logs create-log-group") echo "sin retencion" > "$FAKE_WAF/grupo-$grupo" ;;
  "logs put-retention-policy") echo "$dias" > "$FAKE_WAF/grupo-$grupo" ;;
  "logs delete-log-group") rm -f "$FAKE_WAF/grupo-$grupo" ;;
  "cloudfront get-distribution-config") cat "$FAKE_CF/config.json" ;;
  "cloudfront update-distribution")
    [ "$etag" = "$(jq -r .ETag "$FAKE_CF/config.json")" ] || rechazo PreconditionFailed UpdateDistribution "ETag viejo"
    n=$(( $(jq -r .ETag "$FAKE_CF/config.json" | tr -d 'E') + 1 ))
    jq --arg e "E$n" '{ETag: $e, DistributionConfig: .}' "$config" > "$FAKE_CF/config.json.nuevo" && mv "$FAKE_CF/config.json.nuevo" "$FAKE_CF/config.json" ;;
  "cloudfront wait") : ;;
  "cloudfront list-distributions-by-web-acl-id") jq -r --arg a "$arn" 'if .DistributionConfig.WebACLId == $a then 1 else 0 end' "$FAKE_CF/config.json" ;;
  "cloudwatch get-metric-statistics") echo '{"Datapoints":[{"Timestamp":"2026-11-09T00:00:00Z","Sum":12.0,"Unit":"Count"},{"Timestamp":"2026-11-10T00:00:00Z","Sum":340.0,"Unit":"Count"}]}' ;;
  *) echo "aws de mentira: no conozco '$servicio $operacion'" >&2; exit 2 ;;
esac
FIN
chmod +x "$TMP/bin/aws"

cat > "$TMP/cf/config.json" <<'FIN'
{"ETag": "E1", "DistributionConfig": {"CallerReference": "ensayo", "Comment": "distribucion de ensayo", "Enabled": true, "WebACLId": "",
  "DefaultCacheBehavior": {"TargetOriginId": "origen-backend"},
  "Origins": {"Quantity": 1, "Items": [{"Id": "origen-backend", "DomainName": "ec2-ensayo.compute.amazonaws.com",
    "CustomHeaders": {"Quantity": 1, "Items": [{"HeaderName": "X-Origen-Renaser", "HeaderValue": "valor-de-ensayo"}]}}]}}}
FIN
cp "$TMP/cf/config.json" "$TMP/cf/inicial.json"

export PATH="$TMP/bin:$PATH" FAKE_WAF="$TMP/waf" FAKE_CF="$TMP/cf" FAKE_CUENTA=111111111111
export RENASER_CUENTA_AWS=111111111111 RENASER_DISTRIBUCION=EENSAYO RENASER_RESPALDOS="$TMP/respaldos"

crear() { bash "$TMP/copia/crear-web-acl.sh" "$@" 2>&1 | tee -a "$SALIDA"; }
asociar() { bash "$TMP/copia/asociar-web-acl.sh" "$@" 2>&1 | tee -a "$SALIDA"; }
pasar() { bash "$TMP/copia/pasar-regla-a-bloqueo.sh" "$@" 2>&1 | tee -a "$SALIDA"; }
accion() { jq -c --arg r "$1" '.WebACL.Rules[] | select(.Name == $r) | (.Action // .OverrideAction)' "$FAKE_WAF/acl.json"; }
acl_vivo() { [ -f "$FAKE_WAF/acl.json" ] && echo si || echo no; }

echo "== 1. Crear: simulacro (no crea nada) =="
crear | grep -E "check-capacity|limite-|reputacion|entradas|reglas-comunes|SIMULACRO" || true
comprobar "el simulacro no crea el Web ACL" no "$(acl_vivo)"
comprobar "ni el grupo de registros" no "$(compgen -G "$FAKE_WAF/grupo-*" >/dev/null && echo si || echo no)"

echo "== 2. Crear: aplicar =="
crear --aplicar | grep -E "creado|Retencion|Registro configurado|ARN|Respaldo" || true
comprobar "el Web ACL existe" si "$(acl_vivo)"
comprobar "alcance y nombre" "renaser-api" "$(jq -r .WebACL.Name "$FAKE_WAF/acl.json")"
comprobar "cinco reglas" 5 "$(jq '.WebACL.Rules | length' "$FAKE_WAF/acl.json")"
comprobar "todas nacen en Count (ninguna regla bloquea)" 5 "$(jq '[.WebACL.Rules[] | (.Action // .OverrideAction) | has("Count")] | map(select(.)) | length' "$FAKE_WAF/acl.json")"
comprobar "limite global: 2.000 por IP en 5 minutos" "2000/300/IP" "$(jq -r '.WebACL.Rules[] | select(.Name == "limite-global") | .Statement.RateBasedStatement | "\(.Limit)/\(.EvaluationWindowSec)/\(.AggregateKeyType)"' "$FAKE_WAF/acl.json")"
comprobar "limite de /auth: 300 por IP en 5 minutos" "300/300/IP" "$(jq -r '.WebACL.Rules[] | select(.Name == "limite-auth") | .Statement.RateBasedStatement | "\(.Limit)/\(.EvaluationWindowSec)/\(.AggregateKeyType)"' "$FAKE_WAF/acl.json")"
comprobar "lo de /auth solo mira esa ruta" "^/api/v1/auth/" "$(jq -r '.WebACL.Rules[] | select(.Name == "limite-auth") | .Statement.RateBasedStatement.ScopeDownStatement.RegexMatchStatement.RegexString' "$FAKE_WAF/acl.json")"
comprobar "grupos administrados" "AWSManagedRulesAmazonIpReputationList AWSManagedRulesCommonRuleSet AWSManagedRulesKnownBadInputsRuleSet" "$(jq -r '[.WebACL.Rules[].Statement.ManagedRuleGroupStatement.Name | select(.)] | sort | join(" ")' "$FAKE_WAF/acl.json")"
comprobar "nada de Renaser viaja hacia AWS" false "$(jq 'has("Renaser")' "$FAKE_WAF/acl.json")"
comprobar "el registro censura las tres cabeceras" "authorization cookie x-auth-token" "$(jq -r '[.LoggingConfiguration.RedactedFields[].SingleHeader.Name] | sort | join(" ")' "$FAKE_WAF/registro.json")"
comprobar "el registro va al grupo aws-waf-logs-renaser" si "$(jq -r '.LoggingConfiguration.LogDestinationConfigs[0] | endswith(":log-group:aws-waf-logs-renaser") | if . then "si" else "no" end' "$FAKE_WAF/registro.json")"
comprobar "retencion del registro (dias)" 14 "$(cat "$FAKE_WAF/grupo-aws-waf-logs-renaser")"
comprobar "respaldo de la creacion con permiso 600" 600 "$(stat -c %a "$TMP"/respaldos/web-acl-renaser-api-creacion-*.json | head -n 1)"

echo "== 3. Crear otra vez: idempotente =="
ID=$(jq -r .WebACL.Id "$FAKE_WAF/acl.json")
crear --aplicar | grep -E "ya existe" || true
comprobar "sigue el mismo Web ACL" "$ID" "$(jq -r .WebACL.Id "$FAKE_WAF/acl.json")"

echo "== 4. Asociar: simulacro, aplicar, repetir, y no pisar otro Web ACL =="
asociar | grep -E "Simulacro|ya tiene" || true
comprobar "el simulacro no toca la distribucion" E1 "$(jq -r .ETag "$FAKE_CF/config.json")"
asociar --aplicar | grep -E "desplegada|Respaldo|Listo" || true
ARN=$(jq -r .WebACL.ARN "$FAKE_WAF/acl.json")
comprobar "la distribucion lleva el Web ACL" "$ARN" "$(jq -r .DistributionConfig.WebACLId "$FAKE_CF/config.json")"
comprobar "solo cambio WebACLId" "[]" "$(jq -n -c --slurpfile a "$FAKE_CF/inicial.json" --slurpfile b "$FAKE_CF/config.json" '[$a[0].DistributionConfig, $b[0].DistributionConfig] | map(del(.WebACLId)) | if .[0] == .[1] then [] else ["distintas"] end')"
E=$(jq -r .ETag "$FAKE_CF/config.json")
asociar --aplicar | grep -E "nada que cambiar" || true
comprobar "repetirlo no cambia la distribucion" "$E" "$(jq -r .ETag "$FAKE_CF/config.json")"
jq '.DistributionConfig.WebACLId = "arn:aws:wafv2:us-east-1:111111111111:global/webacl/otro/xyz"' "$FAKE_CF/config.json" > "$TMP/otro.json"
cp "$FAKE_CF/config.json" "$TMP/con-nuestro.json"; cp "$TMP/otro.json" "$FAKE_CF/config.json"
set +e; asociar --aplicar > "$TMP/pisar.txt" 2>&1; RC=$?; set -e
cat "$TMP/pisar.txt" >> "$SALIDA"
[ "$RC" -ne 0 ] && grep -q "OTRO Web ACL" "$TMP/pisar.txt" && ok "no pisa otro Web ACL sin --reemplazar" || mal "pisó otro Web ACL"
cp "$TMP/con-nuestro.json" "$FAKE_CF/config.json"

echo "== 5. Bloquear una regla: solo esa, con su bandera aparte =="
pasar --regla limite-auth | grep -E "pedidos contados|SIMULACRO|Block" || true
comprobar "el simulacro no cambia la regla" '{"Count":{}}' "$(accion limite-auth)"
set +e; bash "$TMP/copia/pasar-regla-a-bloqueo.sh" --regla limite-auth --aplicar >/dev/null 2>&1; RC=$?; set -e
comprobar "--aplicar NO bloquea (el bloqueo tiene su propia bandera)" 2 "$RC"
comprobar "y la regla sigue en Count" '{"Count":{}}' "$(accion limite-auth)"
pasar --regla limite-auth --aplicar-bloqueo | grep -E "Listo|Respaldo" || true
comprobar "limite-auth responde 429" 429 "$(jq '.WebACL.Rules[] | select(.Name == "limite-auth") | .Action.Block.CustomResponse.ResponseCode' "$FAKE_WAF/acl.json")"
comprobar "con Retry-After" "Retry-After" "$(jq -r '.WebACL.Rules[] | select(.Name == "limite-auth") | .Action.Block.CustomResponse.ResponseHeaders[0].Name' "$FAKE_WAF/acl.json")"
comprobar "y el cuerpo DEMASIADOS_PEDIDOS" "DEMASIADOS_PEDIDOS" "$(jq -r '.WebACL.CustomResponseBodies["demasiados-pedidos"].Content | fromjson | .codigo' "$FAKE_WAF/acl.json")"
comprobar "las otras cuatro siguen en Count" 4 "$(jq '[.WebACL.Rules[] | select(.Name != "limite-auth") | (.Action // .OverrideAction) | has("Count")] | map(select(.)) | length' "$FAKE_WAF/acl.json")"
pasar --regla reputacion-de-ip --aplicar-bloqueo | grep -E "Listo" || true
comprobar "un grupo administrado en Block usa sus propias acciones (None)" '{"None":{}}' "$(accion reputacion-de-ip)"
pasar --regla reputacion-de-ip --volver-a-count | grep -E "Count" || true
comprobar "vuelta atras de una regla" '{"Count":{}}' "$(accion reputacion-de-ip)"
comprobar "limite-auth sigue bloqueando" Block "$(accion limite-auth | jq -r 'keys[0]')"
set +e; bash "$TMP/copia/pasar-regla-a-bloqueo.sh" --regla no-existe >"$TMP/noexiste.txt" 2>&1; RC=$?; set -e
[ "$RC" -ne 0 ] && grep -q "no hay una regla llamada" "$TMP/noexiste.txt" && ok "una regla que no existe se rechaza" || mal "acepto una regla que no existe"

echo "== 6. Una excepcion por falso positivo (FR-003): se actualiza el Web ACL sin perder el bloqueo =="
jq '.Renaser.Excepciones = [{"Grupo": "reglas-comunes", "Regla": "SizeRestrictions_BODY", "Motivo": "ensayo: textos largos del Muro", "Fecha": "2026-11-20"}]' "$TMP/copia/web-acl.plantilla.json" > "$TMP/copia/p.json" && mv "$TMP/copia/p.json" "$TMP/copia/web-acl.plantilla.json"
crear --actualizar | grep -E "SIMULACRO|Simulacro|reglas-comunes" || true
comprobar "el simulacro de actualizar no cambia nada" 0 "$(jq '.WebACL.Rules[] | select(.Name == "reglas-comunes") | .Statement.ManagedRuleGroupStatement.RuleActionOverrides // [] | length' "$FAKE_WAF/acl.json")"
crear --actualizar --aplicar | grep -E "actualizado|Respaldo" || true
comprobar "la regla excluida queda en Count DENTRO del grupo" "SizeRestrictions_BODY" "$(jq -r '.WebACL.Rules[] | select(.Name == "reglas-comunes") | .Statement.ManagedRuleGroupStatement.RuleActionOverrides[0].Name' "$FAKE_WAF/acl.json")"
comprobar "el grupo NO se apago" "AWSManagedRulesCommonRuleSet" "$(jq -r '.WebACL.Rules[] | select(.Name == "reglas-comunes") | .Statement.ManagedRuleGroupStatement.Name' "$FAKE_WAF/acl.json")"
comprobar "actualizar conserva el bloqueo de limite-auth" Block "$(accion limite-auth | jq -r 'keys[0]')"

echo "== 7. Borrar: solo si esta desasociado =="
set +e; crear --revertir --aplicar > "$TMP/borrar1.txt" 2>&1; RC=$?; set -e
[ "$RC" -ne 0 ] && grep -q "sigue asociado" "$TMP/borrar1.txt" && ok "se niega a borrar un Web ACL asociado" || mal "borro un Web ACL asociado"
asociar --revertir | grep -E "Simulacro" || true
comprobar "el simulacro de desasociar no cambia nada" "$ARN" "$(jq -r .DistributionConfig.WebACLId "$FAKE_CF/config.json")"
asociar --revertir --aplicar | grep -E "Listo|Respaldo" || true
comprobar "desasociar quita el WebACLId" "" "$(jq -r .DistributionConfig.WebACLId "$FAKE_CF/config.json")"
comprobar "y deja la distribucion como estaba" "[]" "$(jq -n -c --slurpfile a "$FAKE_CF/inicial.json" --slurpfile b "$FAKE_CF/config.json" '[$a[0].DistributionConfig, $b[0].DistributionConfig] | if .[0] == .[1] then [] else ["distintas"] end')"
asociar --revertir --aplicar | grep -E "nada que quitar" || true
crear --revertir | grep -E "Simulacro" || true
comprobar "el simulacro de borrar no borra" si "$(acl_vivo)"
crear --revertir --aplicar | grep -E "borrado|Respaldo" || true
comprobar "el Web ACL se borro" no "$(acl_vivo)"
comprobar "y el registro tambien" no "$([ -f "$FAKE_WAF/registro.json" ] && echo si || echo no)"
comprobar "el grupo de registros se deja (14 dias de historia)" 14 "$(cat "$FAKE_WAF/grupo-aws-waf-logs-renaser")"
comprobar "respaldo del Web ACL borrado con permiso 600" 600 "$(stat -c %a "$TMP"/respaldos/web-acl-renaser-api-borrado-*.json | head -n 1)"
crear --revertir --aplicar --borrar-registros | grep -E "no existe" || true

echo "== 8. Cuenta equivocada: los scripts se niegan =="
for s in crear-web-acl asociar-web-acl; do
  set +e; FAKE_CUENTA=222222222222 bash "$TMP/copia/$s.sh" >"$TMP/cuenta.txt" 2>&1; RC=$?; set -e
  [ "$RC" -ne 0 ] && grep -q "no a la esperada" "$TMP/cuenta.txt" && ok "$s se niega con otra cuenta" || mal "$s no se nego con otra cuenta"
done

echo
if [ "$FALLOS" -eq 0 ]; then echo "ENSAYO OK: todo lo comprobado paso."; else echo "ENSAYO CON FALLOS: $FALLOS."; exit 1; fi
