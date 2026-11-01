# Plan de implementación: términos y condiciones editables (spec 027)

**Rama**: `027-terminos-editables` (backend y app), sobre `025-solicitud-con-formacion` · **Números**: V148–V150, D-304 · **Se apoya en**: 022 (formaciones) y 025 (aceptar una solicitud).

## Resumen
Los términos dejan de estar escritos en la app: viven en `onboarding` como versiones por formación (`terminos_versiones`), con un borrador editable por Administración y una constancia de qué versión aceptó cada
persona (`aceptaciones_de_terminos`). La aceptación sigue siendo el hito de Términos del onboarding; ahora lleva la versión y deja la constancia en la misma transacción.

## Constitution Check
| Principio | Cómo se cumple |
|---|---|
| I. Dependencias al dominio | Reglas puras en `ContenidoDeTerminos`, `VersionDeTerminos` y `ReglasDeTerminos`. `onboarding` solo importa `users.api`; `users` pregunta si una formación tiene términos por `users.api.TerminosPublicados`, que implementa `onboarding` (sin ciclo). |
| II. No inventar reglas | Las 12 dudas están resueltas por el dueño (todas A). |
| III. Pruebas | Unitarias de dominio; `TerminosEditablesIT` contra Postgres (migración, constancia, 409 por versión vencida, publicar con y sin pedir aceptar, aislamiento, 403); Jest en la app. |
| IV. La base guarda historia | Las versiones publicadas son inmutables (trigger); las aceptaciones solo se agregan. |
| VII. Documentar | D-304; entrada en `AGENTS.md` de la app. |

## Decisiones de diseño
1. **Dos tablas** (versiones y constancia); sin columnas nuevas en `estado_onboarding`.
2. **Una sola vía de aceptación**: el hito de Términos (`POST /onboarding/milestones`) con `termsVersionId` opcional. La app anterior sigue funcionando y queda contra la vigente.
3. **La formación de los términos de una persona** es `AlcanceDeFormacionFinder.formacionDe` (la del aprendiz; la activa del personal); Administración y Alquimista, la Escuela (Q4-A).
4. **Aceptar de nuevo**: `ReglasDeTerminos.quePide`. Bloquean `NUEVA_VERSION` y `PRIMERA_EN_LA_FORMACION`; el primer ingreso lo lleva el onboarding.
5. **Concurrencia** (FR-008): `baseNumero` — la versión vigente que quien edita tenía a la vista; si cambió, 409.
6. **Una formación sin términos publicados no recibe a nadie** (FR-021): la 025 lo comprueba por `users.api.TerminosPublicados`.
7. **Marcadores** `{formacion}` y `{duracion}` se reemplazan al servir el texto a cada persona.

## Fuera de esta entrega
Notificaciones push (Q9-A: no), formato del texto (Q7-A: plano).
