package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarObjetivosDeLaPersonaUseCase;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivosDelAprendiz;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Los objetivos de una persona para el líder (spec 056, FR-027). Autoriza primero ({@link AccesoDeLiderazgo}: líder, Administración o
 * Alquimista con la cuenta activa; y la persona dentro de sus formaciones) y lee después, por {@link ObjetivosDelAprendizFinder}
 * (de {@code rocks}, el mismo que lee el mentor, spec 057): ya resuelve la zona, el mes y la semana de la persona y suma su plazo y su fase.
 * Aquí no hay ninguna regla ni cálculo propio.
 */
@Service
public class ObjetivosDeLaPersonaService implements ConsultarObjetivosDeLaPersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelPrograma programa;
    private final ObjetivosDelAprendizFinder objetivos;
    private final Clock clock;

    ObjetivosDeLaPersonaService(AccesoDeLiderazgo acceso, PersonasDelPrograma programa, ObjetivosDelAprendizFinder objetivos, Clock clock) {
        this.acceso = acceso;
        this.programa = programa;
        this.objetivos = objetivos;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ObjetivosDelAprendiz objetivosDe(ConsultaObjetivosDeLaPersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        UserId persona = programa.mirarYDecirSiNoSigueSuPrograma(consulta.actorId(), UserId.of(consulta.personaId()), clock.now())
                .personaId();
        return objetivos.objetivosDe(persona);
    }
}
