# Research: Mi Radar en Yo

## R1. Dónde guardar las horas de Mi Radar

- **Decision**: una columna `horas_mi_radar smallint[]` en `participantes_programa` (las horas de todos los días), donde `NULL`
  es apagado, y una columna `dias_mi_radar jsonb` con los días de la semana que tienen horas propias o «Sin Radar ese día»
  (cuarta ronda, 2026-10-29).
- **Rationale**: el Radar ya cuelga de esa fila (`registros_radar.participante_id`), que es una por persona y tiene
  la zona horaria y el día del programa. El dueño pidió no crear tablas.
- **Por qué jsonb para los días**: son a lo sumo siete elementos que siempre se leen y se escriben juntos, y nadie consulta por día de
  la semana. Una tabla hija (una fila por persona y día) la descartó el dueño («no crear una nueva tabla»). Costo aceptado: la base solo
  asegura que sea un arreglo; el dominio (`MiRadar`) valida el resto. Si algún día hay que consultar por día, se normaliza.
- **V119 se modificó, no se creó V120**: V119 no está en `master` ni en producción (solo en esta rama y en bases clonadas locales). Una
  base local que ya la aplicó necesita agregar la columna a mano y reparar la huella (E-157).
- **Alternatives considered**:
  - Una tabla `mi_radar`: la descartó el dueño.
  - `preferencias_notificacion`: guarda temas encendidos o apagados, no horas, y mezclaría avisos con el programa.
  - Guardarlo solo en el teléfono: el mentor no vería el estado y se perdería al cambiar de teléfono (FR-004).

## R2. Hora prevista

- **Decision**: una columna `registros_radar.hora_prevista smallint NULL`, que calcula el servidor al registrar. Los
  registros anteriores quedan en `NULL`, sin rellenarlos.
- **Rationale**: las horas elegidas cambian con el tiempo. Deducirla después daría resultados falsos, así que se
  guarda en el momento (constitución IV). No rellenar evita adivinar los registros que SER hizo fuera de hora.
- **Alternatives considered**:
  - Rellenarla para 08–19: adivina.
  - Que la mande la app: el dueño pidió validar en el servidor.

## R3. Quién lee y por dónde

- **Decision**:
  - El mentor, con su endpoint de siempre.
  - Líder, administración y alquimista, con `GET /leadership/groups/{g}/people/{p}/radar`, detrás del guardián
    `AccesoDeLiderazgo` que ya usa `.../week`.
  - La ficha de Administración pasa el `cellId` del aprendiz.
- **Rationale**: reutiliza una regla de acceso ya probada (rol activo y persona de ese grupo hoy). No suma permisos
  nuevos.
- **Alternatives considered**:
  - Un endpoint `/admin/trainees/{id}/radar`: duplica la autorización.
  - Un permiso nuevo: sobreingeniería.
- **Límite aceptado**: un aprendiz sin grupo, o de un grupo sin mentor, no muestra la sección en la ficha de
  Administración.

## R4. Registro de consulta

- **Decision**: no se agrega. La ficha del líder ya registra la consulta al abrirse (spec 005).
- **Rationale**: `consultas_de_ficha` alimenta el resumen «Tu líder revisó a tu grupo»; meter ahí las lecturas del
  Radar cambiaría ese aviso, y la alternativa era una tabla nueva.

## R5. Avisos del teléfono

- **Decision**:
  - Una función pura `horasDeAviso(dia, miRadar)` decide qué horas avisan.
  - Se reutilizan los identificadores `renaser.radar.slot.HH`, el canal `recordatorios-codigo-renaser`, el disparo
    `DAILY` a HH:02 y la cancelación previa (que ahora cubre de 03 a 23).
  - Desde el día 8 el contenido suma `data.route = '/mi-radar'`.
  - Se sincroniza al abrir la app, al volver al frente (como hoy) y al guardar.
- **Rationale**: es el mismo mecanismo de la semana 1, con otras horas. Doce avisos diarios como máximo, igual que
  hoy y muy por debajo del tope de 64 de iOS.

## R6. Invitación del día 8

- **Decision**:
  - Durante los días 1 a 7, el teléfono programa una sola notificación de fecha (`renaser.radar.invitacion`) para el
    día 8 a las 09:00 locales, con `data.route = '/mi-radar'`.
  - Se marca por cuenta en el almacenamiento local.
  - Se cancela si la persona enciende Mi Radar antes.
- **Rationale**: no necesita servidor, scheduler ni tabla. Cada persona abre la app en la semana 1, porque el Radar
  se abre solo.
- **Límite aceptado**: con dos teléfonos podría llegar dos veces; con la app nunca abierta en la semana 1, no llega.

## R7. Patrones

- **Decision**: cálculo puro en la app, sobre los registros cargados.
  - Desde 3 registros: la hora con más energía promedio y la de menos, cada una con «en N registros» (solo horas con
    al menos 2 registros).
  - «Dijiste que evitabas algo en N de M registros», con el último ejemplo.
- **Rationale**: es lo que muestra la vista previa de gerencia, sin endpoint nuevo.

## R8. Rechazo al registrar con Mi Radar apagado

- **Decision**: 409 con la excepción de regla de negocio del proyecto y un mensaje propio («Mi Radar está apagado»).
  La app nunca ofrece registrar en ese estado, así que solo lo ve quien se salta la app.
- **Rationale**: es una regla de estado, no de permisos. Un 403 apagaría el Radar en la app (`RadarContext`).
