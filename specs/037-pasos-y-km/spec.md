# Feature Specification: Los pasos del teléfono llenan los kilómetros diarios

**Feature Branch**: `037-pasos-y-km` (backend y app)
**Created**: 2026-11-02
**Status**: Implementada en la rama (2026-11-02), sin publicar. Falta un build nativo de la app (`react-native-health-connect` es código nativo) y probarla en un emulador o teléfono con Android 14.
**Input**: Pedido del dueño (2026-11-02), textual: «Me gusta lo de kilómetros diarios, pero el problema viene acá: cómo hacemos que el celular cuente los pasos, tipo Pokémon GO. Procede.»
Maqueta aprobada: opción A, 14 pantallas en 4 filas (Conectar, El día a día, Cuando no hay pasos, Quién más lo ve). Investigación previa: `investigacion-pasos-km.md` (resumen en `research.md`).

## Clarifications

### Session 2026-11-02 (pedido, maqueta y cambios del dueño)

- **Cómo lo hace Pokémon GO.** No cuenta los pasos él mismo: lee lo que el sistema y Google registran en segundo plano. Hacemos lo mismo, **sin servicio propio, sin notificación fija y con la app cerrada**.
- **Opción A de la maqueta (aprobada).** Los km se llenan solos en Training y, al llegar a la meta, un toque «Registrar» cierra el hábito con el origen de la medición. La captura de siempre queda de respaldo. Cerrar el hábito sin tocar nada (B) y en segundo plano (C) quedan **para después**.
- **Cambio del dueño, 2026-11-02 («todos tienen Android 14»).** **Solo Health Connect.** En Android 14 o más el propio sistema cuenta los pasos del teléfono en cuanto una app tiene permiso de lectura de pasos, y los relojes (Samsung, Fitbit, Garmin, Xiaomi/Zepp) escriben en el mismo lugar. **No hay módulo propio, ni Recording API, ni `play-services-fitness`.**
  **Android menor a 14, un teléfono sin Health Connect, Huawei sin Google y el iPhone (web) se quedan con la captura de siempre, por decisión del dueño**, en la pantalla 3.4 de la maqueta («Sube tu captura»). No se ofrece instalar nada.

## Reglas generales del dueño para este lote

Respetar las reglas y no romper la lógica, reutilizar, no crear tablas innecesarias, normalizar siempre (o la desnormalización correcta), buenas prácticas y SOLID, en backend y app.
Aquí se traduce en: **ninguna tabla nueva** (todo vive en `registros_habito`, que ya tiene `valor_medido` y `origen_medicion` «para la fase 2», V84), una columna en `habitos` para la meta, ningún endpoint de lectura nuevo para el aprendiz (la agenda de hoy ya trae la medición), y el cierre sigue pasando por `RegistroService.completar` (puntos, ventana, evento y racha en un solo lugar).

## User Scenarios & Testing

### Historia 1 — Conectar mis pasos (Prioridad: P1)

Una persona con Android 14 abre Training y ve, bajo «Kilómetros diarios», «Cuenta tus pasos solo ›». La toca, lee en tres líneas qué se lee y quién lo ve, toca «Conectar» y el sistema le pide permiso para leer **Pasos** y **Distancia**. Al aceptar, la hoja le dice «Ya contamos tus pasos» y de dónde vienen. Desde ese momento no vuelve a pedir nada.

**Pruebas**: Jest (estado del soporte y del permiso, textos, que «Ahora no» no pida nada y que el permiso denegado cambie el estado); Maestro `.maestro/pasos-y-km/01` (escrito, sin correr).

**Escenarios**
1. **Dado** Android 14 con Health Connect y sin permiso, **cuando** toca «Conectar», **entonces** se abre la pantalla de permisos del sistema; con «Permitir», la hoja pasa a «Ya contamos tus pasos».
2. **Dado** que toca «Ahora no», **entonces** no se pide ningún permiso y la fila sigue como estaba (con «Subir»).
3. **Dado** que rechaza el permiso dos veces, **entonces** la fila dice «Perdimos tus pasos» con «Abrir Health Connect» (Android deja de mostrar el diálogo).

### Historia 2 — Los km se llenan solos y un toque cierra el hábito (Prioridad: P1)

Con los pasos conectados, la fila de «Kilómetros diarios» muestra una barra y «3,2 de 5 km» que se actualizan al abrir Training, al volver a la app y al deslizar para refrescar. Al llegar a la meta aparece **«Registrar»**; un toque registra los km, cierra el hábito, paga los puntos del servidor y dice «Registramos tus 5,4 km».

