#!/usr/bin/env bash
# Muestrea cada 5 s el /actuator/prometheus del backend de la RÉPLICA (puerto de administración
# 8091, abierto solo a la IP de quien prueba) y escribe un CSV con lo que importa para saber qué
# se satura primero: conexiones de Hikari (activas, ociosas, en espera), heap de la JVM, CPU del
# proceso y del sistema (lo que ve la JVM dentro del contenedor) e hilos vivos.
#
# Uso: ./monitorear.sh http://<ip-replica>:8091 salida.csv   (Ctrl+C para cortar)
set -euo pipefail
URL=${1:?falta la URL del puerto de administración, p. ej. http://1.2.3.4:8091}
SALIDA=${2:?falta el archivo CSV de salida}
INTERVALO=${INTERVALO:-5}

echo "ts,hikari_activas,hikari_ociosas,hikari_pendientes,hikari_max,heap_usado_mb,heap_max_mb,cpu_proceso,cpu_sistema,hilos_vivos,gc_pausa_s_total" > "$SALIDA"
while true; do
  M=$(curl -fsS --max-time 4 "$URL/actuator/prometheus" 2>/dev/null || true)
  if [ -n "$M" ]; then
    echo "$M" | awk -v ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)" '
      /^hikaricp_connections_active\{/   {a=$NF}
      /^hikaricp_connections_idle\{/     {o=$NF}
      /^hikaricp_connections_pending\{/  {p=$NF}
      /^hikaricp_connections_max\{/      {mx=$NF}
      /^jvm_memory_used_bytes\{.*area="heap"/ {hu+=$NF}
      /^jvm_memory_max_bytes\{.*area="heap"/  {if ($NF>0) hm+=$NF}
      /^process_cpu_usage\{/ {cp=$NF}
      /^system_cpu_usage\{/  {cs=$NF}
      /^jvm_threads_live_threads\{/ {h=$NF}
      /^jvm_gc_pause_seconds_sum\{/ {gc+=$NF}
      END {printf "%s,%s,%s,%s,%s,%.0f,%.0f,%.3f,%.3f,%s,%.3f\n", ts,a,o,p,mx,hu/1048576,hm/1048576,cp,cs,h,gc}' >> "$SALIDA"
  else
    echo "$(date -u +%Y-%m-%dT%H:%M:%SZ),,,,,,,,,," >> "$SALIDA"
  fi
  sleep "$INTERVALO"
done
