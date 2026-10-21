# E2E de la spec 005: el líder de mentores ve el cumplimiento de cada persona por grupo

**Fecha:** lunes 19/10/2026, de las 13:50 a las 15:30 (hora de Lima). **Todo local**: nunca producción ni AWS.

## Entorno

| Pieza | Valor |
|---|---|
| Backend | rama `005-lider-cumplimiento-por-persona` (worktree `lider-cumplimiento`), jar de la rama, puerto **8095**, `TZ=UTC`, correo `noop`, IA `noop` (no manda nada afuera) |
| Base | `renaser_lider005`, clon de `renaser_habitos_rol` (V96) migrada por el propio arranque hasta **V109** (V97 a V102 y V109) |
| Fotos | RustFS local (`renaser-fotos`, bucket `renaser-local`, `adb reverse tcp:9002`), nunca el bucket de AWS |
| App | worktree `lider-cumplimiento`, JavaScript servido por Metro con `EXPO_PUBLIC_API_URL=http://10.0.2.2:8095`; **sin build nuevo** (ver abajo) |
| Emulador | `Pixel_6` (Android 17), sesiones abiertas por `adb` como hace `.maestro/correr.sh` |
| Cuentas | las del líder y del admin de `~/.config/renaser-e2e/habitos-por-rol.json`; las nuevas `e2e-lid-*@renaser.test` con la misma clave. Ninguna clave en el repo |

**Sobre el APK.** `git diff origin/master..HEAD -- package.json app.json android ios modules plugins` sale vacío: la 005 solo cambia
JavaScript y no suma módulos nativos. Se usó el build debug que ya estaba instalado en el emulador (`com.renaser.app`, debuggable,
actualizado el 12/10, con Rive y los módulos de master); carga el JS desde Metro. No se compiló nada (decisión del dueño).

### Datos sembrados (`siembra-005.sql`, más `fotos-semanales.sql`)

Fecha de siembra: 19/10 (Lima). Semanas de sábado a viernes: W-1 = 3 a 9/10, **W0 = 10 a 16/10 (la última cerrada)**, W+0 = 17 a 23/10 (en curso).

| Grupo | Mentor / guía | Personas |
|---|---|---|
| **Fénix 005** | Luisa Rojas Mentora (MENTOR, con programa propio: sale «No se mide») | Rocío Roja (rojo 47,6 %), Pruebas QA Cuenta (amarillo 73,9 %, la «cuenta de prueba»), **Ana Pérez** (amarillo 74,3 %, la de las evidencias), Inés Inicio (sin datos: Día 1 el 17/10), Valeria Verde (verde 95,1 %), Cecilia Cerrada (**cuenta cerrada**: suspendida con baja solicitada) |
| **Loto 005** | Pedro Mentor Loto (sin programa propio) | Beto Paz (rojo), Carla Cielo (verde) |
| **Cedro 005** | Gabriela Guía Cedro (**GUIA_APRENDIZ**, con programa propio) | Hugo Cedro (amarillo 78,4 %) |
| Recepción | «Recepción de bienvenida (prueba)», `tipo = RECEPCION`, ya estaba en el clon | 45 personas |

Ana (Día 1 el sábado 3/10, así que su primera semana es la del 3 al 9) tiene 855 registros de hábito en total entre todas, con:

- un **hábito con foto y «¿Qué sentiste?»** (Ritual de la mañana), más Agua tibia y Jugo verde con foto: 39 fotos reales subidas al RustFS local;
- la **Pastilla Renacer** (respuesta escrita) y la **Audioterapia semanal** (texto) los domingos;
- el **post diario** publicado en el Muro (12 publicaciones, la del 14/10 con foto);
- una **acción del día con foto** (14/10, «Llamar a 3 clientes»), una sin cumplir (15/10) y una de hoy;
- un **día en pausa** (martes 13/10).

Todo se creó por SQL sobre mi clon (no hay API que escriba días pasados); los cierres de hoy y de las observaciones, el chat y los avisos
se hicieron por la app y la API reales. La «foto del cierre semanal» (`semaforo_semanas`) se sembró como lo deja el barrido del fin
de semana. Los dos scripts SQL están en `e2e/siembra-005.sql` y `e2e/fotos-semanales.sql` (sin contraseñas: el hash se copia de una cuenta del clon).

## Los 13 escenarios

