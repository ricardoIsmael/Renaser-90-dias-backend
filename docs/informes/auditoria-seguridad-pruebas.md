# Auditoría de cumplimiento de CLAUDE.MD §0.2 y §0.3 — pruebas y seguridad

**Fecha:** 2026-08-31
**Alcance:** cumplimiento de las reglas de pruebas (§0.2) y de pruebas de seguridad (§0.3) que el proyecto se dio en `CLAUDE.md`.
**Modo:** solo lectura. **No se ejecutó Maven** (había un build en curso), así que **ningún número de este informe proviene de una corrida de tests**: todo sale de leer el código fuente de `src/main` y `src/test`. Lo que quedó sin verificar por esa razón está marcado explícitamente en §9.

La vara es el texto de §0.2/§0.3, no un criterio propio. Se cita archivo y línea para todo lo afirmado.

---

## 1. Resumen ejecutivo

| Eje | Regla | Estado |
|---|---|---|
| Autorización negativa por endpoint | §0.3 | **133 de 201 endpoints protegidos (66,2 %)**. 68 sin prueba. |
| Mitad "SUSPENDED → 403" de esa misma regla | §0.3 | **87 de 201 (43,3 %)**. 46 endpoints tienen la prueba de rol pero no la de suspendido. |
| El rol no se puede inyectar | §0.3 | El blindaje **existe y es de compilador**; la **prueba que lo fija no existe justo en el alta pública**. Sí existe en otros módulos. |
| Test de reflexión `@RequiresPermission` | §0.3 | No aplicable hoy (las anotaciones no existen). Propuesta de reemplazo en §5. |
| Testcontainers por adaptador | §0.2 | **51 de 90 adaptadores de persistencia (57 %)**. 39 sin prueba de integración. |
| Unitarias de dominio | §0.2 | **65 de 66 clases de dominio con lógica real** tienen prueba. Es el eje mejor cubierto. |

**Lo más grave que se encontró, aparte del `X-Actor-Id`:** el oráculo de enumeración de participantes en `ParticipacionProgramaService` (§6.1) — residuo no razonado de la decisión documentada E-42.

---

## 2. Método, y sus límites

Para poder cuantificar hizo falta resolver, endpoint por endpoint, quién lo protege y quién lo prueba:

1. Se extrajeron los **221 endpoints** de los 71 `*Controller.java` (`@Get/Post/Put/Patch/DeleteMapping`).
2. Para cada uno se resolvió el campo del controller (`private final XxxUseCase`) → interfaz de caso de uso → clase `*Service` que la implementa (`implements`), y el método concreto que el handler invoca.
3. Se marcó el endpoint como **protegido** si el método del servicio (o un privado al que llama, hasta 3 niveles) lanza `NotAuthorizedException` o invoca un guard (`require*`, `esModerador`, `*Guard.*`).
4. Se marcó como **con prueba negativa** si en el `*ServiceTest`/`*ControllerTest` de **esa misma clase** hay un `@Test` que (a) menciona `NotAuthorizedException`/`isForbidden`/`403` y (b) invoca ese método concreto.

**Límites que hay que tener presentes al leer los números:**

- El paso 3 es una heurística de lectura estática. Produjo **falsos "sin guard"** cuando la protección está una capa más adentro, en otro servicio: verificado a mano en `TracksDelDiaProyeccionService.consultar` (`src/main/java/com/renaser/os/habits/application/services/TracksDelDiaProyeccionService.java:59`), que delega en `RegistroService.consultar`, y ahí sí está el `requireSelf(actorId, participanteId)` (`src/main/java/com/renaser/os/habits/application/services/RegistroService.java:89`). Lo mismo con `EventoService.listar`, que guarda vía `accesoEventoService.requireProgreso` (`src/main/java/com/renaser/os/calendar/application/services/EventoService.java:94`). Esos casos **no** se cuentan como hallazgo.
- El paso 4 exige que la prueba esté en el test de **la clase que implementa**, no en cualquier test del módulo. Con el criterio laxo (cualquier test del mismo módulo) el número sube a 152/221; con el estricto queda en 133/201. **Se reporta el estricto**, porque una prueba de `X` no verifica la autorización de `Y`.
- Se verificó a mano una muestra de 8 veredictos contra el archivo de test. Los 8 coincidieron. Detalle en §3.3.

---

## 3. §0.3 — Prueba de autorización negativa por endpoint

> *"cada endpoint protegido debe tener una prueba que verifique que un rol sin permiso recibe 403, y que un usuario `SUSPENDED` recibe 403 aunque su token sea válido"*

### 3.1 A qué nivel están las pruebas que sí existen

De los 133 endpoints cubiertos:

- **126 están cubiertos únicamente a nivel de servicio** (`src/test/java/**/application/services/*ServiceTest.java`).
- **6 lo están a nivel de servicio y además de controller**.
- **1 sólo a nivel de controller**.

Esto es **correcto y cuenta como cubierto**: los guards viven dentro de los servicios, no en los controllers. `WallController` no tiene una sola línea de autorización — delega todo a los casos de uso (`src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/publicacion/WallController.java:78-152`), y la decisión la toma `PublicacionMuroService` (`src/main/java/com/renaser/os/community/application/services/PublicacionMuroService.java:285,302,310,329`). Probar el servicio es probar la autorización real.

El único matiz: estas pruebas verifican la **excepción** (`NotAuthorizedException`), no el **código HTTP**. El mapeo a 403 lo hace un único punto, `GlobalExceptionHandler.handleNotAuthorized` (`src/main/java/com/renaser/os/shared/web/GlobalExceptionHandler.java:35-38`), que sí está centralizado — así que la cadena queda cerrada aunque no se pruebe extremo a extremo.

### 3.2 Cobertura por módulo