**Escenarios**
1. **Dado** 3,2 km de 5, **entonces** hay barra y «3,2 de 5 km», un círculo apagado, y **ningún** botón de registro.
2. **Dado** 5,4 km de 5, **entonces** «5,4 km · ¡Meta lista!» y un botón «Registrar»; al tocarlo la fila queda cumplida («Cumplido a las 17:12 · 5,4 km», «+8 pts» del servidor).
3. **Dado** que ya cumplió con la captura, **entonces** no se ofrece registrar otra vez (la fila queda cumplida con lo que se guardó).
4. **Dado** que la lectura se hizo a las 23:58 y se envía pasada la medianoche, **entonces** no se envía nada: se recarga y se avisa «Tu día cambió…» (regla del 17-oct, spec 002 de la app), y el servidor responde 409 si igual llegara.
5. **Dado** que toca «Registrar» dos veces seguidas o reintenta tras perder la respuesta, **entonces** se registra una vez y la segunda devuelve el registro ya cerrado, sin puntos de más.

### Historia 3 — Cuando no hay pasos (Prioridad: P1)

- **Sin permiso** (lo quitó en Health Connect): «Perdimos tus pasos» + «Quitaste el permiso en Health Connect.» con «Abrir Health Connect» y «Subir captura».
- **Con permiso pero sin pasos hoy**: «Todavía no vemos pasos hoy» con «Actualizar» y «Subir».
- **Dos fuentes a la vez** (teléfono y reloj): se cuenta **una sola vez**, la que la persona puso primero en Health Connect; la hoja lo dice («Galaxy Watch · La usamos» / «Tu teléfono · No se suma») y ofrece «Cambiar prioridad» (abre Health Connect).
- **Pasos escritos a mano** en Health Connect (`MANUAL_ENTRY`) u orígenes de método desconocido **no cuentan**.
- **Android menor a 14, sin Health Connect, iPhone (web):** pantalla 3.4: «Sube tu captura» y «Subir captura». Sin «Conectar» y sin «Instalar».

### Historia 4 — Quién más lo ve (Prioridad: P2)

- **Mentor / guía / líder / Administración** ven en los 7 días de la ficha «5,4 km · Medido por el reloj / por el teléfono / Captura de la persona», la fuente y la hora de la lectura, y **«Por revisar»** en lo dudoso (muy lejos o con una relación pasos/distancia imposible). Al abrir «Ver evidencia» de ese día ven una tarjeta de texto con pasos, distancia, fuente y hora, en lugar de una foto.
- **Ranking → Kilómetros** no cambia en cifras ni en reglas; a quien tiene km automáticos se le marca **«auto»** junto al total, y bajo el selector dice «Km de tus pasos o de tu captura.».

### Historia 5 — Administración fija la meta (Prioridad: P2)

En **Más opciones → Hábitos del programa → Kilómetros diarios**, Administración y Alquimista ven y cambian **«Meta diaria de km»** (por defecto **5**). Rige desde ya para los registros automáticos; la captura sigue aceptando cualquier número mayor que cero.

## Requirements

### Requisitos funcionales

