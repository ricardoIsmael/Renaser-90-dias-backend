#!/usr/bin/env bash
# =====================================================================================
# Ensayo LOCAL de la cabecera de origen (spec 050 T013 y T014): corre el MISMO codigo de produccion contra un
# nginx de verdad en Docker, sin tocar AWS.
#
#   infra/cloudfront/ensayo-local.sh
#
# Que se ensaya:
#   - la configuracion de nginx que genera scripts/despliegue/desplegar-backend.sh (SOLO_PROXY=si), en los dos
#     modos, con vigente y anterior, con IP de operacion, con valores raros en Parameter Store y con una
#     configuracion invalida (la que esta andando no se toca);
#   - infra/cloudfront/cabecera-de-origen.sh de punta a punta: agregar, exigir, rotar y revertir, con sus
#     simulacros, su idempotencia y sus respaldos.
# Un `aws` y un `curl` de mentira (en un directorio temporal) hacen de AWS: Parameter Store y CloudFront son
# archivos, "SSM" corre el comando aqui, y "CloudFront" es un curl que pone la cabecera que tenga la
# distribucion de mentira. NO hace falta ningun secreto ni ninguna cuenta.
#
# Necesita Docker, jq y curl. Crea la red `ensayo050a2-red` y los contenedores `ensayo050a2-*` (puerto 18081 del
# host, PUERTO_HOST) y los borra al terminar. Para parecerse a un visitante de internet, los pedidos "de fuera" salen por la
# IP de red local de esta maquina (no por 127.0.0.1, que es lo que nginx exime).
# =====================================================================================
set -euo pipefail

AQUI=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
CABECERA_SH="$AQUI/cabecera-de-origen.sh"
IMAGEN_PROXY=${IMAGEN_PROXY:-public.ecr.aws/nginx/nginx:1.28-alpine}
RED=ensayo050a2-red
BACKEND=ensayo050a2-backend
PROXY=ensayo050a2-proxy
PUERTO_HOST=${PUERTO_HOST:-18081}
TMP=$(mktemp -d)
FALLOS=0
SALIDA="$TMP/salida-de-todo.txt"
: > "$SALIDA"

LAN=$(hostname -I | tr ' ' '\n' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | grep -v '^127\.' | grep -v '^172\.\(1[6-9]\|2[0-9]\|3[01]\)\.' | head -n 1 || true)
if [ -z "$LAN" ]; then echo "No encuentro una IP de red local distinta de 127.0.0.1 y de las de Docker: sin ella no se puede simular un pedido de fuera." >&2; exit 2; fi

limpiar() {
  docker rm -f "$PROXY" "$BACKEND" >/dev/null 2>&1 || true
  docker network rm "$RED" >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap limpiar EXIT

ok() { echo "  OK   $1"; }
mal() { echo "  MAL  $1"; FALLOS=$((FALLOS + 1)); }
comprobar() { if [ "$2" = "$3" ]; then ok "$1 ($3)"; else mal "$1: esperaba '$2' y fue '$3'"; fi; }

mkdir -p "$TMP/bin" "$TMP/ssm" "$TMP/cf" "$TMP/cmd" "$TMP/proxy" "$TMP/respaldos"
CURL_REAL=$(command -v curl)

# ---- AWS de mentira ---------------------------------------------------------------------------------------
cat > "$TMP/bin/aws" <<'FIN'
#!/usr/bin/env bash
set -u
args=("$@"); limpio=(); i=0
while [ $i -lt ${#args[@]} ]; do
  case "${args[$i]}" in --profile|--region|--output) i=$((i + 2)); continue ;; esac
  limpio+=("${args[$i]}"); i=$((i + 1))
done
servicio=${limpio[0]:-}; operacion=${limpio[1]:-}
nombre=""; consulta=""; id=""; etag=""; config=""; json=""; params=""; cmd=""; valor=""; tipo=""; sobre=no
rest=("${limpio[@]:2}"); j=0
while [ $j -lt ${#rest[@]} ]; do
  case "${rest[$j]}" in
    --name) nombre=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --query) consulta=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --id) id=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --if-match) etag=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --distribution-config) config=${rest[$((j + 1))]#file://}; j=$((j + 1)) ;;
    --cli-input-json) json=${rest[$((j + 1))]#file://}; j=$((j + 1)) ;;
    --parameters) params=${rest[$((j + 1))]#file://}; j=$((j + 1)) ;;
    --command-id) cmd=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --value) valor=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --type) tipo=${rest[$((j + 1))]}; j=$((j + 1)) ;;
    --overwrite) sobre=si ;;
    --description|--comment|--instance-ids|--instance-id|--document-name) j=$((j + 1)) ;;
  esac
  j=$((j + 1))
