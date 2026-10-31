# Implementation Plan: Administración decide si el mentor y el líder ven el Código Renaser

**Branch**: `034-codigo-renaser-visible-por-admin` (backend y app) | **Date**: 2026-10-31 | **Spec**: [spec.md](./spec.md)

## Summary

El interruptor es **una funcionalidad más del catálogo de Soporte TI** (`radar.fichas`, «Código Renaser en las fichas», hija de `radar`), no una tabla ni un ajuste nuevo.
Nace **apagada para MENTOR, TRAINEE_GUIDE y MENTOR_LEAD** (decisión 034-1 A) y prendida para el resto, y cuando está apagada **se esconde** en vez de mostrar el aviso de mantenimiento
(404 `FUNCIONALIDAD_NO_DISPONIBLE`, lo que la app y un APK anterior ya entienden como «esto no está»). Se hace cumplir **en el servidor**: las tres lecturas del Código Renaser
de otra persona (mentor, líder por grupo, líder sin grupo) preguntan a `Interruptores` antes de leer. Administración y Alquimista lo cambian con un endpoint angosto
(`GET/PUT /api/v1/admin/codigo-renaser-en-fichas`, confirmación en la app, **sin motivo**); Soporte TI, desde su consola de siempre (rol por rol) o por ese mismo endpoint.
Todo cambio deja su fila en la auditoría append-only que ya existe y recarga la copia en memoria de todas las instancias (el camino de la consola).

## Technical Context

**Language/Version**: Java 25, Spring Boot (según `pom.xml`); app Expo SDK 57 / TypeScript estricto.

**Primary Dependencies**: las que ya hay: `plataforma` (catálogo, evaluación, `EstadoVigente`, `CopiaDeEstados`, auditoría, evento `FuncionalidadCambiadaEvent`), `shared.application.Interruptores`,
`habits` y `leadership` para las lecturas. Ninguna dependencia nueva.

**Storage**: PostgreSQL. **Ninguna tabla ni migración nueva** (la V133 reservada **no se usa**): el estado vive en `funcionalidades_estado` / `funcionalidades_estado_rol` (V101) y la auditoría
en `cambios_auditados`. Sin fila rige el valor por defecto del **catálogo en código**, que ahora puede decir «apagada por defecto para estos roles».

**Testing**: JUnit 5 + AssertJ + Mockito (unitarias sin Spring), `@WebMvcTest` del controller, IT con Testcontainers (Postgres y Redis reales) para el escenario de punta a punta,
`ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`, `RutasCubiertasPorElFiltroTest`; app: `tsc --noEmit` y Jest.

**Target Platform**: servidor Linux; app Android (y web).

**Project Type**: monolito modular hexagonal (Spring Modulith) + app móvil.

**Performance Goals**: evaluar la funcionalidad es leer un mapa en memoria (≤ 2 ms p95, FR-011 de la spec 002); no se agrega ninguna consulta a la base por pedido.

**Constraints**: el cambio rige en el acto en todas las instancias (≤ 5 s por Redis, como la consola); un APK anterior no se rompe; con el interruptor encendido todo es idéntico a hoy.

**Scale/Scope**: una clave nueva en un catálogo de 55; tres lecturas protegidas; una pantalla de Administración.

## Constitution Check

| Principio | Cumple | Cómo |
|---|---|---|
| I. Las dependencias apuntan al dominio | Sí | La regla («qué roles gobierna el interruptor y qué estado tiene») es dominio puro en `plataforma.domain.model.catalogo`; el controller solo deserializa, autoriza e invoca **un** caso de uso; `habits` y `leadership` importan solo `shared.application.Interruptores` (no `plataforma`). |
| II. No se inventan reglas | Sí | Las tres preguntas y la de Soporte TI las respondió el dueño el 2026-10-31 (spec, «Decisiones del dueño»). Lo que se asumió (motivo fijo en la auditoría, mixto = «no ven») está a la vista abajo. |
| III. Pruebas | Sí | Unitarias de dominio y servicios, `@WebMvcTest` con 403 de rol sin permiso y de cuenta suspendida, e IT de punta a punta (apagado: el mentor y el líder reciben 404 en sus tres lecturas y Administración 200; encendido: igual que hoy; auditoría; reinicio de la copia). |
| IV. La base guarda historia | Sí | Sin migración. Cada cambio es una fila de `cambios_auditados` (trigger append-only). |
| V. El tiempo es del participante | Sí | El instante lo da el puerto `Clock`; no hay nada diario. |
| VI. Los cinco tabs | Sí | Yo → Administración → Más opciones suma una fila y las fichas esconden una sección: autorizado por el pedido (se anota en `AGENTS.md` con «lo que cambia y nada más»). |
| VII. Documentar | Sí | D-299, `AGENTS.md` del frontend, notas «Corregido» en la 012 y en la spec 002 (catálogo). |

