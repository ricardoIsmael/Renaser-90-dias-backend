package com.renaser.os.chat.infrastructure.adapter.out.community;

import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort;
import com.renaser.os.community.api.BienvenidaDeGrupo;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Traduce al API pública de {@code community}, igual que {@code AcompanamientoDelGrupoAdapter}. */
@Component
class BienvenidaEnGrupoAdapter implements BienvenidaEnGrupoPort {

    private final BienvenidaDeGrupo bienvenidaDeGrupo;
    private final Clock clock;

    BienvenidaEnGrupoAdapter(BienvenidaDeGrupo bienvenidaDeGrupo, Clock clock) {
        this.bienvenidaDeGrupo = bienvenidaDeGrupo;
        this.clock = clock;
    }

    @Override
    public Optional<Pendientes> pendientes(UUID celulaId) {
        return bienvenidaDeGrupo.pendientes(celulaId, clock.now()).map(p -> new Pendientes(p.mentorId(),
                p.aprendices().stream().map(a -> new Pendiente(a.asignacionId(), a.aprendizId(), a.desde()))
                        .toList()));
    }

    @Override
    public boolean marcarDada(UUID asignacionId) {
        return bienvenidaDeGrupo.marcarDada(asignacionId, clock.now());
    }
}
