# Pruebas locales con contenedores en Testcontainers Cloud

Configuración solicitada el 2026-09-07: Maven, Java y las pruebas siguen en la laptop; PostgreSQL
(`pgvector/pgvector:pg16`) y Redis (`redis:7-alpine`) se ejecutan en Testcontainers Cloud.
No cambia el motor de base de datos, las migraciones ni los fixtures.

## Primera conexión

1. Iniciar sesión en [Testcontainers Cloud](https://app.testcontainers.cloud/) y crear un token.
2. Guardar **solo el token** en `~/.config/renaser/testcontainers-cloud.token`, con permisos `600`.
   Ese archivo vive fuera del repositorio. También se admite `TC_CLOUD_TOKEN` en el entorno.
3. Tener instalado el agente en `~/.local/bin/testcontainers-cloud-agent`.
   En la laptop de Ricardo ya está instalado, versión 1.26.0. En otro equipo, descargar el
   binario adecuado siguiendo la [documentación oficial](https://testcontainers.com/cloud/docs/).
   `TCC_AGENT_BIN` permite indicar otra ubicación.

No guardar tokens en Git, logs ni mensajes. El comando no imprime el token.
El acceso y los minutos disponibles dependen de la cuenta de Testcontainers Cloud.

## Ejecutar

```bash
./scripts/test-cloud.sh
```

Ejecuta `./mvnw -B -ntp clean verify -Drenaser.tests.cloud-required=true`, incluyendo Surefire,
Failsafe y cobertura JaCoCo. Para una selección de pruebas:

```bash
./scripts/test-cloud.sh -Dtest=HorarioPorFechaPersistenceAdapterTest test
```

El script inicia su agente, espera hasta 45 segundos por la conexión y configura un solo worker.
Al terminar, cierra el agente y libera sus workers mediante `--terminate`, también ante errores.
Los logs privados del agente quedan en `~/.cache/renaser-testcontainers/` (o `XDG_CACHE_HOME`).
No lanzar este script simultáneamente con otra sesión del agente en el mismo usuario.

Si falta el token o falla la conexión, **Maven no arranca**. Además,
`TestcontainersConfiguration` consulta la versión del runtime antes de crear cualquiera de los
contenedores: si no identifica Testcontainers Cloud, falla en vez de usar Docker local.
`TestcontainersCloudGuardTest` cubre esa negativa sin iniciar Docker.

El CI existente conserva Docker en el runner de GitHub; no consume recursos de la laptop.
`./mvnw` directo conserva el comportamiento anterior. Para descargar los contenedores en la nube
hay que usar `test-cloud.sh`; no iniciar la suite completa con Docker local sin indicación del usuario.

## Estado de la conexión

La integración y el agente están preparados. Falta proporcionar el token para comprobar una
sesión real y ejecutar `clean verify` en Cloud. Instalar el agente no equivale a conectar la cuenta.
