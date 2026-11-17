package com.renaser.os.evidence.infrastructure.adapter.out.community;

import com.renaser.os.community.api.AprendicesDelAcompananteFinder;
import com.renaser.os.evidence.application.ports.out.acompanamiento.AcompananteDelAprendizPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Traduce la pregunta de la evidencia a la API pública de {@code community}, como hacen los tickets de mentoría. */
@Component
class AcompananteDelAprendizAdapter implements AcompananteDelAprendizPort {

    private final AprendicesDelAcompananteFinder acompanamiento;

    AcompananteDelAprendizAdapter(AprendicesDelAcompananteFinder acompanamiento) {
        this.acompanamiento = acompanamiento;
    }

    @Override
    public boolean acompanaA(UserId acompanante, UserId aprendiz, Instant instante) {
        return acompanamiento.aprendicesQueAcompana(acompanante, instante).contains(aprendiz);
    }
}
