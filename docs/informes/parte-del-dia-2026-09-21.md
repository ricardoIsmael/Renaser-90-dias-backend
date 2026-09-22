# Parte del día — 2026-09-21

**Lo que tienes que saber en diez renglones:** se cerró la auditoría de seguridad (seis fases, dos
olas) y se arreglaron **los 4 hallazgos de severidad alta y los 11 medios**. Todo eso **ya está
corriendo en producción**: el backend despliega solo al empujar a `master`, así que cada push del
día fue un despliegue. El frontend también se subió, con seis cambios de interfaz que **nadie ha
visto correr en un teléfono** — dos de ellos necesitan que los pruebes tú.

Nada te obliga a levantarte de madrugada. El único punto con reloj —un fallo que yo mismo
introduje en la purga de cuentas por la mañana y que podía borrar fotos de terceros a las 04:15
UTC— quedó cerrado con unas cinco horas de margen.

Lo que sigue abierto, en una línea: 6 hallazgos de severidad baja sin tocar, 9 que dependen de que
observes cosas que solo tú puedes ver, y 5 fronteras del sistema que **nadie ha mirado nunca**.

---

## 1. Lo que se cerró y ya está en producción

**39 commits en el backend**, de `b5a8102` a `026cc97`.

### Los cuatro de severidad alta

| | Qué pasaba |
|---|---|
| **JNDI por el dominio del correo** | Un anónimo, sin cuenta y con un solo POST, hacía que el backend abriera una conexión TCP al host y puerto que él eligiera, desde dentro de tu VPC. No era ejecución de código —se comprobó leyendo el `src.zip` del JDK— pero sí baliza saliente y sondeo de lo que vive adentro. Y sobre el proveedor LDAP no había ningún tiempo máximo de espera: contra un socket que acepta y calla, la petición no respondía nunca. |
| **Política de mentoría sin guard** | Un MENTOR reconfiguraba la cohorte entera. |
| **Degradación de rol y chats de soporte** | A quien dejaba de ser administrador le quedaban abiertos los chats privados de **todos** los aprendices, con historial, en vivo y pudiendo escribir. Y el producto no ofrecía ninguna forma de sacarlo: el único quitar exigía que la propia persona pulsara salir. |
| **Purga de cuentas** | Borraba la fila y nada más: evidencias, firmas del Pacto y objetos de S3 sobrevivían a la baja. |

### Los once medios

Borrado del Muro que no tocaba S3 · el oyente que pagaba los puntos dos veces y de paso borraba lo
que el aprendiz había escrito · push que seguía llegando a cuentas suspendidas · guard sin periodo
de grupo · media del Muro reutilizable en el chat · suscripción WebSocket que no revalidaba · el
límite por correo que se podía volver contra la víctima · clave de Redis sin canonizar · endpoint
público que salía a la red sin gastar cupo · límites por IP que un cliente IPv6 evadía
gratis · purga que dejaba viva la solicitud de alta.

### Cuatro tareas extra, ya de madrugada

- **La purga y `medias_publicacion`** — el fallo que yo introduje por la mañana. Ver §4.
- **`VerificacionEmailService`** — el mismo contador que se podía volver contra la víctima. No era
  teórico: la bitácora registra **tres aprendices destrabadas a mano** por eso. Buena noticia: la
  clave vieja queda huérfana con TTL de una hora, así que quien estuviera bloqueado se destrabó
  solo al desplegar.
- **Clase Diaria y Pastilla Renacer** — el mismo cerrojo tardío, en dos servicios hermanos.
- **El voseo**, en los dos repos. En el backend eran 6 sitios de producción del *"No sos
  participante"* —que sí le llegaba al usuario, porque la app muestra el mensaje del servidor tal
  cual— más un `preguntale` **dentro del prompt de sistema de Renasia**. Ese último importaba: no
  era un texto suelto, era la instrucción que recibe el modelo, y decía *"responde… y preguntale"*,
  tuteo y voseo en la misma oración.

### Frontend — 16 commits, subido, sin probar en dispositivo

