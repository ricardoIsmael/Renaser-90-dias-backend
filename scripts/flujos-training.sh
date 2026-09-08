#!/usr/bin/env bash
# Los flujos de Training, de punta a punta, contra el servidor corriendo.
#
# POR QUE FLUJOS Y NO CASOS SUELTOS. Un endpoint que responde 204 no prueba nada: lo que importa es
# si la SECUENCIA que hace una persona termina en el estado que esa persona esperaba. Cada flujo de
# abajo recorre eso entero -- pedir, mirar la base, y mirar que lo de al lado NO se haya movido --
# porque casi todos los defectos de esta pantalla fueron efectos colaterales, no errores del
# endpoint que se estaba llamando. El check que no guardaba, las pastillas de dias que el guardado
# ignoraba y la vista semanal que se salteaba el cambio pendiente pasaban los tres una prueba de
# "responde 200".
#
# DEJA LA BASE COMO LA ENCONTRO. Copia las filas del participante en las cuatro tablas que puede
# tocar y las repone al final, pase lo que pase (trap EXIT). Aun asi: correlo contra dev.
#
# USO
#   RENASER_EMAIL=... RENASER_PASS=... ./scripts/flujos-training.sh [http://host:8080]
#
# Las credenciales van por variable de entorno a proposito: no se escriben en disco ni quedan en el
# historial del shell si se exportan antes.

set -uo pipefail

API="${1:-http://localhost:8080}"
EMAIL="${RENASER_EMAIL:?falta RENASER_EMAIL}"
PASS="${RENASER_PASS:?falta RENASER_PASS}"
DB="${DB_CONTENEDOR:-renaser-db}"

# El shell puede estar dentro de un contenedor distrobox mientras docker vive en el host. Se resuelve
# una vez y todo lo demas usa $DOCKER, para que el script corra igual en las dos situaciones.
if docker ps >/dev/null 2>&1; then DOCKER=(docker)
elif distrobox-host-exec docker ps >/dev/null 2>&1; then DOCKER=(distrobox-host-exec docker)
else echo "No llego a docker ni desde aca ni por el host."; exit 1; fi

# Las cuatro tablas que estos flujos escriben. El orden importa al reponer: el historial y los
# pendientes no dependen de nadie, pero se borran y reponen juntos igual.
TABLAS=(horario_semanal_habito horarios_habito_por_fecha preferencias_horario cambios_horario_pendientes)

ok=0; fallos=0
TMP=$(mktemp -d); trap 'restaurar; rm -rf "$TMP"' EXIT

q()   { "${DOCKER[@]}" exec "$DB" psql -U postgres -d renaser -Atc "$1" 2>&1; }
qi()  { "${DOCKER[@]}" exec -i "$DB" psql -U postgres -d renaser -q 2>&1; }
api() { local m=$1 r=$2; shift 2; curl -s -m 20 -X "$m" -H "X-Auth-Token: $TOKEN" \
        -H 'Content-Type: application/json' "$API$r" "$@"; }
codigo() { local m=$1 r=$2; shift 2; curl -s -o /dev/null -w '%{http_code}' -m 20 -X "$m" \
        -H "X-Auth-Token: $TOKEN" -H 'Content-Type: application/json' "$API$r" "$@"; }
