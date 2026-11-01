package com.renaser.os.onboarding.domain.model.pagos;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Una línea del cronograma de una persona (spec 032): una fecha del calendario, un monto y si ya se pagó. La fecha es la que escribe Administración (sin zona ni hora) y el
 * monto tiene a lo más dos decimales. Es inmutable: cada cambio devuelve otra cuota.
 *
 * <p>Los topes (fecha entre 2020 y 2100, monto de hasta 9 999 999,99) son de tipeo, no de negocio.
 *
 * @param estadoCambiadoPor quien la marcó pagada o la devolvió a pendiente por última vez; {@code null} si nunca se tocó (o si esa cuenta ya no existe)
 * @param estadoCambiadoEn  cuándo; obligatorio si está pagada
 */
public record Cuota(UUID id, LocalDate venceEl, BigDecimal monto, EstadoDeCuota estado, UserId estadoCambiadoPor, Instant estadoCambiadoEn, Instant creadaEn) {

    static final LocalDate PRIMERA_FECHA = LocalDate.of(2020, 1, 1);
    static final LocalDate ULTIMA_FECHA = LocalDate.of(2100, 12, 31);
    static final BigDecimal MONTO_MAXIMO = new BigDecimal("9999999.99");

    public Cuota {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(estado, "estado es obligatorio");
        Objects.requireNonNull(creadaEn, "creadaEn es obligatorio");
        venceEl = fechaValida(venceEl);
        monto = montoValido(monto);
        if (estado == EstadoDeCuota.PAGADA && estadoCambiadoEn == null) {
            throw new IllegalArgumentException("Una cuota pagada dice cuándo se marcó");
        }
    }

    /** Una cuota nueva: siempre pendiente. */
    public static Cuota nueva(UUID id, LocalDate venceEl, BigDecimal monto, Instant creadaEn) {
        return new Cuota(id, venceEl, monto, EstadoDeCuota.PENDIENTE, null, null, creadaEn);
    }

    public boolean pagada() {
        return estado == EstadoDeCuota.PAGADA;
    }

    /**
     * Cambia la fecha y el monto de una cuota pendiente.
     *
     * @throws IllegalStateException (409) si ya está pagada: lo pagado no se edita
     */
    public Cuota conFechaYMonto(LocalDate nuevaFecha, BigDecimal nuevoMonto) {
        if (pagada()) {
            throw new IllegalStateException("Una cuota pagada no se edita: devuélvela a «Pendiente» si hace falta corregirla");
        }
        return new Cuota(id, nuevaFecha, nuevoMonto, estado, estadoCambiadoPor, estadoCambiadoEn, creadaEn);
    }

    /**
     * La marca de Administración. Idempotente: marcar como está no cambia quién ni cuándo (un doble toque deja una sola marca).
     */
    public Cuota marcada(EstadoDeCuota nuevo, UserId quien, Instant cuando) {
        Objects.requireNonNull(nuevo, "El estado es obligatorio");
        if (nuevo == estado) {
            return this;
        }
        return new Cuota(id, venceEl, monto, nuevo, Objects.requireNonNull(quien, "quien es obligatorio"), Objects.requireNonNull(cuando, "cuando es obligatorio"), creadaEn);
    }

    private static LocalDate fechaValida(LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException("Cada cuota lleva su fecha");
        }
        if (fecha.isBefore(PRIMERA_FECHA) || fecha.isAfter(ULTIMA_FECHA)) {
            throw new IllegalArgumentException("La fecha de la cuota (" + fecha + ") no parece correcta: revisa el año");
        }
        return fecha;
    }

    private static BigDecimal montoValido(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto de cada cuota debe ser mayor que cero");
        }
        if (monto.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto admite hasta dos decimales");
        }
        BigDecimal normal = monto.setScale(2, RoundingMode.UNNECESSARY);
        if (normal.compareTo(MONTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("El monto de una cuota no puede pasar de 9 999 999,99");
        }
        return normal;
    }
}
