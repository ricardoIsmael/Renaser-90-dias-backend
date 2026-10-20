# Contrato: mensaje personalizado en la tarjeta diaria del chat (D-281)

La tarjeta diaria del semáforo en el chat de soporte (D-223) manda a las 23:50 locales de cada aprendiz dos mensajes del
programa: la imagen del color y el texto. Este contrato fija cómo cambia **solo el texto**. No hay endpoint nuevo ni cambio
en la app: el chat ya muestra cualquier texto del programa.

## Texto

```text
Hoy llevas 88 % de tus hábitos.

Ana, tu constancia está dando frutos. Mantén el ritmo y demuestra que puedes sostenerlo.
```

- Línea 1: la de siempre, con el entero del día que dio el semáforo.
- Una línea en blanco (`\n\n`).
- El mensaje, ya escrito: sin llaves, en tuteo, hasta 300 caracteres.
- **Sin mensaje** (catálogo sin candidatos, error al elegir): solo la línea 1, como antes. Se registra un `warn`.
- **Sin sticker**: el chat no manda imagen de sticker. La imagen del color es la de siempre.
- El **id del texto no cambia** (`UUID.nameUUIDFromBytes("semaforo-diario|<aprendiz>|<fecha>|texto")`): la idempotencia sigue
  siendo aprendiz + fecha. El reintento de las 23:55 no manda nada si el texto ya salió.
- El cuerpo del push (`RedaccionDelMensajeDeChat`) colapsa los saltos de línea y corta a 140 caracteres.

## Reglas del mensaje del chat

| Regla | Detalle |
|---|---|
| Grupo | El color del **día de la tarjeta** (`VERDE`, `AMARILLO` o `ROJO`), no el de la ventana de 7 días de Hoy. |
| Variables | `{nombre}`, `{dia}`, `{fase}`, `{habitoFlojo}`, `{habitoFuerte}`. **`{faltan}` no**: un mensaje que lo pide no es candidato. |
| Hábito flojo / fuerte | La misma regla y la misma ventana vigente que en Hoy (FR-006). |
| Distinto de Hoy | Nunca el mismo que la persona vio en Hoy ese día (de cualquier grupo). Si lo único posible es ese, no hay mensaje. |
| No repetir | 14 días, **sumando los dos canales** (lo de Hoy cuenta para el chat y al revés); agotado el grupo, se quita solo el de ayer. |
| Fecha | La fecha local de la tarjeta (zona de la persona), nunca la del servidor. |
| Guardado | `mensajes_semaforo_elegidos (participante_id, fecha, canal = 'CHAT', grupo)`; `INSERT … ON CONFLICT DO NOTHING`. |

## API entre módulos

```java
// points.api
public interface MensajeDelChatFinder {
    Optional<String> delDia(UserId aprendiz, LocalDate fecha, ColorSemaforo color);
}
```

- La implementa `points.application.services.MensajeDelChatService`; la consume `chat.application.services.TarjetaEnSoporte`.
- Vacío si el color es `SIN_DATOS`, si la persona no tiene participación o semáforo, o si no queda ningún mensaje posible.
- No autoriza: `chat` decide a quién le corresponde la tarjeta (aprendiz activo con el día medido y con soporte).
