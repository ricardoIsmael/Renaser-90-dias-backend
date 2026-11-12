#!/usr/bin/env bash
# =====================================================================================
# El modo de un grupo del limite por cuenta, SIN reiniciar el backend (spec 050 tramo C: FR-016 y Q6; D12).
# Lo corre el DUENO, desde su maquina, con el perfil `prod`. Nunca un agente ni el CD.
#
# COMO FUNCIONA. El backend lee el modo de cada grupo de la clave de Redis `proteccion:modo:<grupo>` cada 30 s y ese valor manda
# sobre el de application.yaml. Cambiarlo es un SET (o un DEL, para volver al de la configuracion): no hace falta redesplegar.
# Reiniciar costaria 1-2 minutos de 502. Los grupos nacen todos en REGISTRAR: cuentan y anotan «habria bloqueado», pero NUNCA
# responden 429. Con APLICAR, el grupo que pasa su tope responde 429 con Retry-After.
#
#   0. Variables (las dos son obligatorias; el script se niega a seguir sin ellas):
#        export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#        export RENASER_INSTANCIA=<id de la instancia EC2 del backend, i-...>
#   1. Ver el modo de cada grupo (solo lectura):
#        scripts/despliegue/limites-modo.sh estado
#   2. Simulacro (es lo que hace sin --aplicar): muestra el valor de hoy y el que pondria, sin tocar nada:
#        scripts/despliegue/limites-modo.sh <grupo> <modo>
#   3. Aplicar (el backend lo ve en menos de 30 s):
#        scripts/despliegue/limites-modo.sh <grupo> <modo> --aplicar
#   Grupos: lectura escritura muro chat subidas ia-fuera-de-ser ser exportaciones admin webhook-whatsapp
#   Modos : DESACTIVADO | REGISTRAR | APLICAR | CONFIGURACION (borra el valor de Redis: rige el de application.yaml)
#
# VUELTA ATRAS a un comando. Cada --aplicar guarda el valor anterior en ~/.renaser/limites-modo/<grupo>.anterior y lo imprime:
#        scripts/despliegue/limites-modo.sh <grupo> CONFIGURACION --aplicar     # lo mas rapido: vuelve al de la configuracion
#        scripts/despliegue/limites-modo.sh --revertir <grupo> --aplicar        # vuelve al valor que tenia antes del ultimo cambio
#   Apagar TODO el filtro de golpe no es esto: es `renaser.proteccion.limites.habilitado=false` (PROTECCION_LIMITES_HABILITADO) y un despliegue.
#
# Reglas de D12 / FR-011 que cumple: simulacro por omision (solo `--aplicar` cambia algo), perfil `prod` explicito y cuenta
# verificada, idempotente (poner el modo que ya hay no cambia nada), guarda lo anterior para volver atras, nada de secretos en
# pantalla ni en el repositorio (la contrasena de Redis, si existe, la lee la instancia de Parameter Store y entra por REDISCLI_AUTH,
# nunca por argumento) y `set -euo pipefail`.
# =====================================================================================
set -euo pipefail

PERFIL=prod
REGION=${REGION:-${RENASER_REGION:-us-east-1}}
PREFIJO=${PREFIJO:-${RENASER_PREFIJO_PARAMETROS:-/renaser/prod/}}
CONTENEDOR=${CONTENEDOR:-redis}
GRUPOS="lectura escritura muro chat subidas ia-fuera-de-ser ser exportaciones admin webhook-whatsapp"
MODOS="DESACTIVADO REGISTRAR APLICAR CONFIGURACION"
APLICAR=${APLICAR:-no}
RESPALDOS=${RENASER_RESPALDOS:-$HOME/.renaser/limites-modo}

falla() { echo "ERROR: $*" >&2; exit 1; }
en_lista() { case " $2 " in *" $1 "*) return 0 ;; esac; return 1; }

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE DENTRO DE LA INSTANCIA (EN_LA_INSTANCIA=leer|poner). Lo manda el script local por SSM.
# ------------------------------------------------------------------------------------------------
parametro() {
  aws ssm get-parameter --region "$REGION" --with-decryption --name "${PREFIJO}$1" \
    --query Parameter.Value --output text 2>/dev/null || true
}