| Módulo | Endpoints protegidos | Con prueba de rol sin permiso | % | Además con prueba de `SUSPENDED` |
|---|---|---|---|---|
| **chat** | 8 | 8 | **100 %** | 7 |
| **evidence** | 5 | 5 | **100 %** | 3 |
| **notifications** | 6 | 6 | **100 %** | 6 |
| **phasecontracts** | 4 | 4 | **100 %** | 1 |
| **points** | 5 | 5 | **100 %** | 5 |
| **rag** | 5 | 5 | **100 %** | 4 |
| **users** | 27 | 26 | 96 % | 20 |
| **support** | 11 | 9 | 82 % | 2 |
| **habits** | 41 | 25 | 61 % | 15 |
| **onboarding** | 15 | 9 | 60 % | 8 |
| **academy** | 12 | 6 | 50 % | 3 |
| **rocks** | 13 | 6 | 46 % | 5 |
| **community** | 41 | 16 | 39 % | 7 |
| **calendar** | 8 | 3 | 38 % | 1 |
| **TOTAL** | **201** | **133** | **66,2 %** | **87 (43,3 %)** |

**Lo que está impecable, y hay que decirlo:** `chat`, `evidence`, `notifications`, `phasecontracts`, `points` y `rag` tienen el **100 %** de sus endpoints protegidos con prueba de autorización negativa. `users` está al 96 % (falta uno solo) y es además el módulo con mejor cobertura de la mitad `SUSPENDED` (20 de 27). El repo tiene **71 archivos de test** con al menos una aserción de autorización negativa; el problema no es ausencia de cultura de prueba, es **distribución desigual**: se probó un método representativo por servicio y no sus hermanos.

Ese patrón se ve con nitidez en `RocaDiariaServiceTest`, que tiene las dos pruebas que §0.3 pide, escritas explícitamente contra la regla — `@DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")` (`src/test/java/com/renaser/os/rocks/application/services/RocaDiariaServiceTest.java:118`) y `@DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException")` (`:127`) — pero ambas ejercitan **sólo `completar`**. `crear`, `hoy`, `manana` y `solicitarUrl`, en la misma clase y con el mismo guard, no tienen ninguna.

### 3.3 Verificaciones manuales de la muestra

Ocho veredictos se comprobaron leyendo el archivo de test, no por heurística:

| Test | Métodos con prueba negativa (leídos) | Métodos del mismo servicio sin ella |
|---|---|---|
| `RocaDiariaServiceTest` (`:118,:127,:157`) | `completar` | `crear`, `hoy`, `manana`, `solicitarUrl` |
| `HabitoAdminServiceTest` (`:80,:87,:142,:158`) | `crear`, `eliminar`, `listar` | `actualizar`, `cambiarActivo` |
| `GuiaHabitoAdminServiceTest` (`:110,:227,:234,:279,:289`) | `upsert`, `solicitarUrl`, `confirmar` | `crear`, `listar`, `eliminar` |
| `HorarioHabitoAdminServiceTest` (`:81`) | `crear` | `actualizar`, `eliminar`, `listar` |
| `RachaServiceTest` (`:157`) | `cerrar` | `iniciar`, `romper`, `solicitarUrl` |
| `SantuarioServiceTest` (`:105`) | (una sola aserción) | `romper`, `completar` |
| `PublicacionMuroServiceTest` (`:135,:150,:203,:213,:223,:249`) | `ocultar`, `reaccionar`, `editar`, `publicarDesdeEvidencia` | `feed`, `feedOculto`, `publicar`, `restaurar`, `eliminarPermanente`, `solicitarUrl` |
| `ClaseDiariaServiceTest` (`:171,:196`) | `completar` | `claseDeHoy` |

### 3.4 Los 68 endpoints protegidos sin prueba de autorización negativa

Ordenados por riesgo, no por volumen.

#### Riesgo alto — panel de administración o mutación de datos de terceros (30)

**community — panel admin (15).** Todo el panel de células, cohortes y categorías del Muro está guardado (`CelulaService`, `CohorteService`, `CategoriaMuroService` invocan guard) pero **sin una sola prueba de que un no-admin reciba 403**:

| Endpoint | Servicio.método |
|---|---|
| `GET /api/v1/admin/cells` | `CelulaService.listarPorCohorte` |
| `GET /api/v1/admin/cells/mentores` | `CelulaService.mentores` |
| `GET /api/v1/admin/cells/{id}` | `CelulaService.obtener` |
| `PATCH /api/v1/admin/cells/{id}` | `CelulaService.actualizar` |
| `DELETE /api/v1/admin/cells/{id}` | `CelulaService.eliminar` |
| `POST /api/v1/admin/cells/{id}/session` | `CelulaService.programar` |
| `GET /api/v1/admin/cohorts` | `CohorteService.listar` |
| `GET /api/v1/admin/cohorts/{id}` | `CohorteService.obtener` |
| `PATCH /api/v1/admin/cohorts/{id}` | `CohorteService.actualizar` |
| `DELETE /api/v1/admin/cohorts/{id}` | `CohorteService.eliminar` |
| `PATCH /api/v1/admin/cohorts/{id}/status` | `CohorteService.cambiarEstado` |
| `GET /api/v1/admin/wall-categories` | `CategoriaMuroService.listarParaPanel` |
| `POST /api/v1/admin/wall-categories/reorder` | `CategoriaMuroService.reordenar` |
| `PATCH /api/v1/admin/wall-categories/{key}` | `CategoriaMuroService.actualizar` |
| `DELETE /api/v1/admin/wall-categories/{key}` | `CategoriaMuroService.eliminar` |

Controllers: `.../community/infrastructure/adapter/in/rest/{celula/CelulaAdminController, cohorte/CohorteAdminController, categoria/WallCategoryAdminController}.java`.

