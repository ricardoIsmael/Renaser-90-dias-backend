# Research: Hábitos y categorías por formación (spec 023)

## 1. ¿Dónde vive la formación de un hábito y de una categoría?
- **Decisión**: columna `categorias_habito.formacion` y columna `habitos.formacion` (solo del catálogo), unidas por una llave compuesta.
- **Por qué**: los hábitos y las categorías ya son tablas; la formación es un dato más (restricción 1 del dueño). La llave compuesta
  `(formacion, categoria_clave) → categorias_habito (formacion, clave)` hace imposible que un hábito esté en la categoría de otra formación (FR-002) sin
  una regla que olvidar.
- **Descartado**: una tabla puente `habito_formacion` (permitiría un hábito compartido, que el dueño no eligió); derivar la formación del JOIN (no deja
  crear los índices únicos por formación).

## 2. Hábito compartido o copia (decisión 1 = copia)
- La copia es un hábito nuevo con otro id: no hay vínculo posterior. Se copian sus horarios y su guía (textos); **no** los archivos adjuntos (son objetos de
  S3: compartirlos haría que borrar uno rompa el otro). Las audioterapias son contenido global por semana, no del hábito.

## 3. La clave funcional (`clave_sistema`)
- Era única en todo el sistema; los flujos propios (`WAKE_UP`, `PASTILLA_RENACER`, `DAILY_KM`…) la buscan. Ahora es única **por formación** y
  `LoadHabitoPort.porClaveSistema(clave, formacion)` es la única forma de buscarla: seis servicios la reciben de la formación del progreso de la persona.
  Una formación que no ofrece el hábito no tiene el flujo (`Optional.empty`, FR-005).

## 4. La formación que cuenta para los hábitos
- Es **aquella en que la persona hace su programa** (`participantes_programa.formacion`), no la activa del personal. Así la generación diaria produce los
  registros de «Mi programa» aunque el personal tenga otra activa. Las vistas de acompañamiento comparan con la formación del alumno y exigen que el
  actor la vea (spec 022, `exigirQueLaVea`).
- El aviso de «Mi programa» en otra formación es de la app: el servidor sigue entregando los hábitos propios del programa del personal (son SUS datos, no
  los de otra persona). Límite aceptado, anotado en el reporte.

## 5. Escalas de días
- `horarios_habito.dia_inicio/dia_fin` de un hábito del catálogo se leen en la **escala base de 90 días** (`DuracionDelPrograma.enEscalaBase`), el
  contrato de la 022: el catálogo se escribe una vez y vale para el Diplomado de 3 meses y el de 1 año. Un hábito **propio** nace con el día real de la persona
  (puede pasar de 90), por eso el CHECK pasa a 365 (V136). Los puntos que indexan contenido por día (Audioterapia, Pastilla) usan el día en escala base.
- `DesbloqueoHabitoService`: el tope del día de desbloqueo es la duración de la persona; `HabitosDeUnDia` (huecos de tiempo): el último día es el de la persona.
- **Límite aceptado**: los servicios que leen el horario de un registro ya generado (`AvisoHabitoService`, `SantuarioService`, `resolverVentana`…) usan el día
  del registro sin escalar. Para el catálogo creado desde el panel (un solo tramo abierto desde el día 1) da lo mismo; con tramos múltiples de un Diplomado de
  1 año podrían elegir el tramo equivocado. Queda anotado.

## 6. Hábitos propios y categorías
- Un hábito propio tiene la categoría que la persona eligió (cualquiera existente: la llave simple de siempre). Si no está entre las activas de la
  formación vigente, la app lo agrupa bajo «Mis hábitos». El servidor no reescribe nada.

## 7. API de categorías abierta
- La API de hoy dice la categoría con un enum en inglés (`BODY`, `MIND`, `CONSCIENCE`, `SPIRIT`). Pasa a un string abierto: las cuatro de siempre salen igual
  (la app publicada sigue funcionando) y cualquier otra sale con su clave. `categoryKey` lleva siempre la clave de la base.
