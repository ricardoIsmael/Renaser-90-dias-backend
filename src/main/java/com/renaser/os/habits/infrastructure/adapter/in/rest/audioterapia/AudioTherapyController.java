package com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapia;

import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Autoservicio, exclusivo de TRAINEE -- mismo criterio que {@code EspirituController}. */
@RestController
@RequestMapping("/api/v1/audio-therapy")
public class AudioTherapyController {

    private final ConsultarAudioterapiaSemanalUseCase consultarUseCase;

    public AudioTherapyController(ConsultarAudioterapiaSemanalUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping("/status")
    public AudioTherapyStatusResponse status(@ActorAutenticado UserId actor) {
        return AudioTherapyStatusResponse.from(consultarUseCase.consultar(actor));
    }
}
