package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.MiCajaUseCase;
import com.renaser.os.onboarding.application.ports.in.caja.MiCajaUseCase.PedidoDeDestino;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.MiCajaResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.MiDestino;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yo → «Tu Caja Renaser» (D-219): en qué va, a dónde va y «Ya la recibí». Siempre la caja de quien tiene la
 * sesión: la ruta no lleva id.
 */
@RestController
@RequestMapping("/api/v1/me/caja")
public class MiCajaController {

    private static final String PROPIA = "solo la caja propia: el id sale de la sesión (D-219)";

    private final MiCajaUseCase miCaja;

    public MiCajaController(MiCajaUseCase miCaja) {
        this.miCaja = miCaja;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = PROPIA)
    @GetMapping
    public MiCajaResponse ver(@ActorAutenticado UserId actorId) {
        return MiCajaResponse.from(miCaja.ver(actorId));
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = PROPIA + "; antes de que salga")
    @PutMapping("/destino")
    public MiCajaResponse cambiarDestino(@ActorAutenticado UserId actorId, @RequestBody MiDestino request) {
        return MiCajaResponse.from(miCaja.cambiarDestino(actorId, new PedidoDeDestino(request.otraDireccion(),
                request.otroCelular(), request.quienRecibe(), request.referencias(), request.provincia())));
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = PROPIA + "; solo si está en camino")
    @PostMapping("/recibida")
    public MiCajaResponse recibida(@ActorAutenticado UserId actorId) {
        return MiCajaResponse.from(miCaja.confirmarRecibida(actorId));
    }
}