> **Actualizado 2026-10-19 (pasada final).** Esta tabla es el registro de la primera pasada, con la ventana anterior a E-629 (d). Las capturas de
> los escenarios 1, 2, 5, 11 y 12 (`01*`, `02a`, `05*`, `11*`, `12*`) se reemplazaron por las de la pasada final (sección del final): los nombres
> `01a`, `01b`, `01c`, `01d`, `02a`, `05a`–`05c` de esta tabla ya no existen.

Capturas en `e2e/` (`NN-*.png`, 540 a 1000 px de ancho). Las pruebas por `curl` dejan su salida en `e2e/*.txt`.

| # | Escenario | Resultado | Evidencia y notas |
|---|---|---|---|
| 1 | Líder: Hoy → Semáforo por grupos → grupo | **Pasa**, con dos hallazgos. Lista de rojo a verde (Rocío, Pruebas QA, Ana, Inés sin datos, Valeria) y **Luisa al final con «No se mide en el semáforo»**; en Cedro aparece la guía («Guía de aprendiz · No se mide…»). Cecilia (cuenta cerrada) no aparece. **(a)** El encabezado decía «Semana todavía sin cerrar» en una semana ya cerrada: **E-629, arreglado** (ver abajo). **(b)** «Pruebas QA Cuenta» sí aparece: en esta rama `CuentasDePruebaFinder` devuelve siempre vacío, a la espera de la fase 2 de Soporte TI (T046). **SC-006 no se puede verificar en esta rama**; el filtro está cubierto por `GrupoDelLiderServiceTest`. | `01a`, `01b` (antes del arreglo), `01c` (Cedro con la guía), `01d` (Fénix después del arreglo: «Semana cerrada»); `escenario1-api.txt` (404 en recepción, sin mentor, persona de otro grupo y cuenta cerrada; 400 con un día que no es viernes y con semana futura; 403 a aprendiz y mentor) |
| 2 | Tocar a la aprendiz | **Pasa.** Semana «Del sábado 10 al viernes 16 de octubre», 74,3 %, siete días, y el martes 13 dice **«En pausa»**, sin ✗. | `02a` |
| 3 | Abrir cada evidencia | **Pasa.** Foto del Agua tibia; foto del Ritual con «Lo que escribió: Tranquila y enfocada»; texto de la Audioterapia; respuesta de la Pastilla; publicación del Muro del post diario; foto de la acción «Llamar a 3 clientes». La Audioterapia mostraba **el mismo texto dos veces** (la app lo manda como evidencia y como respuesta): **E-629 (b), arreglado**. No se probaron video ni audio (no hay archivos de esos tipos en los datos; los cubren las pruebas de jest). | `03a` a `03f` (la `03d` muestra el texto repetido, antes del arreglo) |
| 4 | Comparar con Administración → ficha | **Pasa.** Por API: las 24 obligaciones de lunes a viernes (12 a 16/10) de Ana tienen **el mismo estado, la misma entrega y el mismo `evidenciaId`** en la semana del líder y en `…/weekly-progress` de Administración. En la app, la ficha del admin muestra los mismos porcentajes por día (67, —, 86, 57, 67, 83, 86) y las mismas semanas cerradas (81,3 y 74,3). | `escenario4-api.txt`; `13a` (ficha del admin) |
| 5 | Semana anterior y volver | **Pasa.** ‹ lleva a la del 3 al 9 (la primera de Ana: la flecha queda apagada); «Volver a esta semana» lleva a «Esta semana» (17 a 23/10) con › apagada, «hoy, todavía sin cerrar» y los días que vienen como «Este día todavía no llega». | `05a`, `05b`, `05c` |
| 6 | «Avisar a su mentor» con alerta por chat | **Pasa.** Observación `ALERTA` de la persona (Ana) y la semana (`semana_desde = 17/10`) con `enviada_por_chat`; Luisa recibe en su chat: «Ana Pérez · semana del sábado 17 al viernes 23 de octubre / Ana lleva dos dias sin el Jugo Verde». Una sola fila. | `06a`; consulta a `GET /chat/conversations` como Luisa |
| 7 | «Escribirle» | **Pasa.** Abre el 1 a 1 con Ana, vacío («Todavía no hay mensajes. ¡Escribe el primero!»). En la base, 0 mensajes de la conversación. | `07` |
| 8 | Escrituras del líder por `curl` | **Pasa.** Completar un registro ajeno, aprobar y anular una evidencia, cambiar el mentor de un grupo, pasar y sacar a Ana de su grupo y cambiarle el rol: **403 en las siete**; la base no cambió (registro `PENDIENTE`, evidencia `VALIDA`, rol `APRENDIZ`, grupo igual, mentor igual). Control: el líder sí lee `GET /evidence/{id}` y `/url` (200). | `escenario8.txt` |
| 9 | Resumen de las 20:00 al mentor | **Pasa.** A Luisa se le puso la zona `Europe/London` (a las 19:15 UTC son las 20:15 allá) y el barrido corrió cada minuto en esa corrida: **un solo aviso**, «Tu líder revisó a tu grupo / Hoy revisó a Ana y Rocío» (Ana, con seis consultas, aparece una vez), con ruta `/mentor/groups/{Fénix}/semaforo`. Corridas siguientes (y la real de las 19:40 UTC con el cron de producción): el evento sale otra vez pero la clave del día lo deduplica; **una fila**. Pedro (sin consultas del líder) y Gabriela (la consulta de Hugo fue a las 14:30 Lima, fuera de su ventana) no reciben nada, y la consulta del ADMIN sobre Beto no cuenta. | `escenario9.txt`. No se movió el reloj del sistema: se movió la zona del mentor, que es el dato que decide la ventana. El cron por minuto se pasó como propiedad solo en esa corrida |
| 10 | Admin abre fichas desde esta vista | **Pasa por API, con una observación.** El ADMIN abrió la ficha de Beto (grupo de Pedro): queda en `consultas_de_ficha` con `rol_consultor = ADMIN`, y Pedro **no** recibe aviso. **En la app el admin no tiene una entrada a esta vista**: su Semáforo de Administración conserva su recorrido de siempre (decisión escrita en `AGENTS.md`: «el recorrido de Administración no cambia»), mientras FR-020 dice que la tarjeta de «el del líder y el de Administración» se abre. Ver «Observaciones». | `escenario10-api.txt`, `escenario9.txt` |
| 11 | Cortar la red al cambiar de persona o de semana | **Pasa.** Con wifi y datos apagados: al pasar a la semana anterior, «No pudimos conectar · Revisa tu conexión» con «Reintentar» y **ningún dato de la semana ni de la persona anterior** (solo el nombre y el rango de la nueva); al abrir a Rocío estando sin red, su cabecera con el error, nada de Ana. Con red, «Reintentar» trae a Rocío. | `11a`, `11b`, `11c` |
| 12 | APK nuevo contra un backend sin 005 | **Pasa.** Backend anterior = el jar de `subida-fase1` (03d855a1), que es **exactamente la base de la rama 005** (20 commits atrás), sobre la misma base con V109 aplicada (`SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false`; V109 solo suma). `GET /semaforo/groups` sin `puedeAbrirse`, la lista del líder responde 404. En la app: el resumen con el texto de siempre («Solo cantidades…»), **tarjetas sin ‹ ›** y sin abrirse al tocarlas; nada se rompe. | `12a`, `12b`. No se usó el `origin/master` de hoy: ya trae V113 a V110 de la fase 2 y los aplicaría sobre mi clon |
| 13 | Fichas de administración y del mentor: «Ver publicación» | **Pasa.** En las dos, el post diario cumplido ofrece «Ver publicación» y abre la publicación (autor, fecha y texto). En la del mentor decía «Evidencia entregada · pendiente de revisión» junto a una publicación (nadie la revisa): **E-629 (c), arreglado**. Por API: la publicación con foto la leen el líder, la mentora del grupo y el admin (200, con la foto firmada que se descarga); un mentor de otro grupo y un aprendiz de otro grupo reciben 403. | `13a`, `13b`; `escenario13-api.txt` |

