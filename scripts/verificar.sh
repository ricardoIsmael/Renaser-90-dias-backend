#!/usr/bin/env bash
# Verificación estándar del backend: el mismo camino para cualquier persona, agente o el CI (arnés propio, 2026-11-06).
#
#   scripts/verificar.sh            reglas del arnés + trazabilidad de specs + ./mvnw -B -ntp clean verify (con la guarda de E-111)
#   scripts/verificar.sh --rapido   reglas del arnés + trazabilidad + compilar: no cierra nada (sale con 2)
#   scripts/verificar.sh --nube     como el primero, con los contenedores en Testcontainers Cloud (scripts/test-cloud.sh)
#   scripts/verificar.sh --ci       lo que corre ci.yml: solo Maven y su guarda (el arnés y la trazabilidad tienen su
#                                   propio workflow, trazabilidad.yml, que no espera los ~45 minutos de la suite)
#
# Salida: 0 = todo corrió y pasó; 1 = algo falló; 2 = nada falló pero quedó algo sin correr (NO es «en verde»).
# En esta laptop: córrelo con ~/.config/renaser-e2e/herramientas/pesado.sh y nunca en el checkout del IDE (E-313).
#
# Por qué la guarda de E-111: con JAVA_HOME mal, `mvnw` termina en 0 sin correr una sola prueba. Se suman las líneas de
# RESUMEN de surefire y failsafe (`Tests run: N, …` sin el sufijo `- in <clase>`) y se falla si el total es menor que
# MINIMO_PRUEBAS. No es una meta de cobertura: es un canario contra el cero.
set -uo pipefail

RAIZ=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$RAIZ"
MODO=completo
for a in "$@"; do
  case "$a" in
    --rapido) MODO=rapido ;;
    --nube) MODO=nube ;;
    --ci) MODO=ci ;;
    *) echo "Opción desconocida: $a" >&2; exit 2 ;;
  esac
done
MINIMO_PRUEBAS=${MINIMO_PRUEBAS:-2000}
LOG=${LOG_MAVEN:-salida-maven.log}
RESUMEN=()
FALLO=0
SIN_CORRER=0
ok()  { RESUMEN+=("✔ $1"); }
mal() { RESUMEN+=("✘ $1"); FALLO=1; }
sin() { RESUMEN+=("– $1"); SIN_CORRER=1; }

if [[ $MODO != ci ]]; then
  echo "▶ Reglas del arnés (scripts/arnes.test.mjs) y pruebas del script de trazabilidad"
  if node --test scripts/arnes.test.mjs scripts/trazabilidad-specs.test.mjs; then ok "Reglas del arnés y del script de trazabilidad"; else mal "Reglas del arnés o del script de trazabilidad"; fi

  APP=${RENASER_APP:-}
  if [[ -z $APP ]]; then
    for c in "$RAIZ/../Renaser-90-dias-frontend-" "$RAIZ/../../Renaser-90-dias-frontend-" "$RAIZ/../../../../Renaser-90-dias-frontend-"; do
      [[ -d $c/src ]] && APP=$(cd "$c" && pwd) && break
    done
  fi
  if [[ -n $APP ]]; then
    echo "▶ Trazabilidad de specs contra $APP"
    SALIDA=$(node scripts/trazabilidad-specs.mjs "$APP" 2>&1); ESTADO=$?
    if [[ $ESTADO -eq 0 ]]; then echo "$SALIDA" | tail -1; ok "Trazabilidad de specs ($(echo "$SALIDA" | tail -1))"
    else echo "$SALIDA"; mal "Trazabilidad de specs (salida $ESTADO): un requisito exigido sin prueba o una marca que no existe"; fi
  else
    sin "Trazabilidad de specs: no encontré la app (pon RENASER_APP=<ruta>)"
  fi
fi

if [[ $MODO == rapido ]]; then
  echo "▶ Compilar (./mvnw -B -ntp -q compile)"
  if ./mvnw -B -ntp -q compile; then ok "Compila"; else mal "No compila"; fi
  sin "Suite completa (clean verify): --rapido no cierra nada"
else
  if [[ $MODO == nube ]]; then CMD=(./scripts/test-cloud.sh clean verify); else CMD=(./mvnw -B -ntp clean verify); fi
  echo "▶ ${CMD[*]}  (la salida queda en $LOG)"
  "${CMD[@]}" 2>&1 | tee "$LOG"
  ESTADO=${PIPESTATUS[0]}
  RES=$(grep -E 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' "$LOG" | grep -v -- '- in ' || true)
  if [[ -z $RES ]]; then
    mal "Maven: no aparece ninguna línea 'Tests run:'. O el build falló antes de las pruebas, o terminó sin correr nada (JAVA_HOME mal, E-111). Mira $LOG."
  else
    suma() { echo "$RES" | grep -oE "$1: [0-9]+" | grep -oE '[0-9]+' | awk '{s+=$1} END {print s+0}'; }
    TOTAL=$(suma 'Tests run'); FALLOS=$(suma Failures); ERRORES=$(suma Errors)
    if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then echo "### Pruebas ejecutadas: **$TOTAL** (fallos: $FALLOS, errores: $ERRORES)" >> "$GITHUB_STEP_SUMMARY"; fi
    if [[ $ESTADO -ne 0 || $FALLOS -ne 0 || $ERRORES -ne 0 ]]; then
      mal "Maven: $TOTAL pruebas, $FALLOS fallos, $ERRORES errores (salida $ESTADO). Los informes: target/surefire-reports y target/failsafe-reports."
    elif [[ $TOTAL -lt $MINIMO_PRUEBAS ]]; then
      mal "Maven: solo $TOTAL pruebas, el piso es $MINIMO_PRUEBAS. Faltó correr un bloque de la suite, o el piso quedó viejo."
    else
      ok "Maven clean verify: $TOTAL pruebas, 0 fallos, 0 errores"
    fi
  fi
fi

echo
echo "── Verificación · $(date '+%Y-%m-%d %H:%M') · $(git rev-parse --abbrev-ref HEAD) @ $(git rev-parse --short HEAD) · modo $MODO"
for r in "${RESUMEN[@]}"; do echo "$r"; done
if [[ $FALLO -ne 0 ]]; then
  echo; echo "Algo falló: no está terminado."
  [[ -n ${GITHUB_ACTIONS:-} ]] && echo "::error::Verificación en rojo (ver el resumen de arriba)."
  exit 1
elif [[ $SIN_CORRER -ne 0 ]]; then
  echo; echo "Nada falló, pero hay pasos sin correr: dilo en el PR."; exit 2
fi
echo; echo "Todo en verde."
