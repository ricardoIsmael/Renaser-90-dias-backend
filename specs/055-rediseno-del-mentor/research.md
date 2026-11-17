# Investigación — Spec 055 fase 1

Hecha el 17/11 sobre `master` de los dos repos (backend `6d838115`, app `8c7e41e5`), con `graphify query` y lectura dirigida.

## R1. ¿De dónde salen la semana y los hábitos del día?

- **Decisión**: un endpoint nuevo del mentor, `GET …/learners/{u}/semana?semanaHasta=`, que reutiliza el armado de la ficha del líder (`ArmadoDeLaFicha` / `SemanaDeLaPersona`), con su semana de **sábado a viernes**, `primeraSemana` (el sábado de la semana del Día 1) y los días con `ObligacionDelDia`. Cambia solo la puerta: el guard es el del mentor (`acompanaVigente` + aprendiz del grupo), igual que `progress`.
- **Por qué**: la spec pide la semana del semáforo (sábado a viernes, FR-011) y el límite del Día 1 (FR-015, FR-017). `progress` va de lunes a domingo y no expone el Día 1; la ficha del líder ya tiene las dos cosas y ya reutiliza `ObligacionDelDia`.
- **Descartado**: cambiar `progress` a sábado–viernes (rompe la app instalada y la vista de Administración, que usan el mismo `SemanaDelAlumno`); armar la semana en la app juntando dos semanas de lunes a domingo (dos pedidos por semana y lógica de días duplicada).

## R2. El color de cada día (círculos y mes)

- **Decisión**: un endpoint liviano `GET …/learners/{u}/dias?desde=&hasta=` (máx. 62 días) que devuelve por fecha el color y el % del semáforo diario, leídos de `semaforo_dias` y derivados con la misma regla del dominio (`ColorSemaforo`: verde ≥ 80 %, ámbar 60–79,9 %, rojo < 60 %), más los días `PAUSADO`, `CUENTA_SUSPENDIDA` y `SIN_DATOS`. La app lo pide una vez por mes visible.
- **Por qué**: hoy el color por día solo viene en la ventana `vigente` del semáforo del aprendiz; el historial trae un color por semana. FR-012 prohíbe una regla nueva: se reutiliza la del semáforo.
- **Descartado**: pedir las 5 semanas del mes para pintar el mes (5 pedidos pesados para solo colores).

## R3. El pilar de cada hábito

- **Decisión**: `ObligacionDelDia` suma el campo `pilar` (la `categoria_clave` del hábito: `CUERPO`, `MENTE`, `ESPIRITU`, `EMOCIONES`; `null` si el hábito no tiene). Es aditivo: las apps instaladas lo ignoran.
- **Por qué**: FR-013 agrupa por pilar y hoy la semana no lo trae; la columna existe en `habitos`.

## R4. Estado y hora de cada hábito

- **Decisión**: cumplido = `COMPLETADO`; vencido = `FALLIDO` o `EXPIRADO`; pendiente = `PENDIENTE` o `EN_CURSO`. La hora que se muestra es `entregadaEn` (la primera entrega o publicación), en la zona del aprendiz; un cumplido sin entrega se muestra sin hora.
- **Por qué**: el servidor no guarda una hora de cumplimiento aparte ni la ventana horaria en la obligación. Se ajusta FR-014 en la spec (la ventana del pendiente queda fuera de la fase 1).

## R5. Evidencia

- **Decisión**: reutilizar `EvidenciaDeObligacion` de la app (visor con `HojaDesdeAbajo`, foto con `expo-image`, audio, video y texto) y `GET /api/v1/evidence/{id}/url` (URL firmada de 10 min, 204 si es texto). Se le suman «anterior / siguiente» dentro del día y el enlace al Muro con `publicacionId`.
- **Riesgo anotado**: el permiso de la evidencia mira `participantes_programa.mentor_id`, no la asignación vigente del grupo. Es el comportamiento actual de la ficha (no cambia), pero en el E2E se prueba con un mentor rotado.

## R6. Notas privadas

- **Decisión**: un módulo nuevo dentro de `mentoring` (hexagonal: dominio puro, servicio, puertos, adaptadores JPA y REST), tablas `notas_del_mentor` y `notas_del_mentor_versiones` (migración V197 o la siguiente libre al fusionar). Las correcciones guardan el texto anterior en versiones (principio IV).
- Lectura del líder: no hay relación líder→mentor en el código; el líder (`MENTOR_LEAD`) ve a las personas de su formación (`AccesoDeLiderazgo` + `AlcanceDeFormacionFinder`). Se reutiliza esa regla y se ajusta FR-024 («el liderazgo de su formación»).
- Permisos: el mentor con `USE_APP` (como toda la ficha) y guard en el servicio; el líder con `VIEW_MENTOR_CORPS`; Administración con `MANAGE_TRAINEES`. No hace falta un `Permission` nuevo, así que `MatrizDeMentorYGuiaTest` no cambia; `MatrizPorRolEnTodoEndpointTest` y `EndpointAuthorizationDeclarationTest` las recorren solas.

## R7. Avisos leídos al abrir la ficha (FR-009)

- **Decisión**: la app marca leídos, con el `PUT /api/v1/notifications/{id}/read` que ya existe, los avisos sin leer cuya `route` es `/mentor/groups/{g}/learners/{u}`. No hace falta endpoint nuevo.
- **Depende de** #165 (un aviso vigente por aprendiz y motivo).

## R8. Mis grupos y Mi grupo (solo app)

- **Decisión**: los grupos salen de `gruposQueAcompana` (contexto del mentor, ya existe). Por grupo, el semáforo del grupo (`GET /mentor/groups/{g}/semaforo`, ya existe) da la cantidad, el color, el % y el `motivo` de cada aprendiz, así que no hace falta endpoint nuevo. La línea gris usa el aviso vigente (por `route`) y, si no hay, la etiqueta del semáforo.

## R9. App: carga y caché

- **Decisión**: no hay React Query; se sigue el patrón de la app (`useState` y `useEffect` sobre `apiFetch`). Un hook nuevo `useSemanasDelAprendiz` guarda en un `Map` por `semanaHasta` lo ya cargado mientras la ficha está montada, y al elegir una semana pide también la anterior (FR-016). El mes se pinta con `CalendarioDelMes`, generalizado para que no dependa del tipo de los eventos (o un `MesDeColores` que reutiliza su lógica de la grilla), con la semana de sábado a viernes en la fila plegada.

## R10. Dónde se toca la app

- Navegación: no hay rutas de navegador para el mentor; las vistas viven en `vistasDeLosPaneles` (Yo) y `vistasDelMentor` (Comunidad). Se agrega la vista `misGrupos` en los dos y se mantiene `miGrupo` y `alumno`.
- Las fichas del líder (`PersonaDelLiderScreen`) y de Administración (`FichaAprendizScreen`) suman solo el lápiz de lectura.
