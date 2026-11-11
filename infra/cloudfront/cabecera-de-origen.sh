#!/usr/bin/env bash
# =====================================================================================
# Cabecera secreta CloudFront -> origen (spec 050 tramo A2: FR-006, FR-007, FR-008 y FR-011; D8 y D12).
# Lo corre el DUENO, desde su maquina, con el perfil `prod`. Nunca un agente ni el CD.
#
# QUE HACE. CloudFront agrega a cada pedido hacia el origen la cabecera X-Origen-Renaser con un secreto, y
# nginx (contenedor `proxy`, config en scripts/despliegue/desplegar-backend.sh) rechaza con 403
# ORIGEN_NO_PERMITIDO lo que no la trae. Asi nadie puede saltarse CloudFront (y el WAF del tramo B)
# hablandole directo a la IP del origen. El secreto vive en Parameter Store (`origen-secreto`, SecureString)
# y nunca se imprime ni entra al repositorio. nginx exime lo que viene del propio host (el CD comprueba la
# salud por 127.0.0.1:8080) y las IP de `origen-ips-directas` (la IP fija de operacion del security group, si
# hace falta que entre sin pasar por CloudFront).
#
# ORDEN DE SALIDA (cada paso se verifica antes del siguiente):
#   0. Variables (obligatorias; sin ellas el script se niega a seguir):
#        export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#        export RENASER_DISTRIBUCION=<id de la distribucion de CloudFront del backend>
#        export RENASER_INSTANCIA=<id de la instancia EC2 del backend, i-...>
#        (si la distribucion tiene mas de un origen: RENASER_ORIGEN_ID=<Id del origen del backend>)
#   1. Fusionar la rama y dejar que el CD despliegue: nginx queda en modo `registrar` (anota, no rechaza).
#   2. Simulacro y luego aplicar: genera el secreto, se lo da a nginx y lo agrega a la distribucion
#        infra/cloudfront/cabecera-de-origen.sh --paso agregar
#        infra/cloudfront/cabecera-de-origen.sh --paso agregar --aplicar
#   3. Esperar 24 h y mirar el estado: cuantos pedidos llegaron SIN la cabecera (deben ser solo de operacion)
#        infra/cloudfront/cabecera-de-origen.sh --paso estado
#   4. Exigirla (rechaza con 403 lo que no la trae; comprueba por CloudFront y vuelve a `registrar` si falla)
#        infra/cloudfront/cabecera-de-origen.sh --paso exigir            # simulacro
#        infra/cloudfront/cabecera-de-origen.sh --paso exigir --aplicar
#   5. Rotar el secreto, sin corte (nginx acepta los dos -> CloudFront cambia -> espera -> nginx suelta el viejo)
#        infra/cloudfront/cabecera-de-origen.sh --paso rotar --aplicar
#
# VUELTA ATRAS: `--revertir` pone nginx en `registrar` y DESPUES quita la cabecera de la distribucion (en ese
# orden, para que nunca haya un 403 de por medio). Quita solo la cabecera: no restaura la copia completa de la
# distribucion, asi no pisa lo que se haya cambiado despues (p. ej. el WAF del tramo B). Antes de cada cambio
# se guarda la configuracion completa con su ETag en $RESPALDOS (fuera del repositorio, permiso 600).
#        infra/cloudfront/cabecera-de-origen.sh --revertir [--aplicar]
#
# Reglas de D12 / FR-011: simulacro por omision (solo `--aplicar` cambia algo), perfil `prod` y cuenta
# verificada, idempotente (repetir un paso ya hecho no cambia nada), respaldo antes de cambiar, ningun secreto
# en pantalla ni en el repositorio, `set -euo pipefail`.
# =====================================================================================
set -euo pipefail

PERFIL=prod
REGION=${RENASER_REGION:-us-east-1}
PREFIJO=${RENASER_PREFIJO_PARAMETROS:-/renaser/prod/}
CABECERA=X-Origen-Renaser
PROXY=${RENASER_PROXY_CONTENEDOR:-proxy}
RESPALDOS=${RENASER_RESPALDOS:-$HOME/.renaser/respaldos}
AQUI=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
DESPLEGAR="$AQUI/../../scripts/despliegue/desplegar-backend.sh"

