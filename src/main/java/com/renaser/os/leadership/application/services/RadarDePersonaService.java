package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.CodigoRenaserDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarRadarDePersonaUseCase;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * El Código Renaser de una persona para el líder (spec 012, US4): estado de Mi Radar, sus horas y todos sus registros,
 * de los días 1 a 7 y los de después.
 *
 * <p>Misma puerta que la ficha semanal ({@link SemanaDePersonaService}): {@code AccesoDeLiderazgo} (MENTOR_LEAD, ADMIN
 * o ALCHEMIST con la cuenta activa), grupo regular con mentor y persona que hoy es de ese grupo. Autoriza primero y lee
 * después: el camino que termina en 403 o en 404 no pide nada. El Radar lo lee {@code habits} por su contrato público.
 */
@Service
public class RadarDePersonaService implements ConsultarRadarDePersonaUseCase {

    /**
     * Doce franjas por día durante los días 1 a 7 son 84 registros, la primera semana entera: igual que la lectura del
     * mentor ({@code CodigoRenaserDelAlumnoController}). Después del día 8 hay hasta doce por día, así que sigue
     * habiendo página siguiente ({@code siguienteCursor}).
     */
    static final int TAMANO_PAGINA = 84;

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final CodigoRenaserDePersonaFinder radar;
    private final Clock clock;

    RadarDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, CodigoRenaserDePersonaFinder radar,
                          Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.radar = radar;
        this.clock = clock;
    }

    @Override
    public CodigoRenaserDePersona radarDe(ConsultaRadarDePersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        return radar.de(miembro.perfil().id(), consulta.cursor(), TAMANO_PAGINA);
    }
}
