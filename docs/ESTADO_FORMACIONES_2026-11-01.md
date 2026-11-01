# Estado de las formaciones (specs 022–034) — 01/11/2026, 04:10

> **Reemplazado por `INFORME_FORMACIONES_Y_PAGOS_2026-11-01.md`** (cierre del 01/11, con pagos, Hoy/Plan y la segunda jornada de E2E). Este documento queda como historia de la prueba nocturna.

Rama `integracion-formaciones` en los dos repos, **local y sin publicar**: nada se subió, nada se fusionó a `master`, nada tocó producción. Los worktrees están en
`.claude/worktrees/integracion` de cada repo (HEAD del backend y de la app, al final de este documento).

## 1. Qué hay

Las doce specs, fusionadas en este orden y con los choques resueltos conservando los dos lados: 022 formaciones (base, aislamiento, duraciones de 90 a 365 días), 028 «solo esta semana»,
030 Código Renaser con voz, 034 Código Renaser en las fichas, 033 «Cierra tu día» en Plan, 023 hábitos por formación, 029 el líder ve todos los hábitos, 025 aceptar con formación
y duración, 027 términos por formación, 031 análisis 80/20 con IA, 026 ficha con diagnóstico (el Mapa se retira) y 024 comunidad por formación.

Migraciones: V125 a V157 seguidas, sin números repetidos. **Se publican todas juntas** (Flyway las corre en orden).

## 2. Verificación completa antes del E2E (02:50)

| | Resultado |
|---|---|
| Backend `clean verify` | pruebas unitarias 8570 sin fallos; pruebas de integración 758, 2 fallaron y se corrigieron (`RachaDelHabitoIT`, `ConsultasDeFichaDelDiagnosticoIT`) y se repitieron en verde |
| App `tsc --noEmit` | 0 errores |
| App Jest completo | 664 suites, 7269 pruebas, en verde |

Después de las correcciones del E2E se corrió: backend, las pruebas dirigidas de todo lo tocado (330 más las de los controladores corregidos), más las de arquitectura y permisos
(`ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`, `RutasCubiertasPorElFiltroTest`, `NingunaPruebaDeclaraSuPropioRelojTest`): en verde. App: `tsc` 0
errores y Jest completo 664 de 665 suites en la primera corrida (la que falló era una prueba que no simulaba la formación de la sesión; se arregló y se repitió en verde).
Pruebas de integración con base real (Docker) corridas después de las correcciones, solo las que tocan lo cambiado: grupos y mentoría (`GruposPorFormacionIT`, `ComunidadPorFormacionIT`,
`MisCelulasIT`, `MentorEnVariosGruposIT`, `ComposicionDeCelulaIT`, `GruposEnCursoIT`), `FormacionesIT`, `CentroDeFormacionesIT`, `CumplimientoDelLiderIT`, `AceptarConFormacionIT`,
`HabitosPorFormacionIT`, `HabitosDeTodosIT`, `HomeMensajeDelSemaforoIT`, `MapaDelPersonalAutenticacionIT`, `SoportesPaginadosIT`, `DiagnosticoDeIngresoIT` y `AnalisisDelLiderIT`: **157 pruebas
en verde**. Una sola falla intermedia, `AnalisisDelLiderIT.elLiderLoLee`: pasa sola y en la corrida completa de las 02:50; falla cuando otras pruebas ya escribieron constancias de la misma
persona en `consultas_de_ficha` (la tabla solo se agrega y el desempate de la última constancia, por `id`, es al azar). Es un desorden de la prueba, no del código.
**Lo que NO se repitió después de las correcciones:** el `clean verify` completo (52 minutos) y el resto de las 758 pruebas de integración.

## 3. E2E final (01/11, 02:52 a 04:08)

Base propia `renaser_e2e_final` (copia de `renaser`), backend y Metro en la máquina, emulador Pixel_6 con la app de depuración, diez cuentas `ef-*` (aprendiz de la Escuela, del
Diplomado de 3 meses, del Diplomado de 1 año y de la Certificación; mentora con dos formaciones; guía de la Certificación; líder; Administración; Alquimista; Soporte TI) más
cuentas aceptadas por solicitud durante la prueba. Dos agentes: uno por pantalla (emulador, Maestro) y otro por API (curl). Evidencia en `~/Imágenes/e2e-2026-11-01-final/`
(`pantalla/`, `maestro/`, `api/`); resultados en `pantalla/resultados.md` y `api/resultados.md`.

### 3.1 Primera pasada

