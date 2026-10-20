# Quickstart: validar 009 en local (junto con la 008)

Se valida en local: base clonada `renaser_alarmas`, backend del worktree con V111 y V112, build debug instalado y Metro del worktree. **Sin APK.** Turno de máquina avisado.

| # | Pasos | Esperado |
|---|---|---|
| T1 | Editor de Gimnasio: ver «Cómo suena» | 4 filas con rótulo de 17 px, explicación de 15 px e interruptor dorado de 72 × 40; fila de 56 px o más (árbol de accesibilidad) |
| T2 | Apagar Sonido | Voz se apaga y queda deshabilitada con «La voz necesita el sonido» |
| T3 | Intentar apagar Sonido y Vibración | Mensaje en línea; no se guarda |
| T4 | S: Sonido no, Vibración sí. Guardar | «Guardar alarma · S». El sábado la alarma sale por el canal «solo vibrar» (`dumpsys notification` / canal); L–V no cambian |
| T5 | Marcar V y S con combinaciones distintas | Interruptores con «Varía» |
| T6 | Repetir encendido, hábito a +3 min, no tocar | Suena a la hora, a +5 y a +10 |
| T7 | Repetir encendido, tocar la alarma | No suenan las repeticiones de hoy (`dumpsys alarm` sin `-rep-{hoy}`); las de mañana siguen |
| T8 | Repetir encendido, completar el hábito antes de la hora | No suenan las repeticiones de hoy |
| T9 | Otra sesión o teléfono, misma cuenta | El editor muestra la misma combinación |
| T10 | `force-stop` y abrir | Vuelven alarmas y repeticiones (007) |
| T11 | Día con «Sin aviso» | «Cómo suena» atenuada con «Este día no tiene aviso» |
| T12 | Modo oscuro | Contraste correcto del interruptor y los textos |
| B1 | Base: `PUT …/alarm {sound:false, vibration:false}` | 400 |

**Maestro**: solo `08-planificar-despertar` y los flujos del editor, como en la 008.
