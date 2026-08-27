package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rocks/master")
public class RocaMaestraController {

    private final ConsultarRocasMaestrasUseCase consultarUseCase;

    public RocaMaestraController(ConsultarRocasMaestrasUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping
    public List<RocaMaestraResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.misRocasMaestras(actor).stream()
                .map(RocaMaestraResponse::from)
                .toList();
    }
}
