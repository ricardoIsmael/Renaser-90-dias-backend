package com.renaser.os.rag.infrastructure.adapter.out.contratos;

import com.renaser.os.phasecontracts.api.ContratosDeFaseDelParticipanteFinder;
import com.renaser.os.phasecontracts.api.ContratosDeFaseDelParticipanteFinder.FaseDelContrato;
import com.renaser.os.rag.application.ports.out.contratos.ConsultarContratosDeFasePort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link ConsultarContratosDeFasePort} delegando en {@code phasecontracts.api} (D-41):
 * {@code rag} nunca consulta la tabla de contratos. Es una traduccion y nada mas.
 */
@Component
class ConsultarContratosDeFaseAdapter implements ConsultarContratosDeFasePort {

    private final ContratosDeFaseDelParticipanteFinder finder;

    ConsultarContratosDeFaseAdapter(ContratosDeFaseDelParticipanteFinder finder) {
        this.finder = finder;
    }

    @Override
    public ContratosDeFase delAprendiz(UserId aprendizId) {
        ContratosDeFaseDelParticipanteFinder.ContratosDeFase contratos = finder.delParticipante(aprendizId);
        return new ContratosDeFase(contratos.firmados().stream().map(ConsultarContratosDeFaseAdapter::aFase).toList(),
                contratos.pendienteHoy() == null ? null : aFase(contratos.pendienteHoy()));
    }

    private static Fase aFase(FaseDelContrato fase) {
        return new Fase(fase.numeroFase(), fase.etiqueta());
    }
}
