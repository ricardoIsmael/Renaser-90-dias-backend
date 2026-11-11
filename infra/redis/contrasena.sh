#!/usr/bin/env bash
# =====================================================================================
# Redis con contrasena en produccion (spec 050 tramo A1: FR-010 y FR-011; decisiones D7 y D12).
# Lo corre el DUENO, desde su maquina, con el perfil `prod`. Nunca un agente ni el CD.
#
# POR QUE EN DOS PASOS. El backend lee su configuracion de Parameter Store al ARRANCAR
# (spring.config.import: aws-parameterstore:/renaser/prod/), asi que la contrasena tiene que estar
# en el backend ANTES de que Redis la exija. El backend ya sabe usarla (spring.data.redis.password y
# username existen en application.yaml: cero cambios de codigo) y conectar con `AUTH default <clave>`
# a un Redis que todavia no la exige funciona (lo prueba RedisConContrasenaIT).
#
#   0. Variables (las dos son obligatorias; el script se niega a seguir sin ellas):
#        export RENASER_CUENTA_AWS=<id de 12 digitos de la cuenta de produccion>
#        export RENASER_INSTANCIA=<id de la instancia EC2 del backend, i-...>
#   1. Simulacro (es lo que hace sin argumentos):
#        infra/redis/contrasena.sh --paso parametros
#   2. Guardar la clave (genera 32 bytes aleatorios en esta maquina; nunca se imprime):
#        infra/redis/contrasena.sh --paso parametros --aplicar
#   3. DESPLEGAR EL BACKEND (el CD de siempre): arranca con REDIS_USERNAME y REDIS_PASSWORD.
#   4. Mirar el estado (solo lectura; corre por SSM tambien en simulacro):
#        infra/redis/contrasena.sh --paso estado
#   5. Simulacro y luego exigir la clave (hora tranquila: Redis se reinicia unos segundos):
#        infra/redis/contrasena.sh --paso exigir
#        infra/redis/contrasena.sh --paso exigir --aplicar
#
# VUELTA ATRAS (a la version sin clave; el backend NO necesita redespliegue porque `AUTH default
# <clave>` contra un Redis sin clave sigue siendo valido):
#        infra/redis/contrasena.sh --revertir            # simulacro
#        infra/redis/contrasena.sh --revertir --aplicar
#   Si el paso `exigir` falla a la mitad, el mismo script vuelve atras solo. Los parametros de
#   Parameter Store se pueden dejar donde estan; borrarlos (solo con Redis ya sin clave Y el backend
#   ya redesplegado) es:  aws ssm delete-parameters --profile prod --names <prefijo>REDIS_PASSWORD
#   <prefijo>REDIS_USERNAME
#
# Reglas de D12 / FR-011 que cumple: simulacro por omision (solo `--aplicar` cambia algo), perfil
# `prod` explicito y cuenta verificada, idempotente (volver a correr un paso ya hecho no cambia nada),
# guarda lo anterior para volver atras, nada de secretos en pantalla, en el repositorio ni en el log
# del CD, y `set -euo pipefail`. La clave viaja SOLO por Parameter Store: la instancia la lee con su
# rol (igual que desplegar-backend.sh) y redis-cli la recibe por REDISCLI_AUTH, nunca por argumento.
# =====================================================================================
set -euo pipefail

PERFIL=prod
REGION=${REGION:-${RENASER_REGION:-us-east-1}}
PREFIJO=${PREFIJO:-${RENASER_PREFIJO_PARAMETROS:-/renaser/prod/}}
# Lo que cambia en el ensayo local (infra/redis/ensayo-local.sh); en produccion valen los de siempre.
CONTENEDOR=${CONTENEDOR:-redis}
BACKEND=${BACKEND:-backend}
RED=${RED:-renaser}
DIR_REDIS=${DIR_REDIS:-/opt/renaser/redis}
SALUD_URL=${SALUD_URL:-http://127.0.0.1:8080/actuator/health}

APLICAR=${APLICAR:-no}
PASO=""
REVERTIR=no
SIMULAR=no

log() { echo "== $* =="; }
falla() { echo "ERROR: $*" >&2; exit 1; }

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE DENTRO DE LA INSTANCIA (EN_LA_INSTANCIA=estado|exigir|revertir). Lo manda el script
# local por SSM; nada de esto toca AWS con credenciales del dueno: usa el rol de la instancia.
# ------------------------------------------------------------------------------------------------

# Un parametro de Parameter Store, descifrado. Vacio si no existe. Nunca se imprime.
parametro() {
  aws ssm get-parameter --region "$REGION" --with-decryption --name "${PREFIJO}$1" \
    --query Parameter.Value --output text 2>/dev/null || true
}

fecha_del_parametro() {
  local v
  v=$(aws ssm get-parameter --region "$REGION" --name "${PREFIJO}$1" \
        --query Parameter.LastModifiedDate --output text 2>/dev/null || true)
  case "$v" in
    ''|None) echo 0 ;;
    *[!0-9.]*) date -d "$v" +%s 2>/dev/null || echo 0 ;;   # CLI v2: ISO 8601
    *) echo "${v%.*}" ;;                                   # CLI v1: segundos con decimales
  esac
}

