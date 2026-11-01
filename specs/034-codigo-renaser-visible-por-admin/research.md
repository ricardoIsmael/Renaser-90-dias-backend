# Research: Administración decide si el mentor y el líder ven el Código Renaser

Cada punto es una decisión de diseño con lo que se evaluó. No quedan `NEEDS CLARIFICATION`.

## D1. Dónde vive el interruptor

- **Decisión**: una funcionalidad del catálogo de `plataforma`: `radar.fichas`, hija de `radar`, con `rolesQueLaUsan = {MENTOR, TRAINEE_GUIDE, MENTOR_LEAD}`.
- **Por qué**: el dueño pidió reutilizar el mecanismo de interruptores y de auditoría de Soporte TI y **no crear tablas si sirve lo existente**. Ya da: estado compartido entre instancias
  (`funcionalidades_estado*` + Redis), copia en memoria (≤ 2 ms), auditoría append-only con trigger, apagado por rol, y que `GET /funcionalidades/mias` lo entregue a la app.
- **Alternativas**: (a) una tabla o un ajuste nuevo con su historial — duplica lo que ya existe, descartada por el dueño; (b) una propiedad de Parameter Store — no cambia sin reiniciar; (c) una
  `ReglaDeDisponibilidad` con su propio almacén — habría que guardar el valor en algún lado, o sea (a).

## D2. «Nace apagada» solo para tres roles

- **Problema**: `ValorPorDefecto.APAGADA` apaga la funcionalidad **para todos**, y Administración y Alquimista tienen que seguir viéndola (FR-010). Sembrar una fila con una migración pondría un
  `actualizado_por` inventado (el campo es `NOT NULL` y la auditoría no tiene un autor «sistema»).
- **Decisión**: `DefinicionDeFuncionalidad` suma `apagadaPorDefectoPara: Set<UserRole>` (vacío por omisión). La evaluación lo aplica **solo cuando no hay fila**: la primera vez que alguien
  cambia el interruptor se guarda una fila y manda la fila. Sin migración, sin datos sembrados, y publicar el backend deja el interruptor apagado (FR-005).
- **Alternativas**: seed por migración (V133) — rechazada por el autor inventado y porque una migración aplicada es inmutable; `ValorPorDefecto` nuevo — mezcla dos ideas en un enum.

## D3. Apagado se esconde, no se «mantiene»

- **Problema**: una funcionalidad apagada para un rol responde 503 con el aviso de mantenimiento. La spec exige «sin un hueco, un error ni un aviso de mantenimiento» (FR-007) y que un APK
  anterior cargue la ficha sin la sección (US2-7), y ese APK esconde la sección solo ante 403/404.
- **Decisión**: `DefinicionDeFuncionalidad.seEsconde = true` → la evaluación devuelve `ResultadoDeEvaluacion.noDisponible(clave)`, que ya existe (fase 2: despliegue y roles personalizados). Efecto:
  `Interruptores.exigirPrendidaPara` lanza `FuncionalidadNoDisponibleException` → **404 `FUNCIONALIDAD_NO_DISPONIBLE`**; `GET /funcionalidades/mias` lo lista en `noDisponibles` y la app lo esconde sin
  diálogo (`services/http/erroresSinDialogo.ts`).
- **Alternativas**: 403 propio — no distingue «no te toca» de «no tienes permiso» y la app nueva lo trataría como un fallo; 503 — es el aviso de mantenimiento que se quiere evitar.

## D4. Dónde se hace cumplir en el servidor

- **Decisión**: dentro de los tres casos de uso que sirven el Código Renaser de otra persona, **después** de autorizar y **antes** de leer: `AcompanamientoDeAlumnoService.codigoRenaserDeAlumno`
  (mentor/guía), `RadarDePersonaService.radarDe` (líder por grupo) y `.radarDeUnaPersona` (líder sin grupo, spec 021). Llaman a `Interruptores.exigirPrendidaPara(RADAR_EN_FICHAS, actor)`.
- **Por qué no `@Funcionalidad` en el método**: la del método **reemplaza** a la de la clase (`mentor.mi_grupo`, `lider.cumplimiento`), y se perdería el interruptor de «Mi grupo» y del
  cumplimiento en esas rutas. `InterruptoresDelChat` ya usa este patrón cuando la funcionalidad depende del recurso. Autorizar primero conserva el 403 de siempre para quien no debe ni preguntar.