done
archivo() { echo "$FAKE_SSM/$(echo "$1" | tr / _)"; }
case "$servicio $operacion" in
  "sts get-caller-identity") echo "$FAKE_CUENTA" ;;
  "ssm get-parameter")
    f=$(archivo "$nombre")
    if [ ! -f "$f" ]; then echo "An error occurred (ParameterNotFound) when calling the GetParameter operation: Parameter $nombre not found." >&2; exit 254; fi
    case "$consulta" in Parameter.Version) echo 1 ;; *) cat "$f"; echo ;; esac ;;
  "ssm put-parameter")
    if [ -n "$json" ]; then nombre=$(jq -r .Name "$json"); valor=$(jq -r .Value "$json"); sobre=$(jq -r 'if .Overwrite then "si" else "no" end' "$json"); fi
    f=$(archivo "$nombre")
    if [ -f "$f" ] && [ "$sobre" != si ]; then echo "An error occurred (ParameterAlreadyExists)" >&2; exit 254; fi
    printf '%s' "$valor" > "$f" ;;
  "ssm delete-parameter") rm -f "$(archivo "$nombre")" ;;
  "ssm send-command")
    n=$(( $(ls "$FAKE_CMD" | grep -c '\.sh$') + 1 ))
    jq -r '.commands[]' "$params" > "$FAKE_CMD/$n.sh"
    bash "$FAKE_CMD/$n.sh" > "$FAKE_CMD/$n.out" 2> "$FAKE_CMD/$n.err"; echo $? > "$FAKE_CMD/$n.rc"
    echo "$n" ;;
  "ssm get-command-invocation")
    case "$consulta" in
      Status) [ "$(cat "$FAKE_CMD/$cmd.rc")" = 0 ] && echo Success || echo Failed ;;
      StandardOutputContent) cat "$FAKE_CMD/$cmd.out" ;;
      StandardErrorContent) cat "$FAKE_CMD/$cmd.err" ;;
    esac ;;
  "cloudfront get-distribution-config") cat "$FAKE_CF/config.json" ;;
  "cloudfront update-distribution")
    [ "$etag" = "$(jq -r .ETag "$FAKE_CF/config.json")" ] || { echo "An error occurred (PreconditionFailed)" >&2; exit 254; }
    n=$(( $(jq -r .ETag "$FAKE_CF/config.json" | tr -d 'E') + 1 ))
    jq --arg e "E$n" '{ETag: $e, DistributionConfig: .}' "$config" > "$FAKE_CF/config.json.nuevo" && mv "$FAKE_CF/config.json.nuevo" "$FAKE_CF/config.json" ;;
  "cloudfront wait") : ;;
  "cloudfront get-distribution") echo d111111abcdef8.cloudfront.net ;;
  *) echo "aws de mentira: no conozco '$servicio $operacion'" >&2; exit 2 ;;