> **Deuda ya documentada, no hallazgo nuevo.** `docs/MODULO_COMMUNITY.md:182` lo declara: *"Pendiente (otro agente) | Tests de seguridad: 403 para rol sin permiso, 403 para `SUSPENDED`, test de reflexión de `@RequiresPermission` (bloqueado, ver §6)"*. Este informe le pone el número: **25 de los 41 endpoints de `community`**.

**habits — panel admin (9).** Controllers en `.../habits/infrastructure/adapter/in/rest/{guiaadmin,horarioadmin,habitoadmin}/`:

| Endpoint | Servicio.método |
|---|---|
| `POST /api/v1/admin/habits/{id}` | `HabitoAdminService.actualizar` |
| `POST /api/v1/admin/habits/{id}/toggle` | `HabitoAdminService.cambiarActivo` |
| `POST /api/v1/admin/habits/{habitId}/guide-attachments` | `GuiaHabitoAdminService.crear` |
| `DELETE /api/v1/admin/habits/guide-attachments/{attachmentId}` | `GuiaHabitoAdminService.eliminar` |
| `GET /api/v1/admin/habits/{habitId}/guides` | `GuiaHabitoAdminService.listar` |
| `DELETE /api/v1/admin/habits/guides/{guideId}` | `GuiaHabitoAdminService.eliminar` |
| `GET /api/v1/admin/habits/{habitId}/schedules` | `HorarioHabitoAdminService.listar` |
| `POST /api/v1/admin/habits/schedules/{scheduleId}` | `HorarioHabitoAdminService.actualizar` |
| `DELETE /api/v1/admin/habits/schedules/{scheduleId}` | `HorarioHabitoAdminService.eliminar` |

**calendar — mutación de eventos que otros ven (5).** Todos en `.../calendar/infrastructure/adapter/in/rest/evento/EventoController.java`: `GET /api/v1/calendar/events/{id}` (`EventoService.obtener`), `DELETE /api/v1/calendar/events/{id}` (`.eliminar`), `POST /api/v1/calendar/events/{id}/cancel-occurrence` (`.cancelar`), `POST /api/v1/calendar/events/{id}/portada/upload-url` (`.solicitar`), `POST /api/v1/calendar/events/{id}/portada/confirm` (`.confirmar`). `calendar` es el módulo con **peor cobertura del repo (38 %)** en este eje.

**users — el único que falta (1).** `PATCH /api/v1/users/{mentorId}/mentor-profile` (`.../users/infrastructure/adapter/in/rest/mentorprofile/MentorProfileController.java`). Es el más sensible de los tres: acepta un **id de otro usuario en la ruta** y su guard distingue dos niveles de permiso — `requireRoleManager` para nivel y estado operativo, `requireSelfOrRoleManager` para la bio (`src/main/java/com/renaser/os/users/application/services/MentorProfileService.java:41,50,56-67`). Es exactamente la lógica que §0.3 quiere fijada con una prueba, y es el único hueco de un módulo que por lo demás está al 96 %.

#### Riesgo medio — escriben datos del propio actor (17)

**community — Muro (10):** `GET /api/v1/wall` (`feed`), `POST /api/v1/wall` (`publicar`), `GET /api/v1/wall/hidden` (`feedOculto`), `POST /api/v1/wall/media/upload-url` (`solicitarUrl`), `POST /api/v1/wall/{id}/restore` (`restaurar`), `DELETE /api/v1/wall/{id}/permanent` (`eliminarPermanente`), `GET /api/v1/wall/{postId}/comments` (`ComentarioMuroService.pagina`), `GET /api/v1/me/cell` (`miCelula`), `GET /api/v1/me/cell/members` (`misCompaneros`), `POST /api/v1/testimonios` (`TestimonioService.crear`).

> Contexto que agrava esta fila: `docs/BITACORA_ERRORES.md:872` (E-42) registra que **este mismo servicio ya tuvo un bug real de esta clase** — un actor `SUSPENDIDO` podía reaccionar y comentar, y `editar`/`ocultar` no tenían **ningún** chequeo de actor. Se corrigió y se probaron `reaccionar`/`editar`/`ocultar`; `publicar`, `feed`, `feedOculto`, `restaurar`, `eliminarPermanente` y `solicitarUrl` quedaron guardados pero sin prueba. La lección 2 de esa misma entrada dice literalmente *"revisar TODOS los métodos de esa clase, no solo el reportado"* — se aplicó al código, no a las pruebas.

**habits — Santuario y racha sin celular (5):** `POST /api/v1/habit-tracks/{id}/phone-free/start` (`RachaService.iniciar`), `POST /api/v1/habit-tracks/phone-free/break` (`.romper`), `POST /api/v1/habit-tracks/phone-free/evidence/upload-url` (`.solicitarUrl`), `POST /api/v1/habit-tracks/{id}/santuario/break` (`SantuarioService.romper`), `POST /api/v1/habit-tracks/{id}/santuario/complete` (`.completar`).

**rocks (2):** `POST /api/v1/rocks/plan` (`RocaDiariaService.crear`), `PATCH /api/v1/rocks/weekly/{id}/review` (`RocaSemanalService.cerrar`).

#### Riesgo menor — lecturas del propio actor (21)

- **academy (6):** `GET /api/v1/classroom/clase-diaria` (`ClaseDiariaService.claseDeHoy`), `GET /api/v1/academia/recomendacion`, `GET /api/v1/cursos/{id}/preview`, `GET /api/v1/cursos/{id}/secciones`, `GET /api/v1/lecciones/{id}`, `GET /api/v1/lecciones/{id}/preview`.
- **onboarding (6):** `PUT /api/v1/onboarding/state` (`avanzar`), `POST /api/v1/onboarding/complete` (`completar`), `POST /api/v1/onboarding/milestones` (`aceptar`), `POST /api/v1/onboarding/media` (`MediaService.registrar`), `POST` y `GET /api/v1/onboarding/v90-recordings` (`GrabacionV90Service.registrar`/`.listar`).
- **rocks (5):** `GET /api/v1/rocks/today`, `GET /api/v1/rocks/tomorrow`, `GET /api/v1/rocks/weekly`, `GET /api/v1/enforcer-events`, `POST /api/v1/rocks/{id}/evidence/upload-url`.
- **habits (2):** `GET /api/v1/journal/today` (`BitacoraNocturnaService.consultarHoy`), `DELETE /api/v1/habits/{habitId}/rename` (`RenombreHabitoService.quitar`).
- **support (2):** `GET /api/v1/support-tickets` (`misTickets`), `POST /api/v1/support-tickets/attachments/upload-url` (`solicitar`).

