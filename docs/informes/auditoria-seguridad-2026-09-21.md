# Auditoría de seguridad — 2026-09-21

Segunda pasada con el método de Cloudflare (`security-audit`), esta vez **las seis fases completas y
en dos olas de cacería**: reconocimiento, cacería por unidades de cobertura, validación adversarial
de cada candidato, salida estructurada con sus dos validadores, verificación final con ojos frescos,
e informe. La primera ola cubrió 11 unidades; la segunda, las 9 que el crítico había aceptado y que
se habían quedado sin cazar.

**Source-first, y literal: no se ejecutó una sola línea del backend.** Ni `mvn`, ni la suite, ni
Testcontainers, ni Docker, ni arrancar la app. Tampoco se contactó AWS, CloudFront, la EC2, Redis,
Postgres, S3 ni ningún proveedor de IA, ni se hizo una sola consulta DNS o LDAP. La máquina no
ofrece el sandbox que el método exige para ejecutar código del objetivo, así que la corrida se
declaró **solo-fuente** y cada hallazgo dice en su propio texto que se estableció leyendo, no
ejecutando.

Lo que sí se leyó a fondo, porque no es ejecutar: **el código fuente de las dependencias clavadas**.
Tres hallazgos de la segunda ola se decidieron ahí y no en este repositorio — el `src.zip` del JDK 25
para la cadena JNDI, el jar de fuentes de Hibernate 7.4.5 para el cerrojo pesimista, y el de
`spring-messaging` 7.0.9 para acotar el radio del fanout de Redis.

Rama: `master` @ `b5a8102`, limpia y al día con `origin`. La auditoría anterior (2026-09-18) se hizo
sobre `bdeeec8`; los cinco arreglos que entraron después **se revalidaron, no se dieron por buenos**.

El informe técnico completo está en `~/security-audit-skill/Renaser-90-dias-backend/run-1/`:
`REPORT.md`, `FINDINGS-DETAIL.md` y `NEEDS-VALIDATION.md`.

---

## 0. Lo primero: esta corrida sigue sin cubrir todo

La segunda ola cerró el hueco que la primera había medido —los 47 controllers y 54 servicios que
nadie había abierto, entre ellos `JournalTodayController`, el diario íntimo que alimenta el Espejo de
Sombra—, y ese barrido volvió mayormente tranquilo: el patrón de guards en `habits`, `rocks`,
`onboarding`, `academy`, `points` y `calendar` es uniforme y correcto, con un solo hueco de severidad
baja.

Pero el crítico de la segunda ola devolvió **cinco unidades nuevas**, cada una con archivo y línea
que él mismo abrió. Siguen en estado `deferred`, que no es "se miró y está bien" sino "no se miró".
Están en la sección 4. Cerrarlas es una tercera ola.

En números: **25 unidades de cobertura, 20 cerradas con evidencia y 5 diferidas**; 504 rutas únicas
revisadas y 152 comprobaciones registradas.

---

## 1. Lo que se revalidó de septiembre

| | Resultado |
|---|---|
| **E-196** token de reset contra el contador de intentos | **Aguanta.** Se enumeró todo el espacio de claves `reset-password:` y ninguna otra familia es direccionable |
| **E-197** `scope` del cliente en el prompt | **Aguanta** para `{ambito}`. `{contexto}` no recibió el mismo trato — ver endurecimiento |
| **E-198** `cerrarTodas` y el WebSocket | **Aguanta la mitad de la sesión. La otra mitad sigue abierta** — es un hallazgo confirmado |
| **E-199** SUBSCRIBE contra la proyección | **Aguanta** donde ya se preguntaba |
| **E-200** `fullName` en el push | **Aguanta**, y el saneo está en el borde del dominio |

Y tres hipótesis del reconocimiento anterior quedaron **descartadas**, que también es resultado:
`/actuator` está bien cerrado; el límite de tasa **no** falla abierto con Redis caído (muere en 500,
que es cerrado); y los `@Scheduled` son 20, no 21, con 18 protegidos y los 2 restantes justificados.

---

## 2. Lo que hay que arreglar

