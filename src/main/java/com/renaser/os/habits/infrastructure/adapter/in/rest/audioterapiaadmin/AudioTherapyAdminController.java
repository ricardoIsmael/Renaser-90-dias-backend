package com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapiaadmin;

import com.renaser.os.habits.application.ports.in.audioterapiaadmin.ActualizarDuracionAudioterapiaUseCase;
import com.renaser.os.habits.application.ports.in.audioterapiaadmin.ActualizarDuracionAudioterapiaUseCase.ActualizarDuracionAudioterapiaCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel admin: cuántos días dura cada audioterapia antes de pasar a la siguiente. Solo ADMIN/ALCHEMIST. */
@RestController
@RequestMapping("/api/v1/admin/audio-therapies")
public class AudioTherapyAdminController {

    private final ActualizarDuracionAudioterapiaUseCase actualizarUseCase;

    public AudioTherapyAdminController(ActualizarDuracionAudioterapiaUseCase actualizarUseCase) {
        this.actualizarUseCase = actualizarUseCase;
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PatchMapping("/{week}")
    public AudioTherapyResponse actualizarDuracion(@ActorAutenticado UserId actor, @PathVariable int week,
                                                     @RequestBody @Valid UpdateAudioTherapyDurationRequest request) {
        var audioterapia = actualizarUseCase.actualizar(new ActualizarDuracionAudioterapiaCommand(actor, week,
                request.durationDays()));
        return AudioTherapyResponse.from(audioterapia);
    }
}
