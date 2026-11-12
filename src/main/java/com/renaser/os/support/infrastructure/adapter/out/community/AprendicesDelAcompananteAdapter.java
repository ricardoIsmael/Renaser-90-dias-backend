package com.renaser.os.support.infrastructure.adapter.out.community;

import com.renaser.os.community.api.AprendicesDelAcompananteFinder;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.out.ticketmentor.AprendicesDelAcompanantePort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/** Traduce la pregunta de los tickets a la API pública de {@code community}, como hace el chat con {@code AcompanamientoDelGrupoAdapter}. */
@Component
class AprendicesDelAcompananteAdapter implements AprendicesDelAcompanantePort {

    private final AprendicesDelAcompananteFinder acompanamiento;

    AprendicesDelAcompananteAdapter(AprendicesDelAcompananteFinder acompanamiento) {
        this.acompanamiento = acompanamiento;
    }

    @Override
    public Set<UserId> aprendicesQueAcompana(UserId acompanante, Instant instante) {
        return acompanamiento.aprendicesQueAcompana(acompanante, instante);
    }
}