APLICAR=no
SIMULAR=no
PASO=""
REVERTIR=no
FORZAR=no
ESPERA_ROTACION=${ESPERA_ROTACION:-120}

log() { echo "== $* =="; }
falla() { echo "ERROR: $*" >&2; exit 1; }
uso() { sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --simular) SIMULAR=si ;;
    --aplicar) APLICAR=si ;;
    --revertir) REVERTIR=si ;;
    --forzar) FORZAR=si ;;
    --paso) PASO=${2:?--paso necesita agregar, estado, exigir o rotar}; shift ;;
    -h|--help) uso; exit 0 ;;
    *) echo "Opcion desconocida: $1" >&2; uso >&2; exit 2 ;;
  esac
  shift
done
[ "$SIMULAR" = si ] && APLICAR=no
if [ "$REVERTIR" = no ] && [ -z "$PASO" ]; then echo "Falta --paso (agregar, estado, exigir o rotar) o --revertir." >&2; exit 2; fi
case "$PASO" in ''|agregar|estado|exigir|rotar) ;; *) echo "--paso debe ser agregar, estado, exigir o rotar." >&2; exit 2 ;; esac
[[ "$PREFIJO" =~ ^/[A-Za-z0-9/_.-]*/$ ]] || { echo "El prefijo de parametros debe empezar y terminar con /." >&2; exit 2; }
[[ "$PROXY" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "RENASER_PROXY_CONTENEDOR no es un nombre de contenedor valido." >&2; exit 2; }

for h in aws jq openssl curl; do command -v "$h" >/dev/null || falla "falta $h en esta maquina."; done
: "${RENASER_CUENTA_AWS:?exporta RENASER_CUENTA_AWS con el id de 12 digitos de la cuenta de produccion}"
: "${RENASER_DISTRIBUCION:?exporta RENASER_DISTRIBUCION con el id de la distribucion de CloudFront}"
DIST=$RENASER_DISTRIBUCION

aws_prod() { aws "$@" --profile "$PERFIL"; }

CUENTA=$(aws_prod sts get-caller-identity --query Account --output text)
[ "$CUENTA" = "$RENASER_CUENTA_AWS" ] || falla "el perfil $PERFIL apunta a la cuenta $CUENTA, no a la esperada ($RENASER_CUENTA_AWS). No se cambio nada."
echo "Cuenta verificada ($CUENTA, perfil $PERFIL). Modo: $([ "$APLICAR" = si ] && echo APLICAR || echo SIMULACRO)."

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
umask 077

# ---- Parameter Store ---------------------------------------------------------------------------------------
existe_parametro() {
  local salida
  salida=$(aws_prod ssm get-parameter --region "$REGION" --name "${PREFIJO}$1" --query Parameter.Version --output text 2>&1) && return 0
  case "$salida" in *ParameterNotFound*) return 1 ;; *) falla "no pude consultar ${PREFIJO}$1: ${salida:0:200}" ;; esac
}

valor_de() {  # <nombre> <archivo>: el valor descifrado, directo a un archivo (nunca a la pantalla)
  aws_prod ssm get-parameter --region "$REGION" --with-decryption --name "${PREFIJO}$1" --query Parameter.Value --output text > "$2"
}

guardar_secreto() {  # <nombre> <archivo con el valor>: SecureString, sobrescribe
  jq -n --arg n "${PREFIJO}$1" --rawfile v "$2" \
    '{Name: $n, Value: ($v | rtrimstr("\n")), Type: "SecureString", Overwrite: true, Description: "Cabecera de origen CloudFront a nginx (spec 050 A2). Lo maneja infra/cloudfront/cabecera-de-origen.sh."}' > "$TMP/parametro.json"
  aws_prod ssm put-parameter --region "$REGION" --cli-input-json "file://$TMP/parametro.json" >/dev/null
}

guardar_texto() {  # <nombre> <valor>
  aws_prod ssm put-parameter --region "$REGION" --name "${PREFIJO}$1" --type String --value "$2" --overwrite \
    --description "Cabecera de origen (spec 050 A2)" >/dev/null
}

generar_en() { openssl rand -hex 32 > "$1"; }

