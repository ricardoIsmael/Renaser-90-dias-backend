#!/usr/bin/env bash
# Parámetros de producción del acompañante en AWS Parameter Store (/renaser/prod/).
# Ver docs/DESPLIEGUE_Y_CI.md §6.
#
#   ./scripts/despliegue/parametros-acompanante.sh preparar   # ANTES del push a master
#   ./scripts/despliegue/parametros-acompanante.sh prender    # cuando la gente tenga la app nueva
#
# `preparar` deja producción lista para el despliegue sin cambiarle nada a quien tiene la app de hoy.
#   - Pide la API key nueva de Gemini sin mostrarla.
#   - Revisa IA_PROVEEDOR y RENASIA_CHAT_MODEL.
#   - Crea los interruptores nuevos APAGADOS.
#   - Apaga los barridos del semáforo, para que no salga un aviso sin pantalla donde verlo.
# `prender` enciende todo junto: voz del orbe, voz en vivo, botones, memoria, semáforo y, si se
# confirma, los avisos y logros en el chat.
#
# Nunca sobrescribe un parámetro sin preguntar: si ya existe con otro valor, lo muestra y pregunta.
# La key no pasa por la línea de comandos, así que no queda en el historial ni en la lista de
# procesos: va a un archivo temporal que solo lee tu usuario y se borra al terminar.
#
# Los cambios valen recién cuando el contenedor arranca de nuevo (E-244). El despliegue lo reinicia;
# si corres `prender` después, hace falta `docker restart backend` en el servidor.

set -euo pipefail

PREFIJO="/renaser/prod"
REGION="${AWS_REGION:-us-east-1}"
MODELO_DEL_CHAT="gemini-3.5-flash-lite"

# Los interruptores del acompañante: todos valen `false` por defecto en application.yaml.
INTERRUPTORES=(IA_VOZ_PROVEEDOR IA_VOZ_EN_VIVO IA_ACOMPANANTE_CONFIRMACION_CON_BOTONES IA_ACOMPANANTE_MEMORIA)
# El valor que tiene cada uno "prendido". IA_VOZ_PROVEEDOR no es true/false: apagado es `noop`.
declare -A PRENDIDO=([IA_VOZ_PROVEEDOR]=google [IA_VOZ_EN_VIVO]=true
                     [IA_ACOMPANANTE_CONFIRMACION_CON_BOTONES]=true [IA_ACOMPANANTE_MEMORIA]=true)
declare -A APAGADO=([IA_VOZ_PROVEEDOR]=noop [IA_VOZ_EN_VIVO]=false
                    [IA_ACOMPANANTE_CONFIRMACION_CON_BOTONES]=false [IA_ACOMPANANTE_MEMORIA]=false)
# Los barridos del semáforo (D-168). Con el cron en "-", Spring no los programa: no se calcula ni se
# avisa nada. Al volver a su default, recalcula solo (el cálculo es derivado e idempotente).
CRONES_DEL_SEMAFORO=(renaser.scheduling.semaforo.cron renaser.scheduling.resumen-semaforo.cron)

TEMPORAL="$(umask 077 && mktemp)"
trap 'rm -f "$TEMPORAL"' EXIT

nombre() { printf '%s/%s' "$PREFIJO" "$1"; }

# El valor actual de un parámetro String, o vacío si no existe.
actual() {
  aws ssm get-parameter --region "$REGION" --name "$(nombre "$1")" \
      --query 'Parameter.Value' --output text 2>/dev/null || true
}

existe() {
  aws ssm get-parameter --region "$REGION" --name "$(nombre "$1")" --query 'Parameter.Name' \
      --output text >/dev/null 2>&1
}

preguntar() {
  local respuesta
  read -r -p "$1 [s/N] " respuesta
  [[ "$respuesta" =~ ^[sS]$ ]]
}

# Guarda un parámetro. El valor viaja en un JSON temporal, nunca como argumento.
guardar() {
  local nombre_corto="$1" valor="$2" tipo="$3" sobrescribir="$4"
  VALOR="$valor" NOMBRE="$(nombre "$nombre_corto")" TIPO="$tipo" SOBRESCRIBIR="$sobrescribir" python3 -c '
import json, os
print(json.dumps({"Name": os.environ["NOMBRE"], "Value": os.environ["VALOR"], "Type": os.environ["TIPO"],
                  "Overwrite": os.environ["SOBRESCRIBIR"] == "si"}))' > "$TEMPORAL"
  aws ssm put-parameter --region "$REGION" --cli-input-json "file://$TEMPORAL" >/dev/null
  : > "$TEMPORAL"
}