### Además de los 13

- **Modo oscuro y letra grande** (`cmd uimode night yes`, `font_scale 1,5`): la lista y la ficha se leen sin cortes; los encabezados bajan de renglón. `14a`, `14b`.
- **Apagar `lider.cumplimiento` desde Soporte TI** (FR-005): con la funcionalidad apagada, `puedeAbrirse` vale `false` en todos los grupos y la lista y la ficha responden **503 `FUNCIONALIDAD_APAGADA`** con el aviso editable; al prenderla vuelven. `interruptor-lider.txt`.

## Hallazgos y arreglos (bitácora E-629)

Cada uno con su prueba escrita antes, su commit y su entrada en `docs/BITACORA_ERRORES.md`.

| Id | Dónde | Qué pasaba | Arreglo |
|---|---|---|---|
| **E-629** | backend | La lista del grupo devolvía `cerrada: false` (y la app, «Semana todavía sin cerrar») para la última semana ya cerrada si **un solo integrante** había empezado después y no tenía su foto del cierre. | `GrupoDelLiderService` toma `cerrada` del calendario (`VentanaDeLaFicha`), como la ficha. Prueba: `GrupoDelLiderServiceTest.unaPersonaNuevaNoDejaLaSemanaCerradaSinCerrar`; el doble de la tabla ahora aplica la regla real. Commit `b922b143` (backend) |
| **E-629 (b)** | app | La hoja del líder mostraba dos veces un texto entregado (evidencia de texto y «Lo que escribió»): la app los manda por los dos caminos. | `respuestaYaLeidaComoEvidencia` (`respuestaRepetida.ts`) y su prueba. Commit `042eee0` (app) |
| **E-629 (c)** | app | La ficha del mentor decía «pendiente de revisión» del post diario publicado. | `mentor/utils/etiquetaDeEntrega.ts`, con «Publicado en el Muro». Commit `f80836e` (app) |

