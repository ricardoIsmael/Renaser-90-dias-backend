# Pruebas de carga (k6)

Guion de carga y estrés de la API de Renaser 90 días **sin la IA**. Método y resultados de la primera
corrida: `docs/informes/pruebas-de-carga-2026-10-01.md` y la decisión D-238 en
`docs/MODULOS_A_AVANZAR.md` §8.

> **Nunca contra producción.** El guion escribe: completa hábitos, registra evidencias, manda
> mensajes de chat, comenta, reacciona y publica en el muro. Se corre contra una réplica temporal
> armada desde un snapshot, y la réplica se borra al terminar.

## Qué hay acá

| Archivo | Para qué |
|---|---|
| `renaser.js` | El guion de k6: setup (logins), las pantallas de la app con sus pesos, y los escenarios `humo`, `carga`, `estres` y `pico` |
| `monitorear.sh` | Muestrea cada 5 s el `/actuator/prometheus` (puerto 8091) de la réplica: Hikari, heap, CPU, hilos. Sale un CSV |
| `cloudwatch.sh` | Baja de CloudWatch, por minuto, CPU/créditos/red de la EC2 y CPU/conexiones/memoria/IOPS/créditos de la RDS |
| `analizar.py` | Resume el CSV de k6 por escalón: usuarios, pedidos/s, p50/p95/p99, % de errores y los endpoints más lentos |

## Qué hace un usuario virtual

Cada vuelta abre **una pantalla** y después se queda quieto entre 3 y 10 s, como una persona que
lee. La pantalla dispara **en paralelo** lo mismo que dispara la app al abrirla (se sacó del código
de la app, `origin/master` del frontend, el 2026-10-01):

| Pantalla | Peso | Lecturas | A veces, además |
|---|---|---|---|
| Hoy | 30 % | `home`, `habit-tracks/today`, `rocks/today`, `wall`, `mapa-renacimiento`, `mentor/context` | 1 de cada 3, el arranque de la app: `onboarding/state`, `radar/latest`, `calendar/events`, `rocks/upcoming`, `rocks/tomorrow`, `habit-preferences` |
| Entrenamiento | 20 % | `habit-tracks/today`, `habits`, `habit-preferences`, `habit-unlocks`, `rocks/today`, `evidence`, `spirit-audio/status`, `audio-therapy/status` | La mitad de las veces marca un hábito pendiente; 4 de cada 10 de esas, con evidencia de foto (`upload-url` + registrar evidencia, **sin subir el archivo**) |
| Comunidad | 15 % | `wall`, `me/cells` | Comentarios de una publicación (50 %), reacción (25 %), comentar (5 %), publicar con foto sin subirla (3 %) |
| Chat | 12 % | `chat/conversations`, `chat/members`, `me/cell`, `me/cells` | Abre un grupo o un directo (mensajes, presencia, leído) y 35 % de las veces manda un texto |
| Plan | 8 % | `habits`, `habit-preferences`, `habit-unlocks`, `home`, `rocks/master`, `rocks/monthly/plan`, `mapa-renacimiento`, `rocks/weekly` | — |
| Ranking | 8 % | `ranking`, `me/cell`, `me/cells`, `ranking/groups` (si se pasa `COHORTE`) | — |
| Perfil | 5 % | `home`, `evidence`, `onboarding/state`, `mapa-renacimiento`, `me/caja`, `mentor/context`, `POST users/me`, `profile/logros` | — |
| Notificaciones | 2 % | `notifications` | Marca una como leída (20 %) |

**No se llama a nada de IA:** ni `/renasia/**` (incluido `GET /renasia/memoria`, que la pantalla Yo
sí pide), ni la voz, ni el Espejo. Tampoco al WebSocket.

Los logins se hacen una sola vez, en `setup()`: la sesión de la app dura 30 días y una persona real
casi nunca vuelve a iniciar sesión. Cada login sale con su propia `X-Forwarded-For` porque el login
tiene un tope de 50 intentos por hora y por IP (y 10 por IP y correo) fijo en el código; sin eso,
toda la prueba sería una sola IP y el tope cortaría el setup. Varias sesiones pueden ser de la
misma cuenta (como alguien con el teléfono y la web): las consultas cuestan lo mismo.

