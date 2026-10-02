package com.renaser.os.users.infrastructure.adapter.in.rest.emergencia;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase.PedirAyudaCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * «Tuve una emergencia» del aprendiz (D-244). Pedir no cambia el día: le llega a soporte. Solo aprendices
 * activos (el guard del caso de uso deja afuera al staff que el interceptor deja pasar).
 */
@RestController
@RequestMapping("/api/v1/me/emergency-request")
public class EmergenciaController {

    private final PedirAyudaPorEmergenciaUseCase pedirAyuda;

    public EmergenciaController(PedirAyudaPorEmergenciaUseCase pedirAyuda) {
        this.pedirAyuda = pedirAyuda;
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "solo el pedido del propio aprendiz")
    @GetMapping
    public MiEmergenciaResponse consultar(@ActorAutenticado UserId actor) {
        return MiEmergenciaResponse.from(pedirAyuda.consultar(actor));
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "solo el pedido del propio aprendiz")
    @PostMapping
    public ResponseEntity<EmergenciaResponse> pedir(@ActorAutenticado UserId actor,
                                                    @RequestBody @Valid PedirEmergenciaRequest request) {
        var solicitud = pedirAyuda.pedir(new PedirAyudaCommand(actor, request.queOcurrio(), request.diaPedido()));
        return ResponseEntity.status(HttpStatus.CREATED).body(EmergenciaResponse.from(solicitud));
    }
}