corriendo() { [ -n "$(docker ps -q --filter "name=^/$1$" --filter status=running)" ]; }
existe() { docker inspect "$1" >/dev/null 2>&1; }
inicio_de() { date -d "$(docker inspect -f '{{.State.StartedAt}}' "$1")" +%s 2>/dev/null || echo 0; }

# redis-cli dentro del contenedor, SIN clave y CON clave. La clave entra por REDISCLI_AUTH (pasada por
# nombre: no aparece en la linea de comandos de `docker exec`) y las ordenes que la llevan, por stdin.
rc_sin_clave() { ( unset REDISCLI_AUTH; docker exec -i "$1" redis-cli "${@:2}" ); }
# shellcheck disable=SC2030,SC2031  # el export vive en un subshell a proposito: la clave no se queda en el entorno
rc_con_clave() { ( export REDISCLI_AUTH="$CLAVE"; docker exec -i -e REDISCLI_AUTH "$1" redis-cli "${@:2}" ); }

salud_arriba() {
  case "$(curl -fsS --max-time 4 "$SALUD_URL" 2>/dev/null || true)" in
    *'"status":"UP"'*) return 0 ;;
  esac
  return 1
}

esperar() {  # esperar <segundos> <descripcion> <comando...>
  local tope=$1 que=$2 desde=$SECONDS
  shift 2
  until "$@" >/dev/null 2>&1; do
    [ $(( SECONDS - desde )) -ge "$tope" ] && { echo "Pasaron ${tope}s sin lograr: $que"; return 1; }
    sleep 2
  done
}

sesiones() {  # cuantas claves de sesion hay (SCAN, no KEYS: no bloquea a Redis)
  local patron="$NAMESPACE:*"
  if [ "${1:-con}" = con ]; then rc_con_clave "$CONTENEDOR" --scan --pattern "$patron"
  else rc_sin_clave "$CONTENEDOR" --scan --pattern "$patron"; fi | wc -l | tr -d ' '
}

# Corre redis-cli en OTRO contenedor de la red, como lo haria cualquier intruso de la red `renaser`.
desde_otro_contenedor() {  # <imagen> <con|sin> <orden...>
  local imagen=$1 modo=$2; shift 2
  if [ "$modo" = con ]; then
    # shellcheck disable=SC2031  # a proposito: el export vive solo en este subshell
    ( export REDISCLI_AUTH="$CLAVE"
      docker run --rm --network "$RED" -e REDISCLI_AUTH "$imagen" redis-cli -h "$CONTENEDOR" "$@" )
  else
    docker run --rm --network "$RED" "$imagen" redis-cli -h "$CONTENEDOR" "$@"
  fi
}

aof_terminado() {  # sin tuberia con grep -q: un SIGPIPE de docker exec rompia el pipefail
  local info
  info=$(rc_con_clave "$1" INFO persistence) || return 1
  case "$info" in
    *aof_rewrite_in_progress:0*aof_last_bgrewrite_status:ok*|*aof_last_bgrewrite_status:ok*aof_rewrite_in_progress:0*) return 0 ;;
  esac
  return 1
}

# Si $CONTENEDOR ya corre con el redis.conf del repositorio (montado en /usr/local/etc/redis).
con_la_conf_del_repositorio() {
  local montajes
  montajes=$(docker inspect -f '{{range .Mounts}}{{.Destination}} {{end}}' "$CONTENEDOR")
  case "$montajes" in *redis.conf*) return 0 ;; esac
  return 1
}

limpiar_datos_copiados() {  # lo copiado de un intento anterior no debe mezclarse con lo de ahora
  docker run --rm --user 0 -v "$DIR_REDIS:/d" "$IMAGEN" sh -c 'rm -rf /d/data/* /d/data/.[!.]*' 2>/dev/null || true
}

chown_de_redis() {  # el redis del contenedor corre como 999:1000 y no lee lo que es de root con permiso 600
  docker run --rm --user 0 -v "$DIR_REDIS:/d" "$IMAGEN" sh -c 'chown -R 999:1000 /d/data /d/conf && chmod 600 /d/conf/redis.conf'
}

