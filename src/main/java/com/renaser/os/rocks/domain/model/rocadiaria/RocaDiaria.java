package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Roca Diaria: una de las 1-3 tareas del día para un eje, ordenadas por
 * Pareto (VERDE = la más importante). Se completa una única vez, siempre a
 * través de evidencia (R-02) — no existe un PATCH que la marque completada
 * directamente.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class RocaDiaria {

    private static final int MAX_TITULO = 500;
    private static final int MAX_DESCRIPCION = 2000;

    private final RocaDiariaId id;
    private final UserId participanteId;
    private final LocalDate fecha;
    private final int posicion;
    private final String titulo;
    private final String descripcion;
    private final ColorPareto color;
    private final int puntajeImpacto;
    private final boolean esDelegable;
    private final EjeObjetivo eje;
    private final RocaSemanalId rocaSemanalId;
    private final LocalTime horaInicio;
    private final LocalTime horaFin;
    private boolean completada;
    private Instant completadaEn;
    private int puntosOtorgados;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /** Planifica una Roca Diaria nueva (R-04). El color se deriva de la posición, nunca se recibe suelto. */
    public static RocaDiaria planificar(UserId participanteId, LocalDate fecha, int posicion, String titulo,
                                         String descripcion, int puntajeImpacto, boolean esDelegable,
                                         EjeObjetivo eje, RocaSemanalId rocaSemanalId, LocalTime horaInicio,
                                         LocalTime horaFin, Clock clock) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(eje, "eje es obligatorio");
        ColorPareto color = ColorPareto.paraPosicion(posicion);
        requireImpacto(puntajeImpacto);
        Instant ahora = clock.now();
        return new RocaDiaria(RocaDiariaId.newId(), participanteId, fecha, posicion, requireTitulo(titulo),
                requireDescripcion(descripcion), color, puntajeImpacto, esDelegable, eje, rocaSemanalId, horaInicio,
                horaFin, false, null, 0, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia: reconstruye una roca diaria ya existente. */
    public static RocaDiaria rehydrate(RocaDiariaId id, UserId participanteId, LocalDate fecha, int posicion,
                                        String titulo, String descripcion, ColorPareto color, int puntajeImpacto,
                                        boolean esDelegable, EjeObjetivo eje, RocaSemanalId rocaSemanalId,
                                        LocalTime horaInicio, LocalTime horaFin, boolean completada,
                                        Instant completadaEn, int puntosOtorgados, Instant creadoEn,
                                        Instant actualizadoEn) {
        return new RocaDiaria(id, participanteId, fecha, posicion, titulo, descripcion, color, puntajeImpacto,
                esDelegable, eje, rocaSemanalId, horaInicio, horaFin, completada, completadaEn, puntosOtorgados,
                creadoEn, actualizadoEn);
    }

    /** Marca la roca completada. Solo se puede completar una vez (ALREADY_COMPLETED en el repo viejo). */
    public void completar(Instant completadaEn, Clock clock) {
        if (completada) {
            throw new IllegalStateException("Esta roca ya tiene evidencia registrada");
        }
        this.completada = true;
        this.completadaEn = Objects.requireNonNull(completadaEn, "completadaEn es obligatorio");
        this.actualizadoEn = clock.now();
    }

    /** Idempotencia del pago de puntos: un track solo premia una vez (mismo criterio que `habits`). */
    public boolean puedeOtorgarPuntos() {
        return puntosOtorgados <= 0;
    }

    public void otorgarPuntos(int puntos) {
        if (puntos < 0) {
            throw new IllegalArgumentException("puntos no puede ser negativo: " + puntos);
        }
        this.puntosOtorgados = puntos;
    }

    /**
     * Bloqueo Pareto (Ley IV): AMARILLA/ROJA quedan bloqueadas mientras la
     * VERDE del mismo eje/día no tenga evidencia. VERDE nunca está bloqueada.
     */
    public static boolean bloqueadaPorPareto(ColorPareto color, boolean verdeCompletada) {
        return color != ColorPareto.VERDE && !verdeCompletada;
    }

    private static void requireImpacto(int puntajeImpacto) {
        if (puntajeImpacto < 1 || puntajeImpacto > 10) {
            throw new IllegalArgumentException("puntajeImpacto debe estar entre 1 y 10: " + puntajeImpacto);
        }
    }

    private static String requireTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("titulo es obligatorio");
        }
        String limpio = titulo.trim();
        if (limpio.length() > MAX_TITULO) {
            throw new IllegalArgumentException("titulo supera " + MAX_TITULO + " caracteres");
        }
        return limpio;
    }

    private static String requireDescripcion(String descripcion) {
        if (descripcion == null) {
            return null;
        }
        String limpio = descripcion.trim();
        if (limpio.length() > MAX_DESCRIPCION) {
            throw new IllegalArgumentException("descripcion supera " + MAX_DESCRIPCION + " caracteres");
        }
        return limpio.isBlank() ? null : limpio;
    }

    @Override
    public String toString() {
        return "RocaDiaria[" + id + ", " + fecha + ", " + eje + " " + color + "]";
    }
}