Re-evaluado después del diseño: sin violaciones; **Complexity Tracking vacío**.

## Project Structure

### Documentation (this feature)

```text
specs/034-codigo-renaser-visible-por-admin/
├── spec.md
├── plan.md              # este archivo
├── research.md          # decisiones de diseño y alternativas
├── data-model.md        # el «dato»: una funcionalidad con valor por defecto por rol
├── contracts/api.md     # GET/PUT /api/v1/admin/codigo-renaser-en-fichas y los rechazos
├── quickstart.md        # cómo comprobarlo de punta a punta
└── tasks.md
```

### Source Code (backend)

```text
src/main/java/com/renaser/os/
├── shared/
│   ├── application/ClavesDeFuncionalidad.java            # NUEVO: RADAR_EN_FICHAS = "radar.fichas" (la nombran habits, leadership y plataforma)
│   └── domain/Permission.java                            # + MANAGE_RADAR_VISIBILITY
├── plataforma/
│   ├── domain/model/catalogo/
│   │   ├── DefinicionDeFuncionalidad.java                # + apagadaPorDefectoPara, seEsconde (constructor de 9 argumentos intacto)
│   │   ├── EntradasDelCatalogo.java                      # + radar.fichas
│   │   └── CodigoRenaserEnFichas.java                    # NUEVO, puro: roles que gobierna, estado visible/parcial, el cambio hacia visible/oculto
│   ├── domain/model/estado/EvaluacionDeFuncionalidades.java   # el valor por defecto por rol y «se esconde»
│   ├── application/ports/in/{Consultar,Cambiar}VisibilidadDelCodigoRenaserUseCase.java   # NUEVOS
│   ├── application/services/
│   │   ├── VisibilidadDelCodigoRenaserService.java       # NUEVO: guardia (ADMIN, ALCHEMIST, IT_SUPPORT activos) + lectura + cambio
│   │   ├── EscrituraDeEstado.java                        # NUEVO, extraído de ConsolaDeFuncionalidadesService: guardar + auditar + avisar
│   │   ├── ConsolaDeFuncionalidadesService.java          # usa EscrituraDeEstado (mismo constructor y mismo comportamiento)
│   │   └── FuncionalidadesEnConsola.java                 # porRol y «antes» de la auditoría respetan el valor por defecto por rol
│   └── infrastructure/adapter/in/rest/CodigoRenaserEnFichasController.java   # NUEVO + request/response
├── habits/application/services/AcompanamientoDeAlumnoService.java   # + exigirPrendidaPara(radar.fichas) en la lectura del mentor
├── leadership/application/services/RadarDePersonaService.java        # + exigirPrendidaPara en las dos lecturas del líder
└── notifications/.../FuncionalidadCambiadaNotificationListener.java  # el título ya no dice «Soporte TI» si lo cambió Administración
```

### Source Code (app)

```text
src/features/
├── plataforma/…/clavesDeFuncionalidades.ts               # + radarFichas
├── radar/components/CodigoRenaserDelAlumno.tsx           # no se dibuja ni se pide si radar.fichas no está disponible
└── admin/
    ├── api/codigoRenaserEnFichasApi.ts, hooks/useCodigoRenaserEnFichas.ts
    ├── screens/CodigoRenaserEnFichasAdminScreen.tsx      # NUEVO
    └── (fila «Código Renaser en las fichas» en «Más opciones»)
```

**Structure Decision**: dentro de `plataforma` (dueño de los interruptores, de su auditoría y de su copia en memoria) y no en `habits` o `leadership`: así hay **un solo** lugar que escribe
`funcionalidades_estado`. Los consumidores solo preguntan.

## Complexity Tracking

Vacío: no hay violaciones de la constitución.
