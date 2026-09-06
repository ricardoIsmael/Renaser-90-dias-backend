# Indexación de Sparkie — dónde quedó

**Última actualización: 2026-09-06.**

## Estado

| | |
|---|---|
| Transcripciones extraídas | **124 / 124** ✅ |
| Chunks indexados en `renaser.base_conocimiento` (producción) | **985** (47 lecciones) |
| Chunks que faltan | **1.541** (77 lecciones) |
| Total | 2.526 |

Los 985 están completos y **todos con su vector** — no quedó ninguno a medias
(`SELECT count(*) FROM renaser.base_conocimiento WHERE embedding IS NULL` da 0).

## Por qué está parado

La cuota gratuita de Gemini es de **1.000 embeddings por día y por PROYECTO**. Por eso se cortó en
985. Una clave nueva **no reinicia nada**: comparte el contador del mismo proyecto.

Decisión del dueño (2026-09-06): **esperar los reinicios diarios** en vez de activar facturación.
A ~1.000/día son unos 2 días más. Si en algún momento se prefiere terminarlo de una, activar
facturación en el proyecto cuesta centavos para este volumen (~0,95 M de tokens) — pero confirmar
la tarifa vigente de `gemini-embedding-001` antes, que los precios cambian.

## Cómo retomar

Hace falta un **token de sesión de admin**: entrar al panel como admin, F12 → pestaña Red, y copiar
el valor de la cabecera `X-Auth-Token`.

```bash
python indexar.py <url_del_backend> <token> --desde 47
```

`--desde 47` saltea las 47 lecciones ya indexadas. Antes de correrlo conviene **verificar ese número
contra la base**, no confiar en este archivo:

```sql
SELECT count(DISTINCT leccion_id) FROM renaser.base_conocimiento;
```

El script es idempotente por lección pero **no** por chunk: si se relanza sobre una lección ya
indexada, duplica sus chunks. De ahí que el `--desde` importe.

## Por qué esta carpeta está DENTRO del repo

Todo esto vivía en el directorio temporal de una sesión de Claude Code, que es específico de esa
sesión y lo puede limpiar Windows. Volver a extraer las 124 transcripciones de YouTube cuesta
bastante, así que se conservó.

Está versionado **a propósito**, aunque sean datos derivados y pesen 5,5 MB: la indexación se
retoma desde otra máquina (el dueño trabaja desde dos), y el repo es la única vía que llega segura
a las dos. Una carpeta local u OneDrive no sirve si en la otra máquina esa cuenta no está.

Lo que NO se guardó son los `.vtt` crudos (36 MB): son el paso intermedio del que salieron estos
JSON, y ya no hacen falta para indexar. Si alguna vez se necesitan, se regeneran con
`extraer_todo.py`.

**Cuando la indexación termine, esta carpeta se puede borrar del repo** — su única razón de ser es
sobrevivir hasta que los 2.526 chunks estén adentro.

## Detalle que costó encontrar

`yt-dlp` fallaba en los vídeos del curso hasta que se le pasó
`--extractor-args youtube:player_client=android`. Sin eso parecen vídeos caídos y no lo están.