Una escritura que el negocio rechaza (hábito ya completado, 400/404/409/422) **no** cuenta como
error de capacidad: va al contador `rechazos_de_negocio`. Errores son 5xx, 429, 401/403 y cortes.

## Escenarios

| `ESCENARIO` | Forma | Dura |
|---|---|---|
| `humo` | 3 usuarios, 1 min. Para comprobar que todo responde 200 antes de cargar | 1 min |
| `carga` | 25 → 50 → 100 → 200 usuarios, 5 min sostenidos cada escalón (rampas de 30 s) | ~22 min |
| `estres` | +100 usuarios cada 2 min hasta 1.500 (`ESTRES_PASO`, `ESTRES_ESCALONES`; con `ESTRES_INICIO=300 ESTRES_PASO=25` hace una pasada fina alrededor del quiebre, y `analizar.py estres <csv> 25 300` la resume). No corta por lentitud; **corta solo** si los errores acumulados pasan el 10 % | hasta 30 min |
| `pico` | De 0 a 150 usuarios en 30 s, 3 min sostenidos | 4 min |

## Cómo se corre (resumen; el detalle de la réplica está en el informe)

1. **Réplica.** Snapshot manual de `renaser-prod`, restaurado como `renaser-carga-db` (misma clase,
   **no pública**, SG propio que solo admite a la EC2 de carga). EC2 `renaser-carga-app` de la misma
   clase que producción, con la misma imagen de ECR, el mismo `--memory 1400m` y las mismas
   `JAVA_TOOL_OPTIONS`. Su SG deja entrar 22/8080/8091 solo desde la IP de quien prueba y **no deja
   salir a internet** (solo al 5432 de la RDS de la réplica): aunque algo intentara mandar un push,
   un correo o llamar a Gemini, no llega.
2. **Backend de la réplica** con perfil `carga` (no `prod`: no lee Parameter Store de producción) y
   `IA_PROVEEDOR=noop`, `IA_VOZ_PROVEEDOR=noop`, `EMAIL_PROVEEDOR=noop`, `STORAGE_PROVEEDOR=noop`,
   `RENASER_NOTIFICATIONS_EXPOPUSH_HABILITADO=false`, `MANAGEMENT_HEALTH_MAIL_ENABLED=false`.
   **No poner `SMTP_HOST=` vacío**: crea el `JavaMailSender` igual y el health queda DOWN.
3. **Cuentas de prueba, solo en la base de la réplica**: a los aprendices activos se les cambia el
   correo por `carga-aprendiz-NN@carga.test` y se les pone una contraseña de prueba (bcrypt con
   `pgcrypto`, prefijo `{bcrypt}`). Así ningún correo real sale de AWS. La contraseña va en un
   archivo 600 (`PASS_FILE`), nunca en la línea de comandos.
4. Correr (k6 desde la laptop):

   ```bash
   ./monitorear.sh http://<ip>:8091 monitor-carga.csv &      # en paralelo
   k6 run -e BASE=http://<ip>:8080 -e ESCENARIO=carga -e PASS_FILE=/ruta/pass.txt \
          -e COHORTE=<uuid de cohorte> -e RESULTADOS=./res --out csv=res/carga.csv.gz renaser.js
   python3 analizar.py carga res/carga.csv.gz
   ./cloudwatch.sh <id-ec2> renaser-carga-db <desde> <hasta> ./res
   ```

   Antes de cada corrida, `select pg_stat_statements_reset();` en la réplica; al final, las
   consultas con más tiempo total y medio salen de `pg_stat_statements`.
5. **Borrar todo** (RDS sin snapshot final, snapshot manual, EC2, SG, par de llaves, rol y perfil
   IAM temporales) y comprobarlo con `describe`.

## Cómo leer los números

- Las latencias se miden **desde la laptop en Lima** e incluyen el viaje de red hasta `us-east-1`
  (~105 ms de ida y vuelta medidos con `curl`). Es lo que vive un usuario real, que también está en
  Perú; para el tiempo del servidor solo, restar ~100 ms o mirar
  `http_server_requests_seconds` en el Prometheus de la réplica.
- La réplica no tiene nginx delante (producción sí, desde E-465): nginx agrega menos de 1 ms.
