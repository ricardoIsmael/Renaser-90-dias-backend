package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarDiagnosticoDePersonaUseCase;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LectorDelDiagnostico;
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
 * <p><b>Deja constancia de quién lo abrió</b> (decisión del dueño): {@code onboarding} publica {@code DiagnosticoConsultadoEvent} en la misma transacción
 * de la lectura y {@code leadership} lo anota en {@code consultas_de_ficha} con {@code tipo = DIAGNOSTICO} y sin semana. No entra al resumen de las 20:00 del
 * mentor, que es de la ficha de una semana. Por eso estos métodos ya no son de solo lectura.
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
    @Transactional
    public LecturaDelDiagnostico diagnosticoDe(ConsultaDiagnosticoDePersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.actorId(), consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        return leer(consulta.actorId(), Mirada.deUnGrupo(miembro, grupo));
    }

    @Override
    @Transactional
    public LecturaDelDiagnostico diagnosticoDeUnaPersona(ConsultaDiagnosticoDeUnaPersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Mirada mirada = programa.mirar(consulta.actorId(), UserId.of(consulta.personaId()), clock.now());
        return leer(consulta.actorId(), mirada);
    }

    private LecturaDelDiagnostico leer(UserId actorId, Mirada mirada) {
        return diagnostico.lecturaDe(mirada.personaId(), new LectorDelDiagnostico(actorId, mirada.grupoId(), mirada.mentorId()));
    }
}
