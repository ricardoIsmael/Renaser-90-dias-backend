# Implementation Plan: Análisis 80/20 con IA

**Branch**: `031-analisis-80-20-con-ia` (backend y app) | **Date**: 2026-10-31 | **Spec**: [spec.md](./spec.md)

## Summary

Cada semana y cada mes **del programa** la persona recibe un análisis de SER con cuatro partes («Lo que más movió», «Lo que se te fue», «Cómo estuviste» y de 1 a 3 pasos para el
periodo que viene). Las **cifras las calcula el servidor** (`rocks`, determinista: regla de las verdes **y** regla del 20 % de las cumplidas con más impacto, decisión 031-1 A y B);
la **IA solo redacta** sobre esas cifras, y todo número que cite se valida contra las cifras antes de guardarlo. El análisis **se genera solo, al cerrar el periodo** (031-7 A), por un
barrido horario con `@SchedulerLock`; **abrirlo es leer una fila** y nunca llama a la IA. Se guarda en **una tabla nueva** (`analisis_80_20`, V153), con el estado de cada intento, para que
reintentar, diferir y «sin datos» no dependan de la memoria de un proceso.

Se reutiliza lo de SER, no se duplica: el `ChatModel` y la credencial del proveedor (`GoogleGenAi…`), el circuito automático (`ParteDeLaIa.CHAT`) y los interruptores de Soporte TI (`ia` y el
hijo nuevo `ia.analisis`). Para quién lo ve: la propia persona en Plan; el mentor, la guía, el líder, Administración y Alquimista en la ficha, **respetando `radar.fichas`** (spec 034, porque el
análisis resume lo que la persona respondió en el Código Renaser) y dejando constancia de la consulta cuando la lee el liderazgo.

## Technical Context

**Language/Version**: Java 25, Spring Boot 4 / Spring Modulith, Jackson 3 (`tools.jackson.*`); app Expo SDK 57 / TypeScript estricto.

**Primary Dependencies**: las que ya hay: `rocks` (acciones y su impacto), `habits` (respuestas del Código Renaser), `rag` (modelo de IA, circuito, herramientas de SER), `plataforma` (catálogo e
interruptores), `notifications` (aviso), `shedlock`. Ninguna dependencia nueva, ninguna librería nueva en la app.

**Storage**: PostgreSQL. V153 (`analisis_80_20`) y V154 (valor `ANALISIS_80_20` del enum `tipo_notificacion`). **V155 no se usa.**

**Testing**: JUnit 5 + AssertJ + Mockito (unitarias sin Spring), `@WebMvcTest` de los tres controllers, IT con Testcontainers (`Analisis8020IT`, `AnalisisDelLiderIT`), `ArchitectureTest`,
`EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`; app: `tsc --noEmit` y Jest.

**Performance Goals**: abrir un análisis = una lectura por clave única (`participante_id, periodo, inicio`) más el cálculo de cifras del periodo (consultas por rango); sin llamadas a la IA en lecturas.
La generación cuesta una llamada por persona y periodo, nunca más de 3 intentos, con un tope global de 500 intentos cada 24 horas.

**Constraints**: ninguna llamada a la IA dentro de una transacción; el texto de la IA nunca llega al log; al proveedor no sale correo, teléfono, documento, dirección, nombre ni id; el aviso del teléfono
lleva texto fijo; aislamiento por formación (la fila guarda su `formacion` y solo se muestra la de la formación actual de la persona).

**Scale/Scope**: un barrido por hora que recorre solo a quien tiene un periodo cerrado sin análisis (ventana de puesta al día de 7 días); 3 endpoints de lectura; 1 tabla; 1 funcionalidad nueva.

## Constitution Check