esac
FIN
# ---- curl de mentira: un pedido a "CloudFront" sale por el proxy con la cabecera que tenga la distribucion ----
cat > "$TMP/bin/curl" <<FIN
#!/usr/bin/env bash
url=""; for a in "\$@"; do case "\$a" in https://d111111abcdef8.cloudfront.net*) url=\$a ;; esac; done
if [ -z "\$url" ]; then exec "$CURL_REAL" "\$@"; fi
valor=\$(jq -r '[.DistributionConfig.Origins.Items[].CustomHeaders.Items[]? | select(.HeaderName == "X-Origen-Renaser") | .HeaderValue] | first // empty' "\$FAKE_CF/config.json")
ruta=\${url#https://d111111abcdef8.cloudfront.net}
cabecera=(); [ -n "\$valor" ] && cabecera=(-H "X-Origen-Renaser: \$valor")
exec "$CURL_REAL" -fsS --max-time 10 "\${cabecera[@]}" "http://$LAN:$PUERTO_HOST\$ruta"
FIN
chmod +x "$TMP/bin/aws" "$TMP/bin/curl"

# ---- el mundo de mentira ----------------------------------------------------------------------------------
cat > "$TMP/cf/config.json" <<'FIN'
{"ETag": "E1", "DistributionConfig": {"CallerReference": "ensayo", "Comment": "distribucion de ensayo",
  "Enabled": true, "DefaultCacheBehavior": {"TargetOriginId": "origen-backend", "ViewerProtocolPolicy": "redirect-to-https"},
  "Origins": {"Quantity": 1, "Items": [{"Id": "origen-backend", "DomainName": "ec2-ensayo.compute.amazonaws.com",
    "CustomHeaders": {"Quantity": 0}, "CustomOriginConfig": {"HTTPPort": 8080, "OriginProtocolPolicy": "http-only"}}]}}}
FIN
cp "$TMP/cf/config.json" "$TMP/cf/inicial.json"

echo "== Preparando: red, un backend de mentira y el nginx de verdad (puerto $PUERTO_HOST) =="
docker network create "$RED" >/dev/null
printf 'server { listen 8080; location / { default_type application/json; return 200 %s; } }\n' "'{\"status\":\"UP\",\"ruta\":\"\$uri\"}'" > "$TMP/backend.conf"
docker run -d --name "$BACKEND" --network "$RED" -v "$TMP/backend.conf:/etc/nginx/conf.d/default.conf:ro" "$IMAGEN_PROXY" >/dev/null
printf 'server { listen 8080; location / { return 200 "inicial"; } }\n' > "$TMP/proxy/renaser.conf"
docker run -d --name "$PROXY" --network "$RED" -p "$PUERTO_HOST:8080" -v "$TMP/proxy/renaser.conf:/etc/nginx/conf.d/default.conf:ro" "$IMAGEN_PROXY" >/dev/null

export PATH="$TMP/bin:$PATH" FAKE_SSM="$TMP/ssm" FAKE_CF="$TMP/cf" FAKE_CMD="$TMP/cmd" FAKE_CUENTA=111111111111
# Lo que el script de despliegue lee de su entorno (en produccion valen los de siempre).
export DIR_PROXY="$TMP/proxy" PROXY RED IMAGEN_PROXY CONTENEDOR="$BACKEND" PUERTO=8080 PUERTO_PUBLICO="$PUERTO_HOST"
export RENASER_CUENTA_AWS=111111111111 RENASER_DISTRIBUCION=EENSAYO RENASER_INSTANCIA=i-ensayo RENASER_PROXY_CONTENEDOR="$PROXY" \
       RENASER_RESPALDOS="$TMP/respaldos" ESPERA_ROTACION=2

param() { cat "$FAKE_SSM/_renaser_prod_$1" 2>/dev/null || true; }
ponerp() { printf '%s' "$2" > "$FAKE_SSM/_renaser_prod_$1"; }
codigo() {  # codigo <ip> [valor de la cabecera]: el estado HTTP de un pedido a nginx
  local cab=()
  [ -n "${2:-}" ] && cab=(-H "X-Origen-Renaser: $2")
  "$CURL_REAL" -s -o "$TMP/cuerpo" -w '%{http_code}' "${cab[@]}" "http://$1:$PUERTO_HOST/api/v1/prueba?token=zzz" || true
}
etag() { jq -r .ETag "$FAKE_CF/config.json"; }
cabecera_en_distribucion() { jq -r '[.DistributionConfig.Origins.Items[].CustomHeaders.Items[]? | select(.HeaderName == "X-Origen-Renaser") | .HeaderValue] | first // ""' "$FAKE_CF/config.json"; }
anotados() { docker logs "$PROXY" 2>&1 | grep -c 'origen-sin-cabecera' || true; }
# `nginx -s reload` vuelve antes de que los workers nuevos tomen el relevo: se les da un momento antes de preguntar.
cabecera_sh() { bash "$CABECERA_SH" "$@" 2>&1 | tee -a "$SALIDA"; sleep 2; }
solo_proxy() {  # el despliegue de solo nginx, como lo manda cabecera-de-origen.sh por SSM
  { printf "SOLO_PROXY='si'\nREGION='us-east-1'\nPREFIJO_PARAMETROS='/renaser/prod/'\n"; cat "$AQUI/../../scripts/despliegue/desplegar-backend.sh"; } > "$TMP/solo-proxy.sh"
  bash "$TMP/solo-proxy.sh" 2>&1 | tee -a "$SALIDA"
  sleep 2
}

echo
echo "== 1. nginx sin ningun parametro: modo registrar, nada se rechaza =="
solo_proxy | grep -E "Origen:|recargado"
comprobar "desde fuera sin cabecera" 200 "$(codigo "$LAN")"
comprobar "desde el propio host (127.0.0.1)" 200 "$(codigo 127.0.0.1)"
case "$(docker logs "$PROXY" 2>&1 | grep 'origen-sin-cabecera' | tail -n 1)" in
  *"ip=$LAN "*"ruta=/api/v1/prueba "*) ok "lo de fuera quedo anotado, con la ruta sin parametros" ;;
  *) mal "no quedo anotado el pedido de fuera" ;;