El número E-629 es el único que quedaba libre para la 005; los dos hallazgos de la app van como (b) y (c) de la misma entrada. Si se prefiere
un número propio para cada uno, es solo renumerar la bitácora.

## Observaciones (no son de la 005, o decisión del dueño, y no se tocaron)

1. **SC-006 y la «cuenta de prueba»** no se pueden comprobar en esta rama (T046 espera a la fase 2: `es_cuenta_de_prueba`, V117). Hoy una cuenta de prueba **sí aparece** en la lista del líder.
2. **Admin sin entrada a la vista nueva** (escenario 10): hay que decidir si FR-020 incluye abrir los grupos del Semáforo de Administración con esta vista, o si el registro de consultas del ADMIN/Alquimista queda solo para quien llame al endpoint.
3. **Dos ventanas distintas a la vista**: la tarjeta del resumen de Hoy dice «Últimos 7 días, del lunes 12 al domingo 18» y la lista de cada grupo, «Semana cerrada, del sábado 10 al viernes 16». Por eso Rocío dice 50 % en un lado y 47,6 % en el otro. Es lo que dice el contrato (R-2, solo el líder va de sábado a viernes), pero se nota.

   > **Corregido 2026-10-19 (E-629 d).** Esta observación quedó como «lo dice el contrato, pero se nota». El dueño decidió que
   > **la ventana del resumen manda en todo**: el resumen, la lista y la ficha leen los mismos 7 días (sin `semanaHasta`, los
   > que terminan ayer; con un viernes, esa semana), con el mismo rótulo y las mismas flechas. Resuelto en el backend
   > (`VentanaDeLaFicha.de`, la lista y la ficha leen lo que lee el resumen) y en la app (`useLecturaPorSemana` y
   > `NavegacionDeSemanas` para las tres). Prueba: `MismaVentanaEnLasTresPantallasTest`. **Falta repetir en el emulador** los
   > escenarios 1, 2 y 5 (ver abajo); las capturas `01a`–`01d`, `02a`, `05a`–`05c` y `12a` son de la ventana anterior.
4. **La ficha de administración no muestra la respuesta de la Pastilla** («no pedía evidencia»): no es de la 005 (FR-011 es del líder).
5. El **barrido del semáforo** recalculó, al arrancar, los días de las cuentas E2E viejas del clon («19 con problemas» en el resumen): no tiene que ver con la 005.

## Maestro (suite completa de `.maestro/regresion/`, 11 flujos)

Con `.maestro/correr.sh`, el backend de la rama en 8095 y las cuentas por `-e` (`habrol-aprendiz2` y `habrol-admin`; `E2E_LIMPIAR_LIMITE_LOCAL=1`).
Antes: `GET /habit-tracks/today` de las dos cuentas y el corte del ranking (`POST /admin/ranking/snapshots`), porque el backend estuvo apagado a medianoche.

**Resultado final: 11 de 11 pasan, 0 fallan.** A la primera pasada fueron 8 de 11; los tres que fallaron eran de **datos del clon, no de la app ni de la 005**:

