# Research: Mensaje del semáforo y descarga del podio

## R1 — Dónde se elige y se redacta el mensaje

- **Decision**: el servidor, en el módulo `points`, elige y redacta. La app recibe el texto final y el nombre del
  sticker.
- **Rationale**:
  - La regla (no repetir, variables, cambio de color) es de negocio (constitución I y II).
  - El catálogo se puede corregir sin APK.
  - Los APK viejos no se rompen.
- **Alternatives**: elegir en la app con el catálogo embebido, descartado porque cada cambio de texto pediría un APK,
  no habría historial entre dispositivos y la app recalcularía reglas.

## R2 — Qué color usa el mensaje

- **Decision**: el color de la ventana **vigente**, el mismo de la tarjeta (`resumenParaHoy`, últimos 7 días cerrados).
- **Rationale**: el mensaje nunca contradice lo que se ve. Como la ventana se cierra a medianoche, el color casi
  nunca cambia durante el día. Si cambia (recálculo tardío), se elige uno nuevo del color nuevo (decisión asumida 2).
- **Alternatives**: el color en vivo de hoy (`SemaforoDelDiaFinder`), descartado porque mostraría un color distinto al
  de la tarjeta.

## R3 — Grupo sin color

Se aplica en este orden, y gana el primero que se cumple:

1. La ventana vigente tiene días `CUENTA_SUSPENDIDA` y ningún día MEDIDO (persona recién reactivada; una cuenta
   suspendida hoy recibe 403 en `/home` y no ve nada) → CUENTA_PAUSADA.
2. Programa completado (`programaCompletado`, día > 90) → GRADUADO.
3. `semaforo.pausado` → PAUSA.
4. Antes del día 1, o sin días con datos y día del programa ≤ 7 → INICIO.
5. SIN_DATOS en cualquier otro caso → SIN_PROGRAMA.

- **Graduado**: después del día 90, si `/home` manda `semaforo: null`, el servidor manda igual un `semaforo` con
  color SIN_DATOS y solo el mensaje GRADUADO; la app pinta la tarjeta normal con «Sin datos» y el mensaje (verificar en
  T029 qué manda hoy y elegir el camino que no cambie la tarjeta de quien no se mide).
- **Rationale**: son los estados que el servidor ya conoce (`EstadoDiaSemaforo`, `ParticipacionPrograma`, `users`).

> **Verificado y corregido 2026-10-19 (E-631, D-277).** (1) `/home` **no** manda `semaforo: null` después del día 90:
> `ProgramasActivadosService` no filtra los programas completados, así que la tarjeta sigue y no hizo falta fabricar un
> semáforo SIN_DATOS. (2) `programa_completado` se marca **al llegar** al día 90 (cuando todavía se mide) y
> `dia_programa` queda acotado en 90: no sirve para «después del día 90». GRADUADO = `hoy local > ultimaFecha` del
> calendario del semáforo (`ResumenParaHoy.programaTerminado`). (3) El orden de arriba aplica **aunque la ventana
> todavía tenga color**: 1 a 3 mandan sobre el color; el color solo decide cuando no se cumple ninguna.

## R4 — `{habitoFlojo}` y `{habitoFuerte}`

- **Decision** *(corregido 2026-10-19, E-630: el finder se declara en `points.api` y lo implementa `habits`; `points` no puede importar `habits`)*: un finder nuevo, `CumplimientoPorHabitoFinder`, que devuelve por hábito los días programados
  y los cumplidos en el rango de la ventana vigente.
  - **Flojo**: el de menor tasa, con al menos 2 días programados.
  - **Fuerte**: el de mayor tasa, con al menos 2 días programados y tasa ≥ 50 %.
  - Si los dos salen iguales, o empatan, se omite el dato (no se adivina).
  - Se usa el nombre que ve la persona (renombrado si lo tiene).
- **Rationale**: hoy nada expone el cumplimiento por hábito (solo agregados por día). Los datos están en
  `registros_habito`, y por la constitución I la consulta vive en `habits`.
- **Alternatives**: calcularlo en `points` con SQL directo, descartado porque viola los límites de módulo.

## R5 — `{faltan}`

- **Decision**: se calcula en `HabitosQueFaltan` (dominio):
  1. Se toma la ventana que se cerrará mañana: los últimos 6 días cerrados más hoy.
  2. Con `S` (la suma de los porcentajes de los días MEDIDOS de esos 6) y `n` (cuántos son), el porcentaje que hoy
     necesita es `p = T·(n+1) − S`, con T = 60 para pasar de rojo a amarillo y 80 para pasar de amarillo a verde,
     respetando el redondeo HALF_UP a 1 decimal de `ReglaDelSemaforo`.
  3. Con `P` y `C` de hoy (`SemaforoDelDiaFinder`), `k = ceil(p·P/100) − C`.
     > **Corregido 2026-10-19 (SC-004, D-277).** `P` y `C` ya no salen de `SemaforoDelDiaFinder` (4 consultas por
     > lectura): salen de lo que `/home` ya leyó para `habitosHoy` y `rocasHoy` (`CumplimientoDeHoy`), y si hoy se
     > mide lo dice `ResumenParaHoy.seMideHoy`. Es el mismo conteo; `HomeMensajeDelSemaforoIT` lo compara con el
     > semáforo en vivo.
  4. Se usa solo si `0 < k ≤ P − C`. Si no, el dato no existe y se eligen mensajes sin `{faltan}`.
- **Rationale**: es el «cuánto falta» real según la regla vigente (D-168); se calcula en cada lectura, así baja al
  cumplir.
