package com.renaser.os.habits.application.ports.in.habitosaprendiz;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Vista VERTICAL del panel admin: los habitos de UN aprendiz concreto, no el catalogo de
 * todos. Cierra el hueco entre el admin de catalogo ({@code /api/v1/admin/habits}, que
 * responde "que habitos existen") y el admin de aprendices
 * ({@code /api/v1/admin/trainees/{id}}, que responde "quien es y en que dia va"): hasta
 * ahora nadie cruzaba las dos cosas, asi que era imposible abrir a una persona y ver su
 * horario real, sus renombres, su cuota de cambios gastada y su plan de desbloqueo.
 *
 * <p><b>Solo lectura.</b> Nada de este caso de uso escribe: para cambiar el horario de un
 * aprendiz existe {@code EditarPreferenciaHorarioUseCase} (autoservicio), y para el
 * catalogo, {@code ActualizarHabitoUseCase}/{@code ActualizarHorarioHabitoUseCase}.
 *
 * <p><b>Autorizacion:</b> ADMIN/ALCHEMIST activos. Un rol sin permiso o una cuenta
 * {@code SUSPENDED} reciben {@code NotAuthorizedException} (403), aunque el token sea
 * valido. El aprendiz mirado SI puede estar suspendido — un operador tiene que poder
 * auditar precisamente a quien acaba de suspender.
 */
public interface ConsultarHabitosDeAprendizUseCase {

    VistaHabitosDeAprendiz consultar(ConsultarHabitosDeAprendizCommand command);

    record ConsultarHabitosDeAprendizCommand(@NotNull UserId actorId, @NotNull UserId aprendizId) {
        public ConsultarHabitosDeAprendizCommand {
            SelfValidating.validateConstructorArgs(ConsultarHabitosDeAprendizCommand.class, actorId, aprendizId);
        }
    }

    /**
     * {@code fechaLocal}/{@code diaPrograma} son el contexto contra el que se resolvio la
     * vista (zona horaria del propio aprendiz, no la del servidor ni la del admin): el
     * horario vigente y el dia semanal elegido dependen de ellos, asi que viajan en la
     * respuesta para que el panel no tenga que adivinarlos.
     */
    record VistaHabitosDeAprendiz(UserId aprendizId, int diaPrograma, LocalDate fechaLocal, String zonaHoraria,
                                   CuotaCambiosHorario cuota, List<HabitoDeAprendiz> habitos) {
    }

    /**
     * Cuota semanal de reacomodo de horario, contada sobre {@code historial_cambios_horario}
     * — habitos DISTINTOS cambiados en la semana de programa en curso, no ediciones.
     * {@code periodo}: {@code "FREE"} mientras dura la semana de acomodo inicial (sin cupo),
     * {@code "WEEK"} despues. Mismos literales que ya devuelve el autoservicio
     * ({@code HabitPreferenceResponse}), para que el panel y la app digan lo mismo.
     */
    record CuotaCambiosHorario(int usados, int restantes, int limite, String periodo) {
    }

    /**
     * Un habito activo del aprendiz. {@code tituloPersonal} es {@code null} salvo que lo
     * haya renombrado; {@code horaDisparo}/{@code horaLimite} son las VIGENTES (su
     * preferencia pisando al catalogo, misma precedencia que aplica {@code RegistroService}).
     */
    record HabitoDeAprendiz(HabitoId habitoId, String tituloCatalogo, String tituloPersonal, boolean esPersonal,
                             TipoHabito tipo, String categoriaClave, LocalTime horaDisparo, LocalTime horaLimite,
                             boolean horarioPersonalizado, Boolean recordatorioActivo, Integer minutosRecordatorio,
                             CambioHorarioProgramado cambioPendiente, DesbloqueoDeAprendiz desbloqueo,
                             boolean eleccionDiaSemanal, LocalDate diaSemanalElegido) {
    }

    /** Cambio de horario ya decidido pero que todavia no rige (`cambios_horario_pendientes`). */
    record CambioHorarioProgramado(LocalTime horaDisparo, LocalTime horaLimite, LocalDate fechaEfectiva) {
    }

    /**
     * Dia de programa en el que este habito se le desbloquea. {@code elegidoPorLaPersona}
     * es falso cuando {@code desbloqueos_habito.elegido_en} es NULL, o sea cuando lo puso
     * el relleno automatico y no el aprendiz — es justo el dato que el operador necesita
     * para saber si la escalera la armo alguien o se armo sola.
     */
    record DesbloqueoDeAprendiz(int diaDesbloqueo, boolean elegidoPorLaPersona) {
    }
}