### 3.5 La mitad olvidada: `SUSPENDED`

§0.3 pide **dos** pruebas por endpoint. **46 de los 133 endpoints cubiertos tienen la de rol sin permiso pero no la de `SUSPENDED`**, repartidos así: habits 10, community 9, support 7, users 6, academy 3, phasecontracts 3, calendar 2, evidence 2, chat 1, onboarding 1, rag 1, rocks 1.

Esto no es teórico en este repo: E-42 (`docs/BITACORA_ERRORES.md:872`) es exactamente el bug de "actor suspendido pasa", y fue encontrado **por un E2E manual, no por los tests** — la lección 1 de esa entrada lo dice con todas las letras. La brecha de `SUSPENDED` es, en la evidencia histórica del propio proyecto, la que ya se cobró un incidente.

---

## 4. §0.3 — El rol no se puede inyectar

> *"verificar que mandar `role` en el body de un alta pública no lo cambia (§5.3.3)"*

### 4.1 El blindaje: existe, y es del compilador

Está bien construido, en tres capas, y razonado en el propio código:

- El DTO web público **no tiene campo `role`**: `SubmitAccountRequestRequest` son 6 componentes (`email`, `fullName`, `phone`, `city`, `verificationToken`, `contrasena`) y el javadoc lo declara — *"Sin campo role a proposito: CLAUDE.MD §5.3.3"* (`src/main/java/com/renaser/os/users/infrastructure/adapter/in/rest/accountrequest/SubmitAccountRequestRequest.java:6-22`).
- Tampoco lleva id de usuario: el UUID lo genera el backend (D-49) y el antiguo `supabaseUserId` fue eliminado (mismo archivo, `:6-8`).
- **El blindaje de identidad social es todavía más fino.** `SubmitAccountRequestCommand` **sí** tiene `proveedor` y `sujetoProveedor`, pero el adaptador web **no puede construirlos**: existe una factory `porFormulario(...)` de 7 parámetros que los fija en `null`, y otra `porProveedorSocial(...)` que sólo arma `AutenticacionSocialService` con una identidad ya verificada contra Google/Apple (`src/main/java/com/renaser/os/users/application/ports/in/accountrequest/SubmitAccountRequestUseCase.java:85-107`). El javadoc razona el porqué: *"si el cliente pudiera hacer llegar un `sujetoProveedor`, cualquiera reclamaria la identidad social de otro con solo conocer su `sub`. Lo impide el compilador, no un `if`"* (`:78-84`).

### 4.2 La prueba: falta justo en el alta pública

**`SubmitAccountRequestCommandTest` tiene 4 pruebas y ninguna es la de §0.3:** `construyeUnComandoConDatosValidosSinExplotar` (`src/test/java/com/renaser/os/users/application/ports/in/accountrequest/SubmitAccountRequestCommandTest.java:11`), `aceptaContrasenaNullParaElAltaPorProveedorSocial` (`:20`), `rechazaUnaContrasenaMasCortaQueElMinimo` (`:29`), `noFiltraLaContrasenaNiElTokenEnElToString` (`:37`).

Además, **`SubmitAccountRequestRequest` no aparece referenciado en ningún archivo de `src/test`** (grep sobre todo el árbol de test: 0 resultados). El DTO del alta pública no tiene prueba alguna.

Es un hueco de **fijación**, no de implementación: hoy el código es correcto, pero nada impide que alguien agregue un campo `role` al record o convierta la factory en un constructor público, y el build siga en verde.

Lo mismo vale para el equivalente nuevo que pedía el encargo: **no existe ninguna prueba que verifique que el DTO público no puede recibir `sujetoProveedor`**, pese a que su javadoc identifica explícitamente el riesgo como suplantación de identidad social.

### 4.3 El patrón correcto ya existe en el repo — en otros cinco lugares

La prueba que falta está escrita, casi literalmente, en otro lado:

```java
// src/test/java/com/renaser/os/phasecontracts/application/ports/in/contrato/FirmarContratoCommandTest.java:31-37
@DisplayName("el comando SOLO tiene participanteId: no hay campo `fase` para inyectar (CLAUDE.MD §5.3.3)")
void noTieneCampoFaseInyectable() {
    assertThat(FirmarContratoCommand.class.getRecordComponents()).hasSize(1);
    assertThat(FirmarContratoCommand.class.getRecordComponents()[0].getName()).isEqualTo("participanteId");
}
```

El mismo patrón, por reflexión sobre los `RecordComponents` o por aserción equivalente, está en:

- `src/test/java/com/renaser/os/onboarding/application/ports/in/estado/AceptarHitoCommandTest.java:40`
- `src/test/java/com/renaser/os/phasecontracts/application/ports/in/contrato/ObtenerUrlFirmaContratoCommandTest.java:32`
- `src/test/java/com/renaser/os/points/application/ports/in/puntaje/AjustarPuntosManualmenteCommandTest.java:45`
- `src/test/java/com/renaser/os/users/application/services/UserAccountServiceTest.java:175` (perfil propio: sin `role`/`programDay`)
- `src/test/java/com/renaser/os/support/application/services/TicketMentorServiceTest.java:196` (*"el rol no se puede inyectar"*)
- `src/test/java/com/renaser/os/users/infrastructure/adapter/in/rest/participante/ParticipacionProgramaControllerTest.java:109` (anti mass-assignment sobre `activate()`)

