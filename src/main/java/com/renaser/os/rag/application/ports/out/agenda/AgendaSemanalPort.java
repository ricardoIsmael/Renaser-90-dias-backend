package com.renaser.os.rag.application.ports.out.agenda;

import com.renaser.os.rag.domain.model.agenda.AgendaSemanal;
import com.renaser.os.shared.domain.UserId;

/**
 * Las horas ocupadas que la persona le conto al acompanante y confirmo guardar (D-161). Guardar
 * reemplaza la agenda entera: la que se pasa es la version completa.
 */
public interface AgendaSemanalPort {

    AgendaSemanal de(UserId participanteId);

    void guardar(UserId participanteId, AgendaSemanal agenda);
}
