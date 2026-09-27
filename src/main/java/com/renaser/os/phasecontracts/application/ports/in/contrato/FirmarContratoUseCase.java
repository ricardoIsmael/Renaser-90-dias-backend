package com.renaser.os.phasecontracts.application.ports.in.contrato;

import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface FirmarContratoUseCase {

    ContratoFase firmar(FirmarContratoCommand command);

    /**
     * {@code fase}: el pacto que la persona cree que esta firmando, o {@code null} si el pedido no lo
     * dice (el unico pedido que existia antes de D-216).
     *
     * <p><b>No es mass-assignment (§5.3.3).</b> La fase no ELIGE que se firma: lo sigue decidiendo el
     * servidor ({@code FasePrograma.faseAFirmar}, D-193), y una fase que no es la que toca se rechaza.
     * Sirve para que repetir el pedido no firme OTRO pacto (TRN-21 del e2e): con la fase, un doble
     * envio devuelve el mismo pacto; sin ella, con dos o mas pendientes, el segundo envio firmaba el
     * siguiente sin su firma dibujada. La ruta de la firma sigue sin viajar desde el cliente.
     */
    record FirmarContratoCommand(@NotNull UserId participanteId, FasePrograma fase) {

        public FirmarContratoCommand {
            // Los 2 componentes, en orden: el validador compara contra la firma del constructor canonico.
            SelfValidating.validateConstructorArgs(FirmarContratoCommand.class, participanteId, fase);
        }

        /** El pedido sin fase de siempre. */
        public FirmarContratoCommand(UserId participanteId) {
            this(participanteId, null);
        }
    }
}
