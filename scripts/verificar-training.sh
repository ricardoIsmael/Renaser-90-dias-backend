#!/usr/bin/env bash
# Foto del estado de Training: que se guardo de verdad y que no.
#
# POR QUE EXISTE. Durante el 2026-09-07 aparecieron varias funciones que se veian bien en pantalla
# y no escribian nada: el check de la tarjeta, el "agregar habito", las pastillas de dias. El
# sintoma era siempre el mismo -- la app decia que si y la base no tenia nada -- y descubrirlo
# requeria abrir psql y acordarse de los nombres de tabla. Esto es eso, en un comando.
#
# NO es un test: no afirma que algo este bien. Muestra lo que hay, para poder comparar con lo que
# la pantalla dijo que hizo. Si guardaste 5 dias y aca aparecen 0, la pantalla mintio.
#
# USO
#   ./scripts/verificar-training.sh                  # todo
#   ./scripts/verificar-training.sh DESPERTAR        # solo los habitos cuyo titulo contenga eso
#
# Requiere el contenedor `renaser-db` levantado (docker-compose.yml de este repo).

set -uo pipefail

FILTRO="${1:-}"
DB_CONTENEDOR="${DB_CONTENEDOR:-renaser-db}"
DB_USUARIO="${DB_USUARIO:-postgres}"
DB_NOMBRE="${DB_NOMBRE:-renaser}"

q() { docker exec "$DB_CONTENEDOR" psql -U "$DB_USUARIO" -d "$DB_NOMBRE" -Atc "$1" 2>&1; }
titulo() { printf '\n\033[1m== %s\033[0m\n' "$1"; }

if ! docker exec "$DB_CONTENEDOR" true 2>/dev/null; then
  echo "No encuentro el contenedor '$DB_CONTENEDOR'. Levantalo con: docker compose up -d"
  exit 1
fi

# `LIKE '%%'` cuando no hay filtro: una sola consulta sirve para los dos casos.
COMO="%${FILTRO}%"

titulo "MIGRACIONES"
q "select 'ultima aplicada: V'||max(version::int) from public.flyway_schema_history where success;"
q "select case when count(*) = 0 then 'sin fallidas' else count(*)||' FALLIDAS' end
   from public.flyway_schema_history where not success;"

titulo "BACKEND"
if ss -ltn 2>/dev/null | grep -q ':8080'; then echo "arriba en :8080"; else echo "CAIDO"; fi

titulo "HORA GENERAL  (preferencias_horario — una para toda la semana)"
q "select h.titulo||'  ->  '||p.hora_disparo||coalesce('  hasta '||p.hora_limite,'')
   from renaser.preferencias_horario p join renaser.habitos h on h.id = p.habito_id
   where h.titulo ilike '$COMO' order by h.titulo;" | sed 's/^/  /'

titulo "HORA POR DIA DE LA SEMANA  (V39/V40 — se repite todas las semanas)"
q "select h.titulo||'  '||
     case s.dia_semana when 1 then 'LUN' when 2 then 'MAR' when 3 then 'MIE' when 4 then 'JUE'
                       when 5 then 'VIE' when 6 then 'SAB' else 'DOM' end||
     case when s.activo then '  ->  '||s.hora_disparo else '  ->  APAGADO' end
   from renaser.horario_semanal_habito s join renaser.habitos h on h.id = s.habito_id
   where h.titulo ilike '$COMO' order by h.titulo, s.dia_semana;" | sed 's/^/  /'

titulo "EXCEPCIONES POR FECHA  (V37/V38 — un dia puntual, no se repite)"
q "select h.titulo||'  '||f.fecha||
     case when f.activo then '  ->  '||coalesce(f.hora_disparo::text,'(sin hora)') else '  ->  APAGADO' end
   from renaser.horarios_habito_por_fecha f join renaser.habitos h on h.id = f.habito_id
   where h.titulo ilike '$COMO' order by f.fecha;" | sed 's/^/  /'

titulo "PAUSAS  (desbloqueos_habito)"
q "select h.titulo||'  pausado desde '||d.pausado_en::date||coalesce('  hasta '||d.pausado_hasta,' (indefinida)')
   from renaser.desbloqueos_habito d join renaser.habitos h on h.id = d.habito_id
   where d.pausado_en is not null and h.titulo ilike '$COMO' order by h.titulo;" | sed 's/^/  /'

titulo "LO QUE SE HIZO  (registros del dia y evidencias)"
q "select 'registros hoy: '||count(*)||'  (completados: '||count(*) filter (where estado='COMPLETADO')||')'
   from renaser.registros_habito where fecha_ejecucion = current_date;"
q "select 'evidencias en total: '||count(*) from renaser.evidencias;"

titulo "HABITOS PROPIOS  (los que creaste desde la app)"
q "select titulo||coalesce('  icono: '||icono_clave,'  sin icono')
   from renaser.habitos where ambito = 'PERSONAL' and titulo ilike '$COMO' order by creado_en desc;" | sed 's/^/  /'

printf '\n\033[2mLos recordatorios NO figuran acá: son alarmas del telefono, no filas de la base.\033[0m\n'
