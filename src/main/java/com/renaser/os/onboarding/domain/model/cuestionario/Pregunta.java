package com.renaser.os.onboarding.domain.model.cuestionario;

import java.time.Instant;
import java.util.Objects;

/**
 * Catalogo, solo lectura desde este modulo en este alcance (ver {@link Seccion}).
 *
 * <p>{@code configEscala}/{@code reglasValidacion} son JSON crudo, tratados como dato
 * OPACO (decision de este modulo, CLAUDE.MD): el DSL de validacion/condicionales lo
 * evalua el motor de formularios del cliente, este backend solo lo transporta. Por eso
 * NO hay un interprete de {@code reglasValidacion} en Java — {@link #esCondicional()} solo
 * expone el HECHO de que la pregunta depende de otra, no la condicion en si.
 */
public record Pregunta(int id, short seccionId, String clavePregunta, String texto, TipoPreguntaOnboarding tipo,
                        String configEscala, boolean requerida, short orden, String reglasValidacion,
                        Integer preguntaPadreId, Instant creadoEn) {

    public Pregunta {
        Objects.requireNonNull(clavePregunta, "clavePregunta es obligatoria");
        Objects.requireNonNull(texto, "texto es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
    }

    /** true si esta pregunta solo aplica bajo alguna condicion sobre la respuesta de otra. */
    public boolean esCondicional() {
        return preguntaPadreId != null;
    }
}
