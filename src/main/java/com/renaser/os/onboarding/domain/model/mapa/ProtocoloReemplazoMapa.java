package com.renaser.os.onboarding.domain.model.mapa;

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
 * Un protocolo de reemplazo (V07): "Cuando [disparador], en lugar de [conducta actual],
 * hare [respuesta alternativa]."
 *
 * <p>El manual pide que la respuesta alternativa sea una accion de 2 a 30 minutos ejecutable en el
 * acto. Eso no se puede verificar leyendo un texto, asi que <b>no se finge validarlo</b>: se
 * valida lo que si se puede — que los cuatro campos esten y no vengan vacios.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class ProtocoloReemplazoMapa {

    private static final int MAX_CARACTERES = 300;

    private final UUID id;
    private final UserId usuarioId;
    private final String protocoloId;
    private final String patron;
    private final String disparador;
    private final String conductaActual;
    private final String respuestaAlternativa;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    public static ProtocoloReemplazoMapa crear(UUID id, UserId usuarioId, String protocoloId, String patron,
                                                String disparador, String conductaActual,
                                                String respuestaAlternativa, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Instant ahora = clock.now();
        return new ProtocoloReemplazoMapa(id, usuarioId, requerido(protocoloId, "protocoloId"),
                requerido(patron, "patron"), requerido(disparador, "disparador"),
                requerido(conductaActual, "conductaActual"),
                requerido(respuestaAlternativa, "respuestaAlternativa"), ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static ProtocoloReemplazoMapa rehydrate(UUID id, UserId usuarioId, String protocoloId, String patron,
                                                    String disparador, String conductaActual,
                                                    String respuestaAlternativa, Instant creadoEn,
                                                    Instant actualizadoEn) {
        return new ProtocoloReemplazoMapa(id, usuarioId, protocoloId, patron, disparador, conductaActual,
                respuestaAlternativa, creadoEn, actualizadoEn);
    }

    /** La frase del manual, armada del mismo modo en que la arma el cliente. */
    public String frase() {
        return "Cuando " + disparador + ", en lugar de " + conductaActual + ", hare " + respuestaAlternativa + ".";
    }

    private static String requerido(String valor, String campo) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        if (limpio.length() > MAX_CARACTERES) {
            throw new IllegalArgumentException(campo + " no puede pasar de " + MAX_CARACTERES + " caracteres");
        }
        return limpio;
    }
}