**API: 357 escenarios** (matriz de aislamiento de 1.066 celdas de lista y 875 pedidos por id a objetos ajenos): **332 pasaron, 13 fallaron (12 errores distintos), 6 no se
probaron, 6 observaciones.** Pasaron por completo, entre otras: sesión y selector de formación del personal, duraciones de 90 y 365 días con fases estiradas, centro «Formaciones»
y sus cifras, traslado con y sin historial (022); aceptar con formación, duración, rol y diagnóstico, errores 400/409, concurrencia (025); diagnóstico completo, meta medible, 12
hitos mensuales para 365 días, lecturas con registro, importación CSV (026); términos por formación con versiones (027); hábitos por formación (023); «solo esta semana», repetir la
semana, planificar el mes (028); el líder ve todos los hábitos (029); contrato del 80/20 (031); «Cierra tu día» en Plan (033); Código Renaser en las fichas (034).

**Pantalla: 31 comprobaciones de aislamiento pasaron, 1 falló.** Cubierto: aprendices de la Escuela, del Diplomado de 3 meses y de 1 año y de la Certificación (Hoy, Yo con «Día N de
90/365» y la fase estirada, Training, Plan con «objetivo de 90 días / 3 meses / 1 año», Muro, Eventos, Cursos, Tribu y Ranking por formación; sin Gimnasio en el Diplomado y sin
Kilómetros ni Gimnasio en la Certificación); la mentora con dos formaciones (pregunta inicial, «Cambiar de formación», «Mi grupo» por formación); el líder («Mis mentores»,
«Hábitos de todos», «Todos los aprendices» por formación); Administración («Formaciones» con cifras y detalle, hoja «Aceptar solicitud» con formación, duración, rol y diagnóstico).

### 3.2 Lo que falló y cómo se corrigió (un commit por error)

| Id | Qué pasaba | Corrección |
|---|---|---|
| P2-A, P2-A2 | El líder veía de una mentora con dos formaciones los grupos de las dos (`/leadership/mentors`, `/leadership/report`, ficha del mentor) | `MedicionDeMentoresFinder.vigente(alcance)` y `IndicadoresDeMentores` filtran por el alcance del líder (`a48ac2ba`) |
| P2-B | `GET /wall/{id}` mostraba al líder una publicación de otra formación | 404 si es de una formación que no ve (`ed33979e`) |
| P2-J | `GET /ranking/groups` mostraba a todos, aprendices incluidos, los grupos de las tres formaciones de la cohorte | el ranking filtra por el alcance de quien pregunta; Administración y Alquimista ven todos (`0a64007a`) |
| P3-C | Una mentora con dos formaciones abría por id los grupos de la formación que no tenía activa (`/me/cells/{id}/members`, `/admin/cells/{id}`) | 404 (`ee9f5d12`) |
| P3-K | `/admin/cells?cohortId=` listaba los grupos de las dos formaciones | solo los de la activa (`1af18b37`) |
| P3-D | «cuántos grupos tiene la cohorte» sumaba los de las tres formaciones (21) | cuenta solo los de la misma formación (`852e5c9b`) |
| P3-E | `/home` del personal sin programa propio decía Escuela | dice su formación activa (`515ec6e2`) |
| P3-F | `GET /admin/formaciones/NOEXISTE` daba 400 | 404, como dice el contrato; las escrituras y la comunidad siguen en 400 como dicen sus contratos (`21f4d47d`, `6012e4b4`) |
| P3-H | El directorio del chat ofrecía a la mentora de Escuela+Diplomado, pero abrir el 1 a 1 daba 404 si su activa era la Escuela | el directo se abre en la formación que comparten (`e49baa80`) |
| P3-L | Al cambiarle las formaciones a una persona del personal, su programa propio sin empezar seguía en la formación que ya no tenía | pasa a la primera que conserva; uno ya empezado o con grupo no se toca (`7d05b93d`) |
| P4-G | `GET /mapa-renacimiento` no traía `retired: true` | lo trae (`a0dd94e8`) |
| P4-I | Cambiar la duración de alguien que no es aprendiz daba 404 | 409 (`ee6f0c41`) |
| BUG 1 (pantalla) | Al cambiar de formación, Hoy decía «Escuela Renaser» pero mostraba la «Última evidencia del muro» de la anterior hasta pasar 2 minutos | la lectura guardada lleva su formación; lo mismo para lo que guardan Training y Plan (eventos de la semana, días cerrados, cierres) (app `18456a2`, `45a2226`) |
| Ranking «Gimnasio» (antes del E2E) | La pestaña no salía nunca: se buscaba la clave `WORKOUT` y la real es `GYM` | clave corregida (app `8797c24`) |

