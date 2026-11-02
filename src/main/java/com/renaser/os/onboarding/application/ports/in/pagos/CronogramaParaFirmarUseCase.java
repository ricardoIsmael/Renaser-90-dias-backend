package com.renaser.os.onboarding.application.ports.in.pagos;

import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

/**
 * El cronograma de pagos de una persona tal como se le ofrece para firmarlo junto con los términos (spec 039): el mismo que ve en «Mi ingreso» (spec 032), con su
 * huella. Lo usan la lectura de los términos (para dibujarlo) y la aceptación (para comprobar que lo que firma es lo vigente).
 */
public interface CronogramaParaFirmarUseCase {

    /** Vacío si la persona no tiene un cronograma anotado (sin anotar o «pagó completo»). */
    Optional<CronogramaParaFirmar> de(UserId persona);

    /**
     * @param huella       la de {@code CronogramaDePagos#huella()}: moneda, fechas y montos, sin el estado de pago
     * @param yaConfirmado si la persona ya lo confirmó con esta misma huella (por ejemplo en «Mi ingreso»): no hay nada que pedirle
     */
    record CronogramaParaFirmar(Moneda moneda, List<Cuota> cuotas, String huella, boolean yaConfirmado) {

        public CronogramaParaFirmar {
            cuotas = List.copyOf(cuotas);
        }
    }
}
