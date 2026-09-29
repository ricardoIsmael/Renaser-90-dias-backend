package com.renaser.os.chat.domain.model.semaforo;

import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.UserId;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * La tarjeta del semáforo de UN aprendiz para UN día (D-223): la imagen del color y la línea «Hoy llevas
 * 85 % de tus hábitos.», como dos mensajes del programa en su chat de soporte.
 *
 * <p><b>Dos mensajes y no una foto con texto</b>, por lo mismo que la bienvenida (D-174): el APK publicado
 * muestra el texto de una foto solo cuando la foto no carga.
 *
 * <p><b>Ids que se calculan, no que se sortean.</b> Cada mensaje tiene un id fijo derivado de aprendiz +
 * fecha ({@link UUID#nameUUIDFromBytes}), y se inserta ignorando el conflicto con la PK de
 * {@code mensajes}. Correr el barrido dos veces, tarde, o en dos instancias a la vez no duplica nada, y no
 * hace falta ninguna tabla de marcas: la marca es el mensaje mismo.
 *
 * @param porcentaje el entero del día que dio el semáforo (0 a 100); es el mismo que eligió el color
 */
public record TarjetaDelSemaforo(UserId aprendiz, LocalDate fecha, ColorDeTarjeta color, int porcentaje) {

    public TarjetaDelSemaforo {
        Objects.requireNonNull(aprendiz, "aprendiz es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(color, "color es obligatorio");
        if (porcentaje < 0 || porcentaje > 100) {
            throw new IllegalArgumentException("porcentaje fuera de rango: " + porcentaje);
        }
    }

    /** El texto que pidió el dueño, con el entero del día. */
    public String texto() {
        return "Hoy llevas " + porcentaje + " % de tus hábitos.";
    }

    public MensajeId idDeLaImagen() {
        return idDe("imagen");
    }

    public MensajeId idDelTexto() {
        return idDe("texto");
    }

    private MensajeId idDe(String pieza) {
        String clave = "semaforo-diario|" + aprendiz.value() + "|" + fecha + "|" + pieza;
        return MensajeId.of(UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)));
    }
}
