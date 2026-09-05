package com.renaser.os.habits.domain.model.aviso;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Un aviso que corresponde mandar AHORA para un habito concreto.
 *
 * @param tipo    cual de los dos avisos es
 * @param momento el instante al que se refiere (la hora de disparo, o el plazo de entrega)
 * @param falta   cuanto falta para {@code momento} desde el instante en que se calculo — es lo
 *                que el texto del aviso le dice al aprendiz ("empieza en 15 minutos"). Se
 *                calcula aca y no en quien arma el texto para que ese numero no dependa de
 *                cuando se formatee: entre el calculo y el envio puede pasar tiempo.
 */
public record AvisoHabito(TipoAvisoHabito tipo, Instant momento, Duration falta) {

    public AvisoHabito {
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        Objects.requireNonNull(momento, "momento es obligatorio");
        Objects.requireNonNull(falta, "falta es obligatoria");
    }

    /** Redondeado hacia arriba: faltando 30 segundos se dice "1 minuto", nunca "0 minutos". */
    public long minutosQueFaltan() {
        long segundos = Math.max(falta.getSeconds(), 0);
        return (segundos + 59) / 60;
    }
}
