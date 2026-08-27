package com.renaser.os.habits.infrastructure.adapter.in.rest.renombre;

import com.renaser.os.habits.application.ports.in.renombre.QuitarRenombreHabitoUseCase;
import com.renaser.os.habits.application.ports.in.renombre.QuitarRenombreHabitoUseCase.QuitarRenombreHabitoCommand;
import com.renaser.os.habits.application.ports.in.renombre.RenombrarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.renombre.RenombrarHabitoUseCase.RenombrarHabitoCommand;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Ruta literal del contrato viejo (D-36): {@code PUT/DELETE /api/v1/habits/{habitId}/rename}. */
@RestController
@RequestMapping("/api/v1/habits/{habitId}/rename")
public class HabitRenameController {

    private final RenombrarHabitoUseCase renombrarUseCase;
    private final QuitarRenombreHabitoUseCase quitarUseCase;

    public HabitRenameController(RenombrarHabitoUseCase renombrarUseCase, QuitarRenombreHabitoUseCase quitarUseCase) {
        this.renombrarUseCase = renombrarUseCase;
        this.quitarUseCase = quitarUseCase;
    }

    @PutMapping
    public HabitRenameResponse renombrar(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
                                          @RequestBody @Valid RenameHabitRequest request) {
        var renombre = renombrarUseCase.renombrar(new RenombrarHabitoCommand(actor, HabitoId.of(habitId),
                request.customTitle(), request.reason()));
        return HabitRenameResponse.from(renombre);
    }

    @DeleteMapping
    public void quitar(@ActorAutenticado UserId actor, @PathVariable UUID habitId) {
        quitarUseCase.quitar(new QuitarRenombreHabitoCommand(actor, HabitoId.of(habitId)));
    }
}