| Flujo | Primera pasada | Motivo y arreglo | Repetición |
|---|---|---|---|
| 01 kilómetros con foto | pasa | | |
| 03 publicar en el Muro | pasa | | |
| 05 chats del admin | falla | `renaser_lider005` tenía el chat global «Formación Renaser Global» (`GLOBAL`, 52 participantes) pero **la cuenta admin E2E no era participante**: «Scrolling DOWN until "Formación Renaser Global"… FAILED». Se agregó a las cuentas `habrol-*` a `participantes_conversacion` del chat global | **pasa** |
| 06 chats del aprendiz | falla | lo mismo con `habrol-aprendiz2` | **pasa** |
| 07 Yo y Ajustes | pasa | | |
| 08 planificar Despertar | pasa | | |
| 10 gimnasio con foto | pasa | | |
| 11 ranking con Gimnasio | falla | el corte del ranking se hizo **antes** de que el flujo 10 registrara el día de gimnasio: «Assert that "[0-9]+ días?" is visible… FAILED». Se repitió el corte | **pasa** |
| 20 admin entrar y volver | pasa | | |
| 21 admin personas y volver | pasa | | |
| 22 admin crear grupo con teclado | pasa | | |

No se corrieron los 4 de `.maestro/interruptores/` (30 a 33): el README dice que no corren solos, cada uno pide apagar antes una funcionalidad por la consola
de Soporte TI (con contraseña, y tres son críticas) y volver a prenderla. Sí se probó por API el apagado de `lider.cumplimiento` (arriba).
Salida de la primera pasada: `e2e/maestro-suite-1.log`; de la repetición: `e2e/maestro-reintento.log`.

## Jest y typecheck

Con el emulador, Metro y el backend apagados, en el worktree de la app (config local por estar bajo `.claude/`):

- `node node_modules/jest/bin/jest.js` completo: **362 suites, 3060 pruebas, todas en verde** (la corrida del principio, antes de los arreglos: 360 suites y 3052 pruebas; los arreglos suman 2 suites y 8 pruebas).
- `node node_modules/typescript/bin/tsc --noEmit`: 0 errores.

Backend: no se repitió `clean verify` (ya estaba en verde, 6588 de surefire y 443 de failsafe, sin cambiar nada de eso salvo el arreglo de E-629). Tras el arreglo se corrieron `GrupoDelLiderServiceTest`
(16, con la prueba nueva), `SemanaDePersonaServiceTest` (20), `CumplimientoDelLiderControllerTest` (10) y luego todo el paquete `leadership`,
`ArchitectureTest`, `FuncionalidadDeclaradaTest` y `EndpointAuthorizationDeclarationTest`: en verde. **Falta repetir el `clean verify` completo del backend antes del merge**
(solo cambió `GrupoDelLiderService` y el doble `BancoDeCumplimiento`).

## Qué quedó sin verificar

- **SC-006** (ninguna cuenta de prueba en las listas): no verificable en esta rama (T046, fase 2).
- **Video y audio** como evidencia en la hoja del líder: sin archivos de esos tipos en los datos; solo pruebas de jest.
- **Tocar el aviso de las 20:00 abre «Mi grupo»** (FR-018) y el interruptor «Revisiones de tu líder» en Yo → Notificaciones (T045): no se probaron en el emulador (no se mandó el push al teléfono); los cubren las pruebas de jest de `rutaDeAviso` y de Notificaciones. La notificación sí se creó y trae la ruta correcta.
- **Los 4 flujos de `.maestro/interruptores/`** (ver arriba).
- **Una cuenta de Alquimista** abriendo la vista: solo se probó con líder y con admin (por API, los tres roles comparten el guard y su prueba).
- **Producción**: nada se probó contra producción (regla del dueño).

## Qué repetir en el emulador tras E-629 (d)

> **Hecho 2026-10-19.** Los escenarios 1, 2, 5, 11 y 12 se repitieron en la pasada final (sección siguiente).

> **Corregido 2026-10-19.** Los escenarios 1, 2 y 5 se grabaron con la lista en «Semana cerrada · sábado 10 al viernes 16» y la
> ficha en la semana en curso. Con la ventana única hay que repetirlos, con el backend y el APK de esta rama:

| # | Qué comprobar ahora |
|---|---|
| 1 | Resumen «Últimos 7 días · Del lunes 12 al domingo 18 de octubre» → tocar «Fénix 005»: la lista dice **lo mismo** (mismo título, mismo rango) y Rocío sale con **el mismo %** que el resumen cuenta para ella; «No se mide» sigue al final |
| 2 | Tocar a Ana: la ficha dice **«Últimos 7 días · Del lunes 12 al domingo 18»**, con los siete días y el mismo % y color que en la lista; sin «Hoy», sin días por venir, sin «La semana sigue en curso» |
| 5 | ‹ en la ficha: «Semana cerrada · Del sábado 10 al viernes 16»; otro ‹: del 3 al 9 (la primera de Ana: ‹ apagada); › vuelve hasta «Últimos 7 días» y se apaga. Las mismas flechas, con el mismo texto, en el resumen y en la lista. Mover ‹ en la lista antes de tocar a una persona: la ficha abre en esa semana |
| 11, 12 | Sin cambios de comportamiento; repetirlos de paso si la máquina lo permite |


