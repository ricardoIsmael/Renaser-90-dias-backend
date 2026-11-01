# Research: decisiones de diseño de la spec 024

## D1 — Una columna por contenido, no una tabla de pertenencia ni una comunidad paralela
**Decisión:** `formacion` en `publicaciones_muro`, `testimonios`, `eventos`, `cursos`, `conversaciones` y `ranking_aprendices`. **Por qué:** es la pauta de la 022
(`celulas`, `participantes_programa`) y la restricción del dueño («extender, no duplicar»). **Descartado:** tablas `muro_por_formacion`… (duplican y obligan a mover filas) y
una columna en `cohortes` (los chats y el Muro no viven en cohortes).

## D2 — Los tipos de comunidad son una columna del catálogo, no una tabla
**Decisión:** `formaciones.tipos_de_comunidad text[]` con los tipos prendidos. **Por qué:** 1–4 valores que se leen y escriben juntos y nadie une con otra tabla (igual que
`duraciones_dias`, 022). **Quién la escribe:** `users` (dueño de `formaciones`), por `ConfigurarComunidadDeLaFormacionUseCase`; `chat` solo la lee por
`users.api.ComunidadDeLaFormacionFinder` y suma el conteo de conversaciones. **Descartado:** tabla `tipos_de_comunidad_por_formacion` (tabla innecesaria) y interruptores de Soporte TI por
formación (esos son globales por funcionalidad, y el dueño los quiere así).

## D3 — El aislamiento se aplica en el servidor, sobre el punto único de la 022
`AlcanceDeFormacionFinder.de(actor)` da el alcance; `FormacionDelContenido.paraLeer/paraCrear` resuelve el parámetro `formation`. Una pieza de otra formación se trata como
**inexistente** (el mismo `404` y el mismo texto) filtrando al cargarla, no con un `403` aparte. **Descartado:** que la app filtre (FR-004 pide que el servidor responda igual que
un inexistente) y una nueva excepción `403`.

## D4 — Quién ve una conversación: una sola clase
Las cuatro copias de la regla de acceso (`MensajeService`, `ConversacionService`, `PresenciaService`, `AutorizacionDeConversacionService`) ya habían dejado un hueco una vez
(el soporte de un ex administrador; ver el comentario de `MensajeService.requireParticipante`: «si se toca una, se tocan las cuatro»). La regla por formación vive en **`VisibilidadPorFormacion`** y las cuatro la invocan; la presencia solo la aplica al *leer* quién está en línea, no al
repartir el aviso (nadie recibe un tópico al que no puede suscribirse).

## D5 — Una comunidad general por formación, nombres sin romper la Escuela
La GLOBAL pasa a una por formación (índice único por `formacion`). La Escuela conserva «Formación Renaser Global» y «`<Nombre>` – Formación Renaser» (decisión del dueño);
las otras usan el nombre de su formación. Quien se une: aprendiz → la suya; personal → todas las asignadas (para recibir avisos); Admin/Alq → las tres; se completa **al abrir la
Tribu** (idempotente), así no hace falta un evento de «formaciones del personal cambiadas».

## D6 — Soporte y directos: la clave lleva la formación fuera de la Escuela
Un aprendiz que pasa de formación necesita un soporte nuevo sin perder el viejo, y dos personas que comparten dos formaciones, un directo por formación. La clave de la Escuela
no cambia (no se migra nada); las demás agregan `:CÓDIGO`. **Descartado:** mover el soporte de formación (mezclaría el historial) y borrar el viejo (se pierde historia).

## D7 — Ranking: cada formación se ordena y numera aparte; el corte guarda la formación
Los puntajes se calculan en lote para todos (D-43) y se ordenan por formación. `ranking_aprendices.formacion` se guarda **además** de derivarse de la persona por una sola razón:
el corte diario es historia (regla 04). El caché `ranking` se vacía entero al regenerar (las claves varían por alcance). El porcentaje de cursos usa los cursos de la formación de
cada aprendiz (tres consultas por formación, no una por persona). Kilómetros/Gimnasio según los hábitos de la formación: **depende de la 023**.

## D8 — Podio semanal: uno por formación, ids compatibles
Los ids del mensaje de la Escuela son los de siempre (no se republica la semana ya salida); los de las otras incluyen la formación. Cada formación falla por separado.

## D9 — El cambio de formación del aprendiz rige al instante (decisión del dueño, 024-9 B)
Coincide con la 022. `chat` escucha `AprendizTrasladadoDeFormacionEvent`: sale de la comunidad general y del soporte de la anterior, entra a los de la nueva. Los directos se
esconden solos (ya no comparten formación) y el chat del grupo lo reconcilia `community` (022). Lo que escribió se queda donde lo escribió.

## D10 — Cursos: no hay alta en la app
La columna y todas las lecturas filtran; el alta (importación o panel web) usa el `DEFAULT 'ESCUELA'` hasta que se cree un servicio de alta, que la exigirá. `dia_desbloqueo`
llega a 365 para el Diplomado de 1 año.
