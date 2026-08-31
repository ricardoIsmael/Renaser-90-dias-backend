package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lectura de la configuracion de horarios del propio aprendiz. Contraparte de
 * {@link EditarPreferenciaHorarioUseCase}: sin esto el aprendiz solo podia ESCRIBIR su horario
 * (PATCH) sin poder leer lo que rige ni lo que ya dejo programado (E-55) — no podia planificar.
 */
public interface ConsultarPreferenciasHorarioUseCase {

    /** Autoservicio estricto: solo el propio participante, y solo si no esta suspendido. */
    ResumenPreferenciasHorario consultar(UserId actorId);

    /**
     * {@code cuota}: la misma que devuelve el PATCH, con los mismos literales de {@code periodo}
     * ("FREE"/"WEEK", D-36) — el cliente no tiene que reconciliar dos formas del mismo dato.
     */
    record ResumenPreferenciasHorario(List<HorarioDeHabito> habitos, CuotaEdicion cuota) {
    }

    /**
     * {@code horaDisparo}/{@code horaLimite} son lo VIGENTE HOY (preferencia propia si la hay, si
     * no el default del catalogo). {@code personalizado} distingue "elegi este horario" de "es el
     * que vino de fabrica". {@code cambioProgramado} es {@code null} salvo que haya un cambio
     * diferido esperando su fecha.
     */
    record HorarioDeHabito(HabitoId habitoId, String titulo, LocalTime horaDisparo, LocalTime horaLimite,
                            boolean personalizado, CambioProgramado cambioProgramado) {
    }

    record CambioProgramado(LocalTime horaDisparo, LocalTime horaLimite, LocalDate fechaEfectiva) {
    }

    record CuotaEdicion(int cambiosUsados, int cambiosRestantes, int cambiosLimite, String periodo) {
    }
}
