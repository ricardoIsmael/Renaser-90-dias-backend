package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.habits.api.RutaDeCarreraFinder;
import com.renaser.os.habits.api.RutaDeCarreraFinder.LectorDeLaRuta;
import com.renaser.os.habits.api.RutaDeCarreraFinder.RutaLeida;
import com.renaser.os.leadership.application.ports.in.ConsultarRutaDeCarreraDePersonaUseCase;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * La ruta de una carrera de una persona para el líder, Administración y Alquimista (spec 042, FR-041). Autoriza primero y lee
 * después, igual que {@link DiagnosticoDePersonaService}: MENTOR_LEAD, ADMIN o ALCHEMIST con la cuenta activa, y una persona que hoy
 * es de un grupo regular con mentor, o cualquier aprendiz que se muestre (sin grupo). El aislamiento de formaciones lo hacen
 * {@code PersonasDelGrupo} y {@code PersonasDelPrograma}.
 *
 * <p>Deja constancia de quién la abrió: {@code habits} publica {@code RutaDeCarreraConsultadaEvent} en esta transacción y este mismo
 * módulo lo anota en {@code consultas_de_ficha} con {@code tipo = RUTA_DE_CARRERA}.
 */
@Service
public class RutaDeCarreraDePersonaService implements ConsultarRutaDeCarreraDePersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final PersonasDelPrograma programa;
    private final RutaDeCarreraFinder rutas;
    private final Clock clock;

    RutaDeCarreraDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, PersonasDelPrograma programa,
                                  RutaDeCarreraFinder rutas, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.programa = programa;
        this.rutas = rutas;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<RutaLeida> rutaDe(ConsultaRutaDePersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.actorId(), consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        return leer(consulta.actorId(), Mirada.deUnGrupo(miembro, grupo), consulta.carreraId());
    }

    @Override
    @Transactional
    public Optional<RutaLeida> rutaDeUnaPersona(ConsultaRutaDeUnaPersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Mirada mirada = programa.mirar(consulta.actorId(), UserId.of(consulta.personaId()), clock.now());
        return leer(consulta.actorId(), mirada, consulta.carreraId());
    }

    private Optional<RutaLeida> leer(UserId actorId, Mirada mirada, UUID carreraId) {
        return rutas.rutaDe(mirada.personaId(), carreraId, new LectorDeLaRuta(actorId, mirada.grupoId(), mirada.mentorId()));
    }
}
