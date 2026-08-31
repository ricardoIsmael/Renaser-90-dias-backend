# Diagramas de arquitectura

Generados con [archify](https://github.com/tt-a1i/archify) (skill instalada en `~/.claude/skills/archify`).
El diagrama **no se dibuja a mano**: la fuente de verdad es el `.json` de esta carpeta y el `.html`
se regenera desde él. Editar el HTML directamente es tirar el trabajo a la basura en la próxima corrida.

## Qué hay acá

| Archivo | Qué es |
|---|---|
| `arquitectura-backend.architecture.json` | **La fuente.** Especificación tipada del diagrama |
| `arquitectura-backend.html` | Artefacto entregado: HTML autocontenido, tema claro/oscuro, zoom, búsqueda, export |
| `arquitectura-backend.visual-check.json` | Recibo de la comprobación en navegador real (contención a 1440×900 y hasta 2048×1320) |

Las capturas `.visual-check.*.png` y la hoja de contactos están en `.gitignore`: pesan ~700 KB
y se regeneran en un comando.

## Cómo regenerarlo

```bash
SKILL=~/.claude/skills/archify
REPO="$(git rev-parse --show-toplevel)"

# 1. Validar (obligatorio antes de entregar). 9 comprobaciones, 0 errores, 0 warnings.
node $SKILL/bin/archify.mjs validate architecture \
  "$REPO/docs/arquitectura/arquitectura-backend.architecture.json" \
  --quality showcase --repo-root "$REPO" --json

# 2. Entregar: congela la especificación, renderiza y devuelve SHA-256 de fuente y artefacto.
node $SKILL/bin/archify.mjs deliver architecture \
  "$REPO/docs/arquitectura/arquitectura-backend.architecture.json" \
  "$REPO/docs/arquitectura/arquitectura-backend.html" \
  --quality showcase --repo-root "$REPO" --json

# 3. Evidencia de navegador: verifica que el HTML entra sin scroll en pantallas de escritorio.
node $SKILL/bin/archify.mjs visual-check "$REPO/docs/arquitectura/arquitectura-backend.html" --json
```

## Evidencia contra el código

`meta.repository` fija el commit y cada componente declara en `sources` un archivo real del repo.
Archify **verifica que esos archivos existan en ese commit** antes de renderizar: si alguien borra
o mueve `WebSocketConfig.java`, la regeneración falla en vez de dibujar una mentira.

Por eso, **al mover un archivo referenciado hay que actualizar su `sources` y el `revision`** —
es el precio de que el diagrama no pueda envejecer sin avisar.

## Qué afirma el diagrama, y de dónde sale

| Afirmación | Evidencia |
|---|---|
| 14 módulos hexagonales en un solo proceso | `src/main/java/com/renaser/os/` — un paquete por módulo + `shared` |
| 72 controllers REST bajo `/api/v1` | directorios `*/infrastructure/adapter/in/rest` |
| 9 jobs `@Scheduled` | `*/infrastructure/adapter/in/scheduler` |
| Redis para sesión, pub/sub de chat, cuota de RAG y tokens | `spring.session.data.redis` en `application.yaml` + adaptadores `adapter/out/redis` de `chat`, `rag` y `users` |
| Auth propia + OIDC (Google, Apple, Facebook) | `V3__auth_credenciales_e_identidades.sql` + `users/.../adapter/out/oauth` |
| La IA está **apagada**: los adaptadores activos son `NoOp` | `spring.ai.model.chat: none` y `embedding: none` en `application.yaml`; clases `NoOp*IAAdapter` |
| El outbox es una tabla del **mismo** Postgres | `V2__spring_modulith_event_publication.sql` |

> **Nota sobre el idioma:** el contenido está en español, pero la interfaz del visor (botones
> Light/Dark, Present, Export) queda en inglés. Archify solo localiza esa interfaz a `en` y `zh-CN`;
> no hay opción de español, así que `meta.locale` se omite a propósito.

## Diagrama de la base de datos

El ER vive aparte, en `docs/db/`, porque es otro tipo de diagrama (entidad-relación, en draw.io)
y otra herramienta. Se verifica contra el esquema real con:

```bash
node docs/db/verificar-er-vs-sql.mjs
```

Correrlo **al agregar una migración** — ver `docs/BITACORA_ERRORES.md` E-50, que es exactamente
lo que pasa cuando no se corre.