- **FR-001** Health Connect es la **única** fuente automática. No hay servicio propio, Recording API, Google Fit, ni segundo plano.
- **FR-002** Está disponible solo con Android **14 o más** (API 34) **y** Health Connect presente y listo (`SDK_AVAILABLE`). En cualquier otro caso la app ofrece la captura de siempre y **no ofrece instalar nada**.
- **FR-003** La app pide **solo** `READ_STEPS` y `READ_DISTANCE`. No pide frecuencia cardiaca, sueño, ejercicio, ubicación ni lectura en segundo plano ni historial.
- **FR-004** La app lee **solo el día del registro** (`fechaEjecucion`), de las 00:00 al instante actual, y no guarda lecturas crudas.
- **FR-005** **Una fuente por día, sin doble conteo:** se usa el **agregado** de Health Connect (que ya elimina duplicados según la prioridad de fuentes que fijó la persona), restringido a los orígenes «limpios». Un origen es limpio si ninguno de sus registros del día es `MANUAL_ENTRY` ni de método desconocido.
- **FR-006** Km = distancia agregada si hay; si no, pasos × 0,75 m, marcado «aprox.». Se muestra con coma y un decimal («5,4 km»; «7 120 pasos»).
- **FR-007** Con la meta alcanzada la app ofrece «Registrar». Antes de enviar comprueba el id y el día del registro; si el día cambió, no envía nada, recarga y avisa.
- **FR-008** El servidor cierra el hábito con `POST /habit-tracks/{id}/auto-measurement` (ver `contracts/api.md`): valida **la meta** (la de Administración; 5 km si no hay), un **tope** de 60 km y 150 000 pasos por día, que la lectura no sea del futuro ni de otro día, que el hábito sea el de km, y que el día del registro no haya terminado (D-259, 409).
- **FR-009** **Idempotente:** si el registro ya está cumplido por Health Connect, devuelve ese registro sin pagar de nuevo. Si ya está cumplido con la captura (MANUAL), responde 409.
- **FR-010** Lo dudoso **se acepta y se marca «por revisar»**, no se rechaza: más de 30 km, o una relación de entre 0,3 y 1,6 m por paso que no se cumple. El mentor lo ve; el ranking lo cuenta igual (decisión del dueño por confirmar, ver Supuestos).
- **FR-011** Se guardan en `registros_habito`: km, origen `HEALTH_CONNECT`, pasos, fuente (etiqueta legible), tipo de dispositivo (`TELEFONO` / `RELOJ` / `OTRO`), hora de la lectura y si está por revisar. **No** se guardan frecuencia cardiaca, sueño, rutas, series por minuto, identificadores del dispositivo ni registros crudos.
- **FR-012** La meta vive en `habitos.meta_medicion` (nullable: sin dato, 5 km), la cambia solo ADMIN/ALQUIMISTA (`MANAGE_HABIT_CATALOG`) y llega a la app en la agenda de hoy (`medicion.meta`).
- **FR-013** El registro manual con captura **no cambia** (mayor que cero, hasta 100 km).
- **FR-014** El ranking de km no cambia de reglas; sí marca quién tiene km automáticos (`kilometrosAutomaticos`).
- **FR-015** Ninguna pantalla nueva sin las tres cosas de siempre: tipo oración, áreas de 48 y fuente por los roles de `theme/fuentes.ts`.

### Requisitos no funcionales

- **Privacidad:** la política (`/privacidad/`) dice que se leen pasos y distancia, para qué, que no se venden ni se usan para publicidad, quién los ve y cómo se desconecta. El permiso lo pide el sistema, la persona acepta con una hoja que dice que su mentor y el ranking ven el total.
- **Seguridad:** el servidor no confía en el teléfono: topes, banderas de revisión y el origen guardado. Cualquier app con permiso de escritura puede escribir pasos en Health Connect; por eso se descartan los manuales y se deja el origen a la vista del mentor.
- **Rendimiento:** una sola lectura de Health Connect por apertura/foco (con 30 s de enfriamiento), con paginación acotada (20 páginas).
- **Compatibilidad:** campos nuevos opcionales; la app publicada ignora todo lo nuevo; la nueva contra un backend anterior no ofrece «Conectar» (la agenda no trae `meta`).

## Supuestos para el dueño (a confirmar)

1. **La meta diaria es 5 km** y la cambia Administración. *Es un supuesto mío; con 2 o 3 km casi cualquiera la cumple solo caminando por la casa.*
2. **Solo el cierre automático exige la meta.** La captura sigue aceptando cualquier km mayor que cero (como pediste que no cambiara).
3. **Qué ve el mentor:** km, «Medido por el reloj / el teléfono / Captura de la persona», fuente, hora de lectura y «Por revisar». No ve nada más de salud.
4. **El ranking mezcla** km de GPS del reloj y km estimados de pasos (±10–20 %), y marca **«auto»**. La alternativa (ranking por días cumplidos) queda para después.
5. **Los km de pasos son «aprox.»**: el teléfono subcuenta en el día completo (12–30 % según los estudios) y estima mal los km corriendo.
6. **Lo dudoso entra, marcado.** Si prefieres que lo dudoso **no cuente** hasta que alguien lo revise, es un cambio chico.
7. **Android menor a 14 se queda con la captura** (decisión tuya del 2026-11-02). `minSdk` pasa de 24 a **26** porque lo exige la librería; deja fuera Android 7.x (API 24–25), que no cuenta contigo.
8. **Play Console:** hay que declarar «Activity and fitness» con pasos y distancia en el formulario «Health apps» y actualizar Data safety antes de publicar. Google no da plazo de revisión (planifica 1–2 semanas).
9. **Pasos de todo el día**, no solo caminatas: simple y funciona con solo el teléfono, pero cuenta trayectos normales.

## Fuera de alcance (para después)

B (se cierra solo al abrir la app), C (en segundo plano, pide `READ_HEALTH_DATA_IN_BACKGROUND`), iPhone (HealthKit), fila «Mis pasos» en Yo, Recording API para Android 10 a 13 (**descartada por el dueño**), historial de más de un día.
