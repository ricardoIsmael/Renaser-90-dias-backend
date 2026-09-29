package com.renaser.os.chat.infrastructure.adapter.out.community;

import com.renaser.os.chat.application.ports.out.conversacion.MentorDeLosGruposPort;
import com.renaser.os.community.api.MentorVigenteFinder;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Traduce la pregunta del chat a la API pública de {@code community} (D-221), como {@code AcompanamientoDelGrupoAdapter}. */
@Component
class MentorDeLosGruposAdapter implements MentorDeLosGruposPort {

    private final MentorVigenteFinder mentorVigenteFinder;
    private final Clock clock;

    MentorDeLosGruposAdapter(MentorVigenteFinder mentorVigenteFinder, Clock clock) {
        this.mentorVigenteFinder = mentorVigenteFinder;
        this.clock = clock;
    }

    @Override
    public Map<UUID, GrupoConSuMentor> deLosGrupos(Collection<UUID> celulaIds) {
        if (celulaIds == null || celulaIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, GrupoConSuMentor> porGrupo = new LinkedHashMap<>();
        for (var grupo : mentorVigenteFinder.deLosGrupos(celulaIds, clock.now())) {
            porGrupo.put(grupo.grupoId(), new GrupoConSuMentor(grupo.nombre(), grupo.mentorId()));
        }
        return porGrupo;
    }
}