Cada corrección lleva su prueba (unitaria o de controlador). Backend: `MedicionDeMentoresServiceTest`, `ConsultarPublicacionServiceTest`, `RankingDeGruposServiceTest`,
`CelulaServiceTest`, `CambioDeDuracionDelProgramaServiceTest`, `AsignacionDeFormacionesDelPersonalServiceTest`, `ConversacionServiceTest`, `CentroDeFormacionesControllerTest`.
App: `lecturasGuardadasPorFormacion.test.ts` y `ultimaPublicacionMuro.test.ts`.

### 3.3 Segunda pasada (03:48 a 04:04, con todo corregido y reiniciado a las 03:45)

- **API: 367 escenarios, 355 pasaron, 1 falla, 6 no probados, 5 observaciones.** Los 12 errores quedaron corregidos, con sus controles positivos (Administración sigue viendo
  las tres formaciones; el líder y la mentora siguen viendo lo suyo; el 1 a 1 con la mentora se abre en la formación que comparten y la Certificación sigue sin poder). Se repitieron
  completas la matriz de listas (**1.066 celdas, 0 fugas**) y la de pedidos por id (**875 pedidos a objetos ajenos, 0 respuestas 2xx**), más 022, 024, 028, 029, 031, 033, 034 y las
  lecturas del diagnóstico: sin regresiones.
- **Pantalla: 38 comprobaciones pasaron, 1 falló** (la del cambio de formación, ya corregida y vista en pantalla: «Última evidencia del muro» y «Próximo evento» de la formación
  elegida, en el acto, en los dos sentidos). Además pasaron la guía de la Certificación (una formación, sin «Cambiar de formación», «Mi grupo» con su alumna), los términos por
  formación con su historial, «Código Renaser en las fichas» (apagado), Fichas y diagnóstico (39 preguntas, versión 1, «Asignar a varias personas»), el Alquimista y Soporte TI
  (consola y Comunidad con las tres formaciones).
- **Pendiente, no es de las formaciones (existía antes):** `POST /admin/cells/{id}/trainees`, luego `DELETE` de ese aprendiz y otra vez `POST`: responde 200 pero **no lo vuelve a
  dejar dentro**. La clave de operación `alta-manual|aprendiz|grupo` (`ComposicionDeCelulaService.claveDeAltaManual`, línea 295) es única desde V45 y volver a sumar a alguien que
  se quitó es un no-op silencioso (`asignaciones_celula` conserva una sola fila, cerrada). Viene desde la rama #7 (`20926d68`). Arreglarlo es decidir la clave (por ejemplo, incluir el
  número de la asignación cerrada) y no se tocó esta noche. Prioridad baja.
- **Observación (spec 028):** `PUT /habit-preferences/{id}/week` acepta semanas después del día 90 o 365 (204). La app no las ofrece (FR-017) y no tiene efecto visible; ver el punto 5.7.
- **Observación:** `GET /ranking` trae las filas de Kilómetros y Gimnasio también en formaciones cuyo catálogo no tiene esos hábitos; la app esconde esas pestañas según el catálogo (spec 023).

## 4. Lo que NO se probó

- **IA real del análisis 80/20** y **SER leyendo el diagnóstico**: no hay clave del modelo en el entorno de prueba. Solo se probó el contrato de lectura, los estados `SIN_DATOS` / `EN_CURSO`,
  el calendario (53 semanas / 12 meses según la duración) y el aislamiento. **Falta probarlo con la IA real antes de publicar.**
- **Flujos Maestro de las specs y la regresión de siempre**: no se corrieron en el E2E final (el controlador de Maestro tarda ~10 s por toque en esta build y la prioridad era el
  aislamiento). Los flujos están escritos en `.maestro/` de la app (`formaciones`, `habitos-por-formacion`, `comunidad-por-formacion`, `solicitud-con-formacion`,
  `ficha-con-diagnostico`, `terminos-editables`, `planificacion`, `cierre-en-plan`, `codigo-renaser`, `codigo-renaser-en-fichas`, `analisis-80-20`, `habitos-de-todos`). Las
  specs 028, 030 y 033 sí pasaron su E2E de pantalla en la rama de cada una (tanda 1, 31/10), pero **no con 365 días** ni juntas.
