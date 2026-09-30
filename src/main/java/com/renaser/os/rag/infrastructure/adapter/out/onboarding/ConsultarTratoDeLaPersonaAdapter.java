package com.renaser.os.rag.infrastructure.adapter.out.onboarding;

import com.renaser.os.onboarding.api.TratoDeLaPersonaFinder;
import com.renaser.os.rag.application.ports.out.participante.ConsultarTratoDeLaPersonaPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/** Traduce {@code onboarding.api.TratoDeLaPersonaFinder} al puerto propio de {@code rag} (D-41, E-457). */
@Component
class ConsultarTratoDeLaPersonaAdapter implements ConsultarTratoDeLaPersonaPort {

    private final TratoDeLaPersonaFinder finder;

    ConsultarTratoDeLaPersonaAdapter(TratoDeLaPersonaFinder finder) {
        this.finder = finder;
    }

    @Override
    public TratoDeLaPersona de(UserId participanteId) {
        return switch (finder.de(participanteId)) {
            case MASCULINO -> TratoDeLaPersona.MASCULINO;
            case FEMENINO -> TratoDeLaPersona.FEMENINO;
            case NEUTRO -> TratoDeLaPersona.NEUTRO;
        };
    }
}
