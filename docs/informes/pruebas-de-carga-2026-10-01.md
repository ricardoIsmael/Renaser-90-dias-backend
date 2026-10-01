# Pruebas de carga y estrés — 2026-10-01 (D-238)

Pedido del dueño: saber cuántas personas a la vez aguanta el programa con respuestas rápidas, dónde
se rompe, qué se rompe primero y si la base de datos queda chica. **Sin la IA** (ni SER ni voz: no
se gastó cuota de Gemini) y **sin tocar producción**: todo corrió contra una réplica temporal en
AWS que ya se borró. Guion, scripts y forma de repetirlo: `pruebas-de-carga/README.md`.

## 1. La respuesta corta

| Pregunta | Respuesta |
|---|---|
| ¿Cuántos usuarios a la vez, con respuesta rápida (p95 < 1 s)? | **~300 usuarios usando la app al mismo tiempo.** Con 200 el 95 % de las respuestas tarda menos de 0,36 s |
| ¿Cuándo empieza a ponerse lento (p95 > 1 s)? | **Desde ~350 a la vez.** Entre 350 y 450 sube y baja entre 0,7 y 2,6 s |
| ¿Cuándo empieza a fallar (errores > 1 %)? | **A los ~500 a la vez** (475: 0,24 %; 500: 4,9 %) |
| ¿Cuándo colapsa? | **Entre 550 y 600 a la vez**: 550 → 10,8 % de errores; 600 → 38 %, y atiende **menos** pedidos que con 400 |
| ¿Qué se rompe primero? | **El procesador del servidor de la aplicación** (EC2 t3.medium, 2 núcleos): al 84–97 % desde 325 usuarios. Las 20 conexiones a la base quedan tomadas por pedidos que esperan procesador, se forma una cola de hasta 1.136 pedidos y a los 5 s salen con error 500 |
| ¿La base de datos queda chica? | **No.** En el peor momento la RDS `db.t4g.micro` estuvo al **43 %** de CPU, con memoria y disco tranquilos. Se rompe antes la aplicación |
| ¿Cuántas personas inscritas son eso? | Con supuestos explicados abajo: **300 a la vez ≈ 3.000 a 6.000 inscritos**. Hoy hay 12 aprendices y 14 personas de staff |
| ¿Hay que cambiar algo hoy? | **No.** Ver §6 para saber qué mirar y qué cambiar cuando crezca |

## 2. Qué se armó (y qué se borró)

**Réplica idéntica en lo que importa:**

| Pieza | Producción | Réplica |
|---|---|---|
| Base | RDS `renaser-prod` db.t4g.micro, PG 16.15, 20 GB gp3, single-AZ, `default.postgres16` | `renaser-carga-db`, restaurada de un snapshot manual de hoy: misma clase, motor, disco y parameter group. **No pública**, en el mismo VPC, con un security group propio que solo deja entrar a la EC2 de carga |
| Servidor | EC2 t3.medium (créditos `unlimited`), misma AMI | `renaser-carga-app` t3.medium `unlimited`, **misma AMI**, misma subred/zona |
| Backend | imagen `527e376b…` (la desplegada hoy), `--memory 1400m`, `-XX:MaxRAMPercentage=60.0` | **la misma imagen**, mismo tope de memoria y mismas opciones de JVM; Redis 7 local |
| Delante | nginx (`proxy`) y CloudFront | **sin nginx ni CloudFront**: k6 le pega directo al 8080 (nginx agrega menos de 1 ms) |

**Para que no le llegue nada a nadie:**

- El backend arrancó con un perfil propio (`carga`), **no `prod`**: no leyó ni un parámetro de
  `/renaser/prod/`. IA en `noop` (sin clave de Gemini), voz en `noop`, correo `noop`,
  almacenamiento `noop` (no escribió en el bucket de producción: las fotos se "pidieron" pero no se
  subieron), push de Expo apagado.
