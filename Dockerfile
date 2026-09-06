# Imagen del backend de Renaser OS. Tres etapas: compilar, partir el jar en capas, y armar la
# imagen final solo con lo que hace falta para ejecutar.
#
# Las dos bases que se usan (`25-jdk-noble` para compilar, `25-jre-noble` para extraer y ejecutar)
# son multi-arquitectura y publican linux/arm64/v8 ademas de linux/amd64 (verificado contra el
# manifiesto de Docker Hub), asi que la misma imagen se puede construir para instancias Graviton.
# En GitHub Actions eso se pide con `platforms:` en docker/build-push-action.

# ---------------------------------------------------------------------------------------------
# 1. COMPILAR
# ---------------------------------------------------------------------------------------------
# Se usa el JDK pelado + el wrapper del repo (./mvnw) en vez de una imagen `maven:*`: asi la
# version de Maven la sigue mandando .mvn/wrapper/maven-wrapper.properties, que es la misma que
# usa cualquiera en su maquina. Una imagen de Maven traeria otra version y las diferencias de
# build serian invisibles hasta que rompan algo.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

# Primero SOLO lo que define las dependencias. Mientras el pom.xml no cambie, Docker reutiliza la
# capa de descargas y una compilacion normal no vuelve a bajar medio Maven Central.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# El bit de ejecucion no sobrevive a un checkout en Windows; sin esto el RUN de abajo falla con
# "permission denied".
RUN chmod +x ./mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# -DskipTests a proposito: las pruebas son responsabilidad del workflow de CI, que las corre con
# Docker disponible para Testcontainers. Construir la imagen no es el lugar para levantar un
# Postgres dentro de otro contenedor.
RUN ./mvnw -B -ntp clean package -DskipTests

# ---------------------------------------------------------------------------------------------
# 2. PARTIR EL JAR EN CAPAS
# ---------------------------------------------------------------------------------------------
# El jar de Spring Boot es un solo archivo grande donde las dependencias (que casi nunca cambian)
# y nuestras clases (que cambian en cada commit) viven juntas. Extraerlo en capas hace que un
# cambio de codigo solo invalide la ultima capa: el `docker push` sube unos pocos MB en vez de
# los ~90 del jar entero, y el `docker pull` del despliegue tambien.
#
# OJO CON EL COMANDO: `-Djarmode=layertools` (el que aparece en casi todos los tutoriales y en
# guias escritas para Boot 3.x) **fue eliminado en Spring Boot 4.1**. Quedo deprecado en 3.3,
# siguio funcionando con aviso en 4.0 y ya no existe en la version que usa este repo. El
# reemplazo es `-Djarmode=tools ... extract --layers`, que ademas deja el layout que entienden
# CDS y la cache de AOT.
FROM eclipse-temurin:25-jre-noble AS extract
WORKDIR /extract
COPY --from=build /build/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------------------------
# 3. IMAGEN FINAL
# ---------------------------------------------------------------------------------------------
# JRE, no JDK: no hace falta un compilador para ejecutar. Medido contra el manifiesto de Docker
# Hub (capas comprimidas, arm64): `25-jdk-noble` son 133 MB y `25-jre-noble` 98 MB.
#
# Se elige `noble` (Ubuntu, glibc) y no `alpine` (musl), aunque `25-jre-alpine` son 71 MB —27 MB
# menos— y tambien publica arm64. El motivo es que desde aca no se puede levantar el contenedor
# para probarlo: Lettuce (Redis) arrastra Netty, y las diferencias de resolucion DNS entre glibc
# y musl son una fuente conocida de fallos que solo aparecen en ejecucion. 27 MB no pagan una
# apuesta a ciegas. Cambiar a alpine es una linea; si alguien lo prueba y anda, dejarlo
# registrado.
FROM eclipse-temurin:25-jre-noble
WORKDIR /application

# Sin usuario propio, el proceso corre como root: cualquier escape del proceso Java arranca con
# UID 0 dentro del contenedor. Se crea un usuario del sistema sin shell y sin contrasena.
RUN groupadd --system --gid 1001 renaser \
 && useradd --system --uid 1001 --gid renaser --home-dir /application --shell /usr/sbin/nologin renaser

# El orden importa: de la capa que menos cambia a la que mas. Cada COPY es una capa de Docker.
COPY --from=extract --chown=renaser:renaser /extract/extracted/dependencies/ ./
COPY --from=extract --chown=renaser:renaser /extract/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=renaser:renaser /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=renaser:renaser /extract/extracted/application/ ./

USER renaser
EXPOSE 8080

# Perfil por defecto de la imagen. Es lo que hace que se lea application-prod.yaml y, con el,
# AWS Parameter Store. Se puede pisar en el despliegue (-e SPRING_PROFILES_ACTIVE=staging).
ENV SPRING_PROFILES_ACTIVE=prod

# La JVM en un contenedor toma por defecto ~25% de la memoria del cgroup, que en una tarea chica
# deja la mitad de la RAM sin usar. Se pasa por JAVA_TOOL_OPTIONS y no por el ENTRYPOINT para que
# el `java` siga siendo el PID 1 en forma exec: asi recibe el SIGTERM del orquestador y Spring
# apaga ordenado, en vez de que un `sh -c` se coma la senal.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# `application.jar` NO es el uber-jar: es el jar liviano que dejo la extraccion, con las
# dependencias referenciadas desde las capas de al lado por el Class-Path del manifiesto.
ENTRYPOINT ["java", "-jar", "application.jar"]
