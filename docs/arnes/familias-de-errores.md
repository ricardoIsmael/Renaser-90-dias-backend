# Familias de errores que se repiten (y qué chequeo las frena)

Fecha: 2026-11-06. Principio (learn-harness-engineering): **una regla que se repite se vuelve prueba**. Este informe
clasifica la bitácora y los issues `bug` en familias, cuenta cuántas veces apareció cada una y dice qué la atrapa hoy.
La versión de la app (`../Renaser-90-dias-frontend-/docs/arnes/familias-de-errores.md`) detalla los chequeos de allá.

## Con qué se contó

- **Bitácora**: `docs/BITACORA_ERRORES.md`, **604 entradas** (E-01 a E-671; faltan números que nunca se usaron).
- **Issues `bug`**: 26 del backend y 57 de la app (`gh issue list --label bug --state all`), **83 en total**.
- **Método**: búsqueda por palabras clave en el título, el síntoma y la causa de cada entrada (script en el informe del PR),
  revisada a mano en los títulos de las 604. Una entrada puede caer en dos familias (E-541 es «tiempo» y «prueba
  frágil»). **Las cuentas son aproximadas (±15 %)**: sirven para ordenar, no para auditar. 411 de 604 entradas cayeron
  en alguna familia; el resto son casos únicos (comportamiento de la IA, contenido, infraestructura puntual).

## Las familias, de la más frecuente a la menos

| # | Familia | Bitácora | Issues | Ejemplos | Qué la frena hoy |
|---|---|---|---|---|---|
| 1 | **Entorno y herramientas**: `JAVA_HOME`, dos Maven sobre el mismo `target/`, worktrees, `pkill -f` que se mata a sí mismo, tmpfs lleno, CRLF, emulador viejo | ≈80 | 10 | E-02, E-39, E-59, E-104, E-461, E-511, E-52 | `pesado.sh` (uno a la vez), `verificar.sh`; **nuevo**: CRLF (`arnes-familias`), scripts de worktree (`worktree-nuevo/cerrar/viejos.sh`) |
| 2 | **Seguridad**: ruta fuera del filtro de sesión, cuenta suspendida que pasa, dueño no comprobado, entrada sin tope | ≈70 | 2 | E-148, E-181, E-254, E-258, E-366, E-481, E-528, E-542, E-670, E-374 | Ya cubierta: `EndpointAuthorizationDeclarationTest`, `RutasCubiertasPorElFiltroTest`, `CuentaSuspendidaEnTodoEndpointTest`, `FuncionalidadDeclaradaTest` |
| 3 | **Concurrencia**: doble envío, doble toque, carrera al crear, operación no idempotente, `duplicate key` | ≈60 | 7 | E-70, E-72, E-76, E-168, E-378, E-380, E-596, E-597, E-655, E-668, back#55, back#34 | **Sin chequeo automático** → regla en `AGENTS.md` («Errores que se repiten») |
| 4 | **Avisos y alarmas**: no llegan, llegan tarde, dos veces o no se apagan | ≈56 | 11 | E-109, E-195, E-301, E-302, E-314, E-410, E-632, E-645, back#22, app#33 | Pruebas propias de cada caso (alarmas, push con caducidad); sin chequeo general → regla |
| 5 | **Tiempo**: fecha u hora del servidor/UTC en vez de la zona de la persona; pruebas que dependen de la hora | ≈60 | 3 | E-91, E-105, E-126, E-189, E-534, E-550, E-556, E-557, E-615, app#108 | `ArchitectureTest` (adapter/in, domain, application), `RelojDePrueba`; **nuevo**: `LocalDate/LocalDateTime/…now()` en TODO `src/main` (`arnes-familias`) y, en la app, `toISOString().slice(0,10)` y `new Date('AAAA-MM-DD')` |
| 6 | **Un 4xx/5xx tratado como error genérico**, o el motivo del servidor que se pierde | ≈40 | 5 | E-45, E-61, E-73, E-84, E-373, E-416, E-464, E-473, app#81 | Sin chequeo general → regla |
| 7 | **Contrato app↔backend y librerías**: campo renombrado, enum en otro idioma, ids corridos, Jackson 2 dentro de Boot 4 | ≈34 | 0 | E-33, E-63, E-65, E-89, E-93, E-107, E-114, E-163, E-333, E-393 | Trazabilidad de specs; **nuevo**: Jackson 2 (anotaciones y `ObjectMapper` inyectado) en `arnes-familias` |
| 8 | **Textos que ve la persona**: sin tildes, MAYÚSCULAS, ids internos, «90 días» fijo, inglés | ≈17 | 15 | E-99, E-141, E-270, E-416, E-636, back#53, back#63, back#71, app#94, app#103, app#104 | App: `rotulosEnTipoOracion`, `botonesDoradosSinMayusculas`, `ningunNoventaDiasEscrito`…; **nuevo** (backend): mensajes de error sin tildes, deuda congelada (#95) |
| 9 | **Huecos y layout**: pantalla con blanco anómalo, contenido tapado o cortado | ≈19 | 12 | E-136, E-419, E-507, E-525, E-540, app#82, app#85, app#90, app#96, app#97, app#107 | Revisión de capturas en el E2E (AGENTS.md de la app); sin chequeo estático |
| 10 | **Pruebas frágiles**: fixture con fecha a mano, semilla con `CURRENT_DATE`, datos compartidos, mock re-stubeado | ≈22 | 6 | E-154, E-164, E-189, E-541, E-558, E-648, E-658, back#47, back#57, back#64 | `NingunaPruebaDeclaraSuPropioRelojTest`, Jest en `America/Lima`; resto → regla |
| 11 | **Falla en silencio**: excepción tragada, oyente que no existe, efecto que no pasa sin avisar | ≈20 | 1 | E-109, E-120, E-121, E-177, E-180, E-300 | **Nuevo**: `catch` vacío sin comentario (`arnes-familias`); en la app, `Alert` de react-native (diálogo muerto en la web) |
| 12 | **Migraciones**: editar una aplicada, número que choca, sin `search_path`, destructiva | ≈19 | 1 | E-86, E-157, E-170, E-211, E-262, E-210, back#61 | **Nuevo**: versión única, `search_path` o `renaser.`, no editar lo de master y numerar por encima (`arnes-familias`) |
| 13 | **Pantalla que no se refresca** o lectura vieja que pisa a la nueva; algo que se escribe y nadie lee | ≈12 | 2 | E-53, E-101, E-145, E-194, E-315, E-597, E-615, back#76 | Sin chequeo general → regla |
| 14 | **Pruebas que no corren** y pasan en verde | ≈12 | 1 | E-27, E-111, E-139, E-142, E-256, E-349, E-489, app#47 | `verificar.sh` (cuenta `Tests run`); **nuevo**: clase con `@Test` que Maven no incluye por su nombre; en la app, `.test.tsx`/`.spec.ts` que Jest no corre |
| 15 | **Build nativo**: parche de postinstall que no llega al APK, módulo nativo ausente | ≈13 | 0 | E-220, E-403, E-428, E-463, E-571, E-637 | App, **nuevo**: todo `arreglar-*.js` en postinstall y todo módulo `expo-*` precompilado que se parcha en `buildFromSource` |
| 16 | **Documentos que se contradicen** o quedaron viejos | ≈12 | 0 | E-09, E-10, E-50, E-131, E-356, E-357, E-519 | `arnes.test.mjs` (AGENTS.md corto y con rutas que existen); regla 05 |
| — | **Dos agentes en paralelo** (subfamilia de 1): misma clase o mismo método creados dos veces | 3 | 0 | E-29, E-32, E-359 | **Nuevo**: dos beans con el mismo nombre simple (`arnes-familias`) |

