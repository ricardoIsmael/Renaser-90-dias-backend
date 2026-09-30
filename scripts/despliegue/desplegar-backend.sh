#!/usr/bin/env bash
# Reemplaza el contenedor `backend` de la instancia EC2 por la imagen IMAGEN. Lo corre el CD
# (`.github/workflows/cd.yml`) DENTRO de la instancia, por SSM; tambien se puede correr a mano
# en la instancia para volver a una version anterior (IMAGEN=<la anterior>).
#
# DOS MODOS (E-465, D-235):
#
#  - SIN CORTE. Delante del backend hay un nginx (`proxy`) publicado en el puerto 8080 del host,
#    que es el que mira CloudFront. El backend nuevo arranca como `backend-nuevo`, SIN puerto
#    publicado, mientras el viejo sigue atendiendo. Cuando el nuevo dice UP, se intercambian los
#    nombres (el nuevo pasa a llamarse `backend`), nginx lo encuentra por el DNS de Docker en
#    <=5 s, y recien despues se apaga el viejo, ordenado (SIGTERM, apagado gradual de Spring).
#
#  - REEMPLAZO (lo de siempre, ~50 s de corte). Solo si no hay memoria para dos JVM a la vez, o si
#    se pide a proposito con MODO=reemplazo (migracion que rompe al codigo viejo, ver abajo).
#
# LA CONDICION DE MEMORIA NO SE NEGOCIA. En la t3.small (1.909 MB) dos JVM no entran: se probo el
# 2026-09-07 y produccion quedo caida SIETE HORAS (E-155), porque lo primero que mato la falta de
# memoria fue el agente de SSM, el unico canal para mandar el arreglo. Por eso el modo sin corte
# se elige MIRANDO la memoria disponible, no por configuracion: en esa maquina este script hace
# exactamente lo mismo que antes, y el dia que la instancia crezca pasa solo al modo sin corte.
#
# Variables de entrada: IMAGEN (obligatoria), REGISTRO y REGION (para ECR; sin REGISTRO no hay
# login ni pull, util para probarlo en local), ESPERA (segundos de tope para el UP), MODO
# (auto | reemplazo). El resto tiene default de produccion y solo se cambia para probarlo.
set -euo pipefail

: "${IMAGEN:?falta IMAGEN}"
REGISTRO=${REGISTRO:-}
REGION=${REGION:-us-east-1}
ESPERA=${ESPERA:-240}
MODO=${MODO:-auto}

CONTENEDOR=${CONTENEDOR:-backend}
NUEVO="${CONTENEDOR}-nuevo"
VIEJO="${CONTENEDOR}-viejo"
PROXY=${PROXY:-proxy}
RED=${RED:-renaser}
PUERTO=${PUERTO:-8080}                  # el de la aplicacion dentro del contenedor
PUERTO_PUBLICO=${PUERTO_PUBLICO:-8080}  # el del host, el que mira CloudFront
PUERTO_ENSAYO=${PUERTO_ENSAYO:-8081}    # solo en el primer paso a proxy, y solo en localhost
DIR_PROXY=${DIR_PROXY:-/opt/renaser/proxy}
IMAGEN_PROXY=${IMAGEN_PROXY:-public.ecr.aws/nginx/nginx:1.28-alpine}

# V-8 (D-180): tope de memoria y porcentaje de heap. Van tambien en la vuelta atras porque las
# imagenes viejas traen horneado MaxRAMPercentage=75.
MEMORIA=${MEMORIA:-1400m}
JVM_OPCIONES=${JVM_OPCIONES:--XX:MaxRAMPercentage=60.0}
# Lo que tiene que quedar disponible, ADEMAS del tope del contenedor nuevo, para levantarlo con
# el viejo andando: sistema, dockerd, redis, nginx y el agente de SSM.
MARGEN_MB=${MARGEN_MB:-512}
# Tras el cambio: nginx guarda la IP resuelta 5 s; se deja terminar lo que el viejo ya tenia.
DRENAJE=${DRENAJE:-20}
# `docker stop -t`: el apagado gradual de Spring espera hasta 30 s lo que este en curso.
APAGADO=${APAGADO:-45}

log() { echo "== $* =="; }

memoria_disponible_mb() { awk '/^MemAvailable:/ {print int($2 / 1024)}' /proc/meminfo; }

# "1400m" -> 1400, "2g" -> 2048.
en_mb() {
  local v=${1,,}
  case "$v" in
    *g) echo $(( ${v%g} * 1024 )) ;;
    *m) echo "${v%m}" ;;
    *)  echo $(( v / 1048576 )) ;;
  esac
}