**21 hallazgos confirmados: 4 altos, 11 medios, 6 bajos.** Ninguno es un fallo de diseño, y la
segunda ola no cambió ese diagnóstico: lo confirmó desde ángulos nuevos. El patrón se repite —
**una regla que se aplica en veintiún sitios y falta en el veintidosavo**— y la segunda ola le agregó
una variante propia: una decisión de autorización **copiada en cuatro archivos en vez de compartida**,
y el caso de uso que existe justamente para no duplicarla tiene un solo consumidor.

### 2.1 Alta — el dominio del correo se usa como nombre JNDI

`POST /api/v1/account-requests/verify-email` es público y su cuerpo es `{"email":"..."}`. El DTO
renuncia a `@Email` a propósito —para poder *responder* "formato inválido" en vez de rebotar con
400—, así que la única reja es el regex de `Email`, que prohíbe la arroba y los espacios pero **deja
pasar `:` y `/`**. La parte de dominio baja entera hasta `DnsResolverMxAdapter` y ahí se entrega como
nombre a `InitialDirContext#getAttributes`.

Ese método no consulta DNS primero: mira si el nombre empieza con un esquema de URL y, si lo hay,
encamina la llamada al proveedor JNDI de ese esquema. Con `a@ldap://host:1389/x` el backend **abre
una conexión TCP al host y puerto que puso el atacante**. Sirve para sacar tráfico de la red del
backend hacia afuera y para sondear qué destinos internos responden desde la EC2 — `127.0.0.1`, el
rango privado de la VPC o `169.254.169.254` pasan el regex porque llevan puntos.

**No es ejecución de código**, y eso está comprobado y no supuesto: se leyó el `src.zip` del JDK 25 y
`c_getAttributes` devuelve los atributos crudos sin pasar por `Obj.decodeObject`. Lo que sí hay, y es
peor de lo que parecía: **el tiempo máximo de espera de 3 s es una propiedad del proveedor DNS; sobre
el proveedor LDAP no hay ninguna**. Contra un socket que acepta y no contesta, la petición no
responde nunca.

El arreglo son dos líneas en el adaptador: exigir que el dominio sea un nombre de host antes de tocar
JNDI, y agregar `com.sun.jndi.ldap.connect.timeout` y `.read.timeout` al entorno.

### 2.2 Alta — bajarle el rol a un administrador no le cierra ningún chat de soporte

El chat de soporte de cada aprendiz está definido por rol: el aprendiz y los ADMIN/ALCHEMIST activos,
nadie más. Esa regla la confirmaste tú y está escrita dos veces. Cuando a alguien lo ascienden, el
bus de eventos lo mete en **todas** las conversaciones de soporte que existen; cuando lo bajan, nada
lo saca. El único consumidor del evento tira `rolAnterior` y `rolNuevo` —que viajan justamente para
esto— y llama a `incorporar`, que solo sabe sumar.

Resultado: un ex administrador degradado a MENTOR o incluso a TRAINEE conserva el chat privado de
cada aprendiz con la administración, con historial completo, en vivo por WebSocket, y con capacidad
de escribir presentándose como parte de la administración. **El producto no ofrece ninguna forma de
sacarlo**: el único quitar exige que la propia persona pulse salir.

Dos cosas que la verificación final agregó y que cambian el arreglo:

- El acceso no se limita a las conversaciones que existían al ascenderlo: **también se le escribe una
  fila cada vez que nace el soporte de un aprendiz nuevo**, mientras siga siendo staff.
- La rama de autorización que concede con la fila vieja **está escrita cuatro veces**, no dos:
  `MensajeService`, `ConversacionService` (marcar leído), `PresenciaService` (presencia) y
  `AutorizacionDeConversacionService` (suscripción STOMP). Arreglar solo dos deja la presencia
  repartiendo el aviso de conexión al topic de cada chat de soporte.