leer_estado_inicial() {
  existe "$CONTENEDOR" || falla "no existe el contenedor $CONTENEDOR."
  IMAGEN=$(docker inspect -f '{{.Config.Image}}' "$CONTENEDOR")
  NAMESPACE=$(parametro REDIS_SESSION_NAMESPACE)
  NAMESPACE=${NAMESPACE:-renaser:session:v2}
  CLAVE=$(parametro REDIS_PASSWORD)
  USUARIO=$(parametro REDIS_USERNAME)
}

validar_clave() {
  [[ "$CLAVE" =~ ^[A-Za-z0-9]{32,128}$ ]] || falla "REDIS_PASSWORD no existe en ${PREFIJO} o no tiene el formato esperado (32 a 128 caracteres alfanumericos). Corre antes: contrasena.sh --paso parametros --aplicar."
}

instancia_estado() {
  leer_estado_inicial
  log "Contenedor $CONTENEDOR"
  docker inspect -f 'imagen={{.Config.Image}} corriendo={{.State.Running}} desde={{.State.StartedAt}} reinicio={{.HostConfig.RestartPolicy.Name}} memoria={{.HostConfig.Memory}}' "$CONTENEDOR"
  docker inspect -f '{{range .Mounts}}montaje: {{.Type}} {{.Source}} -> {{.Destination}}{{"\n"}}{{end}}' "$CONTENEDOR"
  echo "puertos publicados: [$(docker port "$CONTENEDOR" 2>/dev/null | tr '\n' ' ')] (debe estar vacio)"
  local respuesta
  respuesta=$(rc_sin_clave "$CONTENEDOR" PING 2>&1 || true)
  echo "PING sin clave: ${respuesta:0:60}"
  log "Parametros en $PREFIJO"
  echo "REDIS_PASSWORD: $([ -n "$CLAVE" ] && echo "presente, ${#CLAVE} caracteres (modificado $(date -d @"$(fecha_del_parametro REDIS_PASSWORD)" '+%F %T'))" || echo AUSENTE)"
  echo "REDIS_USERNAME: ${USUARIO:-AUSENTE}"
  log "Backend $BACKEND"
  if corriendo "$BACKEND"; then
    echo "arranco: $(date -d @"$(inicio_de "$BACKEND")" '+%F %T')"
    if [ -n "$CLAVE" ] && [ "$(inicio_de "$BACKEND")" -gt "$(fecha_del_parametro REDIS_PASSWORD)" ]; then
      echo "arranco DESPUES de guardar la clave: ya la lleva."
    else
      echo "NO arranco despues de guardar la clave: falta desplegar el backend antes de exigirla."
    fi
  else
    echo "NO esta corriendo."
  fi
  echo "salud: $(salud_arriba && echo UP || echo 'NO responde UP')"
  if [ -n "$CLAVE" ]; then
    if [ "$(rc_sin_clave "$CONTENEDOR" PING 2>&1 || true)" = PONG ]; then
      echo "sesiones en Redis: $(sesiones sin)"
    else
      echo "sesiones en Redis: $(sesiones con)"
    fi
  fi
}

# Lo que se guarda para volver atras: el contenedor viejo NO se borra, se renombra y queda parado.
PREVIO="${CONTENEDOR}-previo"

guardar_respaldo() {
  mkdir -p "$DIR_REDIS/respaldo"
  chmod 700 "$DIR_REDIS" "$DIR_REDIS/respaldo"
  if [ ! -f "$DIR_REDIS/respaldo/previo.txt" ]; then
    ( umask 077
      docker inspect "$CONTENEDOR" > "$DIR_REDIS/respaldo/previo.json"
      docker inspect -f 'imagen={{.Config.Image}} reinicio={{.HostConfig.RestartPolicy.Name}} memoria={{.HostConfig.Memory}}' "$CONTENEDOR" > "$DIR_REDIS/respaldo/previo.txt" )
    echo "Respaldo de lo anterior en $DIR_REDIS/respaldo/ (el contenedor viejo queda parado como $PREVIO)."
  fi
}

escribir_conf() {
  [ -n "${PLANTILLA_B64:-}" ] || falla "falta la plantilla del redis.conf (la manda el script local)."
  mkdir -p "$DIR_REDIS/conf" "$DIR_REDIS/data"
  ( umask 077
    printf '%s' "$PLANTILLA_B64" | base64 -d | sed "s/__CLAVE__/$CLAVE/" > "$DIR_REDIS/conf/redis.conf" )
  chmod 600 "$DIR_REDIS/conf/redis.conf"
}

arrancar_redis() {  # <nombre> [argumentos extra de redis-server]; sin puertos publicados, nunca
  local nombre=$1; shift
  local memoria extra=()
  memoria=$(docker inspect -f '{{.HostConfig.Memory}}' "$PREVIO" 2>/dev/null || echo 0)
  [ "${memoria:-0}" -gt 0 ] && extra=(--memory "$memoria")
  docker run -d --name "$nombre" --network "$RED" --restart unless-stopped "${extra[@]}" \
    --log-opt max-size=10m --log-opt max-file=2 \
    -v "$DIR_REDIS/data:/data" \
    -v "$DIR_REDIS/conf/redis.conf:/usr/local/etc/redis/redis.conf:ro" \
    "$IMAGEN" redis-server /usr/local/etc/redis/redis.conf "$@" >/dev/null
}

# Un RDB solo se convierte en AOF encendiendo el AOF con Redis en marcha: con `appendonly yes` y sin
# carpeta de AOF, Redis arranca VACIO e ignora dump.rdb (comprobado en el ensayo local). Por eso, si
# lo que se copio es solo un RDB, se levanta un contenedor TEMPORAL con `--appendonly no`, se enciende
# el AOF y se espera a que termine de escribirse; el definitivo ya arranca con el AOF.
convertir_rdb_a_aof() {
  local temporal="${CONTENEDOR}-conversion"
  docker rm -f "$temporal" >/dev/null 2>&1 || true
  docker run -d --name "$temporal" --network none --memory 256m \
    -v "$DIR_REDIS/data:/data" \
    -v "$DIR_REDIS/conf/redis.conf:/usr/local/etc/redis/redis.conf:ro" \
    "$IMAGEN" redis-server /usr/local/etc/redis/redis.conf --appendonly no >/dev/null || return 1
  esperar 30 "el contenedor temporal responde" rc_con_clave "$temporal" PING || return 1
  rc_con_clave "$temporal" CONFIG SET appendonly yes >/dev/null || return 1
  esperar 60 "el AOF termina de escribirse" aof_terminado "$temporal" || return 1
  docker stop -t 30 "$temporal" >/dev/null || return 1
  docker rm "$temporal" >/dev/null
}

# Despues de cambiar: lo que tiene que ser verdad. Devuelve 1 y dice por que si algo no lo es.
verificar_con_clave() {
  local mal=0 respuesta despues eventos
  respuesta=$(desde_otro_contenedor "$IMAGEN" sin PING 2>&1 || true)
  case "$respuesta" in
    *NOAUTH*) echo "OK   PING sin clave desde otro contenedor: NOAUTH." ;;
    *) echo "MAL  PING sin clave desde otro contenedor devolvio: ${respuesta:0:80}"; mal=1 ;;
  esac
  respuesta=$(desde_otro_contenedor "$IMAGEN" con PING 2>&1 || true)
  if [ "$respuesta" = PONG ]; then echo "OK   PING con clave: PONG."
  else echo "MAL  PING con clave devolvio: ${respuesta:0:80}"; mal=1; fi
  eventos=$(rc_con_clave "$CONTENEDOR" CONFIG GET notify-keyspace-events | tail -n 1 | tr -d '\r')
  if [[ "$eventos" == *E* && "$eventos" == *g* && "$eventos" == *x* ]]; then
    echo "OK   notify-keyspace-events incluye E, g y x (Spring Session)."
  else echo "MAL  notify-keyspace-events vale '$eventos', no tiene Egx: las sesiones vencidas no se limpiarian."; mal=1; fi
  if [ -n "$(docker port "$CONTENEDOR" 2>/dev/null)" ]; then echo "MAL  Redis publica un puerto en el host."; mal=1
  else echo "OK   Redis no publica ningun puerto."; fi
  despues=$(sesiones con)
  echo "sesiones en Redis: antes ${SESIONES_ANTES:-?}, despues $despues."
  if [ -n "${SESIONES_ANTES:-}" ] && [ $(( SESIONES_ANTES - despues )) -gt 5 ] && [ $(( despues * 10 )) -lt $(( SESIONES_ANTES * 9 )) ]; then
    echo "MAL  se perdieron sesiones."; mal=1
  else echo "OK   las sesiones siguen ahi."; fi
  if esperar 90 "el backend responde UP" salud_arriba; then echo "OK   el backend responde UP ($SALUD_URL)."
  else echo "MAL  el backend no responde UP tras el cambio."; mal=1; fi
  return $mal
}

