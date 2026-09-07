package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamensual;

import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarRocasMensualesUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase.DefinirRocaMensualCommand;
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
@RequestMapping("/api/v1/rocks/monthly")
public class RocaMensualController {

    private final ConsultarRocasMensualesUseCase consultarUseCase;
    private final DefinirRocaMensualUseCase definirUseCase;

    public RocaMensualController(ConsultarRocasMensualesUseCase consultarUseCase,
                                  DefinirRocaMensualUseCase definirUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.definirUseCase = definirUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping
    public List<RocaMensualResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.misRocasMensuales(actor).stream()
                .map(RocaMensualResponse::from)
                .toList();
    }

    /**
     * Define el tramo de ese mes en ese eje, o corrige el que ya estaba.
     *
     * <p>{@code PUT} y no {@code POST} porque es idempotente por diseno: por (eje, mes) hay un solo
     * objetivo mensual ({@code UNIQUE (roca_maestra_id, numero_mes)}), asi que mandar dos veces lo
     * mismo deja lo mismo. El eje y el mes van en la ruta y el dueno sale de la sesion; ninguno de
     * los tres se acepta desde el cuerpo.
     */
    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "solo sobre las rocas del propio aprendiz")
    @PutMapping("/{eje}/{numeroMes}")
    public RocaMensualResponse definir(@ActorAutenticado UserId actor, @PathVariable EjeObjetivo eje,
                                        @PathVariable int numeroMes,
                                        @Valid @RequestBody DefinirRocaMensualRequest request) {
        return RocaMensualResponse.from(definirUseCase.definir(new DefinirRocaMensualCommand(
                actor, eje, numeroMes, request.titulo(), request.meta(), request.avance(), request.unidad())));
    }
}
