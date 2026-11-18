package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.shared.domain.RechazoConCodigoException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Los días que el líder pide de una persona para pintar su semana y su mes (spec 056, FR-017 y FR-018): hasta 62 de una vez, y
 * siempre recortados a [Día 1, hoy] en la fecha local de ella. Es la misma regla con la que el mentor lee los días de su aprendiz
 * (spec 055): lo anterior al Día 1 y lo que está por venir no se devuelve.
 */
public record RangoDeDias(LocalDate desde, LocalDate hasta) {

    /** El rango más largo que se pide de una vez: un mes visible con sus días de relleno. */
    public static final int DIAS_MAXIMOS = 62;
    public static final String RANGO_INVALIDO = "RANGO_INVALIDO";

    public RangoDeDias {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
    }

    /**
     * @throws RechazoConCodigoException {@code RANGO_INVALIDO} si va al revés o pasa de {@value #DIAS_MAXIMOS} días
     */
    public static RangoDeDias pedido(LocalDate desde, LocalDate hasta) {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS) {
            throw RechazoConCodigoException.invalido(RANGO_INVALIDO,
                    "Pide entre 1 y " + DIAS_MAXIMOS + " días, y que «desde» no sea posterior a «hasta».");
        }
        return new RangoDeDias(desde, hasta);
    }

    /**
     * @param diaUno el Día 1 de su programa
     * @param hoy    hoy en la zona de la persona
     * @return el mismo rango acotado a [Día 1, hoy]; vacío si no queda ningún día
     */
    public Optional<RangoDeDias> recortadoA(LocalDate diaUno, LocalDate hoy) {
        LocalDate recortadoDesde = desde.isBefore(diaUno) ? diaUno : desde;
        LocalDate recortadoHasta = hasta.isAfter(hoy) ? hoy : hasta;
        return recortadoDesde.isAfter(recortadoHasta) ? Optional.empty() : Optional.of(new RangoDeDias(recortadoDesde, recortadoHasta));
    }
}