**Conclusión:** el proyecto sabe escribir esta prueba y la escribió seis veces; **no la escribió en el único lugar que §0.3 nombra por su nombre** — el alta pública.

---

## 5. §0.3 — Test de reflexión: qué verificación sí sirve hoy

`@RequiresPermission`/`@PublicEndpoint` no existen (deuda confirmada en `docs/MODULO_USERS.md:578`: *"El enum `Permission` y `@RequiresPermission` siguen sin construirse (bloqueado por R-2...)"*), así que el test literal de §0.3 no es escribible. La autorización vive en los servicios. Estas cuatro pruebas **son escribibles hoy, sin construir ninguna anotación**, y atacan las brechas medidas arriba.

### 5.1 "Todo método público de caso de uso pasa por un guard" — la que más rinde

Una regla ArchUnit que, para cada clase `*Service` de `application/services` que implementa una interfaz `*UseCase`, recorra sus métodos públicos y **falle si ninguna llamada alcanzable desde ese método toca un guard conocido** (`NotAuthorizedException`, `Require*Guard.*`, `esModerador`, `actorActivo`, `require*`). Se implementa con un `ArchCondition` sobre `JavaMethodCall` transitivo — el mismo mecanismo que ya usa la regla `controllersDoNotTouchPersistence`.

Encaja en el archivo que **ya existe y ya corre en cada build**: `src/test/java/com/renaser/os/ArchitectureTest.java` (hoy 6 reglas, `:24,:30,:44,:54,:67,:81`). Se agrega como séptima, con una lista de excepciones **explícita y comentada** para los casos legítimamente públicos: `AutenticacionService.iniciarSesion`, `AutenticacionSocialService.iniciarSesion`, `AccountRequestService.submit`/`consultar`, `ConsultaEmailService.*`, `ResetContrasenaService.solicitar`/`confirmar`, `VerificacionEmailService.enviar`, `CategoriaMuroService.listarPublicas`, `TestimonioService.listarDestacados`, y `PublicacionMuroService.ultimoAutor` si se decide dejarlo público (§6.2).

Esa lista de excepciones **es** el inventario de endpoints públicos, que hoy no está escrito en ningún lado; revisarla en cada PR cumple funcionalmente el rol que §0.3 le asignaba a `@PublicEndpoint`. Y la regla habría detectado E-42 (`ComentarioMuroService.escribir/editar/ocultar` sin ningún guard) antes del E2E manual.

### 5.2 "Todo método público guardado tiene su prueba negativa"

El mismo cruce que produjo §3 de este informe, convertido en test: para cada método público de `*Service` que alcance un guard, exigir que exista en su `*ServiceTest` al menos un `@Test` que lo invoque y espere `NotAuthorizedException`. Se implementa leyendo el bytecode de los tests con ArchUnit (los `JavaMethodCall` desde clases `*Test` hacia el método, filtrando los que además referencian `NotAuthorizedException`).

Se arranca con una lista de pendientes de 68 entradas (§3.4) que sólo puede **achicarse**: congela la deuda medida y prohíbe que crezca. Es la única de las cuatro que ataca directamente el 33,8 % faltante.

### 5.3 "El actor autenticado no se ignora"

Regla barata y de alto valor: fallar si un handler declara `@ActorAutenticado` y **no usa** ese parámetro en su cuerpo. Se barrieron los 221 endpoints y hoy hay **exactamente un** caso — y es un hallazgo real (§6.2): `WallController.latestAuthor` (`src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/publicacion/WallController.java:136-141`). Una regla que hoy pasa con una sola excepción es una regla que se puede activar mañana.

### 5.4 "Los comandos de entrada no tienen campos privilegiados"

Generalizar el patrón de `FirmarContratoCommandTest` (§4.3) a **todos** los `record` de `application/ports/in/**Command`: fallar si alguno declara un componente llamado `role`, `rol`, `estado`, `status`, `programDay`, `diaPrograma`, `coherenceScore`, `leaguePoints`, `currentPhase`, `fase`, `proveedor` o `sujetoProveedor` **y** es construible desde un adaptador web (constructor canónico público sin factory restrictiva). Una sola prueba cubre los 14 módulos y cierra el hueco de §4.2 de forma permanente, en vez de agregar un test por comando.

---

## 6. Superficie de seguridad, más allá de las reglas

Se da por conocido y auditado que `SecurityConfig` está en `permitAll()` y que el actor sale del header `X-Actor-Id`. Lo que sigue es lo que hay **además** de eso.

### 6.1 Oráculo de enumeración de participantes — el hallazgo más grave

`ParticipacionProgramaService` carga el recurso **antes** de aplicar el gate de admin, en cuatro métodos:

```java
// src/main/java/com/renaser/os/users/application/services/ParticipacionProgramaService.java:242-249
public void fijarDia(SetProgramDayCommand command) {
    ParticipacionPrograma participacion = loadParticipacionProgramaPort.byParticipanteId(command.traineeId())
            .orElseThrow(() -> new NoSuchElementException(
                    "Participante no inscripto en el programa: " + command.traineeId()));
    requireAdminGuard.requireAdminActivo(command.actorId());
    ...
}
```

Mismo orden en `assign` (`:259-265`), `remove` (`:272-278`) y `obtener` (`:231-236`).

**Efecto observable para un actor que NO es admin:**

- `traineeId` inscripto en el programa → **403**
- `traineeId` inexistente o no inscripto → **404**, con el mensaje `"Participante no inscripto en el programa: <uuid>"`

Es decir: un actor sin permiso puede **distinguir por código de estado si un UUID dado corresponde a un participante inscripto**, y el cuerpo del error se lo confirma por escrito. Es un oráculo de enumeración sobre la tabla de participantes, expuesto en cuatro endpoints (`GET /api/v1/admin/trainees/{traineeId}`, `PUT /api/v1/participants/{traineeId}/...`, y las dos operaciones de célula).