# $PREVIO (el Redis de antes, parado) vuelve a ser $CONTENEDOR. Con "si", antes se le pasan los datos del
# Redis que esta corriendo ahora (las sesiones creadas desde el cambio solo existen ahi). El Redis que se
# aparta no se borra: queda parado como ${CONTENEDOR}-con-clave. Al arrancar, el viejo ya no tiene la
# clave que se le puso en caliente (era solo de memoria): vuelve sin ella.
volver_al_previo() {
  local con_datos=${1:-no}
  if [ "$con_datos" = si ]; then rc_con_clave "$CONTENEDOR" SAVE >/dev/null 2>&1 || true; fi
  docker stop -t 30 "$CONTENEDOR" >/dev/null 2>&1 || true
  if [ "$con_datos" = si ]; then
    if docker cp "$CONTENEDOR:/data/dump.rdb" "$DIR_REDIS/dump-de-vuelta.rdb" 2>/dev/null \
       && docker cp "$DIR_REDIS/dump-de-vuelta.rdb" "$PREVIO:/data/dump.rdb"; then
      echo "Las sesiones de ahora pasaron al Redis viejo."
    else
      echo "AVISO: no se pudo pasar el dump al Redis viejo: vuelve con sus datos de antes del cambio."
    fi
    rm -f "$DIR_REDIS/dump-de-vuelta.rdb"
  fi
  docker rm -f "${CONTENEDOR}-con-clave" >/dev/null 2>&1 || true
  if existe "$CONTENEDOR"; then
    docker rename "$CONTENEDOR" "${CONTENEDOR}-con-clave"
    docker update --restart no "${CONTENEDOR}-con-clave" >/dev/null
  fi
  docker rename "$PREVIO" "$CONTENEDOR"
  docker update --restart unless-stopped "$CONTENEDOR" >/dev/null
  docker start "$CONTENEDOR" >/dev/null
  esperar 30 "Redis responde" rc_sin_clave "$CONTENEDOR" PING
}