corriendo() { [ -n "$(docker ps -q --filter "name=^/$1$" --filter status=running)" ]; }
existe() { docker inspect "$1" >/dev/null 2>&1; }

ip_de() {
  docker inspect -f "{{(index .NetworkSettings.Networks \"$RED\").IPAddress}}" "$1" 2>/dev/null || true
}

# Arranca un backend. $1 = nombre, $2 = politica de reinicio, $3 = "publicar" o nada.
# Nada temporal nace con `unless-stopped` (E-155, punto 2): el nuevo arranca con `no` y recien
# se le cambia la politica cuando ya es EL backend.
correr_backend() {
  local puertos=()
  if [ "${3:-}" = publicar ]; then puertos=(-p "$PUERTO_PUBLICO:$PUERTO"); fi
  docker run -d \
    --name "$1" \
    --network "$RED" \
    "${puertos[@]}" \
    --restart "$2" \
    --memory "$MEMORIA" \
    -e JAVA_TOOL_OPTIONS="$JVM_OPCIONES" \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e AWS_REGION="$REGION" \
    "$IMAGEN" >/dev/null
}

# Espera /actuator/health UP del contenedor $1, hablandole a su IP en la red de Docker (asi
# funciona tenga o no puerto publicado). Devuelve 1 si muere o si se pasa del tope.
esperar_sano() {
  local desde cuerpo ip
  desde=$(date +%s)
  while true; do
    ip=$(ip_de "$1")
    cuerpo=""
    if [ -n "$ip" ]; then
      cuerpo=$(curl -fsS --max-time 3 "http://$ip:$PUERTO/actuator/health" 2>/dev/null || true)
    fi
    case "$cuerpo" in
      *'"status":"UP"'*) log "$1 sano tras $(( $(date +%s) - desde ))s: $cuerpo"; return 0 ;;
    esac
    if ! corriendo "$1"; then
      echo "ERROR: el contenedor $1 dejo de correr durante el arranque."
      docker logs --tail 120 "$1" 2>&1 || true
      return 1
    fi
    if [ $(( $(date +%s) - desde )) -ge "$ESPERA" ]; then
      echo "ERROR: $1 no respondio UP en ${ESPERA}s."
      docker logs --tail 120 "$1" 2>&1 || true
      return 1
    fi
    sleep 3
  done
}

# La configuracion de nginx. Es transparente a proposito: NO toca X-Forwarded-For ni ninguna
# otra cabecera de CloudFront (la app toma la IP del cliente del valor mas a la izquierda, E-149;
# agregar la del borde a la derecha romperia el dia que se lea desde la derecha, OUT-1), y
# conserva el Host original. Lo demas: WebSocket (/ws y la voz en vivo), sin buffer para el
# streaming del chat y para las subidas, y el tope de tamano lo sigue poniendo la aplicacion.
escribir_config_proxy() {
  mkdir -p "$DIR_PROXY"
  cat > "$DIR_PROXY/renaser.conf.nuevo" <<'FIN_CONF'
# Generado por scripts/despliegue/desplegar-backend.sh en cada despliegue. No editar a mano.
map $http_upgrade $conexion_hacia_atras {
    default upgrade;
    ''      close;
}

server {
    listen __PUERTO__;

    # DNS de Docker. `valid=5s` es lo que hace que el cambio de contenedor se vea en 5 s; el
    # destino va en una variable justamente para que nginx lo resuelva en cada peticion y no
    # una sola vez al arrancar.
    resolver 127.0.0.11 valid=5s ipv6=off;
    resolver_timeout 3s;

    access_log off;
    client_max_body_size 50m;
    proxy_request_buffering off;
    proxy_buffering off;

    proxy_http_version 1.1;
    proxy_set_header Host $http_host;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $conexion_hacia_atras;

    proxy_connect_timeout 5s;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;

    location / {
        set $destino http://__CONTENEDOR__:__PUERTO__;
        proxy_pass $destino;
    }
}
FIN_CONF
  sed -i "s/__PUERTO__/$PUERTO/g; s/__CONTENEDOR__/$CONTENEDOR/g" "$DIR_PROXY/renaser.conf.nuevo"
  if cmp -s "$DIR_PROXY/renaser.conf.nuevo" "$DIR_PROXY/renaser.conf" 2>/dev/null; then
    rm -f "$DIR_PROXY/renaser.conf.nuevo"
    return 1
  fi
  # Se sobreescribe el MISMO archivo y no se hace `mv`: el proxy lo monta como archivo suelto, y
  # un bind mount de archivo queda atado al inodo; con `mv` nginx seguiria leyendo el viejo.
  cat "$DIR_PROXY/renaser.conf.nuevo" > "$DIR_PROXY/renaser.conf"
  rm -f "$DIR_PROXY/renaser.conf.nuevo"
  return 0
}

