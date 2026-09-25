package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Pausa del semáforo de alguien del staff con programa propio (respuesta del dueño, 2026-09-25):
 * elige hasta qué día, esos días no se miden ni salen en rojo, lo medido antes se conserva y al pasar
 * la fecha vuelve a medirse solo. Puede volver a encenderlo antes: desde ese día se mide.
 *
 * <p>Que un aprendiz no pueda pausar no es regla de este objeto sino del caso de uso, que conoce el
 * rol. Acá viven las reglas de las fechas.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class PausaDeMedicion {

    private final PausaId id;
    private final UserId usuarioId;
    private final LocalDate desde;
    private LocalDate hasta;
    /** Día local en que se volvió a encender antes de tiempo; ese día ya se mide. */
    private LocalDate reanudadaEl;
    private final Instant creadaEn;
    private Instant reanudadaEn;

    /** Empieza hoy: el día de hoy todavía no cerró, así que tampoco se mide. */
    public static PausaDeMedicion iniciar(PausaId id, UserId usuarioId, LocalDate hoy, LocalDate hasta, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        exigirHastaDesdeHoy(hasta, hoy);
        return new PausaDeMedicion(id, usuarioId, hoy, hasta, null, Objects.requireNonNull(ahora), null);
    }

    public static PausaDeMedicion rehidratar(PausaId id, UserId usuarioId, LocalDate desde, LocalDate hasta,
                                             LocalDate reanudadaEl, Instant creadaEn, Instant reanudadaEn) {
        return new PausaDeMedicion(id, usuarioId, desde, hasta, reanudadaEl, creadaEn, reanudadaEn);
    }

    /** Si ese día quedó sin medir por esta pausa. */
    public boolean cubre(LocalDate fecha) {
        return !fecha.isBefore(desde) && !fecha.isAfter(ultimoDiaPausado());
    }

    /** Si hoy sigue pausado: no se volvió a encender y no pasó la fecha elegida. */
    public boolean vigenteEl(LocalDate hoy) {
        return reanudadaEl == null && !hoy.isBefore(desde) && !hoy.isAfter(hasta);
    }

    /** Pide otra fecha de regreso para la pausa en curso. */
    public void cambiarHasta(LocalDate nuevaHasta, LocalDate hoy) {
        exigirVigente(hoy);
        exigirHastaDesdeHoy(nuevaHasta, hoy);
        this.hasta = nuevaHasta;
    }

    /** Vuelve a encenderlo hoy: desde hoy se mide. */
    public void reanudar(LocalDate hoy, Instant ahora) {
        exigirVigente(hoy);
        this.reanudadaEl = hoy;
        this.reanudadaEn = Objects.requireNonNull(ahora);
    }

    /** El último día que de verdad quedó sin medir. Puede quedar antes de {@code desde}: no cubre nada. */
    public LocalDate ultimoDiaPausado() {
        if (reanudadaEl == null) {
            return hasta;
        }
        LocalDate antesDeReanudar = reanudadaEl.minusDays(1);
        return antesDeReanudar.isBefore(hasta) ? antesDeReanudar : hasta;
    }

    private void exigirVigente(LocalDate hoy) {
        if (!vigenteEl(hoy)) {
            throw new IllegalStateException("La pausa ya termino");
        }
    }

    private static void exigirHastaDesdeHoy(LocalDate hasta, LocalDate hoy) {
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        if (hasta.isBefore(hoy)) {
            throw new IllegalArgumentException("La pausa tiene que terminar hoy o despues: " + hasta);
        }
    }
}
