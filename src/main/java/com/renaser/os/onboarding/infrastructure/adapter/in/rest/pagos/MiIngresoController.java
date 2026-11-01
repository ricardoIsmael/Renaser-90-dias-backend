package com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.MiIngresoUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.MiIngresoUseCase.ConfirmacionPedida;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos.PagosDtos.ConfirmRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos.PagosDtos.MiIngresoResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yo → «Mi ingreso» (spec 032): los pasos de la entrada de la persona y su cronograma de pagos. Siempre lo de quien tiene la sesión: la ruta no lleva id, así que no hay forma de
 * pedir lo de otra persona.
 */
@RestController
@RequestMapping("/api/v1/me/ingreso")
@Funcionalidad("pagos")
public class MiIngresoController {

    private static final String PROPIO = "solo lo propio: el id sale de la sesión (spec 032)";

    private final MiIngresoUseCase miIngreso;

    public MiIngresoController(MiIngresoUseCase miIngreso) {
        this.miIngreso = miIngreso;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = PROPIO)
    @GetMapping
    public MiIngresoResponse ver(@ActorAutenticado UserId actor) {
        return MiIngresoResponse.from(miIngreso.ver(actor));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = PROPIO + "; la confirmación nunca bloquea nada")
    @PostMapping("/cronograma/confirmacion")
    public MiIngresoResponse confirmar(@ActorAutenticado UserId actor, @RequestBody ConfirmRequest request) {
        return MiIngresoResponse.from(miIngreso.confirmar(actor, new ConfirmacionPedida(request.name(), Boolean.TRUE.equals(request.reviewed()))));
    }
}
