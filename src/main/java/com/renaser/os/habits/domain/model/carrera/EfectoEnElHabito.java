package com.renaser.os.habits.domain.model.carrera;

import com.renaser.os.habits.domain.model.medicion.OrigenMedicion;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que la carrera hizo con el hábito, tal como se respondió la primera vez (spec 042, FR-019): se guarda con la carrera para que
 * una respuesta repetida sea la misma, sin recalcular.
 *
 * @param registroId   el registro del día al que se aplicó o que se miró; {@code null} si no había
 * @param kmDelDia     los km del registro después de aplicar; {@code null} si no hay registro cumplido
 * @param origenDelDia de qué fuente salió ese número; {@code null} si no hay registro cumplido
 * @param puntos       lo que pagó ESTA carrera: solo {@link ResultadoEnElHabito#CERRADO} paga
 */
public record EfectoEnElHabito(ResultadoEnElHabito resultado, UUID registroId, BigDecimal kmDelDia, OrigenMedicion origenDelDia,
                               int puntos) {

    public EfectoEnElHabito {
        Objects.requireNonNull(resultado, "resultado");
        if (puntos < 0 || (puntos > 0 && resultado != ResultadoEnElHabito.CERRADO)) {
            throw new IllegalArgumentException("Solo la carrera que cierra el hábito paga puntos");
        }
    }

    /** Sin registro que mirar: no cuenta, día cerrado o sin hábito. */
    public static EfectoEnElHabito sinRegistro(ResultadoEnElHabito resultado) {
        return new EfectoEnElHabito(resultado, null, null, null, 0);
    }
}
