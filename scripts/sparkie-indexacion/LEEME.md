# Indexación del RAG (Sparkie / Renasia) — guía para el dueño

**Última actualización: 2026-09-26.**

Esta carpeta sirve para meter **contenido general del programa** en la base de conocimiento de
producción (`renaser.base_conocimiento`), que es de donde el acompañante saca sus citas. La corre
el dueño desde su máquina; nadie más escribe en producción.

## Qué se indexa y qué no (regla de privacidad)

| Fuente (`--fuentes`) | Qué es | Cuántos | Visible desde |
|---|---|---|---|
| `lecciones` | Las 124 clases de los cursos (`transcripciones/salida/*.json`) | 124 (2.526 chunks) | el día en que se desbloquea cada lección |
| `drive-audios` | Drive «36. SESIONES / Transcripción de audios»: `DIA01 - …` a `DIA43 - …` | 43 | **el día 0** (ver abajo) |
| `drive-canal` | Drive «37. TRANSCRIPCIONES YT»: transcripciones del canal de YouTube | 81 − 1 − 5 = **75** | **el día 0** |

Del canal se dejan afuera:
- «El engaño de creer que sabes»: ya es una de las 124 lecciones; indexarlo otra vez lo duplica.
- Los **5 testimonios** (títulos con «TESTIMONIO»): hablan personas concretas de su proceso.
  Quedan afuera **salvo que se pase `--incluir-testimonios`**. Decisión del dueño.

**Nunca se indexa nada más de ese Drive**: sesiones individuales, mentorías grupales, atenciones,
postulaciones, fichas. Es información de personas concretas, y un RAG no distingue quién puede ver
qué: el acompañante podría citarle a un aprendiz lo que otro contó en su sesión. Por eso:

- El script **no acepta ids de carpeta**. Solo conoce las dos carpetas de arriba, escritas a mano
  en `drive.py` (`CARPETAS_PERMITIDAS`). Sumar otra es editar ese archivo y queda en git.
- No entra a subcarpetas y solo toma `.txt`.
- De la carpeta de audios solo toma archivos que empiezan con `DIAnn -`.
- Si algún día aparece un título con «sesión individual», «mentoría grupal», «atención»,
  «postulación», «ficha» o «entrevista», se excluye aunque esté en una carpeta permitida.

**Por qué el contenido de Drive se ve desde el día 0:** las lecciones van atadas a su `leccionId`, y
la búsqueda solo le devuelve a cada aprendiz las lecciones que ya desbloqueó. El contenido de Drive
no es una lección, va con `leccionId` vacío, y el backend **nunca filtra** los chunks sin lección
(`VectorStorePort`). Si se quiere que un audio `DIAnn` solo aparezca a partir del día *nn*, hace
falta un cambio en el backend; este script no lo resuelve.

## Paso a paso

Todo se corre desde esta carpeta: `cd scripts/sparkie-indexacion`.

### 1. Ver qué hay ya en producción (solo lectura)

```bash
./consulta-indexados.sh
```

Necesita `aws` con el perfil `prod` y `python3`. Verifica que la cuenta sea la de producción
(302277511407) y si no, no hace nada. La consulta corre en una transacción **de solo lectura**:
Postgres rechazaría cualquier escritura. Deja dos archivos en `consulta/`:

- `saltear.txt`: lo que ya está en producción, para pasárselo al indexador.
- `informe.txt`: cuántos documentos hay completos, **a medias** y **duplicados**. Leerlo.

**Correrlo siempre antes de cada tanda.** Es la fuente de verdad; el registro local
(`indexados.jsonl`) solo sabe lo que se mandó desde esta máquina.

### 2. Ensayo (no envía nada)

```bash
python3 indexar.py --saltear-archivo consulta/saltear.txt
```

Sin `--enviar` el script **no manda nada**: lista cada documento, cuántas partes tiene, cuántas
faltan, y cuántos embeddings va a gastar en total. Baja los `.txt` de Drive para poder contarlos
(no guarda nada en disco). Para ver solo los títulos sin bajar nada: `--solo-listar`.
Para una sola fuente: `--fuentes lecciones` (o `drive-audios`, `drive-canal`, separadas por coma).

### 3. Sacar el token de admin

El backend no pide usuario y contraseña en cada pedido: al entrar te da un **token de sesión**
(un código largo) y la app lo manda en cada pedido en la cabecera `X-Auth-Token`. El script
necesita ese mismo código para hablar en tu nombre.

1. Entrá a la **web** de Renaser con tu cuenta de **admin** (tiene que ser ADMIN o ALCHEMIST; con
   otro rol el backend responde 403).
2. Abrí las herramientas del navegador (**F12**) → pestaña **Consola**.
3. Escribí `localStorage.getItem('renaser.sesion.token')` y Enter. Lo que sale entre comillas es
   el token. (También se ve en **Aplicación → Almacenamiento local**, clave `renaser.sesion.token`.)
4. En la terminal, cargalo **sin que quede en el historial** (pegalo cuando lo pida; no se ve):

```bash
read -rs RENASER_ADMIN_TOKEN && export RENASER_ADMIN_TOKEN
```

Ese token vale **lo mismo que tu contraseña de admin** mientras la sesión esté viva (hasta 30
días sin uso). No lo pegues en chats ni en archivos. El script lo lee de la variable de entorno,
nunca como argumento, y nunca lo imprime. **Al terminar, cerrá sesión en la web**: eso lo anula.

### 4. Enviar, en tandas de hasta 900 por día

```bash
python3 indexar.py --enviar --url https://djbooeq09skac.cloudfront.net \
    --saltear-archivo consulta/saltear.txt
```

