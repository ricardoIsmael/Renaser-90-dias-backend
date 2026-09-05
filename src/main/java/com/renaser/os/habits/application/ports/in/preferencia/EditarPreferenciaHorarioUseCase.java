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
     * <b>D-91 — el dia en curso no se edita, nunca.</b> Todo cambio de horario queda PROGRAMADO
     * para el dia siguiente, sin importar la hora a la que se pida ni si la ventana del habito
     * ya arranco. La regla del producto es "el dia se cierra a la medianoche": para que un
     * horario rija el dia D hay que pedirlo antes de que termine el dia D-1.
     *
     * <p>Antes de D-91 el cambio se aplicaba en el acto si la hora de disparo todavia no habia
     * llegado, y solo se difería si ya habia pasado. Eso permitia reacomodar el dia en curso,
     * que es justamente lo que el dueño pidio impedir.
     *
     * <p>Nunca se rechaza por la hora: "no se improvisa el dia" no puede volverse "perdiste la
     * decision". El pedido se guarda y {@code PromocionCambioHorarioService} lo hace regir esa
     * noche. Un segundo pedido sobre el mismo habito el mismo dia pisa al anterior (la PK de
     * {@code cambios_horario_pendientes} es participante+habito), asi que el aprendiz puede
     * cambiar de opinion todas las veces que quiera antes de la medianoche.
     *
     * <p>Si se rechaza por CUPO: hasta el dia 7 de programa (o el limite propio del habito, el
     * que sea mayor) los cambios son ilimitados; despues cuesta cupo semanal —
     * {@code WEEKLY_SCHEDULE_EDIT_LIMIT} habitos DISTINTOS por semana, y agotado lanza
     * {@link IllegalStateException}. El cupo se mide contra la semana de la FECHA EFECTIVA y
     * cuenta tambien los cambios ya programados que van a regir en esa semana.
     */
    ResultadoEdicionPreferencia editar(EditarPreferenciaHorarioCommand command);

    record EditarPreferenciaHorarioCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                            @NotNull LocalTime horaDisparo, LocalTime horaLimite,
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