instancia_revertir() {
  leer_estado_inicial
  if existe "$PREVIO"; then
    echo "Vuelta atras: $PREVIO vuelve a ser $CONTENEDOR, con las sesiones de ahora."
    if [ "$APLICAR" != si ]; then
      echo "Simulacro: no se cambio nada. Con --aplicar se pasa el dump de ahora al Redis viejo y este arranca sin clave; el Redis con clave queda parado como ${CONTENEDOR}-con-clave."
      return 0
    fi
    if [ "$(rc_sin_clave "$CONTENEDOR" PING 2>&1 || true)" != PONG ]; then validar_clave; fi
    volver_al_previo si || falla "el Redis viejo no arranco; ${CONTENEDOR}-con-clave sigue ahi, parado."
    esperar 90 "el backend responde UP" salud_arriba || echo "AVISO: el backend no responde UP todavia; miralo con --paso estado."
    echo "Listo: Redis vuelve a estar sin clave. El Redis con clave queda parado como ${CONTENEDOR}-con-clave."
  else
    echo "No existe $PREVIO: no hay un Redis viejo al que volver."
    [ "$APLICAR" = si ] || { echo "Simulacro: solo se quitaria la clave en caliente (CONFIG SET requirepass '')."; return 0; }
    validar_clave
    printf 'CONFIG SET requirepass ""\n' | docker exec -i -e REDISCLI_AUTH "$CONTENEDOR" redis-cli >/dev/null || true
    echo "AVISO: la clave se quito en caliente. Si este contenedor monta el redis.conf con requirepass, volveria a exigirla al reiniciarse: recrealo sin ese montaje."
  fi
}

# Paso 3. Cada orden que importa termina en `|| return 1`: dentro de un `if !` el `set -e` no corta, y
# un fallo a medias tiene que llevar a la vuelta atras, no a seguir.
persistir_con_conf() {
  escribir_conf || return 1
  rc_con_clave "$CONTENEDOR" SAVE >/dev/null || return 1
  docker stop -t 30 "$CONTENEDOR" >/dev/null || return 1
  docker rename "$CONTENEDOR" "$PREVIO" || return 1
  docker update --restart no "$PREVIO" >/dev/null || return 1
  limpiar_datos_copiados
  docker cp "$PREVIO:/data/." "$DIR_REDIS/data/" || return 1
  chown_de_redis || return 1
  if [ ! -d "$DIR_REDIS/data/appendonlydir" ] && [ -f "$DIR_REDIS/data/dump.rdb" ]; then convertir_rdb_a_aof || return 1; fi
  arrancar_redis "$CONTENEDOR" || return 1
  esperar 60 "Redis responde con clave" rc_con_clave "$CONTENEDOR" PING
}