## Pasada final con la 006 y el Código Renaser (2026-10-19)

**Fecha:** lunes 19/10/2026, de las 18:28 a las 20:20 (Lima). **Todo local**: nunca producción ni AWS.

### Entorno

| Pieza | Valor |
|---|---|
| Backend | rama `005-lider-cumplimiento-por-persona` con la 006 juntada (HEAD `c765aa3d`), jar de las 18:18, puerto **8095**, `TZ=UTC`, correo e IA `noop`. Al final, el jar reconstruido con el arreglo E-629 (e) (`f048330b`) |
| Base | `renaser_lider005` (siembra de la 005) más la V110, que el arranque aplicó. Copia de seguridad antes de tocarla: `renaser_lider005_pre` |
| Siembra de la 006 | las 14 cuentas `e2e-sem-*` con los dos scripts de la sesión de la 006 (`antes-del-arranque.sql` y `despues-del-arranque.sql`), aplicados **sobre mi clon**; `renaser_semaforo` no se modificó (solo se leyó el hash de contraseña de una de sus cuentas para sembrar la misma clave en mi clon) |
| App | worktree `lider-cumplimiento`, JavaScript de Metro con `EXPO_PUBLIC_API_URL=http://10.0.2.2:8095`. `git diff origin/master..HEAD -- package.json app.json android ios modules plugins` sale **vacío**: ni la 006 ni el arreglo del Código Renaser traen nada nativo, no se compiló ningún APK |
| Emulador | `Pixel_6`, arranque en frío (`-no-snapshot-load`); la app instalada es el build debug del 12/10 (`lastUpdateTime` 2026-10-12) |
| Máquina | la RAM llegó a menos de 500 MB libres con 10 GB de swap: el backend quedó parado unos 10 minutos y se saltó las corridas de las 23:50 y 23:55 UTC (ver el flujo 33). Algunas entradas por `adb` perdieron letras al escribir la contraseña (ver Maestro) |

Ajustes de datos en mi clon (ninguno es un hallazgo de la app ni del backend):

- La zona de **Luisa** (mentora de Fénix 005) había quedado en `Europe/London` del escenario 9 de la primera pasada, y su ficha abría la ventana del 13 al 19. Vuelta a `America/Lima`.
- Los 7 aprendices `e2e-sem-*` con color y los demás tienen sus registros relativos al 19/10, así que la prueba quedó atada a ese día de Lima (pasada la medianoche de Lima la ventana se corre).
- El flujo 40 y el radar de `e2e-sem-inicio`: ver Maestro.

### Escenarios 1, 2 y 5 con la ventana alineada (mobile-mcp)

Cuenta: el líder `habrol-lider`. Hoy 19/10 (Lima). Las cifras de la app coinciden con las de la API (`GET /semaforo/groups`, `GET /leadership/groups/{id}/semaforo` y `…/people/{id}/week`, mismas fechas) para las tres ventanas.

