package com.renaser.os.habits.infrastructure.adapter.in.rest.habito;

import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Autoservicio: catalogo SISTEMA activo + habitos PERSONAL propios del actor, sin filtrar por
 * dia — a diferencia de {@code GET /habit-tracks/today}, que solo trae los tracks de hoy.
 */
@RestController
@RequestMapping("/api/v1/habits")
public class MisHabitosController {

    private final ConsultarMisHabitosUseCase consultarUseCase;

    public MisHabitosController(ConsultarMisHabitosUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping
    public List<MiHabitoResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.consultar(actor).stream().map(MiHabitoResponse::from).toList();
    }
}
