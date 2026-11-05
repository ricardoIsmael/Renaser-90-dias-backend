package com.renaser.os.habits.domain.model.medicion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * El número que una persona registró un día para un hábito medible (hoy, los km de {@code DAILY_KM}),
 * con su origen (D-226) y, si lo leyó el teléfono, su {@link LecturaAutomatica} (spec 037).
 *
 * <p>Este objeto solo garantiza lo que la COLUMNA puede guardar ({@code numeric(7,2)}, {@code >= 0}):
 * dos decimales, redondeados al más cercano, y dentro del rango. Si el valor tiene sentido para ESE
 * hábito (mayor que cero, un tope por día) lo decide su política, porque es regla del hábito y no del
 * número: los pasos tendrán otro tope.
 *
 * <p>La lectura va si y solo si el origen es {@link OrigenMedicion#HEALTH_CONNECT} (lo exige también la base,
 * V161).
 */
public record MedicionDiaria(BigDecimal valor, OrigenMedicion origen, LecturaAutomatica lectura) {

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
        if ((origen == OrigenMedicion.HEALTH_CONNECT) != (lectura != null)) {
            throw new IllegalArgumentException("La lectura automática va si y solo si el origen es Health Connect");
        }
    }

    /** Sin lectura: la forma de los llamadores anteriores a la spec 037 (D-226). */
    public MedicionDiaria(BigDecimal valor, OrigenMedicion origen) {
        this(valor, origen, null);
    }

    /** Lo que escribió la persona. {@code null} si no escribió nada: no hay medición. */
    public static MedicionDiaria manualSiHay(BigDecimal valor) {
        return valor == null ? null : new MedicionDiaria(valor, OrigenMedicion.MANUAL);
    }

    /** Lo que leyó el teléfono de Health Connect (spec 037). */
    public static MedicionDiaria automatica(BigDecimal valor, LecturaAutomatica lectura) {
        return new MedicionDiaria(valor, OrigenMedicion.HEALTH_CONNECT, Objects.requireNonNull(lectura, "lectura"));
    }

    /** La suma de las carreras con GPS del día (spec 042). Sin lectura: el detalle vive en las carreras. */
    public static MedicionDiaria gps(BigDecimal valor) {
        return new MedicionDiaria(valor, OrigenMedicion.GPS);
    }

    /** {@code true} si la leyó el teléfono de Health Connect (spec 037). Una carrera con GPS no lo es: ver {@link #esDelTelefono()}. */
    public boolean esAutomatica() {
        return origen == OrigenMedicion.HEALTH_CONNECT;
    }

    /**
     * {@code true} si el número lo midió el teléfono, por Health Connect o por una carrera con GPS (spec 042): las dos fuentes que
     * siguen la regla «el mayor» del día (FR-022). La captura escrita a mano no.
     */
    public boolean esDelTelefono() {
        return origen == OrigenMedicion.HEALTH_CONNECT || origen == OrigenMedicion.GPS;
    }
}