# redis-cli dentro del contenedor. Si Redis ya exige contrasena (spec 050 A1), entra por REDISCLI_AUTH; si no, sin ella.
rc() {
  local clave
  clave=$(parametro REDIS_PASSWORD)
  if [ -n "$clave" ] && [ "$clave" != None ]; then
    ( export REDISCLI_AUTH="$clave"; docker exec -i -e REDISCLI_AUTH "$CONTENEDOR" redis-cli "$@" )
  else
    docker exec -i "$CONTENEDOR" redis-cli "$@"
  fi
}

valor_de() { rc GET "proteccion:modo:$1" | tr -d '\r'; }

if [ -n "${EN_LA_INSTANCIA:-}" ]; then
  en_lista "${GRUPO:-}" "$GRUPOS" || [ "$EN_LA_INSTANCIA" = estado ] || { echo "Grupo desconocido." >&2; exit 2; }
  case "$EN_LA_INSTANCIA" in
    estado)
      for g in $GRUPOS; do printf '%s=%s\n' "$g" "$(valor_de "$g")"; done
      ;;
    poner)
      en_lista "$MODO" "$MODOS" || { echo "Modo desconocido." >&2; exit 2; }
      anterior=$(valor_de "$GRUPO")
      echo "ANTERIOR=$anterior"
      nuevo=$MODO; [ "$MODO" = CONFIGURACION ] && nuevo=""
      if [ "$nuevo" = "$anterior" ]; then
        echo "Sin cambios: el grupo $GRUPO ya esta en ${MODO}."
      elif [ "$APLICAR" != si ]; then
        echo "SIMULACRO: pondria $GRUPO=${MODO} (hoy: ${anterior:-CONFIGURACION}). No se cambio nada."
      else
        if [ -z "$nuevo" ]; then rc DEL "proteccion:modo:$GRUPO" >/dev/null; else rc SET "proteccion:modo:$GRUPO" "$nuevo" >/dev/null; fi
        echo "APLICADO: $GRUPO=${MODO}. El backend lo ve en menos de 30 s."
      fi
      ;;
    *) echo "Accion desconocida." >&2; exit 2 ;;
  esac
  exit 0
fi

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE EN LA MAQUINA DEL DUENO
# ------------------------------------------------------------------------------------------------
uso() { sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; }

ACCION=""; GRUPO=""; MODO=""; REVERTIR=no
while [ $# -gt 0 ]; do
  case "$1" in
    --aplicar) APLICAR=si ;;
    --simular) APLICAR=no ;;
    --revertir) REVERTIR=si ;;
    -h|--help) uso; exit 0 ;;
    estado) ACCION=estado ;;
    -*) echo "Opcion desconocida: $1" >&2; uso >&2; exit 2 ;;
    *) if [ -z "$GRUPO" ]; then GRUPO=$1; elif [ -z "$MODO" ]; then MODO=$1; else echo "Sobra: $1" >&2; exit 2; fi ;;
  esac
  shift
done
[ "$ACCION" = estado ] && GRUPO=""
if [ "$ACCION" != estado ]; then
  [ -n "$GRUPO" ] || { echo "Falta el grupo (o la palabra «estado»)." >&2; uso >&2; exit 2; }
  en_lista "$GRUPO" "$GRUPOS" || { echo "El grupo debe ser uno de: $GRUPOS" >&2; exit 2; }
  if [ "$REVERTIR" = si ]; then
    [ -f "$RESPALDOS/$GRUPO.anterior" ] || falla "no hay un respaldo de $GRUPO en $RESPALDOS: no se cambio nada."
    MODO=$(cat "$RESPALDOS/$GRUPO.anterior"); [ -n "$MODO" ] || MODO=CONFIGURACION
  fi
  [ -n "$MODO" ] || { echo "Falta el modo: $MODOS" >&2; exit 2; }
  en_lista "$MODO" "$MODOS" || { echo "El modo debe ser uno de: $MODOS" >&2; exit 2; }
  ACCION=poner