**Por qué no es un hallazgo enteramente nuevo, y por qué igual lo reporto como el más grave:** el orden es **deliberado y está documentado** como E-42 (`docs/BITACORA_ERRORES.md:872`, lección 4) y reafirmado en `docs/MODULO_USERS.md:305-306` y `:572`. Pero el razonamiento registrado cubre **un solo escenario**, el del *actor* inválido: *"así un actor inválido siempre cae a 403, nunca a un 404 con mensaje distinto que delataría si el recurso existía"*. Eso es correcto para el actor. **El escenario del recurso probado por un no-admin no está razonado en ninguna parte**, y en ese escenario el orden elegido produce exactamente la fuga que la regla pretendía evitar.

La contradicción es visible dentro del propio repo: `docs/MODULO_COMMUNITY.md:157` (CM-19) resuelve el caso simétrico **al revés**, haciendo `esModerador()` fail-closed a `false` para que un actor inexistente dé 403 y no 404 — *"un mensaje que solo se alcanza si el comentario/publicación YA se confirmó que existe, filtrando esa existencia a quien prueba con un actor falso"*. `community` falla cerrado; `users` falla abierto sobre el mismo eje.

**Camino sugerido (la decisión es de producto, no la tomo acá — §0.6):** no invertir el orden a secas, porque eso reintroduciría lo que E-42 arregló. `RequireAdminGuard` ya es fail-closed y **nunca lanza `NoSuchElementException`** (`docs/MODULO_USERS.md:298-301`), así que puede ir **antes** del `load` sin recrear el problema original; alternativamente, devolver el mismo 403 genérico ante actor no-admin sin importar si el recurso existía.

### 6.2 `GET /api/v1/wall/latest-author` — nombre completo de un tercero, sin ninguna verificación

Único endpoint del sistema que **recibe el actor y no lo usa**:

```java
// src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/publicacion/WallController.java:136-141
@GetMapping("/latest-author")
public Map<String, String> latestAuthor(@ActorAutenticado UserId actorId) {
    Map<String, String> body = new HashMap<>();
    body.put("authorName", consultarFeedUseCase.ultimoAutor().orElse(null));
    return body;
}
```

`ultimoAutor()` no recibe actor ni consulta guard alguno (`src/main/java/com/renaser/os/community/application/services/PublicacionMuroService.java:196-200`): resuelve la última publicación visible y devuelve `PerfilUsuario::nombreCompleto`. **Cualquiera** — incluido un actor suspendido, inexistente o de otra cohorte — obtiene el nombre completo de la última persona que publicó en el Muro. Es dato personal de un tercero servido sin ninguna verificación de relación, en un módulo donde el resto de las lecturas (`feed`, `feedOculto`) sí guardan (`:285,:310,:329`). No está documentado como decisión en `docs/MODULO_COMMUNITY.md`.

Es el único caso de este tipo: se barrieron los 221 endpoints buscando handlers que ignoran el actor (1 resultado, éste) y los 71 controllers buscando ids de terceros en la ruta (6 resultados, los otros 5 correctamente guardados — §6.3).

### 6.3 Lo que se buscó y salió limpio

- **Ids de terceros en la ruta.** Seis endpoints reciben el id de otro usuario: `DELETE /api/v1/admin/cells/{id}/trainees/{traineeId}`, `GET /api/v1/admin/trainees/{traineeId}/habits`, `GET /api/v1/points/{participanteId}`, `PATCH /api/v1/users/{mentorId}/mentor-profile`, `PUT /api/v1/participants/{traineeId}/mentor`. Todos tienen guard. El mejor resuelto es `PuntajeService.consultar`: hace self-or-admin **y además** revalida que el propio actor no esté suspendido antes de dejarlo leer su propio puntaje (`src/main/java/com/renaser/os/points/application/services/PuntajeService.java:84-93`), con comentario explicando esa tercera capa. `MentorProfileService` distingue correctamente `requireRoleManager` de `requireSelfOrRoleManager` (`.../MentorProfileService.java:41,50`).
- **404 antes de 403.** Se barrieron los ~90 servicios buscando métodos donde un `orElseThrow`/`NoSuchElementException` precede al guard. Cinco resultados: tres son los de §6.1 y **dos son falsos positivos verificados a mano** — `ClaseDiariaService.claseDeHoy` guarda primero con `requireProgresoTrainee` (`src/main/java/com/renaser/os/academy/application/services/ClaseDiariaService.java:68`) y `ConfirmacionService.confirmar` con `accesoEventoService.requireProgreso` (`src/main/java/com/renaser/os/calendar/application/services/ConfirmacionService.java:55`).
- **`GET /api/v1/account-requests/{id}/status`** es público a propósito y devuelve una proyección mínima: `AccountRequestStatusResponse(status, rejectionReason)`, con javadoc *"PUBLIC_ENDPOINT (gap #9): 'mi solicitud'. Proyeccion minima, sin datos personales"* (`src/main/java/com/renaser/os/users/infrastructure/adapter/in/rest/accountrequest/AccountRequestStatusResponse.java:5-7`). Quien tenga el UUID de una solicitud ve su estado y motivo de rechazo; el UUID no es adivinable y no se expone PII. Aceptable.
- **Mitigación de ataque de temporización en login**, que ninguna regla pedía y está igual: se compara contra un hash BCrypt señuelo cuando el email no existe, para que `passwordEncoder.matches` haga siempre el mismo trabajo (`docs/MODULO_USERS.md:571`, `AutenticacionService.java:43-52`).
- **`domain/` no loguea**: `grep` de `Logger`/`log.` sobre `domain/` da 0 resultados, consistente con §5.4.9 (verificado en `docs/MODULO_USERS.md:566`).

---

## 7. §0.2 — Pruebas de integración con Testcontainers