# $1 = nombre, $2 = puerto del host, $3 = politica de reinicio. Solo en 127.0.0.1 si es el ensayo.
correr_proxy() {
  local publicar="$2:$PUERTO"
  if [ "$1" != "$PROXY" ]; then publicar="127.0.0.1:$2:$PUERTO"; fi
  docker run -d \
    --name "$1" \
    --network "$RED" \
    -p "$publicar" \
    --restart "$3" \
    --memory 64m \
    --log-opt max-size=10m --log-opt max-file=2 \
    -v "$DIR_PROXY/renaser.conf:/etc/nginx/conf.d/default.conf:ro" \
    "$IMAGEN_PROXY" >/dev/null
}

sano_por() {
  local i
  for i in $(seq 1 10); do
    case "$(curl -fsS --max-time 3 "http://127.0.0.1:$1/actuator/health" 2>/dev/null || true)" in
      *'"status":"UP"'*) return 0 ;;
    esac
    sleep 1
  done
  return 1
}

comando_para_volver() {
  local publicar=""
  if ! corriendo "$PROXY"; then publicar="-p $PUERTO_PUBLICO:$PUERTO "; fi
  echo "docker rm -f $CONTENEDOR; docker run -d --name $CONTENEDOR --network $RED ${publicar}--restart unless-stopped --memory $MEMORIA -e JAVA_TOOL_OPTIONS=$JVM_OPCIONES -e SPRING_PROFILES_ACTIVE=prod -e AWS_REGION=$REGION $ANTERIOR"
}

# Restos de un despliegue anterior que se corto a mitad (p. ej. se cayo el agente de SSM).
limpiar_restos() {
  if existe "$NUEVO"; then
    log "Borrando $NUEVO, resto de un despliegue que no termino"
    docker rm -f "$NUEVO" >/dev/null
  fi
  if existe "$VIEJO"; then
    if existe "$CONTENEDOR"; then
      log "Borrando $VIEJO, resto de un despliegue que no termino"
      docker rm -f "$VIEJO" >/dev/null
    else
      log "Se corto entre los dos renombres: $VIEJO vuelve a llamarse $CONTENEDOR"
      docker rename "$VIEJO" "$CONTENEDOR"
      docker update --restart unless-stopped "$CONTENEDOR" >/dev/null
    fi
  fi
}

elegir_modo() {
  if [ "$MODO" = reemplazo ]; then echo reemplazo; return; fi
  if ! corriendo "$CONTENEDOR"; then echo reemplazo; return; fi
  local hay necesita
  hay=$(memoria_disponible_mb)
  necesita=$(( $(en_mb "$MEMORIA") + MARGEN_MB ))
  if [ "$hay" -lt "$necesita" ]; then
    echo "AVISO: ${hay} MB disponibles y hacen falta ${necesita} para dos JVM a la vez (E-155). Va con corte." >&2
    echo reemplazo
    return
  fi
  echo "Memoria: ${hay} MB disponibles, hacen falta ${necesita}. Va sin corte." >&2
  echo sin-corte
}

desplegar_con_reemplazo() {
  log "Reemplazando el contenedor (hay unos 50 s sin servicio)"
  local publicar=publicar
  if corriendo "$PROXY"; then
    publicar=""
    if escribir_config_proxy; then docker exec "$PROXY" nginx -s reload; fi
  fi
  docker rm -f "$CONTENEDOR" >/dev/null 2>&1 || true
  correr_backend "$CONTENEDOR" unless-stopped "$publicar"
  if ! esperar_sano "$CONTENEDOR"; then
    echo "PARA VOLVER A LA VERSION ANTERIOR: $VOLVER"
    exit 1
  fi
}

