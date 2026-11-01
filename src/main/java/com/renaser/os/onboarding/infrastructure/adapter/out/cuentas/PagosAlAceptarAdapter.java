package com.renaser.os.onboarding.infrastructure.adapter.out.cuentas;

import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CuotaPedida;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.PedidoDeCronograma;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.PagosAlAceptar;
import com.renaser.os.users.api.PlanDePagoPedido;
import org.springframework.stereotype.Component;

/**
 * Lo que {@code users} pide al aceptar una solicitud con el pago decidido (spec 032): delega en el mismo caso de uso con el que Administración arma el cronograma desde la ficha. Corre
 * dentro de la transacción de aceptar, así que un plan inválido (400) o cualquier otra falla deshace la aceptación entera.
 */
@Component
class PagosAlAceptarAdapter implements PagosAlAceptar {

    private final PagosDeLaPersonaUseCase pagos;

    PagosAlAceptarAdapter(PagosDeLaPersonaUseCase pagos) {
        this.pagos = pagos;
    }

    @Override
    public void anotar(UserId persona, UserId quien, PlanDePagoPedido plan) {
        pagos.asignar(quien, persona, new PedidoDeCronograma(plan.conCronograma(), plan.moneda(),
                plan.cuotas().stream().map(cuota -> cuota == null ? null : new CuotaPedida(cuota.venceEl(), cuota.monto())).toList()));
    }
}