> **Nota de implementación 2026-10-19 (D-277).** `HabitosQueFaltan` no despeja `p = T·(n+1) − S` con los umbrales 60
> y 80 a mano: prueba cada cumplimiento posible de hoy (de lo ya cumplido a lo programado) con `ReglaDelSemaforo`
> —`porcentajeDelDia`, `promedio`, `colorDe`— sobre la ventana de mañana y devuelve el primero que sube de color. Da el
> mismo `k` mínimo sin repetir ni los umbrales ni el redondeo. Si lo cumplido hoy ya alcanza, no hay dato (`k ≤ 0`).

- **Confirmado por el dueño (2026-10-19)**: N cuenta hábitos + objetivos, como el semáforo (D-168, vigente desde el
  2026-09-25). Ningún texto del catálogo dice «hábitos» junto a `{faltan}`.

## R6 — Elección al azar y no repetición

- **Decision**:
  - Los candidatos son los mensajes activos del grupo cuyas variables tiene la persona.
  - Se quitan los elegidos en los últimos 14 días; si no queda ninguno, se quita solo el de ayer.
  - Se elige uniforme con un `AzarPort` inyectable, determinista en las pruebas.
  - Se guarda en `mensajes_semaforo_elegidos (participante_id, fecha, canal, grupo)` *(corregido 2026-10-19, D-281: el
    canal `HOY` | `CHAT` entró en la clave)*. Si ya hay fila para la persona, la fecha, el canal y el grupo de hoy, se
    reutiliza.
- **Concurrency**: `INSERT … ON CONFLICT DO NOTHING RETURNING` (se relee solo si se perdió el cruce): dos lecturas simultáneas de `/home` terminan con el
  mismo mensaje.

## R7 — Nombre y fase

- `/home` no trae el nombre: se lee de `users` (finder que da `fullName`) y se usa el primer nombre.
- La fase usa los nombres que la app ya muestra («El Ciclo Alquímico», «El Maestro Interno»…). Se agrega un
  `nombreVisible()` en `users/api/FasePrograma`, para no depender de `phasecontracts`.

## R8 — Permiso del podio

- **Decision**:
  - `Permission.EXPORT_RANKING`, concedido a MENTOR_LEAD, ADMIN, ALCHEMIST e IT_SUPPORT.
  - `GET /api/v1/mentor/context` publica `capabilities.canExportRanking`.
  - No hay endpoint de exportación: la imagen sale de `GET /api/v1/ranking`, que todos ya pueden leer.
- **Rationale**: la app muestra según capacidades del servidor (patrón `canAdminister`), no deduce del rol. No hay
  datos nuevos que proteger: el ranking ya es visible para todos.
- **Alternatives**:
  - Generar la imagen en el servidor: descartado (más infraestructura, sin ganancia).
  - Deducir del rol en la app: descartado por la constitución I.

## R9 — Imagen 9:16 y compartir

- **Decision**:
  - La imagen sale de un componente fuera de pantalla de 1080×1920, capturado con `captureRef` (`react-native-view-shot`,
    ya instalado y usado en `SignatureCanvas`) en formato PNG.
  - Se comparte con `Sharing.shareAsync` (expo-sharing, ya instalado).
  - En la web, `captureRef` (html2canvas) da un data URI y se descarga con un `<a download>`.
  - Lleva el logo/fénix de `assets/imagenes/fenix-renaser.png`.
  - El podio es plano (sin el 3D animado): el 3D depende de transforms y animaciones que no se capturan bien.
- **Alternatives**: capturar el podio en pantalla, descartado (animado, con el tamaño del teléfono, no 9:16).

## R10 — Stickers en la app

- **Decision**:
  - Los 15 WebP de 512 px van en `assets/stickers/semaforo/`, con un mapa `nombre → require` que es el único lugar que
    los nombra.
  - El servidor manda el nombre, por ejemplo `dorado-orgulloso`.
  - Un nombre desconocido muestra solo el texto.
  - Una prueba verifica que el catálogo (`data/mensajes-semaforo.json`) solo use nombres del mapa.

## R11 — La tarjeta diaria del chat con el mismo catálogo (D-281, 2026-10-19)

- **Decision**: el chat pide su mensaje a `points` por una API pública (`MensajeDelChatFinder`), con un segundo canal
  (`CHAT`) en el mismo historial. El texto es «Hoy llevas N % de tus hábitos.», una línea en blanco y el mensaje.
- **Rationale**:
  - La elección y la redacción son reglas de `points` (constitución I); `chat` solo consume una API y no lee tablas ajenas.
  - Una sola historia de lo visto (misma tabla, columna `canal`) permite las dos reglas nuevas con una lectura:
    el del chat distinto del de Hoy ese día, y 14 días sin repetir entre los dos canales.
  - Mantener el mismo texto en el mismo mensaje (mismo id calculado) deja intacta la idempotencia de D-223.
- **Sin `{faltan}`**: a las 23:50 el día ya casi cerró; `CanalDelMensaje.CHAT` lo excluye en el dominio y el recolector no
  lo calcula.
- **Grupo = color del DÍA de la tarjeta**, no el de la ventana de 7 días de Hoy: el chat habla del día que termina.
- **El mensaje es un adorno**: se pide recién cuando el texto va a salir y, si falla, sale el texto de siempre.
- **Alternatives**:
  - Una tabla aparte para el chat: descartada, duplica la historia y obligaría a dos lecturas para los 14 días.
  - Una migración V111: descartada porque V110 aún no está en master (se editó V110, con la advertencia para las bases
    locales que ya la aplicaron).
  - Mandar el mensaje como un tercer mensaje del programa: descartada, el dueño pidió «porcentaje + mensaje» en el mismo
    texto, y un mensaje más sería un push más cada noche.