# Deja un parámetro String en `deseado`: lo crea si falta; si tiene otro valor, pregunta.
asegurar() {
  local nombre_corto="$1" deseado="$2" motivo="$3" valor
  valor="$(actual "$nombre_corto")"
  if [[ -z "$valor" ]]; then
    guardar "$nombre_corto" "$deseado" String no
    echo "  + $nombre_corto = $deseado ($motivo)"
  elif [[ "$valor" == "$deseado" ]]; then
    echo "  = $nombre_corto ya estaba en $deseado"
  elif preguntar "  ? $nombre_corto está en '$valor'. ¿Cambiarlo a '$deseado'? ($motivo)"; then
    guardar "$nombre_corto" "$deseado" String si
    echo "  ~ $nombre_corto = $deseado"
  else
    echo "  - $nombre_corto queda en '$valor' (no se tocó)"
  fi
}

key_de_gemini() {
  local key
  echo "API key de Gemini (GOOGLE_GENAI_API_KEY)"
  read -r -s -p "  Pega la key nueva y Enter (no se muestra; vacío = no tocarla): " key
  echo
  if [[ -z "$key" ]]; then
    echo "  - GOOGLE_GENAI_API_KEY no se tocó"
    return
  fi
  if existe GOOGLE_GENAI_API_KEY; then
    if preguntar "  ? Ya hay una key guardada. ¿Reemplazarla por la nueva?"; then
      guardar GOOGLE_GENAI_API_KEY "$key" SecureString si
      echo "  ~ GOOGLE_GENAI_API_KEY reemplazada"
    else
      echo "  - GOOGLE_GENAI_API_KEY queda como estaba"
    fi
  else
    guardar GOOGLE_GENAI_API_KEY "$key" SecureString no
    echo "  + GOOGLE_GENAI_API_KEY guardada"
  fi
  key=""
}

# El modelo del chat: sin parámetro vale el default del código. Si hay uno con otro modelo, lo pisa.
modelo_del_chat() {
  local valor
  valor="$(actual RENASIA_CHAT_MODEL)"
  if [[ -z "$valor" ]]; then
    echo "  = RENASIA_CHAT_MODEL no está: vale el default del código, $MODELO_DEL_CHAT"
  elif [[ "$valor" != "$MODELO_DEL_CHAT" ]]; then
    asegurar RENASIA_CHAT_MODEL "$MODELO_DEL_CHAT" "el más estable medido, E-233"
  else
    echo "  = RENASIA_CHAT_MODEL ya estaba en $MODELO_DEL_CHAT"
  fi
}

preparar() {
  echo "Preparando $PREFIJO para el despliegue (región $REGION)."
  key_de_gemini
  echo "La IA y el modelo del chat"
  asegurar IA_PROVEEDOR google "sin esto no hay acompañante"
  modelo_del_chat
  echo "Interruptores nuevos: se crean APAGADOS; los que ya existan no se tocan"
  for interruptor in "${INTERRUPTORES[@]}"; do
    if existe "$interruptor"; then
      echo "  = $interruptor ya existe, en '$(actual "$interruptor")' (no se tocó)"
    else
      guardar "$interruptor" "${APAGADO[$interruptor]}" String no
      echo "  + $interruptor = ${APAGADO[$interruptor]}"
    fi
  done
  echo "Semáforo: sin barridos hasta que la gente tenga la app nueva"
  for cron in "${CRONES_DEL_SEMAFORO[@]}"; do
    asegurar "$cron" "-" "nadie tiene todavía la pantalla del semáforo"
  done
  echo "Listo. Ahora sí, el push a master (el despliegue reinicia el contenedor y toma todo esto)."
}

prender() {
  echo "Prendiendo todo junto en $PREFIJO (región $REGION)."
  for interruptor in "${INTERRUPTORES[@]}"; do
    asegurar "$interruptor" "${PRENDIDO[$interruptor]}" "prender con la app nueva"
  done
  if preguntar "  ? ¿Prender también los avisos de hábitos y los logros en el chat? (sus textos siguen marcados como provisorios)"; then
    asegurar IA_ACOMPANANTE_AVISOS_EN_CHAT true "avisos de hábitos en el chat"
    asegurar IA_ACOMPANANTE_LOGROS_EN_CHAT true "logros en el chat"
  fi
  for cron in "${CRONES_DEL_SEMAFORO[@]}"; do
    if existe "$cron"; then
      if preguntar "  ? ¿Borrar $cron para que el semáforo vuelva a su horario de siempre?"; then
        aws ssm delete-parameter --region "$REGION" --name "$(nombre "$cron")"
        echo "  x $cron borrado: vuelve al default del código"
      fi
    else
      echo "  = $cron no está: el semáforo ya corre con su default"
    fi
  done
  echo "Listo. Falta reiniciar el contenedor para que lo tome: docker restart backend (E-244)."
}

case "${1:-}" in
  preparar) preparar ;;
  prender) prender ;;
  *) echo "Uso: $0 preparar|prender" >&2; exit 2 ;;
esac