esac
comprobar "lo del propio host NO se anota (queda 1 linea)" 1 "$(anotados)"
comprobar "permiso del archivo con los secretos" 600 "$(stat -c %a "$DIR_PROXY/renaser.conf")"

echo "== 2. cabecera-de-origen.sh --paso agregar: simulacro y aplicar =="
cabecera_sh --paso agregar | tail -n 8
comprobar "el simulacro no crea el secreto" "" "$(param origen-secreto)"
comprobar "el simulacro no toca la distribucion" E1 "$(etag)"
cabecera_sh --paso agregar --aplicar | grep -E "Origen:|Respaldo|desplegada|creado"
S1=$(param origen-secreto)
[[ "$S1" =~ ^[0-9a-f]{64}$ ]] && ok "el secreto son 32 bytes en hexadecimal" || mal "el secreto no tiene el formato esperado"
comprobar "CloudFront lleva el secreto" "$S1" "$(cabecera_en_distribucion)"
comprobar "solo cambio CustomHeaders en la distribucion" "[]" "$(jq -n --slurpfile a "$FAKE_CF/inicial.json" --slurpfile b "$FAKE_CF/config.json" '[$a[0].DistributionConfig, $b[0].DistributionConfig] | map(del(.Origins.Items[0].CustomHeaders)) | if .[0] == .[1] then [] else ["distintas"] end' -c)"
comprobar "respaldo guardado con permiso 600" 600 "$(stat -c %a "$TMP"/respaldos/cloudfront-EENSAYO-*.json | head -n 1)"
E=$(etag)
cabecera_sh --paso agregar --aplicar | grep -E "nada que cambiar"
comprobar "repetirlo no cambia la distribucion (idempotente)" "$E" "$(etag)"
comprobar "por CloudFront (con la cabecera) llega" 200 "$("$CURL_REAL" -s -o /dev/null -w '%{http_code}' -H "X-Origen-Renaser: $S1" "http://$LAN:$PUERTO_HOST/actuator/health")"
comprobar "desde fuera con la cabecera: pasa y no se anota" 200 "$(codigo "$LAN" "$S1")"