- **Código Renaser con voz** (dictar y escuchar) y **su registro dictado visto en las fichas**: ninguna cuenta de prueba tiene hora de Despertar, así que Mi Radar no tiene horas.
- **Un aprendiz completando el diagnóstico** (retomar, condicionales, meta medible hasta la cifra del mes) **por pantalla**; por API sí pasó de punta a punta.
- **Aceptar de verdad una solicitud por pantalla** (la hoja se vio y se cerró «sin aceptar»; por API sí pasó, también varias a la vez y con concurrencia) y **cambiar la duración de quien ya empezó por pantalla**.
- **Aceptar de nuevo los términos al abrir la app** (el bloqueo con casilla y firma) por pantalla.
- **«Hábitos del programa» por formación, copiar un hábito a otra formación y Comunidad → Testimonios** por pantalla.
- **El Plan de un aprendiz del Diplomado de 1 año a lo largo de los meses** (solo se vio el día 60: «Mes 3 · Semanas 9 a 13»).
- **Aviso «a todos» al crear un evento**: el servidor local no escribe notificaciones (push y correo apagados), así que no hay forma de ver a quién llegó. Falta probarlo con push real.
- **Publicar en el Muro eligiendo formación** (la publicación exige foto y no se subió una por API); eventos y testimonios sí dieron el 400 del contrato.
- **«Ver como» de Soporte TI** respetando el aislamiento de la formación: falta la segunda contraseña de la consola en esa base.
- **El `clean verify` completo y el resto de las pruebas de integración después de las correcciones del E2E**: ver el punto 2. Correrlo entero antes de publicar (52 minutos).

## 5. Lo que necesita el dueño

1. **El texto de la «Declaración de responsabilidad y consentimiento» del diagnóstico.** Sin él, el diagnóstico no se puede publicar. (En la prueba se usó un texto de ensayo.)
2. **Publicar los términos del Diplomado y de la Certificación** desde Administración. Nacen «Por revisar» y, sin ellos, nadie puede ser aceptado en esas formaciones.
3. **La maqueta de Pagos (spec 032)**, que queda para el lunes. El borrador ya está en la rama `032-cronograma-de-pagos` del backend (commit `177322dc`, worktree
   `.claude/worktrees/spec-032`): `specs/032-cronograma-de-pagos/spec.md` y `preguntas-para-el-duenio.md` (10 preguntas con opciones, la recomendada primero).
4. **Revisar los supuestos de la 031** (están en D-306): los mínimos de datos, los reintentos y los meses en bloques de 4 semanas.
5. **Revisar tres supuestos de la 026.** Cada uno se cambia con una línea de código:
   - la meta de Negocio se lee como un nivel (dónde estar al cierre de cada mes), no como un monto que se acumula;
   - las lecturas del diagnóstico no entran al resumen de las 20:00 que recibe el mentor;
   - la lista de quién vio el diagnóstico queda guardada, pero todavía nadie puede verla.
6. **Decidir dos detalles que salieron en pantalla** (no son errores): la «Caja Renaser» aparece en Yo de los aprendices del Diplomado y de la Certificación (la 022 no nombra la
   Caja), y en Ranking «Tu posición» dice «Comunidad Renaser» también en el Diplomado y la Certificación.
7. **Decidir si el servidor debe rechazar «solo esta semana» fuera del programa** (semanas después del día 90 o 365): hoy responde 204 y es la app la que no ofrece esas semanas (FR-017).
   Sin efecto visible hoy.

## 6. Orden de publicación (nada subió todavía)

1. **Backend primero, todo junto** (Flyway V125 a V157). Un merge a `master` del backend **despliega a producción**: lo decide el dueño.
2. **La Política de Privacidad en Vercel**, antes del APK.
3. **Después el APK nuevo**, que es solo JavaScript (sin código nativo nuevo), pero la app no se actualiza por aire.
Con el backend nuevo y la app anterior no se rompe nada (todo campo nuevo es opcional); con la app nueva y el backend anterior no aparecen las formaciones.

## 7. Estado de las ramas

| | Rama | Último commit de código |
|---|---|---|
| Backend | `integracion-formaciones` (worktree `.claude/worktrees/integracion`) | `6012e4b4` (este documento va en el commit siguiente) |
| App | `integracion-formaciones` (worktree `.claude/worktrees/integracion`) | `45a2226` |

Las ramas de cada spec (`022-…` a `034-…`) quedan intactas, sin reescribir su historia, por si hay que volver a una. La 032 (Pagos) no está en la integración: espera la maqueta.
Las carpetas `graphify-out/` no se versionan.

Cómo repetir el E2E: las herramientas y la base están en `~/.config/renaser-e2e/` (`levantar-backend-e2e-final.py`, `herramientas/`, `formaciones/e2e-final-listo.md`); las claves de
prueba viven solo ahí. La base `renaser_e2e_final` (copia de `renaser`) quedó con datos de prueba: términos republicados, categorías y hábitos de prueba desactivados, cuentas `e2e-sol-*`
aceptadas por solicitud. No sirve como base para nada más que para repetir esta prueba.
