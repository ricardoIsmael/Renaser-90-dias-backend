package com.renaser.os.chat.infrastructure.adapter.out.community;

import com.renaser.os.chat.application.ports.out.participante.AcompanamientoDelGrupoPort;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Traduce la pregunta del chat a la API pública de {@code community}, igual que {@code PertenenciaVigenteAdapter}. */
@Component
class AcompanamientoDelGrupoAdapter implements AcompanamientoDelGrupoPort {

    private final AcompanamientoFinder acompanamientoFinder;
    private final Clock clock;

    AcompanamientoDelGrupoAdapter(AcompanamientoFinder acompanamientoFinder, Clock clock) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.clock = clock;
    }

    @Override
    public List<UserId> aprendicesVigentes(UUID celulaId) {
        return acompanamientoFinder.aprendicesVigentes(celulaId, clock.now());
    }

    @Override
    public List<UserId> acompanantesVigentes(UUID celulaId) {
        return acompanamientoFinder.acompanantesVigentes(celulaId, clock.now());
    }
}
