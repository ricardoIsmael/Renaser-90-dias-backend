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
| 12 | APK nuevo contra un backend sin 005 | **Pasa.** Backend anterior = el jar de `subida-fase1` (03d855a1), que es **exactamente la base de la rama 005** (20 commits atrás), sobre la misma base con V109 aplicada (`SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false`; V109 solo suma). `GET /semaforo/groups` sin `puedeAbrirse`, la lista del líder responde 404. En la app: el resumen con el texto de siempre («Solo cantidades…»), **tarjetas sin ‹ ›** y sin abrirse al tocarlas; nada se rompe. | `12a`, `12b`. No se usó el `origin/master` de hoy: ya trae V103 a V110 de la fase 2 y los aplicaría sobre mi clon |
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

1. **SC-006 y la «cuenta de prueba»** no se pueden comprobar en esta rama (T046 espera a la fase 2: `es_cuenta_de_prueba`, V107). Hoy una cuenta de prueba **sí aparece** en la lista del líder.
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

> **Corregido 2026-10-19.** Los escenarios 1, 2 y 5 se grabaron con la lista en «Semana cerrada · sábado 10 al viernes 16» y la
> ficha en la semana en curso. Con la ventana única hay que repetirlos, con el backend y el APK de esta rama:

| # | Qué comprobar ahora |
|---|---|
| 1 | Resumen «Últimos 7 días · Del lunes 12 al domingo 18 de octubre» → tocar «Fénix 005»: la lista dice **lo mismo** (mismo título, mismo rango) y Rocío sale con **el mismo %** que el resumen cuenta para ella; «No se mide» sigue al final |
| 2 | Tocar a Ana: la ficha dice **«Últimos 7 días · Del lunes 12 al domingo 18»**, con los siete días y el mismo % y color que en la lista; sin «Hoy», sin días por venir, sin «La semana sigue en curso» |
| 5 | ‹ en la ficha: «Semana cerrada · Del sábado 10 al viernes 16»; otro ‹: del 3 al 9 (la primera de Ana: ‹ apagada); › vuelve hasta «Últimos 7 días» y se apaga. Las mismas flechas, con el mismo texto, en el resumen y en la lista. Mover ‹ en la lista antes de tocar a una persona: la ficha abre en esa semana |
| 11, 12 | Sin cambios de comportamiento; repetirlos de paso si la máquina lo permite |