- Además, **la EC2 de la réplica no tenía salida a internet**: su firewall solo dejaba salir hacia
  el puerto 5432 de la base de la réplica. Aunque algo hubiera intentado mandar un push, un correo o
  llamar a Gemini, no habría llegado. Se comprobó (`curl` a `exp.host` desde la EC2: sin conexión).
- Los procesos programados (recordatorios, barridos) **quedaron encendidos**: el backend no tiene un
  interruptor general y apagarlos uno por uno era tocar Java. Sin internet y con push apagado no
  podían avisar a nadie, y lo que escribieron fue a la base de la réplica, que se borró. Además así
  la réplica cargaba lo mismo que producción.
- Entrada a la EC2 (SSH, 8080 y 8091) solo desde la IP pública de la laptop. Nada abierto a todo
  internet.
- Cuentas de prueba: **solo en la base de la réplica**, a los 12 aprendices activos se les cambió el
  correo por `carga-aprendiz-NN@carga.test` y se les puso una contraseña de prueba, así ningún correo
  real salió de AWS. La contraseña vivió en un archivo 600 y se borró.
- Para descargar la imagen se creó un **rol IAM temporal** de solo lectura (ECR solo lectura + SSM
  básico). **No se reusó** el rol de producción porque puede escribir y borrar en el bucket de
  producción.

**Creado y borrado (verificado con `describe` al final):**

| Recurso | Creado | Borrado | Verificación |
|---|---|---|---|
| Snapshot manual `renaser-carga-snap-20261001` | sí | sí | `describe-db-snapshots` no lo lista |
| RDS `renaser-carga-db` | sí | sí, sin snapshot final y sin backups automáticos | `DBInstanceNotFound`; sin backups retenidos |
| EC2 `i-011a7c8bd39f92fea` (y su disco) | sí | sí | `terminated`; el volumen da `InvalidVolume.NotFound` |
| Security groups `renaser-carga-app` (sg-072a4e0b…) y `renaser-carga-db` (sg-09abee7d…) | sí | sí | no aparecen |
| Par de llaves `renaser-carga-key` | sí | sí | no aparece |
| Rol y perfil IAM `renaser-carga-ec2` | sí | sí | `NoSuchEntity` |

Producción: `renaser-prod` `available` y la EC2 `running`, sin cambios. No se le mandó tráfico.
**Costo estimado de todo: menos de US$1** (≈1,7 h de RDS y EC2, créditos extra de CPU y ~1,5 GB de
transferencia).

## 3. Qué hace cada "usuario virtual"

Cada usuario virtual es **una persona con la app abierta y tocando**: abre una pantalla, la app
dispara en paralelo lo mismo que dispara la de verdad, la persona mira entre 3 y 10 s y abre otra.
Las llamadas salieron del código de la app (`origin/master` del frontend). Pesos: Hoy 30 %,
Entrenamiento 20 % (la mitad de las veces marca un hábito; a veces con evidencia de foto, sin subir
el archivo), Comunidad 15 % (comentarios, reacciones, publicar), Chat 12 % (abrir un grupo y 35 % de
las veces escribir), Plan 8 %, Ranking 8 % (personal y de grupos), Perfil 5 %, Notificaciones 2 %.
Más lecturas que escrituras, como en el uso real. El detalle está en el README.

Ojo: un usuario virtual es **más intenso** que una persona real (una pantalla cada ~7 s sin parar).

## 4. Resultados

Las latencias se midieron **desde la laptop en Lima** e incluyen ~105 ms de viaje de red hasta
Virginia, que es lo mismo que vive un usuario real en Perú.

### 4.1 Carga escalonada (5 min por escalón)

| Usuarios a la vez | Pedidos/s | Mediana | p95 | p99 | Errores reales* | CPU servidor | CPU base |
|---|---|---|---|---|---|---|---|
| 25 | 23 | 132 ms | 424 ms | 867 ms | 0,00 % | ~17 % | ~7 % |
| 50 | 46 | 118 ms | 250 ms | 457 ms | 0,01 % | ~20 % | ~10 % |
| 100 | 89 | 118 ms | 233 ms | 744 ms | 0,01 % | ~32 % | ~12 % |
| 200 | 179 | 124 ms | 356 ms | 1,1 s | 0,01 % | ~57 % | ~20 % |

