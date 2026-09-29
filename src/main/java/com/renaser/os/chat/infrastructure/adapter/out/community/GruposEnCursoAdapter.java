package com.renaser.os.chat.infrastructure.adapter.out.community;

import com.renaser.os.chat.application.ports.out.participante.GruposEnCursoPort;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Traduce la pregunta del chat a la API pública de {@code community}, como {@code PertenenciaVigenteAdapter}. */
@Component
class GruposEnCursoAdapter implements GruposEnCursoPort {

    private final AcompanamientoFinder acompanamientoFinder;
    private final Clock clock;

    GruposEnCursoAdapter(AcompanamientoFinder acompanamientoFinder, Clock clock) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.clock = clock;
    }

    @Override
    public boolean estaEnCurso(UUID celulaId) {
        return acompanamientoFinder.grupoOperativo(celulaId, clock.now());
    }

    @Override
    public List<UUID> gruposEnCurso() {
        return acompanamientoFinder.gruposOperativos(clock.now()).stream()
                .map(AcompanamientoFinder.GrupoConAprendices::grupoId)
                .toList();
    }
}