# ---- La instancia, por SSM (el patron del CD: el script entero como una sola cadena, sin secretos) -----------
ssm_correr() {  # <archivo con {commands:[...]}> <comentario>
  local cid estado desde
  : "${RENASER_INSTANCIA:?exporta RENASER_INSTANCIA con el id de la instancia EC2 del backend}"
  cid=$(aws_prod ssm send-command --region "$REGION" --instance-ids "$RENASER_INSTANCIA" --document-name AWS-RunShellScript \
          --comment "$2" --parameters "file://$1" --query Command.CommandId --output text)
  echo "Comando SSM $cid enviado; esperando..."
  desde=$SECONDS
  while true; do
    estado=$(aws_prod ssm get-command-invocation --region "$REGION" --command-id "$cid" --instance-id "$RENASER_INSTANCIA" \
               --query Status --output text 2>/dev/null || echo Pending)
    case "$estado" in Success|Failed|Cancelled|TimedOut) break ;; esac
    [ $(( SECONDS - desde )) -ge 600 ] && { estado=SinRespuesta; break; }
    sleep 4
  done
  echo "----- salida de la instancia -----"
  aws_prod ssm get-command-invocation --region "$REGION" --command-id "$cid" --instance-id "$RENASER_INSTANCIA" \
    --query StandardOutputContent --output text || true
  if [ "$estado" != Success ]; then
    aws_prod ssm get-command-invocation --region "$REGION" --command-id "$cid" --instance-id "$RENASER_INSTANCIA" \
      --query StandardErrorContent --output text || true
    return 1
  fi
}

# Reescribe y recarga nginx desde Parameter Store, sin tocar el backend (SOLO_PROXY de desplegar-backend.sh).
nginx_al_dia() {
  [ -f "$DESPLEGAR" ] || falla "no encuentro $DESPLEGAR."
  { printf "SOLO_PROXY='si'\nREGION='%s'\nPREFIJO_PARAMETROS='%s'\n" "$REGION" "$PREFIJO"; cat "$DESPLEGAR"; } \
    | jq -Rs '{commands: [.]}' > "$TMP/solo-proxy.json"
  ssm_correr "$TMP/solo-proxy.json" "origen: nginx al dia"
}

# Cuantos pedidos sin cabecera anoto nginx desde que existe el contenedor, por IP (solo lectura).
pedidos_sin_cabecera() {
  jq -n --arg p "$PROXY" '{commands: [("docker logs --since 24h " + $p + " 2>&1 | grep -o \"origen-sin-cabecera ip=[0-9.]*\" | sort | uniq -c | sort -rn | head -20; echo TOTAL=$(docker logs --since 24h " + $p + " 2>&1 | grep -c origen-sin-cabecera || true)")]}' > "$TMP/logs.json"
  ssm_correr "$TMP/logs.json" "origen: pedidos sin cabecera"
}

# ---- CloudFront ----------------------------------------------------------------------------------------------
leer_distribucion() {  # deja la config en $TMP/config.json y fija ETAG y ORIGEN
  aws_prod cloudfront get-distribution-config --id "$DIST" > "$TMP/config.json"
  ETAG=$(jq -r .ETag "$TMP/config.json")
  local n
  n=$(jq '.DistributionConfig.Origins.Items | length' "$TMP/config.json")
  ORIGEN=${RENASER_ORIGEN_ID:-}
  if [ -z "$ORIGEN" ]; then
    [ "$n" -eq 1 ] || falla "la distribucion tiene $n origenes: dime cual es el del backend con RENASER_ORIGEN_ID=<Id>. Ids: $(jq -r '[.DistributionConfig.Origins.Items[].Id] | join(", ")' "$TMP/config.json")"
    ORIGEN=$(jq -r '.DistributionConfig.Origins.Items[0].Id' "$TMP/config.json")
  fi
  jq -e --arg o "$ORIGEN" '.DistributionConfig.Origins.Items[] | select(.Id == $o)' "$TMP/config.json" >/dev/null \
    || falla "la distribucion no tiene un origen con Id $ORIGEN."
}

