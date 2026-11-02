package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.domain.model.pagos.CronogramaDePagos;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeConfirmacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * El cronograma de la persona listo para firmarse con los términos (spec 039). Una lectura que no decide nada: solo reúne lo que ya guardó la spec 032 (el estado de
 * ingreso con su moneda y su confirmación, y las cuotas) y dice si la confirmación sigue vigente frente a la huella de ahora.
 */
@Service
class CronogramaParaFirmarService implements CronogramaParaFirmarUseCase {

    private final LoadEstadoOnboardingPort cargarEstado;
    private final ArmadorDeVistaDePagos armador;

    CronogramaParaFirmarService(LoadEstadoOnboardingPort cargarEstado, ArmadorDeVistaDePagos armador) {
        this.cargarEstado = cargarEstado;
        this.armador = armador;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CronogramaParaFirmar> de(UserId persona) {
        return cargarEstado.deUsuario(persona).filter(estado -> estado.pago().esConCronograma()).map(estado -> {
            CronogramaDePagos cronograma = armador.cronogramaDe(persona, estado.pago());
            String huella = cronograma.huella();
            return new CronogramaParaFirmar(cronograma.moneda(), cronograma.cuotas(), huella,
                    estado.pago().confirmacion(huella) == EstadoDeConfirmacion.CONFIRMADA);
        });
    }
}
