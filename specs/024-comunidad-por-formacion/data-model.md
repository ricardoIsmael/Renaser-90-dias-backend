# Data model: Comunidad por formación (spec 024)

Reutiliza lo que ya hay. **Ninguna tabla nueva**: la formación (spec 022) es **una columna** en la tabla que ya guarda cada pieza, y los tipos de comunidad son otra columna del
catálogo de formaciones. Las migraciones son aditivas, llevan cabecera con el porqué (regla 04) y todo lo existente pasa a la Escuela por el `DEFAULT 'ESCUELA'`.

## Columnas nuevas

| Migración | Tabla | Columna | Notas |
|---|---|---|---|
| **V137** | `publicaciones_muro` | `formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones(codigo)` | índice parcial `(formacion, creado_en DESC) WHERE NOT oculta` para el feed |
| V137 | `testimonios` | `formacion …` (misma forma) | siempre tiene formación: el camino «sin cuenta» ya no existe (2026-09-18) |
| V137 | `eventos` | `formacion …` | su audiencia se resuelve dentro de ella |
| V137 | `cursos` | `formacion …` | + `cursos.dia_desbloqueo` y `secciones_curso.dia_desbloqueo` pasan de `0..90` a `0..365` (Diplomado de 1 año) |
| **V138** | `conversaciones` | `formacion …` | desnormalización justificada: la lista de chats filtra en UNA consulta sin JOIN; un grupo no cambia de formación (V125) |
| V138 | `conversaciones` | índice único de la GLOBAL pasa de `(tipo) WHERE tipo='GLOBAL'` a `(formacion) WHERE tipo='GLOBAL'` | una comunidad general **por formación** |
| V138 | `formaciones` | `tipos_de_comunidad text[] NOT NULL DEFAULT {GENERAL,GRUPO,SOPORTE,DIRECTO}` con `CHECK <@` | los tipos PRENDIDOS; mismo criterio que `duraciones_dias` |
| **V139** | `ranking_aprendices` | `formacion …` | el corte es historia: la formación en que estaba el aprendiz ESE día (no se reescribe si cambia); posición contada dentro de ella |

## Lo que NO lleva columna (se deriva, regla 04)

- Comentarios, reacciones y fotos del Muro → de la publicación (`publicacion_id`). Secciones, lecciones, recursos y progreso → del curso. «Voy», recordatorios y
  asistencia → del evento. Mensajes → de la conversación. `categorias_muro` y `niveles_membresia` son comunes a las tres formaciones.
- La formación de una **persona** sigue en `participantes_programa.formacion` (aprendiz) / `formaciones_personal` (personal), spec 022.
- `ranking_celulas` ya es de un grupo, y el grupo tiene `celulas.formacion`.

## Claves de conversación

| Tipo | Escuela (igual que antes) | Otra formación |
|---|---|---|
| Soporte | `soporte:<aprendiz>` | `soporte:<aprendiz>:DIPLOMADO` |
| Directo | `<a>_<b>` | `<a>_<b>:DIPLOMADO` |
| Grupo / General | `celula_id` / una por formación | idem |

`clave_directa` ya era UNIQUE (V53/V54): sigue siendo el candado. Quien pasa de formación tiene un soporte nuevo en la nueva y conserva el de la anterior sin verlo.

## Reglas (dominio, sin Spring)

- `shared.domain.TipoDeComunidad` (`GENERAL`, `GRUPO`, `SOPORTE`, `DIRECTO`) y `shared.domain.FormacionDelContenido` (`pedida`, `paraLeer`, `paraCrear`): lo que cada
  sección pregunta sobre su formación, en un solo lugar.
- `Publicacion`, `Testimonio`, `Evento`, `Curso`, `Conversacion` y `PosicionRanking` llevan `formacion` **inmutable**: una pieza no cambia de formación (se copia).
- `Conversacion.claveSoporteDe/claveDirectaDe(…, formacion)`, `NombreDelChat.deSoporte(nombre, formacion)` y `deLaComunidad(formacion)`.

## Una conversación se ve si… (`chat.VisibilidadPorFormacion`)

1. es de una formación del **alcance** de quien mira (aprendiz: la suya; personal: la activa; Admin/Alq/Soporte TI: las tres);
2. su **tipo de comunidad está prendido** en esa formación;
3. si es un directo, **la otra persona también pertenece** a esa formación (Admin/Alq cuentan como de cualquiera).

La misma regla corre en la lista, el acceso a mensajes, la presencia, la autorización en vivo (STOMP), las fotos/integrantes del chat y «Compartir».
