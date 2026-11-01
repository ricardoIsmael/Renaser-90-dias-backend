package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.CodigoRenaserDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarRadarDePersonaUseCase;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.ClavesDeFuncionalidad;
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
    private final PersonasDelPrograma programa;
    private final CodigoRenaserDePersonaFinder radar;
    private final Interruptores interruptores;
    private final Clock clock;

    RadarDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, PersonasDelPrograma programa,
                          CodigoRenaserDePersonaFinder radar, Interruptores interruptores, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.programa = programa;
        this.radar = radar;
        this.interruptores = interruptores;
        this.clock = clock;
    }

    @Override
    public CodigoRenaserDePersona radarDe(ConsultaRadarDePersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        exigirQueSePuedaVer(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.actorId(), consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        return radar.de(miembro.perfil().id(), consulta.cursor(), TAMANO_PAGINA);
    }

    /** Lo mismo sin pasar por un grupo (spec 021): la persona es cualquier aprendiz que se muestre. */
    @Override
    public CodigoRenaserDePersona radarDeUnaPersona(ConsultaRadarDeUnaPersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        exigirQueSePuedaVer(consulta.actorId());
        Mirada mirada = programa.mirar(consulta.actorId(), UserId.of(consulta.personaId()), clock.now());
        return radar.de(mirada.personaId(), consulta.cursor(), TAMANO_PAGINA);
    }

    /**
     * Spec 034 (D-299): Administración decide si el líder ve el Código Renaser. Después de autorizar (el 403 de siempre gana) y
     * antes de leer. Apagado, el líder recibe «no disponible» (404); Administración y Alquimista no están entre los roles que
     * la funcionalidad apaga, así que siguen viéndolo.
     */
    private void exigirQueSePuedaVer(UserId actor) {
        interruptores.exigirPrendidaPara(ClavesDeFuncionalidad.RADAR_EN_FICHAS, actor);
    }
}
