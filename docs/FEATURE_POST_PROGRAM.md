# Graduación y post-programa

Este documento existía sólo como referencia en el javadoc de `ParticipacionPrograma`
(`docs/FEATURE_POST_PROGRAM.md`) y **no estaba escrito**. Acá viven las reglas del final del
programa de 90 días: qué está confirmado, qué escribe el código, y qué sigue sin decidirse.

Reglas confirmadas por el dueño del proyecto el **2026-09-14**. Implementadas el **2026-09-15**
(D-125).

---

## 1. La graduación

| Regla | Valor confirmado |
|---|---|
| **Cuándo** | Al llegar al **día 90**. Nada más |
| **Condiciones** | **Ninguna.** No se miran hábitos cumplidos, ni puntos, ni evidencias, ni firmas de fase |
| `dia_post_programa` | Queda en **0** |
| Los hábitos | **Se le siguen generando** igual que el día 89 |

Las tres columnas de la graduación —`programa_completado`, `programa_completado_en`,
`dia_post_programa`— existían en la base y en el dominio desde el baseline **sin un solo
escritor**: la graduación no se marcaba nunca.

## 2. Quién la escribe

`ParticipacionPrograma.graduarSiLlegoAlDiaNoventa(...)`, un método privado del agregado, llamado
desde `sincronizarDiaDelPrograma(...)` — o sea, desde el **mismo barrido horario del reloj** que
ya materializa `dia_programa` (`AvanzarDiaProgramaScheduler`, cada hora). **No hay un segundo cron
ni un segundo barrido del padrón:** llegar al día 90 es un cambio de estado más del reloj, derivado
de las mismas fechas.

Se ve desde la app sin agregar nada: `GET /api/v1/users/me/profile` ya devolvía los tres campos
(`UserAccountService.aResumenTrainee` → `UserResponse.TraineeProfileResponse`). Hasta hoy devolvía
`false`/`null`/`0` para todo el mundo, siempre.

En el log del barrido, la graduación es la única línea en **INFO** (el resto es DEBUG): es el único
evento de negocio del cron y pasa una vez por participante en 90 días.

## 3. Por qué es derivada y no un evento

`.claude/rules/02-tiempo-zonas-y-schedulers.md` §2. La condición es el **día derivado de las
fechas** (`fecha_inicio`, hoy en la zona del participante, `dias_ajuste_programa`), acotado a
[0, 90] — no "el día que se cumplen 90".

Consecuencias, todas gratis:

- **Una noche con el backend caído no le cuesta la graduación a nadie.** El día 95 de calendario
  sigue derivando 90, y la primera corrida que ocurra gradúa.
- **Correr cada hora durante 90 días deja exactamente una escritura**: sólo escribe mientras
  `programa_completado` sea falso.
- **Las filas que hoy están en el día 90 con la bandera en falso se gradúan en la corrida
  siguiente**, aunque su día ya no cambie. Por eso la sincronización devuelve `true` en ese caso:
  si devolviera `false`, el barrido no guardaría la fila y se quedarían sin graduar para siempre.
- La graduación respeta `dias_ajuste_programa`: a quien se le devolvieron 6 días, su día 90 llega
  6 días más tarde. Es el mismo criterio que `fechaGraduacionEsperada()`.
- **El día es el del participante, no el del servidor.** El barrido calcula
  `clock.now().atZone(participacion.timezone())`. Graduar por `clock.today()` (UTC) graduaría a
  todo el padrón de Lima un día antes, entre las 00:00 y las 05:00 UTC — la misma familia de E-91.

## 4. No se des-gradúa

Un retroceso posterior (`fijarDia`, que puede dejar el día derivado por debajo de 90) **no borra
la graduación**: `programa_completado_en` es la fecha en que algo pasó, no un cálculo.

La regla confirmada dice cuándo se gradúa y **no contempla revertirlo**. Des-graduar sería inventar
una regla de negocio. Si el dueño decide que un retroceso a un graduado tiene que devolverlo a
"cursando", se cambia acá primero y recién después en el código.

## 5. Lo que sigue sin decidirse

- **Qué pasa el día 91.** `dia_post_programa` queda en 0 al graduarse y **nadie lo avanza**. No
  está definido si el post-programa cuenta días, ni qué cambia en la app cuando arranca. Cuando se
  defina: ese contador también se **deriva** de fechas, no se incrementa desde un cron (regla 02 §2)
  — si no, la primera noche caída lo desfasa para siempre.
- **Si la app tiene que mostrar algo distinto al graduado.** Hoy el dato viaja en el perfil y la
  app no lo usa: un graduado ve exactamente la misma pantalla del día 90.
- **Si la graduación avisa** (notificación, correo, certificado). Nada de eso existe.