| # | Qué se comprobó | Resultado | Capturas |
|---|---|---|---|
| 1 | Hoy → «Semáforo por grupos»: **«Últimos 7 días · Del lunes 12 al domingo 18 de octubre»**. Al tocar «Fénix 005»: la lista dice **lo mismo** (mismo título y mismo rango) y las mismas personas con el mismo %: Rocío 50 %, Pruebas QA Cuenta 71,6 %, Ana 74,3 %, Inés sin datos, Valeria 95,1 %, y **Luisa al final con «Mentor · No se mide en el semáforo»**. La tarjeta de Fénix en el resumen (1 al día, 2 requieren atención, 1 con problemas, 1 sin actividad; promedio 72,8 % de 5 aprendices) cuadra con la lista. «Cedro 005» muestra a la guía de aprendiz como «No se mide». La cuenta cerrada (Cecilia) no aparece. «Pruebas QA Cuenta» **sí aparece** (SC-006 sigue sin poder verificarse: T046, fase 2) | **Pasa** | `01a`, `01b`, `01c`, `01d`, `01e` |
| 2 | Tocar a Ana: **«Últimos 7 días · Del lunes 12 al domingo 18 de octubre»**, 74,3 % «Requiere atención» (el mismo % y color que en la lista), siete días, el martes 13 dice **«En pausa»** y el domingo 18 trae sus evidencias; sin «Hoy», sin días por venir y sin «La semana sigue en curso» | **Pasa** | `02a`, `02b` |
| 5 | **Ficha:** ‹ lleva a «Semana cerrada · Del sábado 10 al viernes 16» (74,3 %), otro ‹ al 3 al 9 (81,3 %, «Al día»; es la primera semana de Ana: la flecha queda apagada y tocarla no hace nada); › › vuelve a «Últimos 7 días» y › se apaga. **Lista:** ‹ lleva a «Semana cerrada · 10 al 16» con Rocío 47,6 %, QA 73,9 %, Ana 74,3 % (los de la API); al tocar a Ana, **la ficha abre en esa misma semana**. **Resumen:** ‹ lleva al 10 al 16 y al 3 al 9, y al tocar «Fénix 005» la lista abre en la semana que se miraba. Ninguna llega al futuro | **Pasa en fechas, porcentajes y flechas.** El rótulo del resumen decía «Semana todavía sin cerrar» donde la lista y la ficha decían «Semana cerrada»: **E-629 (e)**, arreglado (abajo) | `05a`, `05b`, `05c`, `05d`, `05e` (antes del arreglo), `05f`, `05g` (después) |
| 11 | Sin red (wifi y datos apagados): al pasar de semana en la lista, «No pudimos conectar · Revisa tu conexión» con «Reintentar» y **ningún dato de la semana ni de la persona anterior** (solo el rango nuevo); al abrir a Rocío sin red, su cabecera con el error y «Escribirle», nada de Ana. Con red, «Reintentar» trae los datos (Rocío 45,8 %) | **Pasa** | `11a`, `11b`, `11c` |
| 12 | Backend anterior = el jar de `subida-fase1` (03d855a1, la base de la rama 005) sobre la misma base (V110 aplicada, `SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false`): `GET /semaforo/groups` sin `puedeAbrirse`, la lista del líder responde 404. En la app: «Últimos 7 días», el texto de siempre («Solo cantidades: a cada aprendiz lo acompaña y lo ve su mentor.»), **tarjetas sin chevron** que no se abren al tocarlas; nada se rompe | **Pasa** | `12a`, `12b` |

### Hallazgo E-629 (e): el resumen rotulaba distinto una semana cerrada (backend, spec 005)

Con ‹ en el resumen, «Semana todavía sin cerrar · Del sábado 10 al viernes 16 de octubre»; la lista y la ficha de esa misma semana, «Semana cerrada».
`GET /semaforo/groups?semanaHasta=2026-10-16` devolvía `periodo.cerrada: false` porque `PeriodoDelSemaforo.de` pide que **todas** las personas tengan la foto
del cierre (15 de los 67 aprendices activos del clon no la tienen: empezaron después), la misma causa de E-629. Arreglo con TDD (rojo: 2 pruebas fallaban;
verde: 115 pruebas de `SemaforoPorGruposServiceTest`, `MismaVentanaEnLasTresPantallasTest`, las del grupo y la ficha del líder, las de las tablas del semáforo y
`ArchitectureTest`): con un viernes pedido el resumen toma `cerrada` del calendario; sin fecha no cambia. Reconstruido el jar y repetido en el emulador: el resumen del
10 al 16 dice «Semana cerrada» (`05g`) y la API da `cerrada: true` para el 16 y el 9 y `false` para la vigente. Commit `f048330b` del backend (bitácora E-629 (e),
contrato y `SEMAFORO_DEL_APRENDIZ.md` al día). **No se tocaron** las tablas del mentor y de administración (`SemaforoDelGrupoService`): conservan el criterio de «todas
las fotos» y, con una persona nueva en el grupo, dicen «todavía sin cerrar» de una semana pasada; queda anotado como pendiente fuera de la 005.

### Maestro: los flujos que se corrieron, uno por uno

`.maestro/correr.sh` contra el backend de la rama en 8095 y la app con Metro. Las cuentas por `-e` (nunca en el repo). Resultado: **28 de 28 flujos corridos pasan**, 4 de ellos
tras repetirse por un motivo de datos o de entrada (no de la app ni del backend), y uno con un reintento interno de entrada.

