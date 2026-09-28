package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Hueco #12 — el aprendiz edita el horario personal de un habito. Traduccion simplificada
 * de {@code updateHabitPreference} (repo viejo, {@code service.ts:2021}) — ver
 * docs/MODULO_HABITS.md por lo que quedo fuera (ventanas extendidas de OTROS habitos no se
 * excluyen de la cuota reportada, `isProgramCompleted` no existe en este backend todavia).
 */
public interface EditarPreferenciaHorarioUseCase {

    /**
     * Con {@code fecha}, guarda el horario exclusivamente para esa fecha futura, en la zona del
     * participante. No altera el horario general ni los cambios de otras fechas. La cuota cuenta
     * habitos distintos de la semana de programa correspondiente a esa fecha.
     * Sin fecha mantiene el contrato legado: cambio general a partir de manana.
     * El dia actual y los anteriores no se editan (D-91).
     */
    ResultadoEdicionPreferencia editar(EditarPreferenciaHorarioCommand command);

    /**
     * {@code antelacionesRecordatorio} (D-217, 2026-09-28): TODAS las antelaciones elegidas, o
     * {@code null} si quien llama solo conoce el número suelto ({@code minutosRecordatorio}), como el APK
     * de producción. Con el conjunto, {@code minutosRecordatorio} se ignora: manda la más temprana.
     */
    record EditarPreferenciaHorarioCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                            @NotNull LocalTime horaDisparo, LocalTime horaLimite,
                                            boolean recordatorioActivo, Integer minutosRecordatorio, LocalDate fecha,
                                            List<Integer> antelacionesRecordatorio) {
        public EditarPreferenciaHorarioCommand {
            SelfValidating.validateConstructorArgs(EditarPreferenciaHorarioCommand.class, actorId, habitoId,
                    horaDisparo, horaLimite, recordatorioActivo, minutosRecordatorio, fecha, antelacionesRecordatorio);
        }

        /**
         * Los minutos que se guardan en {@code minutos_recordatorio}: con el conjunto (D-217), su más
         * temprana; sin él (el APK de producción, el acompañante), los que llegaron sueltos.
         */
        public Integer minutosEfectivos() {
            if (antelacionesRecordatorio == null) {
                return minutosRecordatorio;
            }
            return antelacionesRecordatorio.stream().filter(java.util.Objects::nonNull)
                    .max(Integer::compare).orElse(null);
        }
    }

    /**
     * {@code diferido}: el cambio quedo programado, no rige hoy — {@code horaDisparo}/
     * {@code horaLimite} son entonces lo que regira en {@code fechaEfectivaDiferido} (o desde ella en la ruta legada),
     * no lo vigente hoy. {@code periodo}: "FREE" (sin cupo) o "WEEK" (con cupo) — mismo
     * literal que el contrato viejo (D-36).
     */
    record ResultadoEdicionPreferencia(HabitoId habitoId, LocalTime horaDisparo, LocalTime horaLimite,
                                        boolean diferido, LocalDate fechaEfectivaDiferido, int cambiosUsados,
                                        int cambiosRestantes, int cambiosLimite, String periodo) {
    }
}