# Lee un campo del habito de prueba en la respuesta de /habit-preferences.
campo() { python3 -c "
import sys,json
d=json.load(sys.stdin)
for h in d.get('habits',[]):
    if h['habitId']=='$1': print(h.get('$2')); break
else: print('(ausente)')"; }

esperado() {
  local que=$1 esp=$2 obt=$3
  if [ "$esp" = "$obt" ]; then
    printf '   \033[32m✓\033[0m %-52s %s\n' "$que" "$obt"; ok=$((ok+1))
  else
    printf '   \033[31m✗\033[0m %-52s esperaba %s, obtuvo %s\n' "$que" "$esp" "$obt"; fallos=$((fallos+1))
  fi
}
flujo() { printf '\n\033[1m%s\033[0m\n' "$1"; }

restaurar() {
  [ -f "$TMP/.listo" ] || return 0
  printf '\n\033[2m-- restaurando la base al estado inicial --\033[0m\n'
  for t in "${TABLAS[@]}"; do
    q "delete from renaser.$t where participante_id = '$UID_P';" >/dev/null
    if [ -s "$TMP/$t.tsv" ]; then
      qi <<SQL >/dev/null
\copy renaser.$t from stdin
$(cat "$TMP/$t.tsv")
\.
SQL
    fi
    printf '   %-32s %s filas\n' "$t" "$(wc -l < "$TMP/$t.tsv")"
  done
  # El historial no se repone: se borra lo que este script agrego, para no gastarle cupo al dueno.
  q "delete from renaser.historial_cambios_horario
     where participante_id = '$UID_P' and creado_en >= '$MARCA';" >/dev/null
}

# ---------------------------------------------------------------- sesion
TOKEN=$(curl -s -D - -o /dev/null -m 20 -X POST "$API/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"$EMAIL\",\"contrasena\":\"$PASS\"}" \
        | grep -i '^x-auth-token:' | tr -d '\r' | cut -d' ' -f2)
[ -n "$TOKEN" ] || { echo "No pude iniciar sesion en $API"; exit 1; }
UID_P=$(q "select id from renaser.usuarios where email = '$EMAIL';")
MARCA=$(q "select now();")
echo "Sesion iniciada · participante ${UID_P:0:8}… · $API"

for t in "${TABLAS[@]}"; do
  q "\copy (select * from renaser.$t where participante_id = '$UID_P') to stdout" > "$TMP/$t.tsv"
done
touch "$TMP/.listo"
echo "Copia de seguridad tomada de ${#TABLAS[@]} tablas."

# Un habito del programa que se pueda apagar, y uno que no, para el flujo del obligatorio.
HAB=$(q "select h.id from renaser.habitos h join renaser.horarios_habito hh on hh.habito_id = h.id
         where h.ambito = 'SISTEMA' and h.desactivable and h.activo and hh.hora_disparo is not null
         order by h.orden limit 1;")
NOMBRE=$(q "select titulo from renaser.habitos where id = '$HAB';")
OBLIG=$(q "select id from renaser.habitos where not desactivable and activo limit 1;")
HOY=$(q "select current_date;")
LUN=$(date -d "next monday" +%Y-%m-%d); MAR=$(date -d "next tuesday" +%Y-%m-%d)
echo "Habito de prueba: $NOMBRE · hoy es $HOY"

# ---------------------------------------------------------------- flujos
flujo "FLUJO 1 · Abro la app y entro a Training"
esperado "el dia de programa lo dice el servidor, no el cliente" "si" \
  "$(api GET /api/v1/home | grep -q '"diaPrograma"' && echo si || echo no)"
esperado "el catalogo de habitos carga" "200" "$(codigo GET /api/v1/habits)"
esperado "los horarios de hoy cargan" "200" "$(codigo GET /api/v1/habit-preferences)"

flujo "FLUJO 2 · «Los lunes me levanto mas temprano»"
esperado "guardar la hora del lunes" "204" \
  "$(codigo PUT "/api/v1/habit-preferences/$HAB/weekdays/MONDAY" -d '{"triggerTime":"05:30:00","limitTime":null}')"
esperado "quedo escrito en la base" "05:30:00" \
  "$(q "select hora_disparo from renaser.horario_semanal_habito
        where participante_id='$UID_P' and habito_id='$HAB' and dia_semana=1;")"
esperado "ese lunes ($LUN) resuelve 05:30" "05:30:00" \
  "$(api GET "/api/v1/habit-preferences?date=$LUN" | campo "$HAB" triggerTime)"
esperado "el martes ($MAR) NO se contagio" "no" \
  "$(test "$(api GET "/api/v1/habit-preferences?date=$MAR" | campo "$HAB" triggerTime)" = "05:30:00" && echo si || echo no)"
esperado "consumio cupo semanal (queda en el historial)" "1" \
  "$(q "select count(*) from renaser.historial_cambios_horario
        where participante_id='$UID_P' and habito_id='$HAB' and creado_en >= '$MARCA';")"

flujo "FLUJO 3 · «Los miercoles no lo hago» — y me arrepiento"
esperado "apagar el miercoles" "204" \
  "$(codigo DELETE "/api/v1/habit-preferences/$HAB/weekdays/WEDNESDAY/active")"
esperado "el miercoles queda apagado" "f" \
  "$(q "select activo from renaser.horario_semanal_habito
        where participante_id='$UID_P' and habito_id='$HAB' and dia_semana=3;")"
esperado "el lunes sigue encendido" "t" \
  "$(q "select activo from renaser.horario_semanal_habito
        where participante_id='$UID_P' and habito_id='$HAB' and dia_semana=1;")"
esperado "apagar un dia NO gasta cupo" "1" \
  "$(q "select count(*) from renaser.historial_cambios_horario
        where participante_id='$UID_P' and habito_id='$HAB' and creado_en >= '$MARCA';")"
esperado "volver a encenderlo" "204" \
  "$(codigo DELETE "/api/v1/habit-preferences/$HAB/weekdays/WEDNESDAY")"
esperado "el miercoles vuelve al horario general" "0" \
  "$(q "select count(*) from renaser.horario_semanal_habito
        where participante_id='$UID_P' and habito_id='$HAB' and dia_semana=3;")"

flujo "FLUJO 4 · Intento sacarme un habito OBLIGATORIO del programa"
esperado "apagarlo un dia de la semana: rechazado" "409" \
  "$(codigo DELETE "/api/v1/habit-preferences/$OBLIG/weekdays/MONDAY/active")"
esperado "apagarlo una fecha puntual: rechazado" "409" \
  "$(codigo PATCH "/api/v1/habit-preferences/$OBLIG/days/$MAR" -d '{"active":false}')"
esperado "moverle la hora si se puede" "204" \
  "$(codigo PUT "/api/v1/habit-preferences/$OBLIG/weekdays/MONDAY" -d '{"triggerTime":"06:45:00","limitTime":null}')"
esperado "no quedo ninguna fila que lo apague" "0" \
  "$(q "select count(*) from renaser.horario_semanal_habito
        where participante_id='$UID_P' and habito_id='$OBLIG' and not activo;")"

flujo "FLUJO 5 · «Este martes puntual no puedo» (no se repite)"
esperado "apagar solo esa fecha" "204" \
  "$(codigo PATCH "/api/v1/habit-preferences/$HAB/days/$MAR" -d '{"active":false}')"
esperado "esa fecha queda apagada" "f" \
  "$(q "select activo from renaser.horarios_habito_por_fecha
        where participante_id='$UID_P' and habito_id='$HAB' and fecha='$MAR';")"
esperado "el martes SIGUIENTE no se toco" "0" \
  "$(q "select count(*) from renaser.horarios_habito_por_fecha
        where participante_id='$UID_P' and habito_id='$HAB' and fecha='$(date -d "$MAR +7 days" +%Y-%m-%d)';")"
esperado "volver a encenderlo" "204" \
  "$(codigo PATCH "/api/v1/habit-preferences/$HAB/days/$MAR" -d '{"active":true}')"

flujo "FLUJO 6 · La pantalla pinta la semana entera"
SEM=$(api GET "/api/v1/habit-preferences/$HAB/weekdays")
esperado "vienen los siete dias" "7" \
  "$(echo "$SEM" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['weekdays']))")"
esperado "el lunes viene marcado como propio" "True" \
  "$(echo "$SEM" | python3 -c "
import sys,json;print(next(d['custom'] for d in json.load(sys.stdin)['weekdays'] if d['weekday']=='MONDAY'))")"
esperado "ningun dia encendido viene sin hora" "0" \
  "$(echo "$SEM" | python3 -c "
import sys,json;print(sum(1 for d in json.load(sys.stdin)['weekdays'] if d['active'] and d['triggerTime'] is None))")"
esperado "la vista semanal coincide con la del lunes por fecha" "si" \
  "$(test "$(echo "$SEM" | python3 -c "
import sys,json;print(next(d['triggerTime'] for d in json.load(sys.stdin)['weekdays'] if d['weekday']=='MONDAY'))")" \
       = "$(api GET "/api/v1/habit-preferences?date=$LUN" | campo "$HAB" triggerTime)" && echo si || echo no)"

flujo "FLUJO 7 · Cambio la hora general y dejo un recordatorio"
esperado "guardar hora + recordatorio 15 min antes" "200" \
  "$(codigo PATCH "/api/v1/habit-preferences/$HAB" -d '{"triggerTime":"07:15:00","reminderEnabled":true,"reminderMinutesBefore":15}')"
PREF=$(api GET /api/v1/habit-preferences)
esperado "el recordatorio vuelve encendido" "True"  "$(echo "$PREF" | campo "$HAB" reminderEnabled)"
esperado "y con sus 15 minutos"          "15"    "$(echo "$PREF" | campo "$HAB" reminderMinutesBefore)"
esperado "el lunes conserva SU hora, no la general" "05:30:00" \
  "$(api GET "/api/v1/habit-preferences?date=$LUN" | campo "$HAB" triggerTime)"

flujo "FLUJO 8 · Datos mal formados no ensucian la base"
esperado "hora limite anterior a la de disparo" "400" \
  "$(codigo PUT "/api/v1/habit-preferences/$HAB/weekdays/FRIDAY" -d '{"triggerTime":"09:00:00","limitTime":"07:00:00"}')"
esperado "dia de la semana en castellano" "400" \
  "$(codigo PUT "/api/v1/habit-preferences/$HAB/weekdays/VIERNES" -d '{"triggerTime":"09:00:00"}')"
esperado "sin hora de disparo" "400" \
  "$(codigo PUT "/api/v1/habit-preferences/$HAB/weekdays/FRIDAY" -d '{"limitTime":null}')"
esperado "habito inexistente" "404" \
  "$(codigo PUT "/api/v1/habit-preferences/00000000-0000-0000-0000-000000000000/weekdays/FRIDAY" -d '{"triggerTime":"09:00:00"}')"
esperado "el viernes sigue sin fila" "0" \
  "$(q "select count(*) from renaser.horario_semanal_habito
        where participante_id='$UID_P' and habito_id='$HAB' and dia_semana=5;")"

flujo "FLUJO 9 · Sin sesion no se lee ni se escribe nada"
esperado "leer la semana sin token" "403" \
  "$(curl -s -o /dev/null -w '%{http_code}' -m 20 "$API/api/v1/habit-preferences/$HAB/weekdays")"
esperado "escribir un dia sin token" "403" \
  "$(curl -s -o /dev/null -w '%{http_code}' -m 20 -X PUT -H 'Content-Type: application/json' \
     -d '{"triggerTime":"05:00:00"}' "$API/api/v1/habit-preferences/$HAB/weekdays/MONDAY")"

printf '\n\033[1mRESULTADO: %s pasaron, %s fallaron\033[0m\n' "$ok" "$fallos"
[ "$fallos" -eq 0 ] || exit 1