# Primera vez: el backend viejo publica 8080 y hay que ponerle el proxy delante. Se ensaya el
# proxy en otro puerto ANTES de tocar nada; el unico corte es el segundo entre borrar el viejo
# (que libera 8080) y levantar el proxy en 8080. Pasa una sola vez.
poner_proxy_delante() {
  local ensayo="${PROXY}-ensayo"
  docker rm -f "$ensayo" >/dev/null 2>&1 || true
  docker pull -q "$IMAGEN_PROXY" >/dev/null
  correr_proxy "$ensayo" "$PUERTO_ENSAYO" no
  if ! sano_por "$PUERTO_ENSAYO"; then
    echo "ERROR: el proxy de ensayo no llego al backend nuevo. No se toco nada de produccion."
    docker logs --tail 40 "$ensayo" 2>&1 || true
    docker rm -f "$ensayo" >/dev/null 2>&1 || true
    return 1
  fi
  docker rm -f "$ensayo" >/dev/null
  log "Proxy ensayado. Cambio de $VIEJO (puerto $PUERTO_PUBLICO directo) al proxy"
  docker rm -f "$VIEJO" >/dev/null
  docker rm -f "$PROXY" >/dev/null 2>&1 || true
  correr_proxy "$PROXY" "$PUERTO_PUBLICO" unless-stopped
  if ! sano_por "$PUERTO_PUBLICO"; then
    echo "ERROR: el proxy no responde en $PUERTO_PUBLICO. Se vuelve a publicar el backend directo."
    docker logs --tail 40 "$PROXY" 2>&1 || true
    docker rm -f "$PROXY" >/dev/null 2>&1 || true
    docker rm -f "$CONTENEDOR" >/dev/null 2>&1 || true
    correr_backend "$CONTENEDOR" unless-stopped publicar
    esperar_sano "$CONTENEDOR" || true
    return 1
  fi
}

desplegar_sin_corte() {
  local config_cambio=no
  if escribir_config_proxy; then config_cambio=si; fi
  log "Levantando $NUEVO al lado de $CONTENEDOR, que sigue atendiendo"
  correr_backend "$NUEVO" no
  if ! esperar_sano "$NUEVO"; then
    docker rm -f "$NUEVO" >/dev/null 2>&1 || true
    echo "El despliegue fallo, pero produccion sigue atendiendo con $ANTERIOR: no hubo corte."
    exit 1
  fi

  log "Cambio: $NUEVO pasa a ser $CONTENEDOR"
  docker rename "$CONTENEDOR" "$VIEJO"
  docker rename "$NUEVO" "$CONTENEDOR"
  docker update --restart unless-stopped "$CONTENEDOR" >/dev/null
  docker update --restart no "$VIEJO" >/dev/null

  if corriendo "$PROXY"; then
    if [ "$config_cambio" = si ]; then docker exec "$PROXY" nginx -s reload; fi
    log "Esperando ${DRENAJE}s a que el proxy deje de mandarle trafico a $VIEJO"
    sleep "$DRENAJE"
    log "Apagando $VIEJO de forma ordenada (tope ${APAGADO}s)"
    docker stop -t "$APAGADO" "$VIEJO" >/dev/null || true
    docker rm -f "$VIEJO" >/dev/null 2>&1 || true
  else
    poner_proxy_delante || { echo "PARA VOLVER A LA VERSION ANTERIOR: $VOLVER"; exit 1; }
  fi
  if ! sano_por "$PUERTO_PUBLICO"; then
    echo "ERROR: tras el cambio, el puerto $PUERTO_PUBLICO no responde UP."
    echo "PARA VOLVER A LA VERSION ANTERIOR: $VOLVER"
    exit 1
  fi
}

# ------------------------------------------------------------------------------------------------

if [ -n "$REGISTRO" ]; then
  log "1/5 Autenticando contra ECR"
  aws ecr get-login-password --region "$REGION" \
    | docker login --username AWS --password-stdin "$REGISTRO"
  # Se baja ANTES de tocar nada: si la imagen no existe o el registro no responde, se corta aca
  # y produccion no se entera.
  log "2/5 Bajando $IMAGEN"
  docker pull "$IMAGEN"
fi

limpiar_restos
ANTERIOR=$(docker inspect --format '{{.Config.Image}}' "$CONTENEDOR" 2>/dev/null || echo ninguna)
VOLVER=$(comando_para_volver)
log "Imagen que estaba corriendo: $ANTERIOR"

ELEGIDO=$(elegir_modo)
log "3/5 Modo: $ELEGIDO"
# NO se vuelve solo a la version anterior si el arranque falla (Flyway ya pudo haber migrado).
# En el modo sin corte eso casi no importa: si el nuevo no arranca, el viejo nunca dejo de atender.
if [ "$ELEGIDO" = sin-corte ]; then
  desplegar_sin_corte
else
  desplegar_con_reemplazo
fi

log "5/5 Limpieza y estado final"
# Borra solo imagenes colgadas (sin etiqueta). La anterior queda con su etiqueta de SHA.
docker image prune -f >/dev/null 2>&1 || true
docker ps --format '{{.Names}}  {{.Image}}  {{.Status}}'
LIBRE_KB=$(df -Pk / | awk 'NR==2 {print $4}')
echo "Disco libre en /: $(( LIBRE_KB / 1024 )) MB"
# El disco raiz son 8 GB y cada version de la imagen ocupa ~440 MB.
if [ "$LIBRE_KB" -lt 3145728 ]; then
  echo "AVISO: quedan menos de 3 GB libres. Borrar imagenes viejas de renaser-backend."
fi
