# Modelo de datos: Cierra tu día

Sin tablas nuevas.

## `rocas_diarias` (V121: cuatro columnas nulas)

| Columna | Tipo | Significado |
|---|---|---|
| `cierre_motivo` | `text` | «¿Qué pasó?»: `SIN_TIEMPO`, `SURGIO_OTRA_COSA`, `SIN_ENERGIA`, `PARA_DESPUES` (`CHECK`) |
| `cierre_manana` | `text` | «¿Qué harás mañana?»: `OTRA_HORA`, `MAS_CORTA`, `PEDIR_AYUDA`, `DEJARLA` (`CHECK`) |
| `cierre_nota` | `text` | Lo que la persona quiso contar; 1 a 500 caracteres, solo si hay motivo (`CHECK`) |
| `cierre_en` | `timestamptz` | Cuándo se guardó esta respuesta (la última) |

Invariante de fila (`CHECK`): `cierre_motivo`, `cierre_manana` y `cierre_en` vienen **juntos o ninguno**. Que la acción siga pendiente
al guardar lo impone el servicio (depende de `completada` en el momento de guardar, y una acción puede cumplirse después: no es un
`CHECK`, la lectura la oculta).

Toda fila existente queda con las cuatro columnas en `NULL`. El plan de un día se reemplaza solo en fechas futuras (E-209), así que
un cierre (solo de hoy) no se pierde con eso.

## `entradas_diario` (sin cambios de esquema)

Una fila `tipo = 'EVALUACION_DIA'` por (persona, fecha) — `UNIQUE(participante_id, fecha, tipo)` ya existe desde V1:
- existencia de la fila = «día cerrado»;
- `contenido_texto` = la nota del día (≤ 500 caracteres; `NULL` si no hay);
- `creado_en` = la hora del primer cierre; `actualizado_en` = el último cambio.
`EntradaDiarioFinder.entradasEntre` (Espejo Sombra) **excluye** este tipo.

## Dominio (`rocks.domain.model.cierre` y `rocadiaria.CierreDelDia`)

- `MotivoDeAccionPendiente`, `PlanDeManana`: enums (el nombre es el valor guardado).
- `RespuestaDeCierre(motivo, manana, nota)`: valida (los dos obligatorios; nota recortada, ≤ 500 caracteres de persona —un emoji es uno—, en blanco = `null`).
- `CierreDeAccion(respuesta, cerradoEn)`: lo que guarda `RocaDiaria`.
- `NotaDeCierre`: la regla de la nota (recortar, en blanco = `null`, 500 caracteres de persona), una para la de una acción y la del día.
- `SemanaDeCierres`: qué lunes y qué días se leen en una ficha (desde el Día 1 hasta hoy; nunca el futuro).
- `CierreDelDia.cerrar(rocasDelDia, respuestasPorAccion, ahora)`: exige una respuesta por pendiente, rechaza ids ajenos o repetidos, aplica
  `cerrarConRespuesta` a las pendientes y `quitarCierre` a las cumplidas; devuelve las rocas cuyo cierre cambió.
- `EstadoDelCierre`: `ABIERTO | CERRADO | SIN_CERRAR` a partir de (fecha, hoy, ¿hay marca?).
- `CierreDeOtroDiaException` (409): se quiso cerrar un día que ya pasó (D-259). `CierreDelDia` vive junto a `RocaDiaria` para que `cierre` sea
  un paquete hoja (`RocaDiaria` lo importa, no al revés).
