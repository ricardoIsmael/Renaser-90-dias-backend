package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que hace el Admin con una caja (D-219, spec §9). Todas devuelven el detalle actualizado. Si el estado no
 * corresponde, {@link IllegalStateException} (409); si al enviar falta algo,
 * {@link com.renaser.os.onboarding.domain.model.caja.CajaIncompletaException} (409 con qué falta).
 */
public interface OperarCajaUseCase {

    DetalleDeCaja aprobar(UserId actorId, UserId aprendizId);

    DetalleDeCaja armar(UserId actorId, UserId aprendizId);

    DetalleDeCaja marcarContenido(UserId actorId, UserId aprendizId, List<String> marcados);

    DetalleDeCaja enviar(UserId actorId, UserId aprendizId, PedidoDeEnvio pedido);

    /** @param previa «Ya se envió antes»: sin datos y sin avisarle al aprendiz (spec §8) */
    DetalleDeCaja marcarEntregada(UserId actorId, UserId aprendizId, boolean previa);

    DetalleDeCaja reportarProblema(UserId actorId, UserId aprendizId, String motivo, String nota);

    DetalleDeCaja reenviar(UserId actorId, UserId aprendizId);

    /** Lo que escribió el Admin al enviarla; lo valida el dominio después de saber si puede (403 antes que 400). */
    record PedidoDeEnvio(String medio, String courier, String codigo, BigDecimal costo) {
    }
}
