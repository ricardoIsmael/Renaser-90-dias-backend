package com.renaser.os.community.domain.model.acompanamiento;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Intervalo semiabierto {@code [inicio, fin)}. {@code fin == null} significa vigente.
 *
 * <p>El cierre es exclusivo a propósito. Si fuera inclusivo, el instante del relevo
 * pertenecería al mentor saliente y al entrante a la vez, y la evaluación mensual contaría
 * dos veces la misma obligación de evidencia (plan.md §3 y §8).
 */
public record PeriodoAsignacion(Instant inicio, Instant fin) {

    public PeriodoAsignacion {
        Objects.requireNonNull(inicio, "inicio es obligatorio");
        if (fin != null && !fin.isAfter(inicio)) {
            throw new IllegalArgumentException(
                    "Un periodo de asignacion no puede terminar antes de empezar ni durar cero: " + inicio + " → " + fin);
        }
    }

    public static PeriodoAsignacion abierto(Instant inicio) {
        return new PeriodoAsignacion(inicio, null);
    }

    public static PeriodoAsignacion cerrado(Instant inicio, Instant fin) {
        Objects.requireNonNull(fin, "fin es obligatorio en un periodo cerrado");
        return new PeriodoAsignacion(inicio, fin);
    }

    public boolean vigente() {
        return fin == null;
    }

    /** {@code inicio <= instante < fin}. */
    public boolean contiene(Instant instante) {
        return !instante.isBefore(inicio) && (fin == null || instante.isBefore(fin));
    }

    /** Dos intervalos solapan si comparten al menos un instante. El relevo exacto no solapa. */
    public boolean solapaCon(PeriodoAsignacion otro) {
        Instant arranqueComun = inicio.isAfter(otro.inicio) ? inicio : otro.inicio;
        if (fin == null && otro.fin == null) {
            return true;
        }
        if (fin == null) {
            return arranqueComun.isBefore(otro.fin);
        }
        if (otro.fin == null) {
            return arranqueComun.isBefore(fin);
        }
        Instant cierreComun = fin.isBefore(otro.fin) ? fin : otro.fin;
        return arranqueComun.isBefore(cierreComun);
    }

    /**
     * Recorte contra la ventana evaluada. Devuelve vacío cuando no comparten instantes: es
     * lo que impide atribuir a un mentor obligaciones de un mes en el que no acompañó
     * (plan.md §8.1).
     */
    public Optional<PeriodoAsignacion> interseccionCon(PeriodoAsignacion ventana) {
        if (!solapaCon(ventana)) {
            return Optional.empty();
        }
        Instant desde = inicio.isAfter(ventana.inicio) ? inicio : ventana.inicio;
        Instant hasta = menorCierre(fin, ventana.fin);
        return Optional.of(hasta == null ? abierto(desde) : cerrado(desde, hasta));
    }

    /**
     * Cierra con la hora real de ejecución. No acepta una fecha anterior al inicio: eso
     * sería un intervalo negativo, y el plan prohíbe retrofechar rotaciones (plan.md §5).
     */
    public PeriodoAsignacion cerrarEn(Instant momento) {
        Objects.requireNonNull(momento, "momento es obligatorio");
        if (fin != null) {
            throw new IllegalStateException("El periodo ya estaba cerrado en " + fin);
        }
        return cerrado(inicio, momento);
    }

    private static Instant menorCierre(Instant uno, Instant otro) {
        if (uno == null) {
            return otro;
        }
        if (otro == null) {
            return uno;
        }
        return uno.isBefore(otro) ? uno : otro;
    }
}