deshacer_persistir() {
  docker rm -f "${CONTENEDOR}-conversion" >/dev/null 2>&1 || true
  if existe "$PREVIO"; then
    docker logs --tail 15 "$CONTENEDOR" 2>&1 | sed "s/$CLAVE/***/g" || true
    docker rm -f "$CONTENEDOR" >/dev/null 2>&1 || true
    volver_al_previo no || echo "AVISO: el Redis viejo no respondio; mira $PREVIO y $CONTENEDOR con docker ps -a."
  elif existe "$CONTENEDOR" && ! corriendo "$CONTENEDOR"; then
    docker start "$CONTENEDOR" >/dev/null || true
    echo "El Redis de antes volvio a arrancar (sin clave: la del paso 2 era solo de memoria)."
  else
    echo "El paso 3 fallo ANTES de tocar el contenedor: Redis sigue andando con la clave solo en memoria (paso 2)."
    echo "Arregla la causa y repite --paso exigir --aplicar, o quita la clave con --revertir --aplicar."
  fi
}

instancia_exigir() {
  leer_estado_inicial
  log "Comprobaciones previas"
  local mal=0 ya_exige=no aplicado=no
  validar_clave
  if con_la_conf_del_repositorio; then aplicado=si; fi
  if [ "$(rc_sin_clave "$CONTENEDOR" PING 2>&1 || true)" != PONG ]; then ya_exige=si; fi
  [ "$USUARIO" = default ] || { echo "MAL  REDIS_USERNAME debe valer 'default' (vale '${USUARIO:-vacio}')."; mal=1; }
  [ -z "$(docker port "$CONTENEDOR" 2>/dev/null)" ] || { echo "MAL  $CONTENEDOR publica un puerto en el host."; mal=1; }
  if [ "$aplicado" = no ] && existe "$PREVIO"; then
    echo "MAL  ya existe $PREVIO, resto de un intento anterior: revisalo con --paso estado y, si ya no sirve, docker rm $PREVIO."; mal=1
  fi
  if corriendo "$BACKEND"; then
    if [ "$(inicio_de "$BACKEND")" -gt "$(fecha_del_parametro REDIS_PASSWORD)" ]; then echo "OK   $BACKEND arranco despues de guardar la clave."
    else echo "MAL  $BACKEND arranco ANTES de guardar la clave: despliegalo primero (paso 3)."; mal=1; fi
  else echo "MAL  $BACKEND no esta corriendo."; mal=1; fi
  if salud_arriba; then echo "OK   el backend responde UP."; else echo "MAL  el backend no responde UP."; mal=1; fi
  [ "$mal" -eq 0 ] || falla "las comprobaciones previas no pasan; no se cambio nada."

  if [ "$aplicado" = si ]; then
    [ "$ya_exige" = si ] || falla "$CONTENEDOR monta el redis.conf pero no exige clave: algo esta a medias, mira $DIR_REDIS/conf/redis.conf."
    echo "Redis ya exige contrasena y corre con el redis.conf del repositorio: solo se verifica."
    SESIONES_ANTES=""
    verificar_con_clave || falla "la verificacion no pasa."
    return 0
  fi
  if [ "$APLICAR" != si ]; then
    cat <<PLAN

SIMULACRO. Con --aplicar se haria, en este orden (nada de esto se hizo):
  1. Contar las sesiones ($NAMESPACE:*) y guardar lo anterior en $DIR_REDIS/respaldo/.
  2. En caliente: CONFIG SET requirepass <la clave de Parameter Store>. Las conexiones abiertas siguen
     valiendo; las nuevas piden la clave, que el backend ya tiene. Se comprueba NOAUTH sin clave y PONG con ella.
  3. Persistirlo (si no, un reinicio de la instancia levantaria Redis sin clave): SAVE, parar $CONTENEDOR,
     copiar /data a $DIR_REDIS/data, dejar el contenedor viejo parado como $PREVIO (vuelta atras inmediata)
     y crear $CONTENEDOR con $DIR_REDIS/conf/redis.conf (requirepass, appendonly yes; permiso 600). Es un
     corte de Redis de unos segundos: el backend reconecta solo.
  4. Verificar: PING sin clave desde OTRO contenedor = NOAUTH, con clave = PONG, sin puertos publicados,
     las sesiones siguen y el backend responde UP. Si algo falla, vuelve atras solo.
Vuelta atras: contrasena.sh --revertir --aplicar
PLAN
    return 0
  fi

  if [ "$ya_exige" = si ]; then SESIONES_ANTES=$(sesiones con); else SESIONES_ANTES=$(sesiones sin); fi
  log "Paso 1 de 4: respaldo ($SESIONES_ANTES sesiones)"
  guardar_respaldo
  if [ "$ya_exige" = no ]; then
    log "Paso 2 de 4: exigir la clave en caliente"
    printf 'CONFIG SET requirepass %s\n' "$CLAVE" | docker exec -i "$CONTENEDOR" redis-cli >/dev/null
    local respuesta
    respuesta=$(rc_sin_clave "$CONTENEDOR" PING 2>&1 || true)
    case "$respuesta" in *NOAUTH*) ;; *) falla "tras CONFIG SET requirepass, PING sin clave devolvio '${respuesta:0:60}'." ;; esac
    [ "$(rc_con_clave "$CONTENEDOR" PING)" = PONG ] || falla "con la clave, Redis no responde PONG."
    echo "Redis exige clave en caliente. Esperando 10 s para ver si el backend se queja."
    sleep 10
    if ! salud_arriba; then
      printf 'CONFIG SET requirepass ""\n' | docker exec -i -e REDISCLI_AUTH "$CONTENEDOR" redis-cli >/dev/null || true
      falla "el backend dejo de responder UP al exigir la clave: se quito en caliente. Revisa que arranco con REDIS_PASSWORD."
    fi
  fi
  log "Paso 3 de 4: persistirlo (corte de Redis de unos segundos)"
  if ! persistir_con_conf; then
    echo "No se pudo dejar Redis con su redis.conf: vuelve el Redis de antes."
    deshacer_persistir
    falla "el paso 3 fallo; lo de arriba dice en que estado quedo Redis."
  fi
  log "Paso 4 de 4: verificar"
  if ! verificar_con_clave; then
    echo "La verificacion fallo: se vuelve atras."
    volver_al_previo si || true
    falla "se volvio al Redis sin clave; ${CONTENEDOR}-con-clave queda parado para mirar que paso."
  fi
  cat <<FIN

