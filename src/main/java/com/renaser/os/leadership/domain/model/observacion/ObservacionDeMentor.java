package com.renaser.os.leadership.domain.model.observacion;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el Líder de Mentores le dijo (o anotó) sobre un mentor (SDD 002, RL-15/RL-16; V89).
 *
 * <p><b>Append-only</b>, como {@code ajustes_dia_programa}: no tiene un solo método que la cambie. Una
 * observación equivocada se corrige registrando otra, y las dos quedan a la vista.
 *
 * <p>Que el destinatario sea un MENTOR depende de otra tabla, y lo exige el caso de uso (RL-18).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class ObservacionDeMentor {

    public static final int LARGO_MAXIMO = 1000;
    private static final int LARGO_MAXIMO_CLAVE = 100;

    private final UUID id;
    private final UserId mentorId;
    private final UserId autorId;
    private final TipoObservacion tipo;
    private final String texto;
    /** Si el líder además se la mandó por el chat directo (PL-05). */
    private final boolean enviadaPorChat;
    /** El mensaje del chat en el que se la mandó; null si no se mandó o si el envío falló. */
    private final UUID mensajeId;
    /** El mismo envío repetido (doble toque, reintento) no crea dos observaciones. */
    private final String claveOperacion;
    private final Instant creadoEn;

    /** El id entra por el puerto {@code IdGenerator}: el dominio no genera identidad. */
    public static ObservacionDeMentor registrar(UUID id, Contenido contenido, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        return new ObservacionDeMentor(id, contenido.mentorId(), contenido.autorId(), contenido.tipo(),
                textoValido(contenido.texto()), contenido.envio().enviada(), contenido.envio().mensajeId(),
                claveValida(contenido.claveOperacion()), clock.now());
    }

    /** Solo para el adaptador de persistencia. */
    public static ObservacionDeMentor rehydrate(UUID id, Contenido contenido, Instant creadoEn) {
        return new ObservacionDeMentor(id, contenido.mentorId(), contenido.autorId(), contenido.tipo(),
                contenido.texto(), contenido.envio().enviada(), contenido.envio().mensajeId(),
                contenido.claveOperacion(), creadoEn);
    }

    public Contenido contenido() {
        return new Contenido(mentorId, autorId, tipo, texto, new Envio(enviadaPorChat, mensajeId), claveOperacion);
    }

    private static String textoValido(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Escribe la observacion: no puede quedar vacia");
        }
        String limpio = texto.trim();
        if (limpio.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La observacion no puede pasar de " + LARGO_MAXIMO + " caracteres");
        }
        return limpio;
    }

    private static String claveValida(String clave) {
        if (clave == null || clave.isBlank() || clave.trim().length() > LARGO_MAXIMO_CLAVE) {
            throw new IllegalArgumentException("La clave de operacion es obligatoria y de hasta "
                    + LARGO_MAXIMO_CLAVE + " caracteres");
        }
        return clave.trim();
    }

    public record Contenido(UserId mentorId, UserId autorId, TipoObservacion tipo, String texto, Envio envio,
                            String claveOperacion) {

        public Contenido {
            Objects.requireNonNull(mentorId, "mentorId es obligatorio");
            Objects.requireNonNull(autorId, "autorId es obligatorio");
            Objects.requireNonNull(tipo, "El tipo de observacion es obligatorio");
            Objects.requireNonNull(envio, "envio es obligatorio");
            if (mentorId.equals(autorId)) {
                throw new IllegalArgumentException("Una observacion es sobre otro mentor, no sobre uno mismo");
            }
        }
    }

    /** Un mensaje sin envío no tiene sentido: la base lo repite con un CHECK (V89). */
    public record Envio(boolean enviada, UUID mensajeId) {

        public static final Envio SIN_ENVIAR = new Envio(false, null);

        public Envio {
            if (mensajeId != null && !enviada) {
                throw new IllegalArgumentException("Un mensaje del chat solo acompaña a una observacion enviada");
            }
        }
    }
}