Rueda de 1 a 60 para el recordatorio · teclado que ya no tapa los campos del registro · intro de
Objetivos acortada con "más detalles" · "roca" pasa a llamarse **objetivo semanal** · Grupo y
Miembros unificados en **Tribu** · el objetivo mensual se autocalcula · 39 formas de voseo en 22
archivos.

---

## 2. Los números, sin maquillar

| | |
|---|---|
| Hallazgos | **31** — 21 confirmados, 9 `needs_validation`, 1 rechazado |
| Severidad de los confirmados | 4 altos, 11 medios, 6 bajos |
| Arreglados hoy | los 4 altos y los 11 medios. **Los 6 bajos no** |
| Cobertura | 25 unidades: 20 cerradas con evidencia, **5 diferidas** |
| Pruebas | backend **3178**, frontend **341**, todas en verde |
| Agentes | 88 en la auditoría, 19 más en los arreglos |

**La cobertura es parcial y está declarada así a propósito.** `deferred` significa "nadie la miró",
no "está bien". Si alguien pregunta si el directorio de personas del chat está auditado, la
respuesta honesta es **no**.

---

## 3. Lo que sigue abierto, por orden

### Depende de ti, no de un agente

**Lo de mejor rendimiento de toda la lista, y son cinco minutos:** entrar a la EC2 y mirar si el
Redis exige `AUTH` y si alguien que no sea el contenedor `backend` alcanza el 6379. **Tres**
hallazgos distintos se cierran con esa sola observación — entre ellos si el chat en vivo se puede
falsificar con mensajes que después nadie puede desmentir, porque el reenvío no escribe nada.

Después:

- **Facebook**: el control que exige correo verificado se alimenta, solo en Facebook, de un booleano
  que fabrica nuestro propio adaptador. Se cierra cargando un correo sin verificar en una cuenta de
  prueba de Meta y mirando si `/me?fields=email` lo devuelve igual.
- **El panel de administración**: la cookie de ADMIN queda en `127.0.0.1` sin el prefijo `__Host-`.
  Se mira abriendo las herramientas de desarrollo.
- **Probar en tu Xiaomi**: el gesto del dedo en la firma del Pacto y el teclado del registro.

### Decisiones de producto, no bugs

- **Anonimizar los testimonios al purgar.** Hoy la purga deja sin borrar la foto y el avatar que un
  testimonio referencia —a propósito, para no romperle la vitrina a nadie—, así que la vitrina
  sigue sirviendo material de un ex usuario con su nombre al lado.
- **Guardar semanas incompletas.** Ya no te obliga a llenar los cuatro pasos para navegarlos, pero
  **guardar sigue exigiendo los tres ejes completos**: eso lo impone el backend y aflojarlo es
  tocar cuatro puntos del Java.
- **Los tres contadores de Comunidad** ("12 conversaciones · 3 eventos · 2 mentorías") son valores
  fijos: ningún endpoint los calcula.

### Trabajo pendiente

1. **6 hallazgos de severidad baja**, sin tocar. El más feo es que la superficie de error de toda la
   API escribe en el log el texto crudo de cualquier excepción de dominio — incluido el correo y el
   nombre completo de un tercero, que es justo lo que `CLAUDE.MD` §5.4.9 prohíbe nombrando ese
   mismo sumidero.
2. **`EspirituService.entregar`** — el mismo patrón del cerrojo una capa más arriba, y ahí el
   repositorio de espíritu **no tiene ni un `@Lock`**. No es dinero, pero rompe el 409 que el
   contrato promete.
3. **El bug de la coma en el Mapa**: `"78,5"` se lee como **78,5** para la Roca Maestra y como
   **785** para la validación y los hitos.
4. **La ola 3 de la auditoría** — 5 unidades. Las dos que más pesan: la superficie REST propia del
   chat (el directorio del que sale con quién puede hablar cualquier aprendiz) y la maquinaria que
   mueve gente entre células, cuyo insumo lo escribe justamente el servicio que la auditoría
   confirmó sin guard de rol.

---

## 4. Errores del proceso

Esto no es autocrítica decorativa: son los que costaron tiempo o llegaron a producción.