- Pide escribir `ENVIAR` para confirmar. Se niega si la URL no es `https://` o si falta el token.
- Manda como máximo **900 embeddings por corrida** (`--limite-embeddings N` para cambiarlo). La
  cuota gratuita de Gemini es de **1.000 por día y por proyecto**, y la app también gasta: cada
  pregunta al acompañante es un embedding. Si un documento no entra entero en lo que queda del
  tope, no se empieza: se para ahí y sigue al día siguiente.
- Cada chunk aceptado se anota en `indexados.jsonl`. **Relanzar el mismo comando nunca duplica**:
  saltea lo anotado y lo que dice `saltear.txt`.
- **Ante cualquier error se para en el acto** y dice qué documento quedó a medias y cómo seguir:
  - **429** (se acabó la cuota de Gemini): esperar al reinicio (medianoche, hora del Pacífico) y
    relanzar el mismo comando. Completa lo que quedó a medias.
  - **401/403**: token vencido o sin permiso. Sacar uno nuevo (paso 3) y relanzar.
  - **Corte de red / timeout**: no se sabe si el último chunk llegó. Correr de nuevo el paso 1
    antes de relanzar.
  - **5xx u otro**: mirar los logs del backend antes de relanzar.

Lo pendiente al 2026-09-26 son ~1.504 chunks de lecciones (77 lecciones), más lo que falte de las
lecciones a medias, más lo de Drive (el ensayo lo cuenta). A 900 por día son varios días: repetir
los pasos 1, 2 y 4 cada día.

### 5. Verificar

1. Volver a correr `./consulta-indexados.sh`: los documentos enviados tienen que aparecer como
   completos y no tiene que haber duplicados nuevos. La primera tabla muestra filas por
   `tipo_fuente`/`clase`.
2. Probar en la app: preguntarle al acompañante algo que solo esté en un audio o video nuevo.

## Lecciones a medias o duplicadas

El script viejo contaba el 429 como un error más y seguía con el chunk siguiente (E-282). Por eso
en producción puede haber lecciones **a medias** (les faltan partes sueltas). `informe.txt` las
lista.

- **A medias** → no hay que borrar nada. `saltear.txt` anota qué partes ya están, y el indexador
  manda **solo las que faltan**. Se completan solas en el paso 4.
- **Duplicadas** (la misma parte dos veces) → el indexador las saltea enteras. Arreglarlas es
  borrar filas en producción, y **un DELETE es una escritura**: no está en este script ni se
  corre a ciegas. Antes hace falta (1) aprobarlo explícitamente, (2) un respaldo de esas filas
  (un `COPY (SELECT …) TO` de las filas afectadas, o un snapshot de la base), y (3) un `SELECT`
  que muestre exactamente qué filas se van a borrar y cuántas, revisado antes de ejecutar. Una
  fila duplicada solo hace que el acompañante vea el mismo pedazo dos veces; no es urgente.
- **Raras** (sin `parte` o con totales distintos) → se saltean enteras. Revisarlas a mano.

## Nombres en la base — a confirmar por el dueño

Las lecciones se guardan como ya están en producción (`tipo_fuente = TRANSCRIPCION_CLASE`,
`clase` = nombre del curso). Para lo nuevo el script propone, en constantes al principio de
`indexar.py`:

| Fuente | `tipo_fuente` | `clase` | `documento_id` | `leccion_id` |
|---|---|---|---|---|
| canal | `TRANSCRIPCION_CANAL` | `CANAL DARREN` | id del archivo de Drive | vacío |
| audios | `AUDIO_DIARIO` | `AUDIOS DIARIOS` | id del archivo de Drive | vacío |

**Decidirlo antes de la primera tanda**: cambiarlo después deja filas con dos nombres distintos.

## Pruebas

```bash
python3 -m unittest -v test_indexar
```

Sin red y sin producción: el HTTP y Drive son falsos. Cubren el troceo (y que da los mismos 2.526
chunks que el script viejo), el registro y los salteos, que el 429 corte en el acto y se retome
donde quedó, el tope diario, los filtros de Drive y el procesado de la consulta.

## Estado anterior (histórico)

> **Corregido 2026-09-26.** Esta sección decía que había 985 chunks de 47 lecciones «completos»
> (porque `embedding IS NULL` daba 0) y que se retomaba con `--desde 47`. Las primeras 47
> lecciones suman **1.022** chunks con el mismo troceo: faltaban ~37, repartidos en lecciones a
> medias por el 429 contado como error (E-282). `embedding IS NULL = 0` solo prueba que las filas
> que existen tienen vector, no que estén todas. `--desde` ya no existe: dependía del orden de los
> archivos y no protegía de duplicar.

La cuota gratuita de Gemini es de 1.000 embeddings por día **y por proyecto**: una clave nueva no
reinicia nada. Decisión del dueño (2026-09-06): esperar los reinicios diarios en vez de activar
facturación (costaría centavos para este volumen; confirmar la tarifa vigente de
`gemini-embedding-001` antes, que cambia).

## Por qué esta carpeta está DENTRO del repo

Todo esto vivía en el directorio temporal de una sesión de Claude Code. Volver a extraer las 124
transcripciones de YouTube cuesta bastante, así que se versionó a propósito (5,5 MB de datos
derivados): el dueño trabaja desde dos máquinas y el repo es la única vía que llega a las dos. Los
`.vtt` crudos (36 MB) no se guardaron; se regeneran con `extraer_todo.py`.

`consulta/` e `indexados.jsonl` **no** se versionan (`.gitignore`): en otra máquina, el paso 1
reconstruye desde producción lo que hace falta.

## Detalle que costó encontrar

`yt-dlp` fallaba en los vídeos del curso hasta que se le pasó
`--extractor-args youtube:player_client=android`. Sin eso parecen vídeos caídos y no lo están.
