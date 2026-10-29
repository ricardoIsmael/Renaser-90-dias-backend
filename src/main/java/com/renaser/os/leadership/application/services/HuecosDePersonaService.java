package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarHuecosDePersonaUseCase;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Los huecos de horario de una persona para el líder (spec 015). Autoriza primero y lee después, igual que
 * {@link SemanaDePersonaService}: el camino que termina en 403 o en 404 no pide nada. Quien puede verlo es el mismo que
 * ve su semana: MENTOR_LEAD, ADMIN o ALCHEMIST con la cuenta activa, de un grupo regular con mentor y una persona que
 * hoy es de ese grupo ({@code AccesoDeLiderazgo}, {@code PersonasDelGrupo}).
 *
 * <p>No registra la consulta como la ficha de la semana (spec 005, FR-016): la spec 015 no lo pide, y lo que se lee no
 * son evidencias ni textos de la persona sino tramos de tiempo sin etiqueta.
 */
@Service
public class HuecosDePersonaService implements ConsultarHuecosDePersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final HuecosDeHorarioFinder huecos;
    private final Clock clock;

    HuecosDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, HuecosDeHorarioFinder huecos,
                           Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.huecos = huecos;
        this.clock = clock;
    }

    @Override
    public HuecosDeLaSemana huecosDe(ConsultaHuecosDePersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        return huecos.semanaDe(miembro.perfil().id(), consulta.semana());
    }
}
