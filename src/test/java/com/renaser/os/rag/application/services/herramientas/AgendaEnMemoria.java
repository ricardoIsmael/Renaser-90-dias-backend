package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.agenda.AgendaSemanalPort;
import com.renaser.os.rag.domain.model.agenda.AgendaSemanal;
import com.renaser.os.shared.domain.UserId;

import java.util.HashMap;
import java.util.Map;

/** {@link AgendaSemanalPort} en memoria para las pruebas de las herramientas. */
class AgendaEnMemoria implements AgendaSemanalPort {

    private final Map<UserId, AgendaSemanal> agendas = new HashMap<>();

    @Override
    public AgendaSemanal de(UserId participanteId) {
        return agendas.getOrDefault(participanteId, AgendaSemanal.vacia());
    }

    @Override
    public void guardar(UserId participanteId, AgendaSemanal agenda) {
        agendas.put(participanteId, agenda);
    }
}