tiene_cabecera() {  # <archivo con el valor esperado | "">: 0 si el origen la lleva (con ese valor si se pasa)
  if [ -n "${1:-}" ]; then
    jq -e --arg o "$ORIGEN" --arg n "$CABECERA" --rawfile v "$1" \
      'any(.DistributionConfig.Origins.Items[] | select(.Id == $o) | .CustomHeaders.Items[]?; (.HeaderName | ascii_downcase) == ($n | ascii_downcase) and .HeaderValue == ($v | rtrimstr("\n")))' \
      "$TMP/config.json" >/dev/null
  else
    jq -e --arg o "$ORIGEN" --arg n "$CABECERA" \
      'any(.DistributionConfig.Origins.Items[] | select(.Id == $o) | .CustomHeaders.Items[]?; (.HeaderName | ascii_downcase) == ($n | ascii_downcase))' \
      "$TMP/config.json" >/dev/null
  fi
}

respaldar_distribucion() {
  mkdir -p "$RESPALDOS"; chmod 700 "$RESPALDOS"
  RESPALDO="$RESPALDOS/cloudfront-$DIST-$(date -u +%Y%m%dT%H%M%SZ).json"
  cp "$TMP/config.json" "$RESPALDO"
  chmod 600 "$RESPALDO"
  echo "Respaldo de la distribucion (con su ETag) en $RESPALDO"
}

actualizar_distribucion() {  # <archivo con el DistributionConfig nuevo>
  aws_prod cloudfront update-distribution --id "$DIST" --if-match "$ETAG" --distribution-config "file://$1" >/dev/null
  echo "Distribucion actualizada; esperando a que se despliegue (puede tardar unos minutos)..."
  aws_prod cloudfront wait distribution-deployed --id "$DIST"
  echo "Distribucion desplegada."
}

