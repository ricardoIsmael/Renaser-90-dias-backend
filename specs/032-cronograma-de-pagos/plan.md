# Plan de implementación: cronograma de pagos (spec 032)

**Rama**: `032-cronograma-de-pagos` (backend y app) · **Números**: V158 (V159 y V160 quedan libres), D-307 · **Se apoya en**: 022 (formaciones), 025 (hoja «Aceptar»), 026 (marca del diagnóstico), 027 (términos).

## Resumen
Al aceptar una solicitud, Administración decide si la persona pagó completo («sin cronograma») o paga en cuotas («con cronograma», con moneda y fechas fijas). La persona lo ve en Yo → «Mi ingreso»
(la maqueta del dueño) y lo confirma una vez; Administración y Alquimista marcan cada cuota pagada o pendiente desde la ficha; el teléfono avisa antes de cada cuota con alarmas locales. **La app no
cobra, no bloquea y no recalcula nada.** Los datos viven en el módulo `onboarding`: tres columnas de `estado_onboarding` (modalidad, moneda, confirmación) y una tabla nueva de cuotas.

## Constitution Check
| Principio | Cómo se cumple |
|---|---|
| I. Dependencias hacia el dominio | Reglas puras en `onboarding/domain/model/pagos` (`Cuota`, `CronogramaDePagos`, `PagoDeLaPersona`), sin Spring. `users` no importa `onboarding`: el pago entra por `users.api.PagosAlAceptar` (que implementa `onboarding`), igual que `FichaAlAceptar`. Los controllers invocan un caso de uso. |
| II. No inventar reglas | Todo sale de `decisiones-del-dueno-2026-10-31.md` y de la maqueta; lo asumido está en `spec.md → Lo que se asume` y en el reporte. |
| III. Pruebas | Unitarias de dominio y de servicio (con salidas en memoria); `CronogramaDePagosIT` y `AceptarConFormacionIT` contra Postgres (recorrido, roles, aislamiento entre las tres formaciones, atomicidad, restricciones de la base); app: Jest. |
| IV. La base guarda historia | V158 aditiva y con cabecera; lo pagado no se edita ni se borra; el «quién y cuándo» de cada cuota queda. |
| V. Tiempo del participante | Las fechas son del calendario (sin zona): no hay reloj que desfasar. La hora de una marca sale del `Clock`. «Hoy» para los recordatorios es el del teléfono. |
| VI. Los cinco tabs | Yo suma una fila y una sección de Alarmas; autorizado por el dueño con su maqueta; se anota en `AGENTS.md`. |
| VII. Documentar | D-307 en `docs/MODULOS_A_AVANZAR.md`; entrada en `AGENTS.md` de la app. |

## Decisiones de diseño
1. **Sin tablas de cronogramas, planes ni pagos.** Una tabla (`cuotas_de_pago`) y cinco columnas de `estado_onboarding` (1:1 con la persona, como la marca del diagnóstico). La modalidad nula = «sin anotar».
2. **Dos estados por cuota, nada derivado de la fecha.** La maqueta no tiene «vencida» y la decisión 7 es no reprochar ni bloquear. El único dato derivado es la **vigencia de la confirmación**.
3. **La confirmación se invalida sola** por una huella SHA-256 de la moneda y de las fechas y montos (sin el estado de pago). Así ningún camino que edite cuotas tiene que acordarse de borrarla y marcar un pago no la toca.
4. **Quién opera.** Permiso nuevo `MANAGE_PAYMENTS`; el 403 de mentor, guía y Soporte TI lo da `GuardiaDePagos` (el interceptor falla abierto para esos roles, A-1), igual que la Caja y el diagnóstico. El aprendiz y el líder los rechaza el interceptor.
5. **Atomicidad al aceptar.** `IngresoPorAceptacion` llama a `PagosAlAceptar` dentro de la transacción de aceptar; el adaptador reutiliza el mismo caso de uso de Administración. Un plan inválido es 400 y deshace todo.
6. **Aceptar varias a la vez** = varias llamadas con el mismo `payment`: cada persona recibe su copia del mismo cronograma.
7. **Una fila de `estado_onboarding` puede nacer antes de que la persona empiece** (`iniciado_en` nulo), como ya pasa con el diagnóstico. El conteo del panel de onboarding pasa a contar `iniciado_en IS NOT NULL`, para que esas filas no cuenten como «iniciados».
8. **Los recordatorios son de la app** (alarmas locales): el servidor solo entrega las cuotas pendientes con su fecha en `GET /me/ingreso`; la app programa y cancela. No hay push, bandeja ni mensaje en el chat.
9. **Borrado de cuenta**: las cuotas se borran con el resto de los datos de onboarding (D-243); `cuotas_de_pago` también cae por `ON DELETE CASCADE`.
10. **Soporte TI**: funcionalidad `pagos` (nace prendida); `MiIngresoController` y `PagosAdminController` la declaran.

## Estructura (backend)
```
users/api/{PagosAlAceptar, PlanDePagoPedido}
users/domain/model/accountrequest/PedidoDeIngreso            (suma `pago`)
users/application/services/IngresoPorAceptacion               (llama a PagosAlAceptar)
users/infrastructure/adapter/in/rest/accountrequest/{ApproveAccountRequestRequest, OpcionesDeAceptacionResponse}
onboarding/domain/model/pagos/{Moneda, ModalidadDePago, EstadoDeCuota, EstadoDeConfirmacion, Cuota, CronogramaDePagos, PagoDeLaPersona}
onboarding/domain/model/estado/EstadoOnboarding               (suma `pago`)
onboarding/application/ports/in/pagos/{PagosDeLaPersonaUseCase, MiIngresoUseCase, VistaDePagos, MiIngreso}
onboarding/application/ports/out/pagos/CuotasDePagoPort
onboarding/application/services/pagos/{PagosDeLaPersonaService, MiIngresoService, ArmadorDeVistaDePagos, GuardiaDePagos}
onboarding/infrastructure/adapter/in/rest/pagos/{MiIngresoController, PagosAdminController, PagosDtos}
onboarding/infrastructure/adapter/out/persistence/pagos/{CuotaDePagoJpaEntity, SpringDataCuotaDePagoRepository, CuotasDePagoPersistenceAdapter}
onboarding/infrastructure/adapter/out/cuentas/PagosAlAceptarAdapter
db/migration/V158__cronograma_de_pagos.sql
```

## Estructura (app)
```
features/pagos/{api, hooks, utils, components, screens}      (Mi ingreso, bloque «Pago» de «Aceptar», sección «Pagos» de la ficha)
features/pagos/notificaciones                                 (recordatorios locales de pago)
features/alarmas/SeccionAlarmas                               (+ «Recordatorios de pago»)
features/yo                                                    (+ fila «Mi ingreso»)
.maestro/pagos/
```