fi

for h in aws jq base64; do command -v "$h" >/dev/null || falla "falta $h en esta maquina."; done
: "${RENASER_CUENTA_AWS:?exporta RENASER_CUENTA_AWS con el id de 12 digitos de la cuenta de produccion}"
: "${RENASER_INSTANCIA:?exporta RENASER_INSTANCIA con el id de la instancia EC2 del backend}"
CUENTA=$(aws sts get-caller-identity --profile "$PERFIL" --query Account --output text)
[ "$CUENTA" = "$RENASER_CUENTA_AWS" ] || falla "el perfil $PERFIL apunta a la cuenta $CUENTA, no a la esperada ($RENASER_CUENTA_AWS). No se cambio nada."
echo "Cuenta verificada ($CUENTA, perfil $PERFIL). Modo: $([ "$APLICAR" = si ] && echo APLICAR || echo SIMULACRO)."

PARAMETROS=$(mktemp); trap "rm -f '$PARAMETROS'" EXIT
en_la_instancia() {  # <accion>: manda ESTE script por SSM, con las variables por delante (patron del CD)
  local accion=$1 cid estado desde
  jq -n --arg b64 "$(base64 -w0 "$0")" --arg accion "$accion" --arg aplicar "$APLICAR" --arg prefijo "$PREFIJO" \
        --arg region "$REGION" --arg contenedor "$CONTENEDOR" --arg grupo "$GRUPO" --arg modo "$MODO" \
    '{commands: [
       "set -eu",
       "S=$(mktemp /tmp/limites-modo.XXXXXX)",
       "trap \"rm -f $S\" EXIT",
       ("echo " + $b64 + " | base64 -d > \"$S\""),
       ("EN_LA_INSTANCIA=" + $accion + " APLICAR=" + $aplicar + " PREFIJO=" + $prefijo + " REGION=" + $region + " CONTENEDOR=" + $contenedor + " GRUPO=" + $grupo + " MODO=" + $modo + " bash \"$S\"")
     ]}' > "$PARAMETROS"
  cid=$(aws ssm send-command --profile "$PERFIL" --region "$REGION" --instance-ids "$RENASER_INSTANCIA" \
          --document-name AWS-RunShellScript --comment "limites-modo: $accion $GRUPO" \
          --parameters "file://$PARAMETROS" --query Command.CommandId --output text)
  echo "Comando SSM $cid enviado; esperando el resultado..."
  desde=$SECONDS
  while true; do
    estado=$(aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
               --instance-id "$RENASER_INSTANCIA" --query Status --output text 2>/dev/null || echo Pending)
    case "$estado" in Success|Failed|Cancelled|TimedOut) break ;; esac
    [ $(( SECONDS - desde )) -ge 300 ] && { estado=SinRespuesta; break; }
    sleep 3
  done
  SALIDA=$(aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
             --instance-id "$RENASER_INSTANCIA" --query StandardOutputContent --output text || true)
  echo "----- salida de la instancia -----"
  echo "$SALIDA" | grep -v '^ANTERIOR=' || true
  if [ "$estado" != Success ]; then
    aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
      --instance-id "$RENASER_INSTANCIA" --query StandardErrorContent --output text || true
    falla "la instancia termino con estado $estado."
  fi
}

en_la_instancia "$ACCION"
if [ "$ACCION" = poner ] && [ "$APLICAR" = si ]; then
  anterior=$(echo "$SALIDA" | sed -n 's/^ANTERIOR=//p' | head -n1)
  mkdir -p "$RESPALDOS"
  printf '%s' "$anterior" > "$RESPALDOS/$GRUPO.anterior"
  echo "Valor anterior de $GRUPO guardado en $RESPALDOS/$GRUPO.anterior (${anterior:-CONFIGURACION})."
  echo "Para volver atras:  $0 --revertir $GRUPO --aplicar   (o: $0 $GRUPO CONFIGURACION --aplicar)"
fi
