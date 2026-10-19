package com.renaser.os.leadership.domain.model.observacion;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el Líder de Mentores le dijo (o anotó) sobre un mentor (SDD 002, RL-15/RL-16; V89).
 *
 * <p><b>Append-only</b>, como {@code ajustes_dia_programa}: no tiene un solo método que la cambie. Una
 * observación equivocada se corrige registrando otra, y las dos quedan a la vista.
 *
 * <p>Que el destinatario sea un MENTOR depende de otra tabla, y lo exige el caso de uso (RL-18).
 *
 * <p>Desde la spec 005 puede tratar de una persona y de la semana que el líder miraba al escribirla («Avisar a su
 * mentor» desde la ficha): {@link #personaId} y {@link #semanaDesde}. Son nulos en las de antes y en las que no tratan
 * de nadie en particular. Que esa persona sea de un grupo de ESE mentor depende de otra tabla, y lo exige el caso de
 * uso.
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
    /** La persona de la que trata (spec 005); null si no trata de nadie en particular. */
    private final UserId personaId;
    /** El primer día de la semana que el líder miraba (spec 005); null si no viene de una ficha. */
    private final LocalDate semanaDesde;

    /** El id entra por el puerto {@code IdGenerator}: el dominio no genera identidad. */
    public static ObservacionDeMentor registrar(UUID id, Contenido contenido, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        return new ObservacionDeMentor(id, contenido.mentorId(), contenido.autorId(), contenido.tipo(),
                textoValido(contenido.texto()), contenido.envio().enviada(), contenido.envio().mensajeId(),
                claveValida(contenido.claveOperacion()), clock.now(), personaDe(contenido), semanaDe(contenido));
    }

    /** Solo para el adaptador de persistencia. */
    public static ObservacionDeMentor rehydrate(UUID id, Contenido contenido, Instant creadoEn) {
        return new ObservacionDeMentor(id, contenido.mentorId(), contenido.autorId(), contenido.tipo(),
                contenido.texto(), contenido.envio().enviada(), contenido.envio().mensajeId(),
                contenido.claveOperacion(), creadoEn, personaDe(contenido), semanaDe(contenido));
    }

    public Contenido contenido() {
        return new Contenido(mentorId, autorId, tipo, texto, new Envio(enviadaPorChat, mensajeId), claveOperacion,
                personaId == null ? null : new SobrePersona(personaId, semanaDesde));
    }

    private static UserId personaDe(Contenido contenido) {
        return contenido.sobre() == null ? null : contenido.sobre().personaId();
    }

    private static LocalDate semanaDe(Contenido contenido) {
        return contenido.sobre() == null ? null : contenido.sobre().semanaDesde();
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

    /**
     * @param sobre de qué persona y de qué semana trata (spec 005); null si no trata de nadie en particular
     */
    public record Contenido(UserId mentorId, UserId autorId, TipoObservacion tipo, String texto, Envio envio,
                            String claveOperacion, SobrePersona sobre) {

        /** Sin persona: la forma de las observaciones de antes de la spec 005. */
        public Contenido(UserId mentorId, UserId autorId, TipoObservacion tipo, String texto, Envio envio,
                         String claveOperacion) {
            this(mentorId, autorId, tipo, texto, envio, claveOperacion, null);
        }

        public Contenido {
            Objects.requireNonNull(mentorId, "mentorId es obligatorio");
            Objects.requireNonNull(autorId, "autorId es obligatorio");
            Objects.requireNonNull(tipo, "El tipo de observacion es obligatorio");
            Objects.requireNonNull(envio, "envio es obligatorio");
            if (mentorId.equals(autorId)) {
                throw new IllegalArgumentException("Una observacion es sobre otro mentor, no sobre uno mismo");
            }
            if (sobre != null && sobre.personaId().equals(mentorId)) {
                throw new IllegalArgumentException("La persona de una observacion es de su grupo, no el propio mentor");
            }
        }
    }

    /** La persona de la que trata una observación y, si viene de su ficha, el primer día de la semana que se miraba. */
    public record SobrePersona(UserId personaId, LocalDate semanaDesde) {

        public SobrePersona {
            Objects.requireNonNull(personaId, "personaId es obligatorio");
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
