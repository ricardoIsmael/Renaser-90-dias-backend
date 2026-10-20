package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.horario.VentanaDelDia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Override del participante sobre el horario de un habito (catalogo o
 * personal) — tabla `preferencias_horario`, PK compuesta (participanteId, habitoId).
 * Un cambio dentro de la ventana de edicion gratuita (CLAUDE.MD / limits.ts,
 * WEEKLY_SCHEDULE_EDIT_LIMIT=3, FREE_SCHEDULE_EDITS_UNTIL_DAY=7 — aplicado por
 * el caso de uso, no por este agregado) rige de inmediato; fuera de ella, el
 * cambio se guarda como {@link CambioHorarioPendiente} y rige desde su fecha
 * efectiva.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"participanteId", "habitoId"})
public final class PreferenciaHorario {

    private final UserId participanteId;
    private final HabitoId habitoId;
    private LocalTime horaDisparo;
    private LocalTime horaLimite;
    private boolean recordatorioActivo;
    private Integer minutosRecordatorio;
    /**
     * Todas las antelaciones elegidas, de la más temprana a la más tardía; {@code null} = no se conocen
     * (fila anterior a V81, o recordatorio apagado). {@link #minutosRecordatorio} es siempre su primera
     * (D-217, {@link AntelacionesDelRecordatorio}).
     */
    private List<Integer> antelacionesRecordatorio;
    /**
     * Como suena la alarma de este habito (spec 009, D-283); {@code null} = no eligio nada, rige
     * {@link CombinacionDeAlarma#COMO_HOY}. Es la combinacion GENERAL: cada dia de la semana puede traer la suya
     * ({@link HorarioSemanal#alarma()}).
     */
    private AjusteDeAlarma alarma;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static PreferenciaHorario crear(UserId participanteId, HabitoId habitoId, LocalTime horaDisparo,
                                            LocalTime horaLimite, Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        LocalTime disparo = VentanaDelDia.requireHoraDisparoDentroDelDia(horaDisparo);
        return new PreferenciaHorario(participanteId, habitoId, disparo,
                VentanaDelDia.horaLimiteAjustada(disparo, horaLimite), true, null, null, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static PreferenciaHorario rehydrate(UserId participanteId, HabitoId habitoId, LocalTime horaDisparo,
                                                LocalTime horaLimite, boolean recordatorioActivo,
                                                Integer minutosRecordatorio, Instant creadoEn, Instant actualizadoEn) {
        return rehydrate(participanteId, habitoId, horaDisparo, horaLimite, recordatorioActivo, minutosRecordatorio,
                null, creadoEn, actualizadoEn);
    }

    /** Solo para el adaptador de persistencia, con el conjunto de antelaciones (V81). */
    public static PreferenciaHorario rehydrate(UserId participanteId, HabitoId habitoId, LocalTime horaDisparo,
                                                LocalTime horaLimite, boolean recordatorioActivo,
                                                Integer minutosRecordatorio, List<Integer> antelacionesRecordatorio,
                                                Instant creadoEn, Instant actualizadoEn) {
        return rehydrate(participanteId, habitoId, horaDisparo, horaLimite, recordatorioActivo, minutosRecordatorio,
                antelacionesRecordatorio, null, creadoEn, actualizadoEn);
    }

    /** Solo para el adaptador de persistencia, con la alarma general (V112). */
    public static PreferenciaHorario rehydrate(UserId participanteId, HabitoId habitoId, LocalTime horaDisparo,
                                                LocalTime horaLimite, boolean recordatorioActivo,
                                                Integer minutosRecordatorio, List<Integer> antelacionesRecordatorio,
                                                AjusteDeAlarma alarma, Instant creadoEn, Instant actualizadoEn) {
        return new PreferenciaHorario(participanteId, habitoId, horaDisparo, horaLimite, recordatorioActivo,
                minutosRecordatorio, antelacionesRecordatorio == null ? null : List.copyOf(antelacionesRecordatorio),
                alarma == null || alarma.esVacio() ? null : alarma, creadoEn, actualizadoEn);
    }

    public void aplicarAhora(LocalTime horaDisparo, LocalTime horaLimite, Instant ahora) {
        this.horaDisparo = VentanaDelDia.requireHoraDisparoDentroDelDia(horaDisparo);
        this.horaLimite = VentanaDelDia.horaLimiteAjustada(this.horaDisparo, horaLimite);
        this.actualizadoEn = ahora;
    }

    /**
     * PLN-09: los minutos se validan ANTES de tocar nada, asi un rechazo no deja la preferencia a
     * medio escribir ({@link AntelacionDelRecordatorio}).
     */
    public void actualizarRecordatorio(boolean activo, Integer minutosAntes, Instant ahora) {
        Integer minutos = AntelacionDelRecordatorio.requireDentroDelRango(minutosAntes);
        this.antelacionesRecordatorio = AntelacionesDelRecordatorio.trasMinutosSueltos(antelacionesRecordatorio,
                minutosRecordatorio, activo, minutos);
        this.minutosRecordatorio = minutos;
        this.recordatorioActivo = activo;
        this.actualizadoEn = ahora;
    }

    /**
     * Con TODAS las antelaciones (D-217): {@link #minutosRecordatorio} pasa a ser la más temprana, que es
     * lo que el APK de producción sigue leyendo. Apagado, o sin ninguna, el conjunto queda {@code null}.
     */
    public void actualizarRecordatorioConAntelaciones(boolean activo, List<Integer> antelaciones, Instant ahora) {
        List<Integer> normalizadas = AntelacionesDelRecordatorio.normalizar(antelaciones);
        this.minutosRecordatorio = normalizadas.isEmpty() ? null : normalizadas.getFirst();
        this.antelacionesRecordatorio = activo && !normalizadas.isEmpty() ? normalizadas : null;
        this.recordatorioActivo = activo;
        this.actualizadoEn = ahora;
    }

    /**
     * Como suena la alarma de este habito (spec 009, D-283). {@code null}, o un ajuste que no dice nada, la
     * devuelve a lo de hoy; no toca la hora ni el aviso.
     */
    public void actualizarAlarma(AjusteDeAlarma nueva, Instant ahora) {
        this.alarma = nueva == null || nueva.esVacio() ? null : nueva;
        this.actualizadoEn = ahora;
    }

    /**
     * {@code true} si esta fila existe solo para guardar como suena la alarma: no eligio hora, ni hora limite,
     * ni aviso. No es un horario «personalizado»: la hora sigue siendo la del catalogo. Las filas de antes de
     * la 009 (sin alarma) nunca lo son.
     */
    public boolean esSoloDeAlarma() {
        return alarma != null && horaDisparo == null && horaLimite == null && recordatorioActivo
                && minutosRecordatorio == null && antelacionesRecordatorio == null;
    }

    @Override
    public String toString() {
        return "PreferenciaHorario[" + participanteId + ", " + habitoId + "]";
    }
}
