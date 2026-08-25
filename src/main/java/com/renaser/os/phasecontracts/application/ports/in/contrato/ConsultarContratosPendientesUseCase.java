package com.renaser.os.phasecontracts.application.ports.in.contrato;

import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;

public interface ConsultarContratosPendientesUseCase {

    ContratoPendiente consultarPendiente(UserId participanteId);

    /** fase/etiqueta son null cuando pendiente=false — proyeccion de salida del caso de uso. */
    record ContratoPendiente(boolean pendiente, FasePrograma fase, String etiqueta) {

        public static ContratoPendiente ninguno() {
            return new ContratoPendiente(false, null, null);
        }

        public static ContratoPendiente de(FasePrograma fase) {
            return new ContratoPendiente(true, fase, fase.etiqueta());
        }
    }
}