| Flujo | Cuenta | Resultado | Motivo y arreglo si no pasó a la primera |
|---|---|---|---|
| `codigo-renaser/40-hora-ya-registrada` | `e2e-sem-inicio` | pasa (2.ª) | La siembra de la 006 puso registros del Código Renaser en **todas** las horas 8–19 del día, también las futuras; `GET /radar/latest` devolvía el de las 19:10 y la app, con razón, no daba por respondida la hora de las 18:00. Se borró el registro futuro: el último queda en la hora en curso |
| `semaforo-podio/30-hoy-mensaje-del-semaforo` ×8 | VERDE, AMARILLO, ROJO, INICIO, SIN_PROGRAMA, CUENTA_PAUSADA, GRADUADO, PAUSA | pasan | |
| `semaforo-podio/31-ranking-descargar-podio` ×4 | ADMIN, ALQUIMISTA, LIDER_MENTORES, SOPORTE_TI | pasan | |
| `semaforo-podio/32-ranking-sin-descargar` ×3 | MENTOR, GUIA_APRENDIZ, VERDE | pasan (MENTOR en la 2.ª) | MENTOR: «no entró» dos veces seguidas con «Correo o contraseña incorrectos» (la contraseña llegó con letras perdidas al teclear por `adb`; la API entra bien con esa cuenta) |
| `semaforo-podio/33-chat-tarjeta-diaria` | VERDE | pasa | La tarjeta la manda el barrido real de las 23:50 locales. Con la zona de VERDE en `UTC` el barrido de las 23:50 y 23:55 UTC no corrió (la máquina estaba en swap y el backend parado); se puso `Etc/GMT+1` y el barrido de las 00:50 UTC mandó la imagen y «Hoy llevas 67 % de tus hábitos.» con el mensaje personalizado. Después se devolvió a `America/Lima` |
| `regresion/01-kilometros-con-foto` | `habrol-aprendiz1` | pasa | `habrol-aprendiz2` ya había registrado esos hábitos hoy en la primera pasada: se usó la otra cuenta |
| `regresion/03-publicar-en-el-muro` | `habrol-aprendiz1` | pasa | |
| `regresion/05-chats-admin` | `habrol-admin` | pasa (2.ª) | «no entró» (letras perdidas al teclear); repetido solo |
| `regresion/06-chats-aprendiz` | `habrol-aprendiz1` | pasa | un reintento de entrada interno de `correr.sh` |
| `regresion/07-yo-y-ajustes` | `habrol-aprendiz1` | pasa | |
| `regresion/08-planificar-despertar` | `habrol-aprendiz1` | pasa | |
| `regresion/10-gimnasio-con-foto` | `habrol-aprendiz1` | pasa | |
| `regresion/11-ranking-gimnasio` | `habrol-aprendiz1` | pasa (2.ª) | El corte del ranking se hizo (`POST /admin/ranking/snapshots`) después de que el 10 registrara el día de gimnasio |
| `regresion/20-admin-entrar-y-volver` | `habrol-admin` | pasa | |
| `regresion/21-admin-personas-y-volver` | `habrol-admin` | pasa | |
| `regresion/22-admin-crear-grupo-teclado` | `habrol-admin` | pasa | |

Los flujos se corrieron con el jar anterior al arreglo E-629 (e), que solo cambia el rótulo del resumen de una semana pasada. Salida de cada flujo en `e2e/` no se
guardó: los resultados están en esta tabla.

### Lo que no se corrió

- **Los 4 de `.maestro/interruptores/` (30 a 33):** piden apagar antes una funcionalidad con la contraseña de Soporte TI (tres son críticas) y volver a prenderla. Siguen sin correrse; el apagado de `lider.cumplimiento` se probó por API en la primera pasada.
- **`jest` completo y `tsc`:** no se repitieron (3198 pruebas en verde del juntado, según el coordinador). Del backend se corrieron solo las 115 pruebas focalizadas del arreglo; **no se repitió `clean verify`** (6776 unitarias y 503 de integración del juntado, sin repetir): cambió `SemaforoPorGruposService` y tres archivos de documentación, y queda para quien integre.
- **Escenario 14 (modo oscuro y letra grande), 3, 4, 6, 7, 8, 9, 10 y 13:** no se repitieron; no los toca la ventana alineada (siguen como en la primera pasada).
- **Web y la descarga real del PNG del podio** de la 006: Maestro solo comprueba que se abre la hoja de compartir en el emulador; la imagen se mira en la sesión de la 006.
- **Producción:** nada se probó contra producción.