echo "== 3. todavia en registrar: lo que no trae la cabecera pasa y se anota =="
ANTES=$(anotados)
comprobar "desde fuera sin cabecera" 200 "$(codigo "$LAN")"
comprobar "queda anotado" $((ANTES + 1)) "$(anotados)"

echo "== 4. exigir: se niega si hubo pedidos sin cabecera, y con --forzar exige =="
set +e; cabecera_sh --paso exigir --aplicar | tail -n 4; set -e
comprobar "sin --forzar no cambia el modo" "" "$(param origen-modo)"
cabecera_sh --paso exigir --aplicar --forzar | grep -E "Origen:|OK|Listo"
comprobar "origen-modo" exigir "$(param origen-modo)"
comprobar "desde fuera SIN cabecera: 403" 403 "$(codigo "$LAN")"
comprobar "el cuerpo del 403" '{"message":"No autorizado","codigo":"ORIGEN_NO_PERMITIDO"}' "$(cat "$TMP/cuerpo")"
comprobar "desde fuera con una cabecera equivocada: 403" 403 "$(codigo "$LAN" "$(printf 'a%.0s' $(seq 1 64))")"
comprobar "desde fuera con la cabecera buena: 200" 200 "$(codigo "$LAN" "$S1")"
comprobar "desde el propio host sin cabecera (el health del CD): 200" 200 "$(codigo 127.0.0.1)"
comprobar "el health por el puerto publico, como lo hace el CD" 200 "$("$CURL_REAL" -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PUERTO_HOST/actuator/health")"
if curl -fsS https://d111111abcdef8.cloudfront.net/actuator/health >/dev/null 2>&1; then ok "por CloudFront de mentira llega (200)"; else mal "por CloudFront de mentira no llega"; fi
case "$(docker logs "$PROXY" 2>&1)" in *zzz*|*"$S1"*) mal "el log de nginx trae un parametro de la URL o el secreto" ;; *) ok "el log de nginx no trae parametros de la URL ni el secreto" ;; esac

echo "== 5. rotar: simulacro y aplicar (nginx acepta los dos antes de que CloudFront cambie) =="
cabecera_sh --paso rotar | tail -n 8
comprobar "el simulacro no cambia el secreto" "$S1" "$(param origen-secreto)"
cabecera_sh --paso rotar --aplicar | grep -E "Origen:|Paso|Listo"
S2=$(param origen-secreto)
[ "$S2" != "$S1" ] && ok "el secreto cambio" || mal "el secreto no cambio"
comprobar "CloudFront lleva el nuevo" "$S2" "$(cabecera_en_distribucion)"
comprobar "el anterior ya no esta en Parameter Store" "" "$(param origen-secreto-anterior)"
comprobar "con el secreto viejo: 403" 403 "$(codigo "$LAN" "$S1")"
comprobar "con el nuevo: 200" 200 "$(codigo "$LAN" "$S2")"
grep -q "secretos aceptados=2" "$SALIDA" && ok "durante la rotacion nginx acepto los dos" || mal "nginx nunca acepto los dos"

echo "== 6. IP de operacion y valores raros en Parameter Store =="
ponerp origen-ips-directas "$LAN, 127.0.0.1, 999.1.1.1, 1.2.3.4; return 200"
ponerp origen-secreto-anterior 'no-es-un-secreto-valido";return 200;#'
solo_proxy | grep -E "AVISO|Origen:"
comprobar "la IP de operacion pasa sin cabecera" 200 "$(codigo "$LAN")"
grep -q 'return 200' "$DIR_PROXY/renaser.conf" && mal "se colo una directiva por Parameter Store" || ok "nada de Parameter Store entro como directiva"
rm -f "$FAKE_SSM/_renaser_prod_origen-ips-directas" "$FAKE_SSM/_renaser_prod_origen-secreto-anterior"
solo_proxy | grep -E "Origen:"
comprobar "quitada la IP de operacion: 403" 403 "$(codigo "$LAN")"