> *"obligatorias para todo adaptador nuevo (persistencia, web, IA)"*

La infraestructura es real y correcta: `TestcontainersConfiguration` levanta **Postgres con pgvector** (`pgvector/pgvector:pg16`) y **Redis**, ambos vía `@ServiceConnection` (`src/test/java/com/renaser/os/TestcontainersConfiguration.java:23-34`). Los tests de adaptador inyectan los **puertos**, no la clase concreta — p. ej. `CredencialPersistenceAdapterTest` es `@SpringBootTest @Import(TestcontainersConfiguration.class) @Transactional` y autowirea `SaveUserPort`/`LoadCredencialPort` (`:27-35`). Por eso la cobertura se midió por **nombre de archivo O referencia a la clase en cualquier test**, no sólo por convención de nombre.

### 7.1 Cuantificación

**90 adaptadores en `adapter/out/persistence`. 51 con prueba de integración (57 %). 39 sin ninguna.**

| Módulo | Sin prueba / total |
|---|---|
| evidence | 0 / 1 |
| notifications | 0 / 3 |
| onboarding | 0 / 6 |
| phasecontracts | 0 / 2 |
| points | 0 / 5 |
| rocks | 0 / 7 |
| support | 0 / 2 |
| users | 0 / 7 |
| rag | 2 / 3 |
| academy | 6 / 9 |
| community | 9 / 11 |
| habits | 10 / 21 |
| calendar | **9 / 10** |
| chat | **3 / 3** |

**Impecables (100 %):** `evidence`, `notifications`, `onboarding`, `phasecontracts`, `points`, `rocks`, `support`, `users` — ocho de catorce módulos con todos sus adaptadores de persistencia probados contra Postgres real.

**En rojo:** `chat` (3 de 3 sin prueba) y `calendar` (9 de 10, incluido `EventoPersistenceAdapter`, el adaptador central del módulo). Nótese la inversión exacta respecto del cuadro de §3.2: `chat` tiene el 100 % de autorización negativa y el 0 % de Testcontainers; `community` está al revés.

### 7.2 Los 39 sin prueba de integración

**calendar (9):** `EventoPersistenceAdapter`, `ExcepcionPersistenceAdapter`, `ConfirmacionPersistenceAdapter`, `RecordatorioPersistenceAdapter`, `NivelMembresiaPersistenceAdapter`, `ConsultarMiembrosCelulaCalendarPersistenceAdapter`, `ResolverAudienciaCursoAdapter`, `ResolverAudienciaMasivaPersistenceAdapter`, `ElegibilidadEventoNoOpAdapter`.

**habits (10):** `HabitoPersistenceAdapter`, `HorarioHabitoPersistenceAdapter`, `PreferenciaHorarioPersistenceAdapter`, `HistorialCambioHorarioPersistenceAdapter`, `EntradaDiarioPersistenceAdapter`, `RenombreHabitoPersistenceAdapter`, `RegistroEspirituPersistenceAdapter`, `DesbloqueoHabitoPersistenceAdapter`, `EleccionDiaSemanalPersistenceAdapter`, `ListarParticipantesActivosPersistenceAdapter`, `ConsultarProgresoParticipanteHabitsPersistenceAdapter`.

**community (9):** `CelulaPersistenceAdapter`, `CohortePersistenceAdapter`, `CategoriaMuroPersistenceAdapter`, `ReaccionMuroPersistenceAdapter`, `TestimonioPersistenceAdapter`, `ConsultarPerfilUsuarioPersistenceAdapter`, `ConsultarCelulaDeParticipantePersistenceAdapter`, `ConsultarMiembrosCelulaCommunityPersistenceAdapter`, `ExistePerfilMentorPersistenceAdapter`.

> **Deuda ya documentada:** `docs/MODULO_COMMUNITY.md:181` nombra explícitamente como pendiente el `CAST(? AS renaser.tipo_reaccion)` de `ReaccionMuroPersistenceAdapter` y *"las queries nativas cross-módulo (`participantes_programa`, `perfiles_mentor`, `usuarios`) contra el esquema real"* — que son justamente los tres `Consultar*`/`Existe*` de esta lista.

**academy (6):** `SeccionCursoPersistenceAdapter`, `RecursoLeccionPersistenceAdapter`, `AsignacionCursoPersistenceAdapter`, `MiembroGrupoPersistenceAdapter`, `RecomendacionAcademiaPersistenceAdapter`, `ConsultarProgresoParticipanteAcademyPersistenceAdapter`.

**chat (3):** `ConversacionPersistenceAdapter`, `MensajePersistenceAdapter`, `ParticipanteConversacionPersistenceAdapter`.

**rag (2):** `ConversacionRenasiaPersistenceAdapter`, `MensajeRenasiaPersistenceAdapter`.

**Por qué esto importa más de lo que aparenta:** `docs/MODULO_COMMUNITY.md:186` (E-31) documenta que `feed`/`feedOculto`/`pagina` devolvían **500 contra Postgres real** por el patrón `(:x IS NULL OR col < :x)` en JPQL, y que fue *"no detectado por los tests con mocks"*. La misma clase de defecto — SQL que compila y falla en el `prepare` — es indetectable en los 39 adaptadores sin prueba de integración. `calendar` y `chat`, los dos módulos en cero, son además los que más queries de rango, recurrencia y paginación por cursor concentran.

### 7.3 Otros adaptadores de salida

Fuera de `persistence`: **4 de 5 adaptadores Redis** tienen prueba (falta `ControlCuotaRedisAdapter`, `src/main/java/com/renaser/os/rag/infrastructure/adapter/out/redis/`). De los 18 restantes de `adapter/out/**` (IA, email, DNS, push, almacenamiento), **11 tienen prueba y 7 no**; de esos 7, cinco son `NoOp*` triviales y dos no lo son: `DnsResolverMxAdapter` (`src/main/java/com/renaser/os/users/infrastructure/adapter/out/dns/`) y `DespacharValidacionV90Adapter` (`src/main/java/com/renaser/os/onboarding/infrastructure/adapter/out/async/`).

