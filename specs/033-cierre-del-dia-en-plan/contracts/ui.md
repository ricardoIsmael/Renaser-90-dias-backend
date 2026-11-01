# Contrato de la pantalla: «Cierra tu día» en Plan

No hay contrato de API nuevo: son `GET/PUT /api/v1/rocks/day-closure` de la spec 017 (`specs/017-cierre-del-dia/contracts/api.md`).

## La tarjeta (Plan)

| Estado | Título | Segunda línea | Al tocar |
|---|---|---|---|
| Sin cerrar (hoy) | «Cierra tu día» | «2 minutos · opcional, no da puntos» | abre la hoja completa |
| Cerrado (hoy) | «Día cerrado» (verde) | «Mañana: «Revisar las facturas» a otra hora» o «Cerrado a las 21:40» | abre la hoja con lo guardado, para cambiarlo |
| Sin lectura / plan apagado / servidor anterior | — | — | no se dibuja |

- Debajo, la fila de la semana (L M X J V S D): los días que ya pasaron (desde el Día 1) se pueden tocar; hoy y los que vienen no. Un día tocado muestra «Jueves 29 · Cerrado a las 21:40» o «Jueves 29 · Sin cerrar» y, si hubo cierre, lo que se respondió por cada pendiente (solo lectura). Nunca un botón de cerrar.
- Medidas: filas y días de 48 o más; tipo oración; tuteo; un solo scroll; el botón de SER no la tapa.

## La hoja

La de la 017, con tres diferencias de presentación: cada acción pendiente lleva el ícono de su eje; el pie dice «Ver mañana en Plan» (todos los días, solo con Mapa activo), que cierra la hoja y lleva a «Acciones que mueven la meta»; y no recibe nada de Training.
