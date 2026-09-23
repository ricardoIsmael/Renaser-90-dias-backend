package com.renaser.os.rag.application.ports.out.horarios;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para leer el horario resuelto de un dia del aprendiz y su cupo
 * semanal de cambios de horario (herramienta {@code consultar_horarios}, 2026-09-23).
 *
 * <p>Las tablas de horarios son de {@code habits}: el adaptador que implementa este puerto delega
 * en {@code habits.api.HorarioDelDiaFinder} (D-41). Mismo criterio que
 * {@code ConsultarAgendaHabitosPort}: el puerto define sus propios records y no expone el
 * contrato ajeno a {@code rag.application}.
 */
public interface ConsultarHorariosPort {

    /**
     * @param fecha {@code null} = hoy en la zona del participante, resuelto por {@code habits}
     * @throws RuntimeException si el participante no existe o esta suspendido — la herramienta lo
     *                          traduce a un fallo legible
     */
    HorariosDelDia deFecha(UserId participanteId, LocalDate fecha);

    /** @param diaPrograma el dia del programa que cae en {@code fecha}; puede quedar fuera de [0, 90]. */
    record HorariosDelDia(LocalDate fecha, int diaPrograma, List<HorarioDeHabito> habitos, CuotaCambios cuota) {
    }

    /**
     * @param habitoId    el id del HABITO (no de un registro del dia): lo que va a necesitar una
     *                    herramienta de escritura de horario cuando exista
     * @param horaDisparo {@code null} si ese dia no tiene hora fijada
     */
    record HorarioDeHabito(UUID habitoId, String titulo, LocalTime horaDisparo, LocalTime horaLimite,
                           boolean personalizado, boolean apagado, boolean pausado, boolean obligatorio, CambioProgramado cambioProgramado) {
    }

    record CambioProgramado(LocalTime horaDisparo, LocalTime horaLimite, LocalDate desde) {
    }

    record CuotaCambios(int usados, int restantes, int limite, boolean semanaDeAcomodoLibre) {
    }
}