poner_cabecera() {  # <archivo con el valor>
  jq --arg o "$ORIGEN" --arg n "$CABECERA" --rawfile v "$1" '
    .DistributionConfig
    | .Origins.Items |= map(
        if .Id == $o then
          ((.CustomHeaders.Items // []) | map(select((.HeaderName | ascii_downcase) != ($n | ascii_downcase)))
             + [{HeaderName: $n, HeaderValue: ($v | rtrimstr("\n"))}]) as $items
          | .CustomHeaders = {Quantity: ($items | length), Items: $items}
        else . end)' "$TMP/config.json" > "$TMP/nueva.json"
  actualizar_distribucion "$TMP/nueva.json"
}

quitar_cabecera() {
  jq --arg o "$ORIGEN" --arg n "$CABECERA" '
    .DistributionConfig
    | .Origins.Items |= map(
        if .Id == $o then
          ((.CustomHeaders.Items // []) | map(select((.HeaderName | ascii_downcase) != ($n | ascii_downcase)))) as $items
          | .CustomHeaders = (if ($items | length) == 0 then {Quantity: 0} else {Quantity: ($items | length), Items: $items} end)
        else . end)' "$TMP/config.json" > "$TMP/nueva.json"
  actualizar_distribucion "$TMP/nueva.json"
}

# Un pedido por CloudFront: tiene que llegar al backend (CloudFront pone la cabecera, nginx la acepta).
comprobar_por_cloudfront() {
  local dominio intento
  dominio=$(aws_prod cloudfront get-distribution --id "$DIST" --query Distribution.DomainName --output text)
  for intento in 1 2 3 4 5; do
    case "$(curl -fsS --max-time 10 "https://$dominio/actuator/health?o=$RANDOM$intento" 2>/dev/null || true)" in
      *'"status":"UP"'*) echo "OK   /actuator/health por CloudFront responde UP."; return 0 ;;
    esac
    sleep 3
  done
  echo "MAL  /actuator/health por CloudFront no responde UP."
  return 1
}

# ---- Pasos -------------------------------------------------------------------------------------------------
paso_estado() {
  leer_distribucion
  log "Distribucion $DIST, origen $ORIGEN"
  if tiene_cabecera ""; then echo "El origen lleva la cabecera $CABECERA."; else echo "El origen NO lleva la cabecera $CABECERA."; fi
  if existe_parametro origen-secreto; then
    valor_de origen-secreto "$TMP/secreto"
    if tiene_cabecera "$TMP/secreto"; then echo "Su valor coincide con origen-secreto de Parameter Store."
    else echo "Su valor NO coincide con origen-secreto (o la cabecera no esta)."; fi
  else echo "origen-secreto no existe todavia."; fi
  echo "origen-secreto-anterior: $(existe_parametro origen-secreto-anterior && echo presente || echo ausente)"
  echo "origen-ips-directas: $(existe_parametro origen-ips-directas && echo presente || echo ausente)"
  if existe_parametro origen-modo; then
    aws_prod ssm get-parameter --region "$REGION" --name "${PREFIJO}origen-modo" --query Parameter.Value --output text > "$TMP/modo"
    echo "origen-modo: $(cat "$TMP/modo")"
  else echo "origen-modo: (sin parametro: nginx usa registrar)"; fi
  log "Pedidos sin cabecera en las ultimas 24 h (solo lectura, por SSM)"
  pedidos_sin_cabecera
}

paso_agregar() {
  log "Paso 1 de 3: el secreto en Parameter Store"
  if existe_parametro origen-secreto; then
    echo "origen-secreto ya existe: se reutiliza (idempotente)."
    valor_de origen-secreto "$TMP/secreto"
  elif [ "$APLICAR" = si ]; then
    generar_en "$TMP/secreto"
    guardar_secreto origen-secreto "$TMP/secreto"
    echo "origen-secreto creado como SecureString (32 bytes aleatorios; no se imprime)."
  else
    echo "Simulacro: se generaria origen-secreto (SecureString, 32 bytes aleatorios) en ${PREFIJO}."
    : > "$TMP/secreto"
  fi
  leer_distribucion
  log "Paso 2 de 3: nginx acepta el secreto (en modo registrar: no rechaza nada)"
  if [ "$APLICAR" = si ]; then nginx_al_dia || falla "no se pudo actualizar nginx; la distribucion no se toco."
  else echo "Simulacro: se reescribiria y recargaria nginx desde Parameter Store (SSM, SOLO_PROXY=si), sin tocar el backend."; fi
  log "Paso 3 de 3: la cabecera en la distribucion $DIST (origen $ORIGEN)"
  if [ -s "$TMP/secreto" ] && tiene_cabecera "$TMP/secreto"; then
    echo "El origen ya lleva $CABECERA con ese valor: nada que cambiar."
  elif [ "$APLICAR" = si ]; then
    respaldar_distribucion
    poner_cabecera "$TMP/secreto"
  else
    echo "Simulacro: se guardaria un respaldo y se agregaria $CABECERA al origen $ORIGEN (update-distribution con ETag $ETAG, y espera a 'Deployed')."
  fi
  echo
  echo "Siguiente: espera 24 h y corre --paso estado; si no hay pedidos sin cabecera (salvo operacion), --paso exigir."
  echo "Vuelta atras: --revertir --aplicar"
}

paso_exigir() {
  log "Comprobaciones previas"
  leer_distribucion
  existe_parametro origen-secreto || falla "falta origen-secreto: corre antes --paso agregar --aplicar."
  valor_de origen-secreto "$TMP/secreto"
  tiene_cabecera "$TMP/secreto" || falla "la distribucion no lleva la cabecera con el valor de origen-secreto: corre --paso agregar --aplicar y espera a que se despliegue."
  echo "OK   la distribucion lleva la cabecera con el valor vigente."
  pedidos_sin_cabecera | tee "$TMP/sin-cabecera.txt"
  local total
  total=$(grep -o 'TOTAL=[0-9]*' "$TMP/sin-cabecera.txt" | tail -n 1 | cut -d= -f2 || true)
  if [ "${total:-0}" -gt 0 ] && [ "$FORZAR" != si ]; then
    falla "hubo ${total} pedidos sin cabecera en las ultimas 24 h (arriba, por IP). Si son de operacion, ponlas en ${PREFIJO}origen-ips-directas; si no, investiga. --forzar sigue igual."
  fi
  if [ "$APLICAR" != si ]; then
    echo "Simulacro: se pondria ${PREFIJO}origen-modo = exigir, se recargaria nginx y se comprobaria por CloudFront; si no responde UP, vuelve a registrar."
    return 0
  fi
  log "Exigiendo el origen"
  guardar_texto origen-modo exigir
  nginx_al_dia || { guardar_texto origen-modo registrar; falla "no se pudo recargar nginx; origen-modo vuelve a registrar (corre --paso estado)."; }
  if ! comprobar_por_cloudfront; then
    echo "Vuelta atras automatica: nginx a registrar."
    guardar_texto origen-modo registrar
    nginx_al_dia || echo "AVISO: corre a mano: --revertir --aplicar"
    falla "por CloudFront no responde UP con el origen exigido."
  fi
  echo "Listo: nginx rechaza con 403 lo que no trae $CABECERA. Vuelta atras: --revertir --aplicar"
}

paso_rotar() {
  log "Comprobaciones previas"
  leer_distribucion
  existe_parametro origen-secreto || falla "no hay origen-secreto que rotar: corre --paso agregar --aplicar."
  if existe_parametro origen-secreto-anterior && [ "$FORZAR" != si ]; then
    falla "ya hay un origen-secreto-anterior: una rotacion quedo a medias. Terminala (nginx_al_dia y borrar el anterior) o repite con --forzar."
  fi
  if [ "$APLICAR" != si ]; then
    cat <<PLAN
Simulacro. Con --aplicar, en este orden (el orden es lo que evita el corte):
  1. origen-secreto-anterior := el valor vigente; origen-secreto := uno nuevo (32 bytes aleatorios, no se imprimen).
  2. nginx acepta los DOS (SSM, SOLO_PROXY=si).
  3. CloudFront cambia a el nuevo (respaldo + update-distribution con ETag + espera a 'Deployed').
  4. Se comprueba por CloudFront y se espera ${ESPERA_ROTACION} s a que termine de propagar.
  5. Se borra origen-secreto-anterior y nginx deja de aceptar el viejo.
Vuelta atras a mitad de camino: nada se pierde, nginx acepta los dos hasta el paso 5.
PLAN
    return 0
  fi
  valor_de origen-secreto "$TMP/viejo"
  generar_en "$TMP/nuevo"
  log "Paso 1 de 5: los dos secretos en Parameter Store"
  guardar_secreto origen-secreto-anterior "$TMP/viejo"
  guardar_secreto origen-secreto "$TMP/nuevo"
  log "Paso 2 de 5: nginx acepta los dos"
  nginx_al_dia || falla "no se pudo actualizar nginx; CloudFront no se toco (sigue con el secreto de antes, que nginx aun acepta)."
  log "Paso 3 de 5: CloudFront cambia al nuevo"
  respaldar_distribucion
  poner_cabecera "$TMP/nuevo"
  log "Paso 4 de 5: comprobar y esperar la propagacion (${ESPERA_ROTACION} s)"
  comprobar_por_cloudfront || falla "por CloudFront no responde UP; nginx sigue aceptando los dos secretos. Mira --paso estado."
  sleep "$ESPERA_ROTACION"
  log "Paso 5 de 5: nginx suelta el secreto viejo"
  aws_prod ssm delete-parameter --region "$REGION" --name "${PREFIJO}origen-secreto-anterior" >/dev/null
  nginx_al_dia || falla "no se pudo recargar nginx; origen-secreto-anterior ya se borro: corre --paso estado y repite nginx."
  comprobar_por_cloudfront
  echo "Listo: rotacion completa."
}

revertir() {
  log "Vuelta atras: nginx a registrar, y despues se quita la cabecera de CloudFront"
  leer_distribucion
  if [ "$APLICAR" != si ]; then
    echo "Simulacro: ${PREFIJO}origen-modo = registrar y nginx recargado; luego se quitaria $CABECERA del origen $ORIGEN (con respaldo, ETag y espera). Los secretos de Parameter Store se dejan."
    return 0
  fi
  guardar_texto origen-modo registrar
  nginx_al_dia || falla "no se pudo recargar nginx; la cabecera de CloudFront NO se quito (nginx puede seguir exigiendola)."
  if tiene_cabecera ""; then
    respaldar_distribucion
    quitar_cabecera
  else
    echo "El origen ya no lleva la cabecera: nada que quitar."
  fi
  echo "Listo: sin cabecera de origen. Los secretos siguen en Parameter Store por si se vuelve a poner."
}

if [ "$REVERTIR" = si ]; then
  revertir
else
  case "$PASO" in
    estado) paso_estado ;;
    agregar) paso_agregar ;;
    exigir) paso_exigir ;;
    rotar) paso_rotar ;;
  esac
fi
