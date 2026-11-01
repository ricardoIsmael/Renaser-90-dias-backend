package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarDiagnosticoDePersonaUseCase;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * El diagnóstico de una persona para el líder (spec 026, 026-11). Autoriza primero y lee después, igual que {@link HuecosDePersonaService}: MENTOR_LEAD, ADMIN o
 * ALCHEMIST con la cuenta activa, y una persona que hoy es de un grupo regular con mentor, o cualquier aprendiz que se muestre (sin grupo). El aislamiento de
 * formaciones ya lo hacen {@code PersonasDelGrupo} y {@code PersonasDelPrograma}.
 *
 * <p>A diferencia de la semana y los huecos, <b>no deja registro en {@code consultas_de_ficha}</b>: esa tabla guarda «el lunes de la semana que miró» y el
 * resumen diario que le llega al mentor se arma con ella; el diagnóstico no es de una semana. Queda anotado como decisión de esta spec, para que el dueño la
 * vea.
 */
@Service
public class DiagnosticoDePersonaService implements ConsultarDiagnosticoDePersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final PersonasDelPrograma programa;
    private final DiagnosticoDeLaPersonaFinder diagnostico;
    private final Clock clock;

    DiagnosticoDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, PersonasDelPrograma programa,
                                DiagnosticoDeLaPersonaFinder diagnostico, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.programa = programa;
        this.diagnostico = diagnostico;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public LecturaDelDiagnostico diagnosticoDe(ConsultaDiagnosticoDePersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.actorId(), consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        return diagnostico.lecturaDe(Mirada.deUnGrupo(miembro, grupo).personaId());
    }

    @Override
    @Transactional(readOnly = true)
    public LecturaDelDiagnostico diagnosticoDeUnaPersona(ConsultaDiagnosticoDeUnaPersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Mirada mirada = programa.mirar(consulta.actorId(), UserId.of(consulta.personaId()), clock.now());
        return diagnostico.lecturaDe(mirada.personaId());
    }
}