- **Por qué es completo**: son los únicos caminos que entregan `CodigoRenaserDePersona` / `HistorialRadarPage` de otra persona (`CodigoRenaserDePersonaFinder` solo lo consume `leadership`).
  Una prueba de arquitectura-ligera busca que ninguna otra clase de `leadership` ni `mentoring` use ese Finder.
- **Rol personalizado**: se evalúa por el rol base del actor (`usuarios.rol`), así que «Mentor junior» y similares se tratan como su base, como pide la spec.
- **«Ver como»**: la cuenta de prueba tiene el rol que representa y pasa por la misma evaluación.

## D5. Quién lo cambia y cómo

- **Decisión**: endpoint nuevo `GET/PUT /api/v1/admin/codigo-renaser-en-fichas` (`@RequiresPermission(MANAGE_RADAR_VISIBILITY)`, `@Funcionalidad("admin")`) en `plataforma`. Una guardia del servicio exige
  ADMIN, ALCHEMIST o IT_SUPPORT **activos** (el interceptor de permisos falla abierto para MENTOR/guía/ADMIN/ALCHEMIST: A-1; por eso la guardia, como `GuardiaDeAnimalesDeFase`).
- **Soporte TI**: puede usar el endpoint (decisión 034-4 B) y, sin tocar nada, su consola ya lista `radar.fichas` con un interruptor por cada uno de los tres roles (motivo obligatorio, su flujo de siempre).
  La app no le agrega la fila de Administración: `canAdminister` es de ADMIN y ALCHEMIST y cambiarlo tocaría otras cosas.
- **Sin motivo escrito** (034-5): la auditoría guarda el motivo fijo «Cambio desde Administración (Código Renaser en las fichas)» para que la fila quede completa y la consola la muestre.
- **No se avisa a la persona** (034-6): no se emite ninguna notificación a aprendices, mentores ni líderes. El evento `FuncionalidadCambiadaEvent` (que **recarga la copia en todas las instancias**)
  sí avisa por push a Soporte TI, como cualquier cambio de interruptor; su título deja de decir «Soporte TI cambió…» cuando quien cambió es Administración.
- **Concurrencia** («gana el último»): lee la versión vigente, escribe con bloqueo optimista y, si otro cambio llegó en el medio, relee y reintenta (hasta 3 veces); ambos cambios quedan en la auditoría.
- **Idempotente**: pedir el valor que ya rige responde 200 con el estado actual y no escribe una fila de auditoría.

## D6. Un solo interruptor sobre tres roles

- **Decisión**: «visible» = ninguno de {MENTOR, TRAINEE_GUIDE, MENTOR_LEAD} está apagado y la funcionalidad está prendida; «oculto» = los tres apagados. Escribir `visible=true` deja
  `prendidaGlobal=true` y `apagadaPara = {}`; `visible=false`, `prendidaGlobal=true` y `apagadaPara = {los tres}`.
- **Estado mixto** (Soporte TI apagó solo uno desde su consola): la lectura dice `visible=false` y `porRol` con el detalle; la pantalla lo explica en una línea y cualquier cambio de Administración
  deja los tres iguales. Si Soporte TI apagó la funcionalidad **para todos**, también `visible=false`; al encenderla desde Administración vuelve a `prendidaGlobal=true`.

## D7. App

- La sección «Código Renaser» de las fichas ya es **un solo componente** (`CodigoRenaserDelAlumno`) que usan `AlumnoScreen`, `FichaAprendizScreen` y `PersonaDelLiderScreen`: se esconde ahí,
  con `useFuncionalidad(CLAVES.radarFichas)`. Con `disponible=false` (o apagada) no se dibuja **ni se pide**. Administración no cambia: para ADMIN/ALCHEMIST la funcionalidad siempre está prendida.
- La pantalla de Administración usa `Interruptor`, `Alert` de confirmación, `ConfirmacionEnLinea` y `tacto.logro()` como el resto de «Más opciones».

## D8. Qué se asume (a la vista)

1. El texto del motivo fijo de la auditoría (D5).
2. Un estado mixto se lee como «oculto» (D6).
3. La clave del endpoint usa `@Funcionalidad("admin")`: si Administración entera está en mantenimiento, esta pantalla también.