La causa está en una decisión escrita del propio módulo, **CH-11** (*"nada reconcilia participantes,
nunca"*), que está justificada por la regla 4 —no deshacer la salida voluntaria de quien se fue— pero
cubre por extensión un caso distinto al que la justifica. El arreglo es una excepción acotada, no
derogar CH-11.

### 2.3 Las otras dos altas siguen igual

`ConfiguracionMentoriaService` sin guard de rol (un MENTOR reconfigura la cohorte entera) y la purga
de cuentas que borra la fila y nada más (evidencias, firmas del Pacto de Sangre y objetos de S3
sobreviven a la baja). Están detalladas en el informe anterior y en `FINDINGS-DETAIL.md`.

### 2.4 Media — "borrado físico" del Muro que no toca S3

`DELETE /api/v1/wall/{id}/permanent` es lo que el propio código llama *"Borrado físico"*. Son tres
líneas y ninguna llama a `AlmacenamientoPort.borrar`. El detalle que cierra el caso: `requireOculta`
**ya devuelve el agregado con las claves hidratadas**, y `eliminarPermanente` tira ese retorno al
piso una línea antes de que la cascada de `medias_publicacion` se lleve `ruta_storage`. El backend
tiene la clave en la mano, la descarta, y después no puede limpiar el bucket ni queriendo.

**Ojo con el arreglo, porque dos versiones seguidas destruían datos.** El borrado tiene que pasar dos
filtros, no uno:

1. **Prefijo `muro/`** — `publicarDesdeEvidencia` guarda claves `rocas/<autorId>/<rocaId>` en
   `medias_publicacion`, y ese objeto sigue siendo la evidencia del aprendiz en `evidencias`.
2. **Sin referencias vivas afuera** — compartir una publicación en el chat **no copia el archivo**:
   persiste la misma clave en `mensajes.media_ruta`, y el borrado del chat es un tombstone. Borrar
   por prefijo dejaría en 404 permanente esa foto en cada conversación privada donde se compartió.

### 2.5 Media — el listener del post diario paga los puntos dos veces

`PostDiarioComunidadHabitoService` declara que el cerrojo pesimista serializa los dos caminos y que
*"nunca se paga dos veces"*. No se cumple. La transacción del oyente **ya cargó el registro sin
cerrojo** unos renglones antes, y en Hibernate 7.4.5 una consulta con `@Lock` sobre una entidad ya
gestionada no la rehidrata: `upgradeLockMode` solo escribe el `LockMode` en el `EntityEntry` y las
columnas frescas se descartan. El dominio decide sobre el estado viejo y acredita otra vez.

Efecto extra que no estaba visto: el comando del oyente manda `respuestaTexto` y
`calificacionProductividad` **nulos**, así que la actualización perdida también borra lo que el
aprendiz había escrito.

### 2.6 El resto

Las otras nueve medias y las seis bajas están en `FINDINGS-DETAIL.md` y en `findings.json`. Las tres
bajas nuevas de esta ola: `/diagnostico` del panel autentica pero no autoriza; el horario semanal de
un hábito personal ajeno se lee con solo poner su id en la ruta; y la superficie de error de toda la
API escribe en el log el texto crudo de cualquier excepción de dominio — incluido el correo y el
nombre completo de un tercero, que es exactamente lo que `CLAUDE.MD` §5.4.9 prohíbe y donde nombra a
ese mismo sumidero.

---

## 3. Lo que depende de que mires tú

**Nueve registros** que la fuente no puede cerrar porque el hecho decisivo vive fuera del
repositorio. No tienen severidad a propósito: ponerles una sería inventarla. Cada uno trae en
`NEEDS-VALIDATION.md` el bloqueador exacto y un plan que **observa** en vez de atacar.

**Tres se cierran con la misma observación de cinco minutos**, y es la de mejor rendimiento de toda
la lista: si el Redis productivo exige AUTH y si alguien que no sea el contenedor `backend` alcanza
el 6379. De eso dependen el fanout del chat (quien pueda publicar fabrica mensajes atribuidos a quien
quiera, **que nadie puede desmentir después porque el reenvío no persiste nada**), las credenciales
en claro, y el segundo consumidor de Pub/Sub.

Las otras que conviene priorizar:

- **Facebook**: el control que exige correo verificado se alimenta, en Facebook, de un booleano que
  fabrica nuestro propio adaptador (*"el campo email vino no vacío"*). Google y Apple lo sacan de un
  claim firmado. Se cierra cargando un correo sin verificar en una cuenta de prueba de Meta y mirando
  si `/me?fields=email` lo devuelve igual. `MODULO_AUTH.md` §6.5 ya lo declara como supuesto.
- **El panel de administración**: la cookie de sesión de ADMIN queda en `127.0.0.1` sin el prefijo
  `__Host-`. Lo que de verdad depende de esa degradación no es el reparto entre puertos —eso pasaría
  igual— sino que **la cookie de CSRF se puede ensombrecer** con un `Path` más específico desde
  cualquier proceso que escuche en loopback. Se mira abriendo las herramientas de desarrollo.

---

## 4. Lo que quedó sin cazar

Cinco unidades, todas respaldadas por fuente que el crítico abrió:

1. **La superficie REST propia del chat** — `GET /api/v1/chat/members`, el alta de un mensaje directo,
   el roster de la conversación GLOBAL. Se cruzaron los archivos revisados contra los 90 controllers
   y `ConversacionController`, `MiembroController` y `PresenciaController` **no aparecen en ninguna
   unidad**. Ahí vive el directorio del que sale con quién puede hablar cualquier aprendiz, y el
   propio `@RequiresPermission` documenta que ese directorio no exige ser participante del grupo.
2. **La maquinaria que mueve gente entre células** — `RotacionService`, `TrasladoService` y
   `AvisosDeVencimientoService`, 544 líneas de lógica de pertenencia. Los schedulers se abrieron pero
   solo para mirar cerrojos. Lo que lo vuelve urgente: su insumo es la política de mentoría, que la
   escribe el servicio **sin guard de rol** del punto 2.3.
3. **Censo de columnas de ruta de objeto del esquema** — empezando por las tres que hoy no tienen
   lector: el audio del diario nocturno, la evidencia de salida del Santuario y el adjunto de guía de
   hábito. Las tres aceptan la clave que manda el cliente sin validar prefijo. Hoy no hay fuga porque
   nadie las firma; son agujeros armados esperando su lector.
4. **El segundo consumidor de Pub/Sub de Redis** — Spring Session con `repository-type: indexed`. Al
   arrancar, la acción por defecto hace `CONFIG SET notify-keyspace-events` sobre el **servidor
   entero**, y en ese mismo Redis el nombre de la clave *es* el token (`reset-password:<token>`).
5. **Cota de tamaño de petición** — no hay ni una clave de tope declarada en `application.yaml`, y de
   118 DTO de request solo 25 mencionan `@Size`.

---

## 5. Endurecimiento que conviene mirar

Las dos olas dejaron **127 notas** (65 la primera, 62 la segunda). Las de mejor relación entre lo que
cuestan y lo que evitan:

- **Dos pruebas bloquean arreglos.** `ConsultaEmailServiceTest.noConsumeCuota` afirma que
  `registrarIntento` nunca se invoca; y `AccountRequestServiceTest.eliminarAceptaAdminActivo` corre
  sobre una solicitud PENDING y fija como correcto el borrado que deja viva la cuenta huérfana.
- **La prueba de humo del panel no puede ver su propio fallo**: su backend de mentira responde
  `role: 'ADMIN'` para cualquier sesión que reconoce, justo al revés que el real.
- **El `traceId` en el MDC no está implementado.** `CLAUDE.MD` §5.4.9 lo llama "la regla más
  importante de esta subsección", y no hay ni un `MDC.put` en `src/main/java` ni `micrometer-tracing`
  en el `pom.xml`. Cualquier arreglo de logging que se apoye en él hay que escribirlo de otra forma.
- **Cuatro javadoc afirman lo contrario de lo que hace el código.** Los dos peores son de esta ola:
  `DeleteAccountRequestUseCase` razona sobre la columna equivocada para concluir que "borrar la
  solicitud no deja huérfano a nadie", y `ConversacionSoporteService` afirma que a quien se fue por su
  cuenta no se lo vuelve a meter — cuando el cambio de rol hace exactamente eso.
- **`V55` no tiene explicación en ninguna parte** del repositorio, a diferencia de `V7`, que sí la
  tiene documentada. Un hueco sin explicar invita a rellenarlo, y Flyway rechaza el arranque si
  alguien lo hace.
- **El `README` del panel está desactualizado**: afirma que las rutas de altas siguen en `permitAll()`
  y ya no lo están.

---

## 6. Lo que la auditoría encontró bien hecho

Vale decirlo porque orienta dónde **no** hace falta gastar esfuerzo. Además de lo que ya estaba
(el `actorId` del agente de IA fijado por el servidor, la verificación OIDC en los tres proveedores,
`SecureRandom` en los cinco generadores, el filtro del RAG que falla cerrado), esta ola sumó:

- **El grafo de cuentas del login social está bien construido.** La identidad se resuelve siempre por
  el par (proveedor, sujeto) y nunca por correo; una identidad social que trae el correo de una cuenta
  ajena recibe 409 sin tocar nada; el token del segundo paso es de un solo uso con `GETDEL` atómico. El
  cazador incluso refutó un ataque que parecía plausible: `UriComponentsBuilder.encode()` escapa `=`,
  `&` y `#`, así que no hay inyección de parámetros contra la Graph API.
- **Ningún cliente puede registrar una suscripción STOMP con comodín**: el interceptor exige
  `UUID.fromString` y ningún comodín sobrevive a eso.
- **La sesión de Redis no transporta rol ni estado**: el rol se vuelve a leer de Postgres en cada
  petición. Quien escriba una sesión se hace pasar por un usuario, pero no se inventa un rol.
- **Las siete notificaciones deduplican bien**, con clave no nula y determinista por episodio.
- **El patrón de guards del programa personal es uniforme**: los 44 controllers de `habits`, `rocks`,
  `onboarding`, `academy`, `points` y `calendar` atan la lectura y la escritura al actor de la sesión,
  con un solo hueco (el 2.6) en 114 archivos revisados.

---

## 7. Cómo se corrió, para que se pueda juzgar

Dos olas: 11 unidades y 9 unidades, cada una con su crítico de cobertura, sus verificadores frescos
de fase 3 y sus verificadores finales de fase 5. **88 agentes** sobre un presupuesto de 110. Los dos
validadores del método pasan: 31 registros y 25 unidades de cobertura.

**El árbol se movió durante la corrida.** A tu pedido se corrigió el informe del 2026-09-18 entre la
fase 3 y la fase 5 de la primera ola (voseo y la contradicción sobre la prefix list de CloudFront).
Un verificador detectó que eso invalidaba un bloqueador citado en un registro y lo retiró. Queda
declarado porque una auditoría que se corrige a sí misma sin decirlo no es auditable.

**Qué tan adversarial fue la verificación.** La primera ola cerró con **cero rechazos sobre 19
candidatos**, lo cual era en sí una señal de que a los verificadores les faltaba mordida. La segunda
corrigió eso:

- De 12 candidatos, **uno fue rechazado** y **uno subió** de `needs_validation` a `confirmed`.
- El rechazo no fue por falta de evidencia —los hechos de fuente eran todos ciertos— sino porque **el
  rastro no tenía forma de vulnerabilidad**: el punto de entrada era una sentencia DDL de la propia
  migración y el sumidero una línea de prosa en un markdown, sin ningún principal de menor confianza
  del otro lado. Ese rechazo lo revisó después un agente fresco con una lista de comprobación propia,
  por si escondía un hallazgo más chico, y se sostuvo.
- En la fase 5, **dos registros volvieron con corrección, y los dos porque la remediación propuesta
  destruía datos**: uno borraba del bucket las fotos compartidas en chats privados, el otro cubría la
  mitad de la superficie. Ninguno movió veredicto ni severidad, y los dos se comprobaron en fuente
  antes de aplicarse.

Un rechazo, una promoción y dos remediaciones corregidas sobre 12 candidatos es el rango que uno
espera de una verificación que de verdad intenta refutar.
