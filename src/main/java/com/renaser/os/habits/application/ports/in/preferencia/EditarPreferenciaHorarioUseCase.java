package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Hueco #12 — el aprendiz edita el horario personal de un habito. Traduccion simplificada
 * de {@code updateHabitPreference} (repo viejo, {@code service.ts:2021}) — ver
 * docs/MODULO_HABITS.md por lo que quedo fuera (ventanas extendidas de OTROS habitos no se
 * excluyen de la cuota reportada, `isProgramCompleted` no existe en este backend todavia).
 */
public interface EditarPreferenciaHorarioUseCase {

    /**
     * Si la ventana de HOY de este habito ya arranco, el cambio queda PROGRAMADO para
     * manana (nunca se rechaza — "no se improvisa el dia", pero tampoco se pierde la
     * decision). Si no, rige desde ahora. Antes del dia 7 de programa (o del limite propio
     * del habito, el que sea mayor) los cambios inmediatos son ilimitados; despues, cuesta
     * cupo semanal — {@code WEEKLY_SCHEDULE_EDIT_LIMIT} habitos DISTINTOS por semana de
     * programa, agotado el cupo lanza {@link IllegalStateException}.
     */
    ResultadoEdicionPreferencia editar(EditarPreferenciaHorarioCommand command);

    record EditarPreferenciaHorarioCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                            @NotNull LocalTime horaDisparo, @NotNull LocalTime horaLimite,
                                            boolean recordatorioActivo, Integer minutosRecordatorio) {
        public EditarPreferenciaHorarioCommand {
            SelfValidating.validateConstructorArgs(EditarPreferenciaHorarioCommand.class, actorId, habitoId,
                    horaDisparo, horaLimite, recordatorioActivo, minutosRecordatorio);
        }
    }

    /**
     * {@code diferido}: el cambio quedo programado, no rige hoy — {@code horaDisparo}/
     * {@code horaLimite} son entonces lo que regira desde {@code fechaEfectivaDiferido},
     * no lo vigente hoy. {@code periodo}: "FREE" (sin cupo) o "WEEK" (con cupo) — mismo
     * literal que el contrato viejo (D-36).
     */
    record ResultadoEdicionPreferencia(HabitoId habitoId, LocalTime horaDisparo, LocalTime horaLimite,
                                        boolean diferido, LocalDate fechaEfectivaDiferido, int cambiosUsados,
                                        int cambiosRestantes, int cambiosLimite, String periodo) {
    }
}