\*Cortes de conexión, 5xx o 429. Los pocos cortes (11 en 112.000 pedidos) fueron de la red de la
laptop: el servidor no tuvo ningún pedido de más de 5 s (`http_server_requests` de Prometheus).
Conexiones a la base: 20 de 20 abiertas (el pool), pero como mucho 6 ocupadas a la vez y **nunca
nadie esperando**. Memoria de la JVM: hasta ~600 de 812 MB.

### 4.2 Pico: de 0 a 150 personas en 30 s

Como si todos abrieran la app por un aviso. **Lo aguantó sin problema**: p95 de 154 ms durante la
subida y 216–245 ms después; cero errores de capacidad; como mucho 11 conexiones ocupadas.

### 4.3 Estrés: subir hasta romperlo

Primera pasada de 100 en 100, y una segunda de 25 en 25 entre 325 y 550 para afinar.

| Usuarios a la vez | Pedidos/s | Mediana | p95 | Errores reales | CPU servidor | CPU base |
|---|---|---|---|---|---|---|
| 300 | 264 | 139 ms | 0,51 s | 0,00 % | ~70–78 % | ~27–31 % |
| 325 | 284 | 155 ms | 0,82 s | 0,02 % | ~84 % | ~35 % |
| 350 | 298 | 172 ms | **1,2 s** | 0,01 % | ~85 % | ~35 % |
| 375 | 318 | 200 ms | 1,8 s | 0,00 % | ~88 % | ~38 % |
| 400 | 325–351 | 224 ms | 0,7–2,2 s | 0,01–0,67 % | ~90 % | ~40 % |
| 450 | 345 | 430 ms | 2,6 s | 0,01 % | ~93 % | ~42 % |
| 475 | 331 | 858 ms | 3,4 s | 0,09 % | ~95 % | ~43 % |
| 500 | 280–285 | 1,2 s | 8,5–9,1 s | **4,4–4,9 %** | ~93 % | ~40 % |
| 550 | 254 | 2,1 s | 11,6 s | **10,6 %** | ~90 % | ~40 % |
| 600 | 177 | 8,2 s | 15,4 s | **38 %** | ~85 % | ~30 % |
| 700 | 140 | 11,6 s | 24,2 s | **54 %** | ~75 % | ~15 % |

(Dos corridas en momentos distintos: donde hay un rango, son los dos valores medidos.)

**Cómo se rompe, en orden:**

1. **El procesador de la EC2 se llena** (84–97 % desde 325 usuarios). Es el único recurso que llega
   al tope.
2. Con el procesador lleno, cada pedido tarda más y retiene más tiempo su conexión a la base. Las 20
   del pool quedan siempre ocupadas y se arma una **cola de espera** (hasta 1.136 pedidos). El que
   espera más de 5 s sale con **error 500**: `Connection is not available, request timed out after
   5245ms (total=20, active=20, idle=0, waiting=341)`.
3. Lo más rápido que atiende es **~360 pedidos por segundo** (con 400–425 usuarios). Pasado eso, en
   vez de mantenerse, **atiende menos** (140/s con 700): eso es el colapso.
4. Mientras tanto la base está **ociosa en comparación**: CPU máxima 43 %, cuando la aplicación
   colapsa baja a 15 %, memoria libre estable (~90 MB de 1 GB, lo mismo que en reposo), disco
   tranquilo (menos de 65 operaciones/s, el disco da 3.000).
5. Segundo límite, cerca: la **memoria de la JVM** llegó a 790 de 812 MB y el contenedor a 1,29 de
   1,37 GB, con muchas más pausas de recolección de basura. No hubo caída por falta de memoria
   (`OOMKilled=false`, 0 reinicios).