| Principio | Cumple | Cómo |
|---|---|---|
| I. Las dependencias apuntan al dominio | Sí | Las reglas (periodos, qué cuenta como 20 %, mínimos, validación del texto, máquina de estados) son dominio puro en `rocks.domain` y `rag.domain.model.analisis`; la IA y la base son puertos (`GenerarAnalisis8020Port`, `LoadAnalisisDelPeriodoPort`, `SaveAnalisisDelPeriodoPort`) con adaptadores en `infrastructure`; los controllers invocan un caso de uso. Entre módulos solo se cruzan `*.api` (`Pareto8020Finder`, `PeriodosDelProgramaFinder`, `RespuestasDelRadarFinder`, `Analisis8020Finder`, `AnalisisListoEvent`). |
| II. No se inventan reglas | Sí | Las nueve preguntas las respondió el dueño el 2026-10-31 (spec, «Decisiones del dueño»). Lo que se asumió está en «Assumptions» de la spec y en research.md (mes después del 90, semana 14 parcial, mínimos configurables). |
| III. Pruebas | Sí | Unitarias de cada regla, del validador del texto y de cada estado; `@WebMvcTest` con 403/404; IT de punta a punta (barrido → fila → lectura → aviso) y del líder (constancia, `radar.fichas`, aislamiento). |
| IV. La base guarda historia | Sí | Una fila por persona, periodo y comienzo (UNIQUE); el texto de un análisis LISTO no se reescribe; borrar la cuenta arrastra las filas (cascada + `BorradoDeCuentaEnRagAdapter`). |
| V. El tiempo es del participante | Sí | Los periodos y el «ya cerró» se deciden con la zona de la persona (`ProgramaActivado.zona()`) y el `Clock`; el barrido es de todos y decide por persona. |
| VI. Los cinco tabs | Sí | Plan: la hoja «Tu 80/20» suma vistas (autorizado por el pedido; «Hoy» queda igual); las fichas suman una sección; Yo → Notificaciones suma un interruptor. Se anota en `AGENTS.md`. |
| VII. Documentar | Sí | D-306, `AGENTS.md` del frontend, fila de la herramienta en `docs/MODULO_RAG.md`. |

Re-evaluado después del diseño: sin violaciones; **Complexity Tracking vacío**.

## Project Structure

### Documentation (this feature)

```text
specs/031-analisis-80-20-con-ia/
├── spec.md
├── plan.md              # este archivo
├── research.md          # decisiones de diseño y alternativas descartadas
├── data-model.md        # la tabla analisis_80_20, sus estados y sus periodos
├── contracts/api.md     # GET /analisis-80-20 y las dos rutas de ficha; aviso; herramienta de SER
├── quickstart.md        # cómo comprobarlo de punta a punta
└── tasks.md
```

### Source Code (backend)

```text
src/main/java/com/renaser/os/
├── rocks/
│   ├── api/{Pareto8020Finder, PeriodosDelProgramaFinder}.java        # lo que ve el resto: cifras del periodo y calendario
│   ├── domain/model/analisis/CalendarioDelAnalisis.java              # puro: semanas 1–13, desde la 14 sin tope; meses de 4 semanas
│   └── application/services/{ArmadoDel8020, Pareto8020Service, PeriodosDelProgramaService}.java
├── habits/
│   ├── api/RespuestasDelRadarFinder.java                             # las respuestas del Código Renaser de un rango (lectura)
│   └── application/services/RespuestasDelRadarService.java           # (+ LoadRegistroRadarPort.entre y su adaptador)
├── rag/
│   ├── api/{Analisis8020Finder, AnalisisListoEvent}.java
│   ├── domain/model/analisis/                                         # TipoDePeriodo, EstadoDelAnalisis, TextoDelAnalisis, ReglasDelAnalisis,
│   │                                                                  # AnalisisDelPeriodo (máquina de estados), ValidadorDelTexto
│   ├── application/ports/{in,out}/analisis/                           # 4 casos de uso y 3 puertos
│   ├── application/services/analisis/                                 # armado del pedido, generación, barrido, lectura, alumno, registro
│   ├── application/services/herramientas/ConsultarAnalisis8020Herramienta.java   # SER lo explica (sin parámetros)
│   ├── application/services/circuito/InterruptoresDeSer.java          # + ia.analisis
│   └── infrastructure/adapter/{in/rest/analisis, in/scheduler, out/ia, out/persistence/analisis}, config/AnalisisDe8020Config.java
├── leadership/…/{AnalisisDePersonaService, AnalisisDeLaPersonaController}   # líder, Administración y Alquimista (constancia + radar.fichas)
├── notifications/…/AnalisisListoNotificationListener + TipoNotificacion.ANALISIS_80_20
└── plataforma/domain/model/catalogo/EntradasDelCatalogo.java          # + ia.analisis
src/main/resources/db/migration/V153__analisis_80_20_con_ia.sql, V154__notificacion_analisis_80_20.sql
src/main/resources/prompts/analisis-80-20.txt
```

### Source Code (app)

`src/features/ochenta-veinte/` (api, tipos, hook, componentes, textos) · `TarjetasDelPlan.tsx` (la hoja con Hoy · Semana · Mes) · tres fichas (`AlumnoScreen`, `PersonaDelLiderScreen`,
`FichaAprendizScreen`) · `avisosApi.ts` + `abrirAviso.ts` + `PlanScreen.tsx` (el aviso abre la hoja) · `preferenciasDeNotificacion.ts` (el interruptor de avisos) · `public/privacidad`.
