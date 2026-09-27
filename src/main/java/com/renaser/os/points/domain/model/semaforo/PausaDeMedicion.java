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
 * Un tramo de días que el semáforo de una persona no mide. Hay dos ({@link MotivoDePausa}):
 *
 * <p><b>Pausa pedida por la persona</b> (staff con programa propio, respuesta del dueño,
 * 2026-09-25): elige hasta qué día, esos días no se miden ni salen en rojo, lo medido antes se
 * conserva y al pasar la fecha vuelve a medirse solo. Puede volver a encenderlo antes: desde ese día
 * se mide. Que un aprendiz no pueda pedirla no es regla de este objeto sino del caso de uso, que
 * conoce el rol.
 *
 * <p><b>Cuenta suspendida</b> (D-209, decisión del dueño del 2026-09-27: «Que no se midan»): no se
 * mide ningún día en que la cuenta estuvo suspendida, aunque sea un rato. Empieza el día local de la
 * suspensión, no tiene fecha de regreso ({@code hasta} es null) y termina cuando la reactivan: ese
 * día tampoco se mide, porque la persona lo vivió en parte sin poder usar la app. No es una pausa de
 * la persona: nunca está «vigente» en el sentido de {@link #vigenteEl}, así que no se muestra como
 * pausa ni se puede cambiar ni reanudar a mano.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class PausaDeMedicion {

    private final PausaId id;
    private final UserId usuarioId;
    private final MotivoDePausa motivo;
    private final LocalDate desde;
    /** Último día elegido, inclusive. Null solo en una suspensión: no tiene fecha de regreso. */
    private LocalDate hasta;
    /** Día local desde el que se vuelve a medir: se encendió antes de tiempo o se reactivó la cuenta. */
    private LocalDate reanudadaEl;
    private final Instant creadaEn;
    private Instant reanudadaEn;

    /** Empieza hoy: el día de hoy todavía no cerró, así que tampoco se mide. */
    public static PausaDeMedicion iniciar(PausaId id, UserId usuarioId, LocalDate hoy, LocalDate hasta, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        exigirHastaDesdeHoy(hasta, hoy);
        return new PausaDeMedicion(id, usuarioId, MotivoDePausa.PEDIDA_POR_LA_PERSONA, hoy, hasta, null,
                Objects.requireNonNull(ahora), null);
    }

    /**
     * La cuenta se suspendió en {@code suspendidaEn}, que cae en {@code diaDeLaSuspension} en la zona de
     * la persona: desde ese día no se mide, sin fecha de regreso.
     */
    public static PausaDeMedicion porSuspension(PausaId id, UserId usuarioId, LocalDate diaDeLaSuspension,
                                                Instant suspendidaEn) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(diaDeLaSuspension, "diaDeLaSuspension es obligatorio");
        return new PausaDeMedicion(id, usuarioId, MotivoDePausa.CUENTA_SUSPENDIDA, diaDeLaSuspension, null, null,
                Objects.requireNonNull(suspendidaEn), null);
    }

    public static PausaDeMedicion rehidratar(PausaId id, UserId usuarioId, MotivoDePausa motivo, LocalDate desde,
                                             LocalDate hasta, LocalDate reanudadaEl, Instant creadaEn,
                                             Instant reanudadaEn) {
        return new PausaDeMedicion(id, usuarioId, motivo, desde, hasta, reanudadaEl, creadaEn, reanudadaEn);
    }

    /** Si ese día quedó sin medir por esta pausa. */
    public boolean cubre(LocalDate fecha) {
        LocalDate ultimo = ultimoDiaPausado();
        return !fecha.isBefore(desde) && (ultimo == null || !fecha.isAfter(ultimo));
    }

    /**
     * Si hoy rige esta pausa PEDIDA POR LA PERSONA: no la volvió a encender y no pasó la fecha elegida.
     * Es la que se muestra y la que se puede cambiar. Una suspensión nunca está vigente en este sentido.
     */
    public boolean vigenteEl(LocalDate hoy) {
        return motivo == MotivoDePausa.PEDIDA_POR_LA_PERSONA && reanudadaEl == null
                && !hoy.isBefore(desde) && !hoy.isAfter(hasta);
    }

    /** Si es una suspensión de cuenta que todavía no terminó: la cuenta sigue suspendida. */
    public boolean suspensionEnCurso() {
        return motivo == MotivoDePausa.CUENTA_SUSPENDIDA && reanudadaEl == null;
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

    /**
     * La cuenta se reactivó en {@code reactivadaEn}, que cae en {@code diaDeLaReactivacion} en la zona de
     * la persona. Ese día tampoco se mide (estuvo suspendida una parte); desde el siguiente, sí.
     *
     * <p>Con la misma zona la reactivación nunca cae antes que la suspensión. Si la persona cambió de
     * zona en el medio y el día quedara antes de {@code desde}, se toma {@code desde}: la suspensión
     * cubre al menos el día en que empezó.
     */
    public void terminarSuspension(LocalDate diaDeLaReactivacion, Instant reactivadaEn) {
        if (!suspensionEnCurso()) {
            throw new IllegalStateException("No hay una suspension de cuenta en curso para terminar");
        }
        LocalDate ultimoSuspendido = diaDeLaReactivacion.isBefore(desde) ? desde : diaDeLaReactivacion;
        this.reanudadaEl = ultimoSuspendido.plusDays(1);
        this.reanudadaEn = Objects.requireNonNull(reactivadaEn);
    }

    /**
     * El último día que de verdad quedó sin medir; null mientras siga sin fecha de regreso (una
     * suspensión en curso). Puede quedar antes de {@code desde}: no cubre nada.
     */
    public LocalDate ultimoDiaPausado() {
        if (reanudadaEl == null) {
            return hasta;
        }
        LocalDate antesDeReanudar = reanudadaEl.minusDays(1);
        return hasta == null || antesDeReanudar.isBefore(hasta) ? antesDeReanudar : hasta;
    }

    private void exigirVigente(LocalDate hoy) {
        if (motivo != MotivoDePausa.PEDIDA_POR_LA_PERSONA) {
            throw new IllegalStateException("Una suspension de cuenta no se cambia a mano: termina al reactivar la cuenta");
        }
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