6. **Se recupera solo**: cuando baja la carga vuelve a responder sano en segundos, sin reiniciar.

## 5. ¿La `db.t4g.micro` alcanza? Créditos de CPU

| Personas usando la app a la vez | ≈ Inscritos (ver supuesto) | ¿Alcanza la base? | ¿Alcanza el servidor? |
|---|---|---|---|
| 25 | 250–500 | Sí, sobra (~7 % CPU) | Sí, sobra |
| 50 | 500–1.000 | Sí (~10 %) | Sí |
| 100 | 1.000–2.000 | Sí (~12 %) | Sí |
| 200 | 2.000–4.000 | Sí (~20 %) | Sí (~57 %) |
| 300 | 3.000–6.000 | Sí (~30 %) | Justo: es su límite con respuesta rápida |

**Supuesto para pasar de "a la vez" a "inscritos":** una persona abre la app 3 a 5 veces al día, unos
3–4 minutos cada vez, y buena parte del uso cae en dos horas pico (mañana y noche). Eso da entre
**5 % y 10 % de los inscritos usando la app a la vez en la hora pico**, o sea 1 usuario virtual ≈ 10
a 20 inscritos. Es una estimación: el número real va a salir de Grafana (D-237) cuando haya uso.
Como el usuario virtual es más intenso que una persona, el número tiende a ser conservador.

**Créditos de CPU** (las dos máquinas son "burstable": tienen un consumo base que pueden sostener
gratis y acumulan créditos para pasarse un rato):

- **Las dos están en modo `unlimited`** (la EC2 de producción se verificó; la RDS de la réplica lo
  confirmó en la práctica: pasó del 40 % de CPU con el saldo en 0, sin que la frenaran, y su
  `CPUSurplusCreditBalance` subió de 4 a 12,7). **Quedarse sin créditos no las frena: cobra un
  extra.** Por eso la réplica, que nace sin créditos, midió lo mismo que mediría producción.
- **Capacidad sostenida sin pagar extra** (el consumo base): la base, ~10 % de CPU ≈ **100 usuarios
  a la vez**; el servidor, ~20 % ≈ **50 usuarios a la vez**.
- **Ráfaga que da el saldo de producción hoy** (RDS 288 créditos, el tope; EC2 576, el tope;
  producción hoy usa 4 % y 1 % de CPU):

  | Usuarios a la vez sostenidos | Base: cuánto duran los 288 créditos | Servidor: cuánto duran los 576 |
  |---|---|---|
  | 200 | ~24 h | ~13 h |
  | 300 | ~10–12 h | ~9 h |
  | En el límite (~450) | ~7 h | ~6 h |

  Cualquier pico real (1–2 h) entra de sobra, y los créditos se recuperan de noche.
- **Si alguna vez se pasaran todo el día**, el extra sería chico: con 200 usuarios a la vez las 24 h,
  unos US$11/mes la base y US$27/mes el servidor (precio de lista: US$0,075 y US$0,05 por núcleo-hora
  extra).

## 6. Qué cambiar, y cuándo

**Hoy: nada.** La infraestructura actual aguanta ~300 personas a la vez con respuesta rápida, y hoy
hay 26 cuentas.

**Qué mirar en Grafana (D-237)** para saber que se acerca el límite: CPU de la EC2 sostenida por
encima del 60 %, `hikaricp_connections_pending` mayor que 0, o p95 de la API por encima de 1 s.

**Cuando se acerque, en este orden:**

1. **Más procesador para la aplicación** (es lo que se rompe primero). Ojo: `t3.large` **no sirve**,
   tiene los mismos 2 núcleos (solo más memoria). Hace falta una de 4 núcleos: `t3.xlarge` (4 núcleos,
   16 GB, ~US$121/mes contra ~US$30 de hoy) o `c7i.xlarge` (~US$130/mes, más procesador sostenido).
   Con 4 núcleos y la base actual debería rondar el doble de usuarios; hay que volver a medir.