**Tres de los cuatro parches que la auditoría proponía para los hallazgos altos estaban mal**, y dos
**habrían destruido datos**: uno borraba de S3 las fotos compartidas en chats privados de terceros,
otro le borraba las filas a un administrador legítimo por una reentrega tardía del outbox. Un
tercero ni siquiera compilaba. Los cazó la verificación, no quien los escribió — que es
exactamente para lo que la verificación existe, pero conviene no olvidar que **un parche escrito
por quien nunca compiló es una propuesta, no una solución**.

**Introduje un fallo en producción y lo descubrió otro agente.** Mi arreglo de la purga de cuentas
olvidaba `medias_publicacion` en la consulta de referencias, así que podía borrar de S3 la foto que
la publicación de otra persona usaba. Estuvo vivo unas horas. Lo cerré antes del cron de las 04:15.

**Empujé el frontend sin que `tsc` hubiera pasado.** El encadenado de mi comando no ató la
verificación al push. Verifiqué después y estaba bien, pero verificar después de subir no es
verificar.

**Edité un archivo que un agente todavía tenía abierto.** Quité dos ramas de una consulta, el agente
lo leyó como corrupción y las restauró, y quedó un commit contradictorio que hubo que rehacer.

**Un `git add -A` sin mirar** metió al repositorio del frontend un enlace `node_modules` que se
apuntaba a sí mismo y dos archivos de skills locales. Llegó a `origin`: cualquiera que clonara se
llevaba las dependencias rotas. Se limpió al día siguiente en `a72214b`. La causa de fondo:
`.gitignore` decía `node_modules/` con barra, que no cubre un enlace con ese nombre. Ya está
corregido.

**Dos hipótesis mías sobre bugs del frontend eran falsas**, y los agentes las descartaron con
evidencia en vez de obedecerlas. La del teclado y la del onboarding. En el segundo caso la causa
real era mucho peor de lo que yo suponía: ver §5.

---

## 5. Lo que se descubrió de paso, y que nadie buscaba

**El Pacto que se ve desde `Yo` era una maqueta.** No es que el lienzo de firma estuviera roto:
nunca existió en esa pantalla. El recuadro punteado era un texto con el nombre del perfil en
cursiva, y debajo el rótulo *"FIRMA DIGITAL REGISTRADA & SELLADA"* escrito a mano, fijo, afirmando
un sellado que no ocurría. El botón solo abría un aviso. Venía así desde el commit de andamiaje.
Ahora firma y persiste de verdad.

**El progreso del onboarding no era lento: nunca se releía.** `Yo` es una pestaña que se monta una
vez y no se desmonta, y el hook leía una sola vez. La barra mostraba para siempre la foto del
arranque; solo se actualizaba cerrando y reabriendo la app.

**La mitad de salida del arreglo E-198 nunca funcionó en producción.** Leía las cabeceras como STOMP
y el broker arma cada copia por suscriptor con otro tipo, así que devolvía `null` justo para los
mensajes que debía filtrar. La única prueba pasaba porque fabricaba el frame a mano.

**Compartir al chat una publicación de "Completé mi Roca" estaba roto desde el 2026-09-18**, y lo
descubrió un agente ejecutando, no leyendo.

**Pruebas que bloquean arreglos**: dos fijan como correcto el comportamiento defectuoso, y la prueba
de humo del panel de administración **no puede ver su propio fallo** porque su backend de mentira
responde `role: 'ADMIN'` para cualquier sesión.

**El `traceId` en el MDC que `CLAUDE.MD` llama "la regla más importante de esta subsección" no está
implementado.** Ni un `MDC.put` en todo `src/main/java`.

---

## 6. Una recomendación

**Que `master` despliegue directo a producción es un riesgo estructural.** Hoy salió bien porque
detrás de cada cambio hubo 3178 pruebas y verificación doble, pero un push apurado llega a la EC2
sin que nadie lo mire — y hoy mismo, uno de los míos llegó con un fallo. Una rama con PR no te
cuesta casi nada y corta esa clase de error de raíz.

---

*Artefactos de la auditoría en `~/security-audit-skill/Renaser-90-dias-backend/run-1/`.
Backend en `026cc97`, frontend en `a72214b`.*
