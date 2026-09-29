package com.renaser.os.habits.domain.model.medicion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * El número que una persona registró un día para un hábito medible (hoy, los km de {@code DAILY_KM}),
 * con su origen (D-226).
 *
 * <p>Este objeto solo garantiza lo que la COLUMNA puede guardar ({@code numeric(7,2)}, {@code >= 0}):
 * dos decimales, redondeados al más cercano, y dentro del rango. Si el valor tiene sentido para ESE
 * hábito (mayor que cero, un tope por día) lo decide su política, porque es regla del hábito y no del
 * número: los pasos tendrán otro tope.
 */
public record MedicionDiaria(BigDecimal valor, OrigenMedicion origen) {

    /** Decimales de {@code registros_habito.valor_medido}. */
    public static final int DECIMALES = 2;
    /** El máximo que entra en {@code numeric(7,2)}. */
    public static final BigDecimal MAXIMO_REPRESENTABLE = new BigDecimal("99999.99");

    public MedicionDiaria {
        Objects.requireNonNull(valor, "El valor de la medición es obligatorio");
        Objects.requireNonNull(origen, "El origen de la medición es obligatorio");
        valor = valor.setScale(DECIMALES, RoundingMode.HALF_UP);
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("La medición no puede ser negativa");
        }
        if (valor.compareTo(MAXIMO_REPRESENTABLE) > 0) {
            throw new IllegalArgumentException("La medición es demasiado grande");
        }
    }

    /** Lo que escribió la persona. {@code null} si no escribió nada: no hay medición. */
    public static MedicionDiaria manualSiHay(BigDecimal valor) {
        return valor == null ? null : new MedicionDiaria(valor, OrigenMedicion.MANUAL);
    }
}