2. **Con la aplicación más grande, la base pasa a ser lo siguiente**: con 450 usuarios ya estaba al
   43 %; al doble llegaría al 80–90 %. En ese momento, `db.t4g.small` (2 GB, ~US$23/mes contra ~US$12
   de hoy) o `db.t4g.medium` (4 GB, ~US$47/mes). Se cambia junto con el paso 1, no antes.
3. **El pool de conexiones (Hikari) se deja en 20.** Subirlo no arregla nada (el cuello es el
   procesador) y la `db.t4g.micro` admite 79 conexiones con 1 GB de memoria. Si la aplicación pasa a
   4 núcleos, subirlo a ~30 y medir la memoria de la base.
4. Mejoras chicas encontradas, **registradas y no aplicadas** (regla de alcance):
   - **E-471** — la consulta del "último mensaje" de la lista de chats lee y ordena **todos** los
     mensajes de las conversaciones del usuario: 7 ms con 3.400 mensajes, y crece en proporción
     (`GET /chat/conversations`). Arreglo propuesto: buscar el último de cada conversación con el
     índice que ya existe (`LATERAL … LIMIT 1`), que tarda 0,03 ms.
   - **E-473** — bajo saturación el error es un 500 genérico; debería ser un 503 ("ocupado, reintenta")
     para que la app pueda reintentar.
   - **E-470** — (no es de capacidad) la lista de chats muestra el grupo de un período que ya
     terminó, y al abrirlo da 403 "Tu asignacion cambio: ya no perteneces a ese grupo". Le pasa hoy a
     un aprendiz real (su grupo "Guía Celia…" cerró el 30-sep).
   - **E-472** — (entorno) arrancar con `SMTP_HOST=` vacío deja el health en DOWN.

   > **Actualizado 2026-10-01 (D-239, rama `arreglos-carga`).** E-470, E-471 y E-473 quedaron
   > **resueltos** después de este informe: el grupo terminado ya no se lista (decisión del dueño), el
   > último mensaje sale con `LATERAL … LIMIT 1` sobre el índice, y el pool agotado responde 503 con
   > `Retry-After: 5`. El detalle está en cada entrada de `docs/BITACORA_ERRORES.md`. No se volvió a
   > correr la carga para medir el efecto.

## 7. Límites de esta medición (lo que no se probó)

- **Pocos datos**: la base pesa 59 MB (el 54 % es la base de conocimiento de la IA) y hay 12
  aprendices. Las 200–300 sesiones de prueba se repartieron entre esas 12 cuentas (como alguien con
  el teléfono y la web abiertos). Lo que cuesta cada consulta es el de un aprendiz real; lo que **no**
  se midió es cómo se comporta con meses de historia y miles de cuentas. Lo único que ya se ve crecer
  con el volumen es E-471.
- **El valor del pool en producción no se pudo leer** (`DB_POOL_MAX_SIZE` está en Parameter Store y
  leer parámetros de producción fue denegado por permisos). Se usó 20, el valor documentado
  (`application.yaml`, D-66, D-213). Si producción tiene otro valor, el punto de quiebre se corre:
  menos conexiones = cola antes.
- **Sin nginx ni CloudFront** delante; k6 desde una sola laptop. La red de la laptop no fue el
  límite (en promedio ~5 Mbit/s).
- **No se probó**: la IA (a propósito), el WebSocket del chat en tiempo real (cada persona con la app
  abierta mantiene una conexión: no se midió su costo en memoria), la subida real de fotos a S3 y
  los logins en masa (se hicieron una vez al inicio; la sesión dura 30 días).
- Las escrituras de prueba fueron reales en la réplica, y los hábitos de hoy de las 12 cuentas se
  completaron en los primeros minutos: después, "marcar hábito" se volvió casi todo rechazos de
  negocio (400 "ya completado"/"la Clase Diaria se cumple desde Training"), que **no** se contaron
  como errores.
