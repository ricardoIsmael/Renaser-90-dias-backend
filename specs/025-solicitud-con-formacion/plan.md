# Plan de implementación: aceptar una solicitud con formación (spec 025)

**Rama**: `025-solicitud-con-formacion` (backend y app) · **Números**: V140–V142, D-302 · **Se apoya en**: 022-formaciones.

## Resumen
Aceptar una solicitud pasa a ser una decisión de tres pasos —formación (y duración), rol, ficha— que el servidor aplica en una sola transacción. La pertenencia viva ya la modela la 022
(`participantes_programa`, `formaciones_personal`); la marca «con diagnóstico» es de la 026 (`onboarding`); la 025 solo orquesta y guarda **una foto de lo elegido** para el historial.

## Constitution Check
| Principio | Cómo se cumple |
|---|---|
| I. Dependencias hacia el dominio | Reglas puras en `ReglasDeIngreso` (sin Spring). `users` no importa `onboarding`: la ficha entra por `users.api.FichaAlAceptar`, que implementa `onboarding` (patrón de `GruposDelPersonalFinder`). El controller invoca un caso de uso. |
| II. No inventar reglas | Todo sale de `decisiones-del-dueno-2026-10-31.md`; lo asumido está en `spec.md` y en el reporte. |
| III. Pruebas | Unitarias de dominio y servicio; `AceptarConFormacionIT` contra Postgres (formaciones, atomicidad, concurrencia, listado y filtro, 403); app: Jest de utilidades, API y cableado. |
| IV. La base guarda historia | V140/V141/V142 aditivas con cabecera; `ajustes_dia_programa` sigue append-only. |
| V. Tiempo del participante | `Clock` en `cambiarDuracion`. |
| VII. Documentar | D-302 en `docs/MODULOS_A_AVANZAR.md`; entrada en `AGENTS.md` de la app. |

## Decisiones de diseño
1. **Sin tabla nueva.** Foto en 4 columnas de `solicitudes_cuenta`; duración en la bitácora existente.
2. **Atomicidad (FR-008).** Un solo `@Transactional`: el rol, la inscripción, las formaciones del personal y la ficha se deshacen juntos. `IngresoPorAceptacion` no abre transacción propia.
3. **Concurrencia (FR-018).** `LoadAccountRequestPort.byIdParaDecidir` lee con `PESSIMISTIC_WRITE`; la segunda aceptación espera y encuentra la solicitud ya decidida → 409.
4. **Compatibilidad (FR-013, FR-020).** Sin `formations` es la Escuela y su duración, sin comprobar que esté activa; la app nueva contra un servidor anterior no manda formaciones (el servidor viejo las ignoraría en silencio).
5. **Aceptar varias** (025-3 C) = varias llamadas al mismo endpoint, una por una: cada una es su propia transacción y una que falla no deshace las demás.
6. **El rol personalizado** se resuelve por su rol base después de `updateRole` (la formación se pide según ese rol).
7. **Costura con la 026**: `FichaAlAceptar` opcional (`ObjectProvider`) **hasta integrar la 026**; sin implementación, «con diagnóstico» se rechaza con 409 en vez de no hacer nada; `FichaAlAceptarImplementadaTest` obliga a implementarla al integrar.
8. **Correcciones (FR-014/015)**: se componen con lo que ya hay —`POST /admin/trainees/{id}/formacion` (022, sin historia antes del Día 1), `PUT /admin/users/{id}/formaciones` (022), «Equipo y roles» y la ficha de la 026—; la 025 suma lo que faltaba: cambiar la duración de quien ya empezó.

## Estructura (backend)
```
users/api/FichaAlAceptar
users/domain/model/accountrequest/{PedidoDeIngreso, EleccionDeIngreso, ReglasDeIngreso}
users/application/services/{IngresoPorAceptacion, OpcionesDeAceptacionService, CambioDeDuracionDelProgramaService}
users/application/ports/in/accountrequest/ConsultarOpcionesDeAceptacionUseCase
users/application/ports/in/formacion/CambiarDuracionDelProgramaUseCase
users/infrastructure/adapter/in/rest/accountrequest/{OpcionesDeAceptacionResponse, ApproveAccountRequestRequest, AccountRequestResponse}
db/migration/{V140__solicitudes_con_lo_elegido_al_aceptar, V141__cambio_de_duracion_en_los_ajustes_del_programa, V142__rol_personalizado_en_lo_elegido_al_aceptar}.sql
```
## Estructura (app)
```
features/admin/{utils/aceptarSolicitud, utils/aceptarVarias, hooks/useOpcionesDeAceptacion, components/{AceptarSolicitudSheet, BloqueDeAceptacion}, screens/SolicitudesAdminScreen}
features/formaciones/{components/CambiarDuracionSheet, utils/borradorDeDuracion}
.maestro/solicitud-con-formacion/
```
