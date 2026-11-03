# Implementation Plan: Comunidad por formación — Muro, cursos, Tribu, ranking, testimonios y eventos

**Branch**: `024-comunidad-por-formacion` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: `specs/024-comunidad-por-formacion/spec.md` (con las «Decisiones del dueño») y `specs/022-formaciones/` (la base: formación de la persona, alcance y filtro).

## Summary

Cada formación (Escuela, Diplomado, Certificación) tiene **su** comunidad y lo de una es invisible para las otras: el Muro, los testimonios, los eventos, los cursos, las
conversaciones de la Tribu y el ranking. Enfoque técnico: **una columna `formacion` en la tabla que ya guarda cada cosa** (la misma pauta que la 022 usó en
`participantes_programa` y `celulas`); lo que cuelga de ellas (comentarios, reacciones, fotos, lecciones, «Voy») no repite el dato, lo hereda de su padre. El servidor
filtra siempre con el punto único de la 022 (`users.api.AlcanceDeFormacionFinder` + `FiltroDeFormacionSql`) y trata lo de otra formación como **inexistente** (mismo 404).
Los tipos de comunidad que ofrece cada formación (general, grupo, soporte, directos) son una columna del catálogo (`formaciones.tipos_de_comunidad`) que Administración
prende y apaga; `chat` la lee, no la escribe. La app solo suma un selector de formación para quien ve las tres (Administración) y las etiquetas que de ahí se desprenden.

## Technical Context

**Language/Version**: backend Java 25 / Spring Boot, Spring Modulith; app Expo SDK 57 / React Native / TypeScript estricto

**Primary Dependencies**: Spring Data JPA + `JdbcClient`, Flyway, ArchUnit, Testcontainers; zod en la app

**Storage**: PostgreSQL (schema `renaser`), migraciones `V137` a `V139` (aditivas: columnas con `DEFAULT 'ESCUELA'`, sin reescribir filas)

**Testing**: JUnit 5 + Mockito para dominio y servicios, `ComunidadPorFormacionIT` (Testcontainers) para el aislamiento, `ArchitectureTest`,
`EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`; en la app Jest + `tsc --noEmit` y flujos de Maestro escritos

**Constraints**: compatibilidad hacia atrás (campos nuevos aditivos y opcionales; el APK 1.6.0 sigue mostrando la Escuela completa sin mandar nada nuevo); cero cambios visibles
para la Escuela; el día de cada persona sigue siendo el de su zona

**Scale/Scope**: 6 tablas con columna nueva, ~35 servicios tocados, 2 endpoints nuevos, 3 migraciones

## Constitution Check

| Principio | Cumple | Cómo |
|---|---|---|
| I. Dependencias al dominio | Sí | La regla «en qué formación nace/se lee algo» vive en `shared.domain` (`FormacionDelContenido`, `AlcanceDeFormacion`) sin Spring. Cada módulo habla con `users` solo por `users.api` (`AlcanceDeFormacionFinder`, `ComunidadDeLaFormacionFinder`, `FormacionesFinder`). `chat` reacciona al traslado por el evento de la 022. Los controllers invocan un solo caso de uso. |
| II. No se inventan reglas | Sí | Las nueve dudas las contestó el dueño (31/10); lo no dicho es la opción A de la spec. Los supuestos de implementación están en la spec. |
| III. Pruebas en verde | Sí | Unitarias (dominio, `VisibilidadPorFormacion`, servicios), `ComunidadPorFormacionIT` con los pares de formaciones, `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`; los endpoints nuevos con 403 sin permiso y `SUSPENDED`. |
| IV. La base guarda historia | Sí | Migraciones con cabecera; la única desnormalización (`ranking_aprendices.formacion`, `conversaciones.formacion` de los grupos) está justificada en su cabecera; el historial del ranking y lo que escribió quien cambia de formación no se reescriben. |
| V. El tiempo es del participante | Sí | Los topes por día usan el día del programa de la persona (hasta 365); lo diario se prueba con el reloj en la madrugada UTC. |
| VI. Los cinco tabs | Sí, con autorización | El pedido del dueño del 31/10 autoriza la excepción mínima (selector para Administración y las etiquetas); se anota en `AGENTS.md` del frontend. |
| VII. Documentar en el mismo cambio | Sí | D-301 en `docs/MODULOS_A_AVANZAR.md`, notas en `docs/MODULO_*`, `AGENTS.md` de la app. |

### Complexity Tracking

| Desviación | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| `ranking_aprendices.formacion` guardada además de derivable | El corte diario es historia: un JOIN a la persona de hoy movería los cortes viejos si cambia de formación | JOIN a `participantes_programa` (reescribe el pasado) |
| `conversaciones.formacion` repetida en las de grupo | La lista de chats filtra por formación en una consulta sin JOIN; el grupo no cambia de formación (V125) | JOIN a `celulas` en la lectura más caliente de la Tribu |
| Claves de soporte/directo con sufijo `:CÓDIGO` fuera de la Escuela | Sin tocar las claves existentes, una persona con dos formaciones tiene un soporte/directo por formación | Migrar todas las claves de la Escuela (sin necesidad) |

## Project Structure

### Backend (por módulo, solo lo tocado)

```text
shared/domain/            FormacionDelContenido, TipoDeComunidad (nuevos)
users/api/                ComunidadDeLaFormacionFinder (nuevo)
users/application/        ConfigurarComunidadDeLaFormacionUseCase, ComunidadDeLaFormacionService, TiposDeComunidadPort (+ adaptador JDBC), controller admin
community/                Publicacion, Testimonio, Evento, Curso con formacion; servicios del Muro, testimonios, eventos y cursos filtran por alcance
chat/                     Conversacion con formacion; VisibilidadPorFormacion (la regla única); ConversacionService (una global por formación), traslado, soporte, podio
points/                   ranking_aprendices con formacion; RankingService ordena y numera por formación; caché con el alcance en la clave
db/migration/             V137 contenido, V138 tribu, V139 ranking
```

### App

Selector de formación de la Comunidad (solo Administración), `formation` en lecturas/creaciones, etiquetas por fila con «Todas», pestaña de Administración → Formaciones →
«Comunidad» con los cuatro interruptores y la ruta del aviso `/chat/{id}?formacion=CÓDIGO`. Ver la entrada de `AGENTS.md` de la app.

## Orden de publicación

1. Backend con V137–V139 (aditivo; el APK 1.6.0 sigue igual). 2. APK nuevo (el selector y las etiquetas; sin él Administración lee «Todas» y no puede elegir formación al
crear, el servidor le pide `formation`).