---

## 8. §0.2 — Pruebas unitarias de dominio

> *"obligatorias para toda regla de negocio nueva. Sin Spring, sin base de datos."*

**Éste es el eje mejor cumplido del informe.** De 213 clases bajo `domain/`, **66 tienen lógica real** — el criterio aplicado fue ≥3 entre `if`/`switch`/`throw`, o ≥2 `throw` con ≥3 métodos, que descarta los `record` que sólo llevan datos y los enums planos, tal como pedía el encargo. De esas 66:

- **65 están ejercitadas por al menos un test.**
- **56 tienen su propio `*Test` dedicado.**
- **1 sola no aparece en ningún archivo de test:** `Grupo` (`src/main/java/com/renaser/os/academy/domain/model/asignacion/Grupo.java`; 3 `if`, 1 `throw`, 6 métodos).

Las 9 restantes con lógica que no tienen archivo de test propio pero sí se ejercitan indirectamente, ordenadas por densidad de reglas. Son candidatas a prueba unitaria directa, no hallazgos:

| Clase | if / throw | Archivo |
|---|---|---|
| `ReglaRecordatorio` | 6 / 5 | `src/main/java/com/renaser/os/calendar/domain/model/evento/ReglaRecordatorio.java` |
| `Recurrencia` | 4 / 4 | `src/main/java/com/renaser/os/calendar/domain/model/evento/Recurrencia.java` |
| `MediaPublicacion` | 4 / 4 | `src/main/java/com/renaser/os/community/domain/model/publicacion/MediaPublicacion.java` |
| `AdjuntoGuia` | 3 / 3 | `src/main/java/com/renaser/os/habits/domain/model/guia/AdjuntoGuia.java` |
| `AccionCritica` | 3 / 3 | `src/main/java/com/renaser/os/rocks/domain/model/rocasemanal/AccionCritica.java` |
| `UserId` | 2 / 3 | `src/main/java/com/renaser/os/shared/domain/UserId.java` |
| `RecomendacionAcademia` | 3 / 1 | `src/main/java/com/renaser/os/academy/domain/model/recomendacion/RecomendacionAcademia.java` |
| `DetallesHabito` | 2 / 2 | `src/main/java/com/renaser/os/habits/domain/model/habito/DetallesHabito.java` |
| `AdjuntoSoporte` | 2 / 2 | `src/main/java/com/renaser/os/support/domain/model/ticketsoporte/AdjuntoSoporte.java` |

`ReglaRecordatorio` y `Recurrencia` son las dos de mayor densidad de reglas del listado, y pertenecen al módulo (`calendar`) que ya está último en los otros dos ejes.

---

## 9. Qué quedó sin verificar, y por qué

Por §0.2 (*"si algo quedó sin probar, se dice explícitamente qué y por qué"*):

1. **No se ejecutó `./mvnw clean test`** — instrucción explícita del encargo (build en curso, otros agentes trabajando). **Ningún número de este informe afirma que los tests pasen**; sólo afirma qué tests existen y qué verifican, leyendo el código. No había informes en `target/surefire-reports/` para consultar.
2. **La detección de guards es estática.** Sigue hasta 3 niveles de llamadas privadas **dentro de la misma clase**; no cruza a otros servicios. Por eso `TracksDelDiaProyeccionService`, `HabitosDelDiaFinderService` y `EventoService.listar` figuraron primero como "sin guard" y se descartaron a mano. Puede haber casos simétricos clasificados como "protegido" cuya protección real esté en otro lado — eso no cambiaría el conteo de §3, pero sí podría existir algún endpoint clasificado como público que debiera estar guardado.
3. **La correspondencia prueba↔método es por nombre de método invocado** dentro de un `@Test` que menciona `NotAuthorizedException`/`403`. Se verificaron 8 casos a mano (§3.3) y los 8 coincidieron, pero no se verificaron los 201.
4. **No se auditaron dependencias con vulnerabilidades conocidas** (último punto de §0.3): requiere ejecutar Maven.
5. **No se revisó `adapter/in/web` ni los adaptadores de IA** desde la óptica de Testcontainers; §7 cubre `adapter/out/**`, que es lo que pedía el encargo.

---

## 10. Orden sugerido de trabajo

Por riesgo real, no por volumen:

1. **Cerrar el oráculo de enumeración de §6.1** (4 métodos de `ParticipacionProgramaService`) y registrar la decisión. Es el único hallazgo con impacto de confidencialidad real más allá del `X-Actor-Id`.
2. **Decidir qué hacer con `GET /api/v1/wall/latest-author`** (§6.2): guardarlo o documentarlo como público a propósito. Cinco minutos, y hoy no está escrito en ningún lado.
3. **Escribir la prueba de §4.2** sobre `SubmitAccountRequestRequest`/`SubmitAccountRequestCommand` — la única regla de §0.3 que el proyecto cumple en el código y no fija con un test, y que ya tiene seis ejemplos de cómo escribirla en el propio repo.
4. **Agregar la regla 5.1 a `ArchitectureTest`** ("todo caso de uso pasa por un guard") con su lista de excepciones explícita: convierte el inventario de endpoints públicos en algo revisable en PR y habría atrapado E-42.
5. **`calendar` y `chat` primero** en la deuda de §3 y §7: son los dos módulos que están abajo en los dos ejes a la vez.
6. **Los 15 endpoints del panel admin de `community`** (§3.4) — ya declarados deuda en `docs/MODULO_COMMUNITY.md:182`; este informe sólo les pone el número.
7. **Cerrar la mitad `SUSPENDED`** en los 46 endpoints de §3.5, empezando por `habits` (10) y `community` (9), que son los módulos donde E-42 ya se cobró un incidente.
