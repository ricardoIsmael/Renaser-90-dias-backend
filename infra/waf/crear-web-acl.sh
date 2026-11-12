#!/usr/bin/env bash
# =====================================================================================
# Crea el Web ACL de AWS WAF para CloudFront (spec 050 tramo B: FR-001 a FR-005 y FR-011; decision D2).
# Lo corre el DUENO, desde su maquina, con el perfil `prod`. Nunca un agente ni el CD.
#
# QUE CREA (`infra/waf/web-acl.plantilla.json`, alcance CLOUDFRONT, us-east-1):
#   - dos reglas por tasa por IP: `limite-global` (2.000 pedidos / 5 min en toda la API) y `limite-auth`
#     (300 / 5 min en las rutas que empiezan por /api/v1/auth/);
#   - tres grupos administrados de AWS: reputacion de IP, entradas conocidas como malas y reglas comunes;
#   - TODO EN `Count`: cuenta y deja pasar. Pasar a `Block` es otro paso (pasar-regla-a-bloqueo.sh), regla por regla;
#   - el registro completo en CloudWatch Logs (`aws-waf-logs-renaser`, 14 dias) con `x-auth-token`, `authorization`
#     y `cookie` censurados. El WAF no guarda el cuerpo de los pedidos.
# Crear el Web ACL NO lo asocia a la distribucion: eso es asociar-web-acl.sh. Sin asociar no ve trafico.
#
# ORDEN (cada paso se verifica antes del siguiente):
#   0. Variables:   export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#   1. Simulacro (valida la plantilla con AWS: `check-capacity`; no crea nada):
#        infra/waf/crear-web-acl.sh
#   2. Crear:       infra/waf/crear-web-acl.sh --aplicar
#   3. Asociar en Count: infra/waf/asociar-web-acl.sh --aplicar
#   4. 7 dias de metricas y muestras, y despues pasar a bloqueo con pasar-regla-a-bloqueo.sh.
#
# CAMBIAR LA PLANTILLA DESPUES (p. ej. una excepcion por falso positivo, FR-003: se agrega a
# `Renaser.Excepciones` de la plantilla con su motivo y su fecha, nunca se apaga el grupo entero):
#        infra/waf/crear-web-acl.sh --actualizar [--aplicar]
#   Conserva el Count/Block que cada regla tenga ahora: no devuelve a Count lo que ya se bloqueo.
#
# VUELTA ATRAS (borrar el Web ACL; antes hay que desasociarlo: asociar-web-acl.sh --revertir --aplicar):
#        infra/waf/crear-web-acl.sh --revertir [--aplicar] [--borrar-registros]
#   Guarda una copia del Web ACL en $RESPALDOS (fuera del repositorio) antes de borrarlo. El grupo de registros
#   solo se borra con --borrar-registros (tiene los registros de los ultimos 14 dias).
# =====================================================================================
set -euo pipefail

APLICAR=no; SIMULAR=no; ACCION=crear; BORRAR_REGISTROS=no
while [ $# -gt 0 ]; do
  case "$1" in
    --simular) SIMULAR=si ;;
    --aplicar) APLICAR=si ;;
    --actualizar) ACCION=actualizar ;;
    --revertir) ACCION=revertir ;;
    --borrar-registros) BORRAR_REGISTROS=si ;;
    -h|--help) sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    *) echo "Opcion desconocida: $1 (ver --help)" >&2; exit 2 ;;
  esac
  shift
done
[ "$SIMULAR" = si ] && APLICAR=no
# shellcheck source=infra/waf/comun.sh
source "$(dirname "$0")/comun.sh"
preparar

GRUPO_DE_REGISTROS=$(jq -r .Renaser.RegistroDeCloudWatch.GrupoDeRegistros "$PLANTILLA")
RETENCION=$(jq -r .Renaser.RegistroDeCloudWatch.RetencionEnDias "$PLANTILLA")
ARN_REGISTROS="arn:aws:logs:$REGION:$CUENTA:log-group:$GRUPO_DE_REGISTROS"

existe_grupo_de_registros() {
  [ "$(aws_prod logs describe-log-groups --log-group-name-prefix "$GRUPO_DE_REGISTROS" --query "length(logGroups[?logGroupName=='$GRUPO_DE_REGISTROS'])" --output text)" != 0 ]
}