echo "== 7. exigir sin secretos cae solo a registrar; una configuracion invalida no toca la que anda =="
mv "$FAKE_SSM/_renaser_prod_origen-secreto" "$TMP/guardado"
solo_proxy | grep -E "AVISO|Origen:"
comprobar "sin secretos no se rechaza a nadie" 200 "$(codigo "$LAN")"
mv "$TMP/guardado" "$FAKE_SSM/_renaser_prod_origen-secreto"
solo_proxy | grep -E "Origen:"
SUMA=$(sha256sum "$DIR_PROXY/renaser.conf")
set +e; PUERTO='8080;malo' bash "$TMP/solo-proxy.sh" > "$TMP/invalida.txt" 2>&1; RC=$?; set -e
cat "$TMP/invalida.txt" >> "$SALIDA"
[ "$RC" -ne 0 ] && grep -q "NO es valida" "$TMP/invalida.txt" && ok "una configuracion invalida hace fallar a SOLO_PROXY y lo dice" || mal "una configuracion invalida no hizo fallar a SOLO_PROXY"
comprobar "la configuracion que andaba quedo intacta" "$SUMA" "$(sha256sum "$DIR_PROXY/renaser.conf")"
comprobar "y nginx sigue exigiendo" 403 "$(codigo "$LAN")"

echo "== 8. vuelta atras: nginx a registrar y despues se quita la cabecera =="
cabecera_sh --revertir | tail -n 3
comprobar "el simulacro no quita la cabecera" "$S2" "$(cabecera_en_distribucion)"
cabecera_sh --revertir --aplicar | grep -E "Origen:|Respaldo|Listo"
comprobar "origen-modo" registrar "$(param origen-modo)"
comprobar "CloudFront ya no lleva la cabecera" "" "$(cabecera_en_distribucion)"
comprobar "la distribucion quedo como al principio" "[]" "$(jq -n --slurpfile a "$FAKE_CF/inicial.json" --slurpfile b "$FAKE_CF/config.json" '[$a[0].DistributionConfig, $b[0].DistributionConfig] | map(del(.Origins.Items[0].CustomHeaders)) | if .[0] == .[1] then [] else ["distintas"] end' -c)"
comprobar "desde fuera sin cabecera: pasa" 200 "$(codigo "$LAN")"
comprobar "los secretos se dejaron en Parameter Store" "$S2" "$(param origen-secreto)"
cabecera_sh --revertir --aplicar | grep -E "nada que quitar"

echo "== 9. cuenta equivocada: el script se niega =="
set +e; FAKE_CUENTA=222222222222 bash "$CABECERA_SH" --paso estado >"$TMP/cuenta.txt" 2>&1; RC=$?; set -e
cat "$TMP/cuenta.txt" >> "$SALIDA"
[ "$RC" -ne 0 ] && grep -q "no a la esperada" "$TMP/cuenta.txt" && ok "se niega con otra cuenta" || mal "no se nego con otra cuenta"

echo "== 10. nginx nunca rechazo (nginx -t) una configuracion en los pasos normales =="
comprobar "avisos de configuracion invalida en lo que mandaria SSM" 0 "$(cat "$FAKE_CMD"/*.err | grep -c 'NO es valida' || true)"
comprobar "avisos de configuracion invalida en toda la salida (solo la del caso malo a proposito)" 1 "$(grep -c 'AVISO: la configuracion nueva de nginx NO es valida' "$SALIDA" || true)"

echo "== 11. ningun secreto en lo que se imprimio =="
for secreto in "$S1" "$S2"; do
  if grep -qF "$secreto" "$SALIDA"; then mal "un secreto aparecio en la salida"; else ok "un secreto no aparece en la salida"; fi
done

echo
if [ "$FALLOS" -eq 0 ]; then echo "ENSAYO OK: todo lo comprobado paso."; else
  echo "ENSAYO CON FALLOS: $FALLOS."
  echo "--- stderr de lo que mando 'SSM' (para buscar la causa) ---"; cat "$FAKE_CMD"/*.err 2>/dev/null | head -40
  exit 1
fi
