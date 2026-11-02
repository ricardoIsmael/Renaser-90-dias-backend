package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.medicion.RegistrarMedicionAutomaticaUseCase;
import com.renaser.os.habits.application.ports.in.medicion.RegistrarMedicionAutomaticaUseCase.RegistrarMedicionAutomaticaCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Spec 037 (D-310): el teléfono cierra el hábito de kilómetros con los pasos y la distancia que leyó de Health Connect.
 * Autoservicio: solo el dueño del registro. Va en su propio controller y no en {@link HabitTrackController} para no cambiar
 * lo que ese ya hace; comparte la ruta base. El controller es tonto: valida la forma e invoca UN caso de uso.
 */
@RestController
@RequestMapping("/api/v1/habit-tracks")
@Funcionalidad("habitos")
public class MedicionAutomaticaController {

    private final RegistrarMedicionAutomaticaUseCase registrarUseCase;

    public MedicionAutomaticaController(RegistrarMedicionAutomaticaUseCase registrarUseCase) {
        this.registrarUseCase = registrarUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "dueno del registro de habito")
    @PostMapping("/{id}/auto-measurement")
    public RegistroHabitoResponse registrar(@ActorAutenticado UserId actor, @PathVariable String id,
                                             @RequestBody @Valid AutoMeasurementRequest request) {
        var registro = registrarUseCase.registrar(new RegistrarMedicionAutomaticaCommand(actor,
                RegistroHabitoId.of(UUID.fromString(id)), request.kilometros(), request.pasos(), request.fuente(),
                request.dispositivo(), request.leidoEn()));
        return RegistroHabitoResponse.from(registro);
    }
}
