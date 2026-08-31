package com.renaser.os.habits.infrastructure.adapter.in.rest.habitosaprendiz;

import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase;
import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.ConsultarHabitosDeAprendizCommand;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Vista vertical del panel admin: los habitos de UN aprendiz.
 *
 * <p><b>Por que vive en {@code habits} y no en {@code users}:</b> el dato es de habitos
 * (horarios, renombres, desbloqueos, cuota de reacomodos) y la autorizacion de admin de
 * habitos ya vive en este modulo ({@code HabitoAdminGuard}). Ponerlo en {@code users}
 * obligaria a una llamada entre modulos para leer tablas que son de {@code habits}. La ruta
 * cuelga de {@code /api/v1/admin/trainees/} porque es la pantalla del operador, no porque
 * el codigo sea de {@code users}: Spring rutea por path, no por modulo.
 *
 * <p>Solo ADMIN/ALCHEMIST activos — el gate esta DENTRO del servicio (CLAUDE.MD §5.4.6), no
 * aca. Actor resuelto por {@code @ActorAutenticado} desde la sesion, con respaldo por el
 * header temporal {@code X-Actor-Id} mientras dure la migracion (D-29).
 */
@RestController
@RequestMapping("/api/v1/admin/trainees")
public class HabitosDeAprendizAdminController {

    private final ConsultarHabitosDeAprendizUseCase consultarUseCase;

    public HabitosDeAprendizAdminController(ConsultarHabitosDeAprendizUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping("/{traineeId}/habits")
    public TraineeHabitsResponse habitosDe(@ActorAutenticado UserId actor, @PathVariable UUID traineeId) {
        var vista = consultarUseCase.consultar(new ConsultarHabitosDeAprendizCommand(actor, UserId.of(traineeId)));
        return TraineeHabitsResponse.from(vista);
    }
}
