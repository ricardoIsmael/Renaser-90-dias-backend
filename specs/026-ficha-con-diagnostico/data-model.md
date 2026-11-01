# Data model: ficha con diagnóstico (spec 026)

Reutiliza el motor de onboarding que ya existe (`secciones_onboarding`, `preguntas_onboarding`, `opciones_pregunta`, `respuestas_onboarding`, `estado_onboarding`, `etapas_onboarding_completadas`). El diagnóstico es **un flujo más** (`flujo = 'diagnostico'`).
Tablas nuevas: **una** (`fichas_publicadas`, justificada abajo). Todo lo demás son columnas.

## V143 (hecha) — qué ficha completa cada persona
`estado_onboarding`: `ficha` (`ESTANDAR` por defecto | `CON_DIAGNOSTICO`), `ficha_asignada_por`, `ficha_asignada_en`, `ficha_version` (la versión con la que empezó; NULL hasta que abre el diagnóstico).
Que el diagnóstico se terminó = una fila en `etapas_onboarding_completadas` con `flujo = 'diagnostico'` (como el Mapa). Que se empezó = existen respuestas del flujo.

## V144 — lo que una pregunta del diagnóstico necesita y la tabla no tenía
`preguntas_onboarding`: `ayuda text`, `maximo_opciones smallint`, `se_muestra_si_valor text` (con la `pregunta_padre_id` que ya existía: «se muestra solo si el padre vale esto»), `alimenta text` (CHECK de 6 valores; índice único parcial: un destino, una pregunta activa), `formaciones text[]` (NULL = todas, como `formaciones.duraciones_dias`; sin tabla porque se lee y escribe junto con la pregunta), `precarga text`, `retirada_en timestamptz`.
`tipo_pregunta_onboarding` suma `CUADRICULA` (la cuadrícula de escala: las filas son `opciones_pregunta`, la respuesta es un `valor_json`).
`respuestas_onboarding`: sin cambios.

## V145 — `fichas_publicadas` (la única tabla nueva)
`flujo text, version integer, contenido jsonb NOT NULL, publicada_por uuid → usuarios (SET NULL), publicada_en timestamptz`, PK `(flujo, version)`.
**Por qué hace falta una tabla.** «Una persona que empezó una ficha la termina con la versión con la que empezó» y «la pregunta tal como se le hizo» (FR-031, FR-044) exigen la **foto** de la ficha al publicarla: las filas de `preguntas_onboarding` son el borrador y cambian. El `jsonb` es un documento inmutable que solo se lee entero, nunca se consulta por dentro (salvo «¿alguna versión incluyó esta pregunta?», para decidir eliminar o retirar). Es append-only.
`hayCambiosSinPublicar` se deduce comparando la foto del borrador con la última publicada: no se guarda.

## V146 — de dónde nace cada objetivo
`rocas_maestras`: `origen text NOT NULL DEFAULT 'PLAN'` (`PLAN|MAPA|DIAGNOSTICO`), `confirmada_en timestamptz`. Las filas que existen: `origen = 'MAPA'` si la persona tiene el Mapa activo y `'PLAN'` si no; **todas con `confirmada_en = creada`** (nada pasa a «por confirmar» y nada se reinicia). Solo las que nacen del diagnóstico empiezan sin confirmar.

## V147 — el borrador del diagnóstico del dueño
Siembra (solo si no hay nada del flujo) las secciones y las 29 preguntas del anexo `diagnostico-inicial.csv` (las 27 del formulario, con 16-18 partidas en dos y la del trato), en **borrador**: no se publica sola. El texto de la declaración queda como relleno `[PENDIENTE DEL DUEÑO] …` y bloquea la publicación hasta que Administración lo cambie.

## Dominio (sin Spring)
`onboarding.domain.model.diagnostico`: `Diagnostico` (flujo), `TipoDePregunta` (reglas por tipo: opciones, máximo, escala, formato de respuesta), `DestinoDeRespuesta`, `PreguntaDelDiagnostico` (+ `esProtegida`), `CondicionDeVisibilidad`, `FichaDelDiagnostico` (borrador/foto: validar para publicar, filtrar por formación, qué preguntas son visibles dada una respuesta, qué falta), `RespuestaDelDiagnostico` (validación por tipo), `PlantillaDeImportacion` (lectura del CSV y errores por fila), `ObjetivosDelDiagnostico` (de las respuestas a los tres objetivos, la prioridad y la primera acción).
