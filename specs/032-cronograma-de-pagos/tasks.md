# Tareas: cronograma de pagos (spec 032)

## Backend
- [x] T001 V158: `cuotas_de_pago` y las columnas de pago de `estado_onboarding`
- [x] T002 Dominio: `Moneda`, `ModalidadDePago`, `EstadoDeCuota`, `EstadoDeConfirmacion`, `Cuota`, `CronogramaDePagos`, `PagoDeLaPersona` + pruebas
- [x] T003 `EstadoOnboarding` suma `pago` (anotar sin cronograma / con cronograma / confirmar) + prueba
- [x] T004 Puertos, `ArmadorDeVistaDePagos`, `GuardiaDePagos`, `PagosDeLaPersonaService`, `MiIngresoService` + pruebas con salidas en memoria
- [x] T005 Persistencia: `CuotaDePagoJpaEntity`, repositorio y adaptador; el mapper de `estado_onboarding`
- [x] T006 REST: `MiIngresoController` y `PagosAdminController`; `MANAGE_PAYMENTS`; funcionalidad `pagos`; matcher de `SecurityConfig`
- [x] T007 `users.api.PagosAlAceptar` + `PlanDePagoPedido`; `PedidoDeIngreso`, `ApproveAccountRequestRequest`, `IngresoPorAceptacion`; `paymentScheduleAvailable`
- [x] T008 Conteo de «iniciados» del panel de onboarding (`iniciado_en IS NOT NULL`) y borrado de cuotas con la cuenta
- [x] T009 `CronogramaDePagosIT` (recorrido, roles, suspendida, aislamiento entre formaciones, restricciones de la base) y pruebas de pago en `AceptarConFormacionIT`
- [x] T010 D-307 en `docs/MODULOS_A_AVANZAR.md`

## App
- [x] T020 API y esquemas zod tolerantes (`/me/ingreso`, admin, `payment` al aceptar)
- [x] T021 Utilidades puras: dinero, atajo «N cuotas mensuales», borrador de cuotas, vista de «Mi ingreso», recordatorios (plan de alarmas)
- [x] T022 Yo → «Mi ingreso» (`MiIngresoScreen`) y su fila en Yo
- [x] T023 Bloque «Pago» de la hoja «Aceptar» y su frase de resumen
- [x] T024 Sección «Pagos» de la ficha de Administración (marcar, ajustar, asignar)
- [x] T025 Recordatorios de pago: planificador, sincronizador, Yo → Alarmas y la hoja de «Mi ingreso»; el aviso abre «Mi ingreso»
- [x] T026 Jest; `AGENTS.md`; flujos de Maestro en `.maestro/pagos/`

## Fuera de esta entrega
- Una lista general de «quién debe», planillas, avisos del servidor y el cobro dentro de la app (ver `spec.md → Fuera de alcance`).
