package com.renaser.os.shared.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * El tope <b>absoluto</b> de una sesión (spec 050, tramo E; FR-028 y FR-031; D6): pasados esos días desde que se creó,
 * la sesión ya no vale, aunque se haya usado ayer. Es aparte de la inactividad de 30 días de
 * {@code spring.session.timeout}, que no cambia: ese plazo se renueva con cada pedido y este nunca.
 *
 * <p>Regla pura (sin Spring): recibe los dos instantes y dice si pasó. El «ahora» sale siempre del {@link Clock} del
 * puerto, nunca de {@code Instant.now()} (regla 02): así una prueba puede poner el reloj donde quiera, también entre las
 * 00:00 y las 05:00 UTC, que es la hora en que el día de Lima y el de UTC no coinciden. Como son dos instantes y una
 * duración, el resultado no depende de ninguna zona.
 *
 * <p>Sin tope ({@link #sinTope()}) es la vuelta atrás: ninguna sesión vence por antigüedad.
 */
public final class TopeDeSesion {

    private static final TopeDeSesion SIN_TOPE = new TopeDeSesion(null);
    private static final Duration MINIMO = Duration.ofSeconds(1);

    private final Duration tope;

    private TopeDeSesion(Duration tope) {
        this.tope = tope;
    }

    /** Sin tope absoluto: la vuelta atrás de la propiedad vacía o en cero. */
    public static TopeDeSesion sinTope() {
        return SIN_TOPE;
    }

    /**
     * Un tope de esa duración; {@code null}, cero o negativa es «sin tope» (nunca un tope de cero que cortaría a todos).
     *
     * <p>Una duración positiva pero menor a un segundo es casi seguro un valor sin unidad ({@code 90}, que Spring lee como
     * 90 milisegundos): cortaría a toda la gente en el primer pedido. Se rechaza al armar el tope, o sea al arrancar el
     * backend, con el motivo en el mensaje, en vez de dejar a todos fuera sin entender por qué.
     */
    public static TopeDeSesion de(Duration duracion) {
        if (duracion == null || duracion.isZero() || duracion.isNegative()) {
            return SIN_TOPE;
        }
        if (duracion.compareTo(MINIMO) < 0) {
            throw new IllegalArgumentException("El tope absoluto de sesión (" + duracion + ") es menor a un segundo: ¿falta la "
                    + "unidad? Escríbelo como 90d, 2160h o P90D (vacío o 0 = sin tope).");
        }
        return new TopeDeSesion(duracion);
    }

    public boolean activo() {
        return tope != null;
    }

    /** El tope, o {@code null} si no hay. Solo para mostrarlo en el registro de arranque. */
    public Duration duracion() {
        return tope;
    }

    /**
     * Si ya pasó <b>más</b> del tope desde que se creó la sesión: justo en el instante del tope todavía vale. Una sesión
     * «creada en el futuro» (el reloj de quien la guardó iba adelantado) no vence.
     */
    public boolean vencida(Instant creadaEn, Instant ahora) {
        Objects.requireNonNull(creadaEn, "creadaEn");
        Objects.requireNonNull(ahora, "ahora");
        return tope != null && Duration.between(creadaEn, ahora).compareTo(tope) > 0;
    }
}