validar_con_aws() {  # el simulacro de verdad: AWS revisa la plantilla y dice cuanta capacidad (WCU) usa
  local capacidad
  jq -c '.' <<< "$1" > "$TMP/reglas.json"
  capacidad=$(aws_prod wafv2 check-capacity --scope "$ALCANCE" --rules "file://$TMP/reglas.json" --query Capacity --output text) \
    || falla "AWS rechazo las reglas de la plantilla (check-capacity). Nada se creo."
  echo "check-capacity: las reglas usan $capacidad WCU (un Web ACL incluye 1.500; AWS cobra aparte lo que pase)."
}

configurar_registro() {  # <ARN del Web ACL>
  jq -n --arg acl "$1" --arg destino "$ARN_REGISTROS" --argjson campos "$(jq -c '[.Renaser.RegistroDeCloudWatch.CabecerasCensuradas[] | {SingleHeader: {Name: .}}]' "$PLANTILLA")" \
    '{LoggingConfiguration: {ResourceArn: $acl, LogDestinationConfigs: [$destino], RedactedFields: $campos}}' > "$TMP/registro.json"
  aws_prod wafv2 put-logging-configuration --cli-input-json "file://$TMP/registro.json" >/dev/null
  echo "Registro configurado: $GRUPO_DE_REGISTROS, con censura de $(jq -r '.Renaser.RegistroDeCloudWatch.CabecerasCensuradas | join(", ")' "$PLANTILLA")."
}

crear() {
  log "Plantilla y capacidad"
  resumen_de_reglas "$PLANTILLA"
  validar_con_aws "$(reglas_para_aws)"
  local existente
  existente=$(acl_existente || true)
  if [ -n "$existente" ]; then
    echo "El Web ACL $NOMBRE ya existe ($(jq -r .Id <<< "$existente")): no se crea otro (idempotente). Para cambiarlo: --actualizar."
  fi
  if [ "$APLICAR" != si ]; then
    cat <<PLAN

SIMULACRO. Con --aplicar se haria, en este orden:
  1. Crear el grupo de registros $GRUPO_DE_REGISTROS en $REGION con retencion de $RETENCION dias$(existe_grupo_de_registros && echo " (ya existe: solo se confirma la retencion)").
  2. Crear el Web ACL $NOMBRE (alcance $ALCANCE) con las reglas de arriba, todas en Count, y la respuesta 429 con
     Retry-After lista para cuando una regla por tasa pase a Block.
  3. Configurar el registro hacia $GRUPO_DE_REGISTROS con $(jq -r '.Renaser.RegistroDeCloudWatch.CabecerasCensuradas | length' "$PLANTILLA") cabeceras censuradas.
No se asocia a ninguna distribucion (eso es asociar-web-acl.sh). Vuelta atras: crear-web-acl.sh --revertir --aplicar
PLAN
    return 0
  fi
  log "Paso 1 de 3: grupo de registros"
  if existe_grupo_de_registros; then echo "$GRUPO_DE_REGISTROS ya existe."
  else aws_prod logs create-log-group --log-group-name "$GRUPO_DE_REGISTROS"; echo "$GRUPO_DE_REGISTROS creado."; fi
  aws_prod logs put-retention-policy --log-group-name "$GRUPO_DE_REGISTROS" --retention-in-days "$RETENCION"
  echo "Retencion: $RETENCION dias."
  log "Paso 2 de 3: el Web ACL"
  if [ -z "$existente" ]; then
    entrada_de_creacion > "$TMP/creacion.json"
    respaldar "web-acl-$NOMBRE-creacion" "$TMP/creacion.json"
    aws_prod wafv2 create-web-acl --cli-input-json "file://$TMP/creacion.json" > "$TMP/creado.json"
    existente=$(jq -c .Summary "$TMP/creado.json")
    echo "Web ACL creado: $(jq -r .Id <<< "$existente")."
  fi
  log "Paso 3 de 3: el registro"
  configurar_registro "$(jq -r .ARN <<< "$existente")"
  echo
  echo "Listo. ARN: $(jq -r .ARN <<< "$existente")"
  echo "Siguiente: infra/waf/asociar-web-acl.sh (simulacro) y luego --aplicar. Todo sigue en Count: no bloquea nada."
}

