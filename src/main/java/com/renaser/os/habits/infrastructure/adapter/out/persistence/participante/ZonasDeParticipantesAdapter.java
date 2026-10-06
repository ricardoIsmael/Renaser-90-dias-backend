package com.renaser.os.habits.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.habits.application.ports.out.participante.ConsultarZonasDeParticipantesPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Delega en el contrato publico de {@code users} (D-41): {@code participantes_programa.timezone} no se lee de frente
 * desde {@code habits}. {@link ProgramasActivadosFinder#deVarios} resuelve la pagina entera en una consulta.
 */
@Component
class ZonasDeParticipantesAdapter implements ConsultarZonasDeParticipantesPort {

    private final ProgramasActivadosFinder programasActivados;

    ZonasDeParticipantesAdapter(ProgramasActivadosFinder programasActivados) {
        this.programasActivados = programasActivados;
    }

    @Override
    public Map<UserId, ZoneId> deProgramasActivados(Collection<UserId> participantes) {
        Map<UserId, ZoneId> zonas = new HashMap<>();
        if (participantes.isEmpty()) {
            return zonas;
        }
        programasActivados.deVarios(participantes).forEach((id, programa) -> zonas.put(id, programa.zona()));
        return zonas;
    }
}
