package com.renaser.os.points.infrastructure.adapter.in.rest.home;

import com.renaser.os.points.application.ports.in.home.ConsultarResumenHomeUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final ConsultarResumenHomeUseCase consultarResumenHomeUseCase;

    public HomeController(ConsultarResumenHomeUseCase consultarResumenHomeUseCase) {
        this.consultarResumenHomeUseCase = consultarResumenHomeUseCase;
    }

    @GetMapping
    public ResumenHomeResponse consultar(@ActorAutenticado UserId actor) {
        return ResumenHomeResponse.from(consultarResumenHomeUseCase.consultar(actor));
    }
}