actualizar() {
  local existente vivo
  existente=$(acl_existente || true)
  [ -n "$existente" ] || falla "el Web ACL $NOMBRE no existe: corre primero crear-web-acl.sh --aplicar."
  aws_prod wafv2 get-web-acl --name "$NOMBRE" --scope "$ALCANCE" --id "$(jq -r .Id <<< "$existente")" > "$TMP/vivo.json"
  # La plantilla manda en todo salvo en la accion de cada regla: lo que ya se paso a Block se conserva.
  jq --slurpfile vivo "$TMP/vivo.json" --argjson reglas "$(reglas_para_aws)" '
      ($vivo[0].WebACL.Rules | map({key: .Name, value: {Action: .Action, OverrideAction: .OverrideAction}}) | from_entries) as $actual
      | .Rules = ($reglas | map(. as $r | ($actual[$r.Name] // {}) as $a
          | (if $a.Action then .Action = $a.Action else . end)
          | (if $a.OverrideAction then .OverrideAction = $a.OverrideAction else . end)))' "$PLANTILLA" \
    | jq --arg id "$(jq -r .Id <<< "$existente")" --arg lock "$(jq -r .LockToken "$TMP/vivo.json")" \
         'del(.Renaser) | .Id = $id | .LockToken = $lock' > "$TMP/actualizacion.json"
  vivo="$TMP/vivo.json"
  log "Lo que hay ahora"; resumen_de_reglas "$vivo"
  log "Como quedaria (la accion de cada regla se conserva)"; resumen_de_reglas "$TMP/actualizacion.json"
  validar_con_aws "$(jq -c .Rules "$TMP/actualizacion.json")"
  if [ "$APLICAR" != si ]; then echo "Simulacro: no se cambio nada. Con --aplicar se actualiza el Web ACL."; return 0; fi
  respaldar "web-acl-$NOMBRE-antes-de-actualizar" "$vivo"
  aws_prod wafv2 update-web-acl --cli-input-json "file://$TMP/actualizacion.json" >/dev/null
  echo "Web ACL actualizado. Vuelta atras: la copia de arriba es lo que habia (get-web-acl)."
}

revertir() {
  local existente id arn asociadas
  existente=$(acl_existente || true)
  if [ -z "$existente" ]; then echo "El Web ACL $NOMBRE no existe: nada que borrar."; return 0; fi
  id=$(jq -r .Id <<< "$existente"); arn=$(jq -r .ARN <<< "$existente")
  # shellcheck disable=SC2016  # las comillas invertidas son de JMESPath, no del shell
  asociadas=$(aws_prod cloudfront list-distributions-by-web-acl-id --web-acl-id "$arn" --query 'length(DistributionList.Items || `[]`)' --output text 2>/dev/null || echo desconocido)
  if [ "$asociadas" != 0 ]; then
    falla "el Web ACL sigue asociado a $asociadas distribucion(es) (o no pude comprobarlo): primero asociar-web-acl.sh --revertir --aplicar."
  fi
  echo "El Web ACL $NOMBRE ($id) no esta asociado a ninguna distribucion."
  if [ "$APLICAR" != si ]; then
    echo "Simulacro: se guardaria una copia, se borraria el registro y el Web ACL$([ "$BORRAR_REGISTROS" = si ] && echo ", y el grupo de registros $GRUPO_DE_REGISTROS")."
    return 0
  fi
  aws_prod wafv2 get-web-acl --name "$NOMBRE" --scope "$ALCANCE" --id "$id" > "$TMP/vivo.json"
  respaldar "web-acl-$NOMBRE-borrado" "$TMP/vivo.json"
  aws_prod wafv2 delete-logging-configuration --resource-arn "$arn" >/dev/null 2>&1 || echo "(no tenia registro configurado)"
  aws_prod wafv2 delete-web-acl --name "$NOMBRE" --scope "$ALCANCE" --id "$id" --lock-token "$(jq -r .LockToken "$TMP/vivo.json")" >/dev/null
  echo "Web ACL borrado."
  if [ "$BORRAR_REGISTROS" = si ]; then aws_prod logs delete-log-group --log-group-name "$GRUPO_DE_REGISTROS"; echo "$GRUPO_DE_REGISTROS borrado."
  else echo "El grupo de registros $GRUPO_DE_REGISTROS se deja (borrarlo: --revertir --aplicar --borrar-registros)."; fi
}

case "$ACCION" in
  crear) crear ;;
  actualizar) actualizar ;;
  revertir) revertir ;;
esac
