package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rocks/master")
public class RocaMaestraController {

    private final ConsultarRocasMaestrasUseCase consultarUseCase;
    private final DefinirRocaMaestraUseCase definirUseCase;

    public RocaMaestraController(ConsultarRocasMaestrasUseCase consultarUseCase,
                                  DefinirRocaMaestraUseCase definirUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.definirUseCase = definirUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping
    public List<RocaMaestraResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.misRocasMaestras(actor).stream()
                .map(RocaMaestraResponse::from)
                .toList();
    }

    /**
     * Define el objetivo de 90 dias de ese eje, o corrige el que ya estaba.
     *
     * <p>{@code PUT} y no {@code POST} porque es idempotente por diseno: por eje hay una sola
     * Roca Maestra ({@code UNIQUE (participante_id, eje)}), asi que mandar dos veces lo mismo
     * deja lo mismo. El eje va en la ruta y el dueno sale de la sesion; ninguno de los dos se
     * acepta desde el cuerpo.
     */
    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "solo sobre las rocas del propio aprendiz")
    @PutMapping("/{eje}")
    public RocaMaestraResponse definir(@ActorAutenticado UserId actor, @PathVariable EjeObjetivo eje,
                                        @Valid @RequestBody DefinirRocaMaestraRequest request) {
        return RocaMaestraResponse.from(definirUseCase.definir(new DefinirRocaMaestraCommand(
                actor, eje, request.objetivo(), request.meta(), request.avance(), request.unidad())));
    }
}