Listo: Redis exige contrasena y las sesiones siguen. El Redis anterior queda PARADO como $PREVIO
(vuelta atras inmediata). Pasada una semana sin problemas: docker rm $PREVIO
Vuelta atras ahora:  infra/redis/contrasena.sh --revertir --aplicar
FIN
}

if [ -n "${EN_LA_INSTANCIA:-}" ]; then
  case "$EN_LA_INSTANCIA" in
    estado) instancia_estado ;;
    exigir) instancia_exigir ;;
    revertir) instancia_revertir ;;
    *) falla "accion desconocida en la instancia: $EN_LA_INSTANCIA" ;;
  esac
  exit 0
fi

# ------------------------------------------------------------------------------------------------
# LO QUE CORRE EN LA MAQUINA DEL DUENO
# ------------------------------------------------------------------------------------------------

uso() { sed -n '2,/^set -euo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --simular) SIMULAR=si ;;
    --aplicar) APLICAR=si ;;
    --revertir) REVERTIR=si ;;
    --paso) PASO=${2:?--paso necesita parametros, estado o exigir}; shift ;;
    -h|--help) uso; exit 0 ;;
    *) echo "Opcion desconocida: $1" >&2; uso >&2; exit 2 ;;
  esac
  shift
done
[ "$SIMULAR" = si ] && APLICAR=no
if [ "$REVERTIR" = no ] && [ -z "$PASO" ]; then echo "Falta --paso (parametros, estado o exigir) o --revertir." >&2; exit 2; fi
case "$PASO" in ''|parametros|estado|exigir) ;; *) echo "--paso debe ser parametros, estado o exigir." >&2; exit 2 ;; esac
[[ "$PREFIJO" =~ ^/[A-Za-z0-9/_.-]*/$ ]] || { echo "El prefijo de parametros debe empezar y terminar con /." >&2; exit 2; }

for h in aws jq openssl base64; do command -v "$h" >/dev/null || falla "falta $h en esta maquina."; done
: "${RENASER_CUENTA_AWS:?exporta RENASER_CUENTA_AWS con el id de 12 digitos de la cuenta de produccion}"
: "${RENASER_INSTANCIA:?exporta RENASER_INSTANCIA con el id de la instancia EC2 del backend}"

CUENTA=$(aws sts get-caller-identity --profile "$PERFIL" --query Account --output text)
if [ "$CUENTA" != "$RENASER_CUENTA_AWS" ]; then
  falla "el perfil $PERFIL apunta a la cuenta $CUENTA, no a la esperada ($RENASER_CUENTA_AWS). No se cambio nada."
fi
echo "Cuenta verificada ($CUENTA, perfil $PERFIL). Modo: $([ "$APLICAR" = si ] && echo APLICAR || echo SIMULACRO)."

existe_parametro() {  # no pide el valor: solo la version
  local salida
  salida=$(aws ssm get-parameter --profile "$PERFIL" --region "$REGION" --name "${PREFIJO}$1" --query Parameter.Version --output text 2>&1) && return 0
  case "$salida" in *ParameterNotFound*) return 1 ;; *) falla "no pude consultar ${PREFIJO}$1: ${salida:0:200}" ;; esac
}

