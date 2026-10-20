# Quickstart: validar el mensaje del semáforo y la descarga del podio

> **Solo en local.** Base clonada, backend local y build debug; nunca producción. Antes de levantar nada, avisar a las
> otras sesiones (máquina compartida) y esperar a que esté libre.

## Prerrequisitos

- Ramas `006-mensaje-semaforo-y-podio` en los dos repos, con V110 aplicada sobre una base clonada propia (por ejemplo
  `renaser_semaforo`).
- Backend local en un puerto libre y acordado. La app en web sirve en el puerto 8081 (CORS).
- Cuentas de prueba:
  - una por color (VERDE, AMARILLO, ROJO);
  - una para cada caso sin color: inicio, en pausa, sin programa, graduada y suspendida;
  - Admin, Alquimista, Líder, Soporte TI, Mentor y Aprendiz.

## Backend

1. `./mvnw clean verify` en verde. Leer las líneas `Tests run:` de surefire y failsafe.
2. `GET /api/v1/home` con la cuenta AMARILLA:
   - `semaforo.mensaje` trae `texto` sin `{`, y un `sticker` de la lista;
   - repetido, devuelve el mismo `id`.
3. Mover el reloj de prueba al día siguiente y volver a pedir `/home`: el `id` cambia y no está entre los de los últimos
   14 días.
4. Cuenta en pausa: `mensaje.id` empieza con `s` y es del caso PAUSA. Igual para los otros casos.
5. `GET /api/v1/mentor/context`: `canExportRanking` es `true` para Admin, Alquimista, Líder y Soporte TI, y `false` para
   Mentor, Guía y Aprendiz.

## App

1. Prueba con `tsc --noEmit` y Jest en verde. Jest completo, solo con la máquina libre.
2. **Emulador, cuenta AMARILLA**:
   - Hoy muestra el sticker y el mensaje bajo «Requiere atención»;
   - el lector de pantalla lee el mensaje;
   - en claro y en oscuro.
3. **Con backend anterior** (sin `mensaje`): la tarjeta se ve como hoy.
4. **Admin → Comunidad → Ranking → «Descargar»**:
   - se abre la hoja de compartir con una imagen 9:16;
   - la imagen tiene logo, fecha, tabla, top 3 y del 4.º al 10.º, con nombres completos;
   - cancelar la hoja vuelve sin error.
5. **Mentor y Aprendiz**: no ven «Descargar».
6. **Web**: «Descargar» baja un PNG.
7. **Maestro + mobile-mcp**: la suite entera, más los flujos nuevos.
