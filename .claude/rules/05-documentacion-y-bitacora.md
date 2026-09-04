# Documentación y bitácora

## Documentar en el mismo cambio, no después

- Todo avance se documenta **en el mismo cambio**. Si se cierra un bloqueante, se marca resuelto; si
  se toma una decisión, se agrega al registro de `docs/MODULOS_A_AVANZAR.md` §8.
- **Los documentos son fuente de verdad y no pueden contradecirse.** Si un cambio deja una sección
  vieja, se corrige en el momento — no "más adelante". `CLAUDE.MD` se lee en cada sesión, así que una
  contradicción ahí se propaga hacia adelante hasta que alguien la nota.
- Cuando se corrige una sección vieja, se deja **a la vista qué decía antes y por qué cambió**
  (`> **Corregido 2026-08-31.** Esta línea decía…`). Es lo que evita que la próxima persona
  "arregle" la corrección de vuelta al error.
- **No romper lo que ya funciona.** Documentos y código estable se editan quirúrgicamente. Nada de
  reescrituras que descarten trabajo previo.

## Bitácora de errores — la regla con mejor retorno del repo

**Todo error, bug o comportamiento inesperado se registra en `docs/BITACORA_ERRORES.md`, incluso si
se resolvió en dos minutos.**

El objetivo es concreto: **si vuelve a pasar, se resuelve en un minuto en vez de media hora.**

Se registra:

- El **síntoma exacto** — mensaje de error **literal**, no parafraseado. Es lo que se va a buscar con
  `Ctrl+F` dentro de seis meses.
- La **causa real**, no la primera hipótesis.
- La solución aplicada.
- **Cómo evitar que vuelva a pasar** — y si esa prevención se puede volver ejecutable (un test, una
  regla de `ArchitectureTest`, una entrada en `.claude/rules/`), hacerlo ahí mismo.

Se registra **aunque el error haya sido de configuración, del entorno o "una tontería"** — esos son
justamente los que se repiten.

## Numeración

Las decisiones (`D-nn`) y errores (`E-nn`) se numeran secuencialmente. **Antes de elegir un número,
verificar el último usado** — ya pasó que dos agentes eligieran `D-66` a la vez y quedara una
colisión documentada en la propia bitácora.