## Chequeos agregados el 2026-11-06

**Backend** — `scripts/arnes-familias.test.mjs` (11 pruebas, node puro, corre en segundos con `scripts/verificar.sh` y en
`trazabilidad.yml`; excepciones en `scripts/arnes-excepciones.json`):

1. Tiempo: ningún `LocalDate/LocalDateTime/LocalTime/ZonedDateTime/OffsetDateTime.now()` en `src/main` (excepción: `SystemClock`).
2. Migraciones: versión única.
3. Migraciones: `search_path` o tablas calificadas con `renaser.` (excepciones: V2 y V32, del outbox).
4. Migraciones: no se edita ni se borra una que está en `origin/master`, y una nueva va por encima de la última (el CI
   necesita la historia: `fetch-depth: 0`).
5. Pruebas: clase con `@Test` cuyo nombre no incluye Maven (surefire/failsafe).
6. Jackson: ninguna anotación de Jackson 2.
7. Jackson: ningún bean inyecta el `ObjectMapper` de Jackson 2.
8. Beans: dos componentes de Spring con el mismo nombre simple.
9. Silencio: `catch` vacío sin comentario.
10. Textos: mensajes de error sin tildes; la deuda (189 en 127 archivos, issue #95) solo puede bajar.
11. Entorno: CRLF en archivos de texto (excepciones: `mvnw.cmd` y el CSV de la spec 026).

**App** — `src/__tests__/harness/familiasDeErrores.test.ts` (7 pruebas): fechas UTC (2), `Alert` de react-native (2),
pruebas que Jest no corre, parches nativos y `buildFromSource` (2), CRLF. Detalle en su informe.

Cada chequeo se probó **contra una violación plantada** (falla) y **contra el código actual** (pasa con sus excepciones).

## Violaciones reales que había el día del chequeo

| Dónde | Qué | Issue |
|---|---|---|
| Backend | 189 mensajes de error sin tildes en 127 archivos | [#95](https://github.com/ricardoIsmael/Renaser-90-dias-backend/issues/95) |
| App | `AlumnoScreen` y `usePasosDelDia` usan el `Alert` de react-native (en la web no sale el aviso) | [RENASER-LAB#144](https://github.com/RENASER-LAB/Renaser-90-dias-frontend-/issues/144) |

No se tocó código de producto: quedaron como excepciones con fecha.

## Lo que queda sin chequeo automático, y por qué

- **Concurrencia (3), avisos (4), 4xx genérico (6), refresco (13)**: dependen del comportamiento en el tiempo; un grep
  no distingue el caso bueno del malo. Quedan como reglas cortas en `AGENTS.md` (sección «Errores que se repiten») con
  su E-NN, y se cubren con la prueba de cada caso y el E2E.
- **Huecos (9)**: se ven en pantalla. La regla del E2E (revisar capturas en todos los estados y roles) es el chequeo.
- **Pruebas frágiles (10)** más allá del reloj: hace falta correr la suite muchas veces; no entra en un chequeo rápido.
- **Seguridad (2)**: ya estaba bien cubierta por cuatro pruebas de arquitectura; no se duplicó.