paso_parametros() {
  log "Parametros en ${PREFIJO}"
  local tmp
  if existe_parametro REDIS_PASSWORD; then
    echo "REDIS_PASSWORD ya existe: no se toca (idempotente)."
  elif [ "$APLICAR" = si ]; then
    tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
    ( umask 077
      openssl rand -hex 32 > "$tmp/clave"
      jq -n --arg n "${PREFIJO}REDIS_PASSWORD" --rawfile v "$tmp/clave" \
        '{Name: $n, Value: ($v | rtrimstr("\n")), Type: "SecureString", Overwrite: false, Description: "Contrasena de Redis (spec 050 A1). Rotarla: put-parameter --overwrite, redesplegar el backend y CONFIG SET requirepass."}' > "$tmp/parametro.json" )
    aws ssm put-parameter --profile "$PERFIL" --region "$REGION" --cli-input-json "file://$tmp/parametro.json" >/dev/null
    echo "REDIS_PASSWORD creada como SecureString (32 bytes aleatorios; no se imprime)."
  else
    echo "Simulacro: se crearia REDIS_PASSWORD (SecureString, 32 bytes aleatorios generados aqui) sin sobrescribir."
  fi
  if existe_parametro REDIS_USERNAME; then
    echo "REDIS_USERNAME ya existe: no se toca."
  elif [ "$APLICAR" = si ]; then
    aws ssm put-parameter --profile "$PERFIL" --region "$REGION" --name "${PREFIJO}REDIS_USERNAME" --type String --value default --description "Usuario de Redis (spec 050 A1)" >/dev/null
    echo "REDIS_USERNAME creado con el valor default."
  else
    echo "Simulacro: se crearia REDIS_USERNAME = default."
  fi
  echo
  echo "Siguiente: despliega el backend (el CD de siempre) para que arranque con la clave y luego corre --paso estado --aplicar."
  echo "Vuelta atras de este paso: aws ssm delete-parameters --profile $PERFIL --region $REGION --names ${PREFIJO}REDIS_PASSWORD ${PREFIJO}REDIS_USERNAME (solo si Redis sigue sin clave)."
}

en_la_instancia() {  # <accion>: manda ESTE script por SSM, con las variables por delante (patron del CD)
  local accion=$1 parametros cid estado desde
  parametros=$(mktemp); trap 'rm -f "$parametros"' EXIT
  jq -n --arg b64 "$(base64 -w0 "$0")" --arg plantilla "$(base64 -w0 "$(dirname "$0")/redis.conf.plantilla")" \
        --arg accion "$accion" --arg aplicar "$APLICAR" --arg prefijo "$PREFIJO" --arg region "$REGION" \
    '{commands: [
       "set -eu",
       "S=$(mktemp /tmp/contrasena-redis.XXXXXX)",
       "trap \"rm -f $S\" EXIT",
       ("echo " + $b64 + " | base64 -d > \"$S\""),
       ("EN_LA_INSTANCIA=" + $accion + " APLICAR=" + $aplicar + " PREFIJO=" + $prefijo + " REGION=" + $region + " PLANTILLA_B64=" + $plantilla + " bash \"$S\"")
     ]}' > "$parametros"
  cid=$(aws ssm send-command --profile "$PERFIL" --region "$REGION" --instance-ids "$RENASER_INSTANCIA" \
          --document-name AWS-RunShellScript --comment "redis contrasena: $accion" \
          --parameters "file://$parametros" --query Command.CommandId --output text)
  echo "Comando SSM $cid enviado; esperando el resultado..."
  desde=$SECONDS
  while true; do
    estado=$(aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
               --instance-id "$RENASER_INSTANCIA" --query Status --output text 2>/dev/null || echo Pending)
    case "$estado" in Success|Failed|Cancelled|TimedOut) break ;; esac
    [ $(( SECONDS - desde )) -ge 900 ] && { estado=SinRespuesta; break; }
    sleep 5
  done
  echo "----- salida de la instancia -----"
  aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
    --instance-id "$RENASER_INSTANCIA" --query StandardOutputContent --output text || true
  if [ "$estado" != Success ]; then
    aws ssm get-command-invocation --profile "$PERFIL" --region "$REGION" --command-id "$cid" \
      --instance-id "$RENASER_INSTANCIA" --query StandardErrorContent --output text || true
    falla "la instancia termino con estado $estado."
  fi
}

if [ "$REVERTIR" = si ]; then
  en_la_instancia revertir
else
  case "$PASO" in
    parametros) paso_parametros ;;
    estado) en_la_instancia estado ;;
    exigir) en_la_instancia exigir ;;
  esac
fi
