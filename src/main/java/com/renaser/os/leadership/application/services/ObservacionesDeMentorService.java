package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.leadership.application.ports.in.ConsultarObservacionesUseCase;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarObservacionPort;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Contenido;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Envio;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.SobrePersona;
import com.renaser.os.leadership.domain.model.observacion.TipoObservacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registrar y leer las observaciones del líder sobre un mentor (SDD 002, RL-15..RL-18; D-241).
 * Append-only: no hay un caso de uso que las edite ni las borre.
 *
 * <p>Spec 005 (R-7): «Avisar a su mentor» desde la ficha de una persona es esta misma observación, con la persona y la
 * semana. La persona tiene que estar HOY en un grupo que ese mentor acompaña; si no, 400.
 */
@Service
public class ObservacionesDeMentorService implements RegistrarObservacionUseCase, ConsultarObservacionesUseCase {

    static final int TAMANO_PAGINA = 20;

    private final AccesoDeLiderazgo acceso;
    private final GuardarObservacionPort guardar;
    private final LeerObservacionesPort leer;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final UserSummaryFinder usuarios;
    private final AcompanamientoFinder acompanamiento;

    ObservacionesDeMentorService(AccesoDeLiderazgo acceso, GuardarObservacionPort guardar,
                                 LeerObservacionesPort leer, IdGenerator idGenerator, Clock clock,
                                 UserSummaryFinder usuarios, AcompanamientoFinder acompanamiento) {
        this.acceso = acceso;
        this.guardar = guardar;
        this.leer = leer;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.usuarios = usuarios;
        this.acompanamiento = acompanamiento;
    }

    @Override
    @Transactional
    public ObservacionDeMentor registrar(RegistrarObservacionCommand comando) {
        acceso.requireLiderazgoActivo(comando.actorId());
        acceso.requireMentor(comando.mentorId());
        Optional<ObservacionDeMentor> yaRegistrada = leer.porAutorYClave(comando.actorId(), comando.claveOperacion());
        if (yaRegistrada.isPresent()) {
            return mismaOperacion(yaRegistrada.get(), comando);
        }
        Contenido contenido = new Contenido(comando.mentorId(), comando.actorId(), TipoObservacion.de(comando.tipo()),
                comando.texto(), new Envio(comando.enviadaPorChat(), comando.mensajeId()), comando.claveOperacion(),
                sobre(comando));
        return guardar.insertar(ObservacionDeMentor.registrar(idGenerator.newId(), contenido, clock));
    }

    /** La persona de la que trata, si trata de alguien: tiene que ser hoy de un grupo de ESE mentor. */
    private SobrePersona sobre(RegistrarObservacionCommand comando) {
        if (comando.personaId() == null) {
            return null;
        }
        Instant ahora = clock.now();
        boolean esDeSuGrupo = acompanamiento.gruposConMentorVigente(ahora).stream()
                .filter(grupo -> comando.mentorId().equals(grupo.mentorId()))
                .anyMatch(grupo -> acompanamiento.aprendicesVigentes(grupo.grupoId(), ahora)
                        .contains(comando.personaId()));
        if (!esDeSuGrupo) {
            throw new IllegalArgumentException("Esa persona no esta en un grupo que ese mentor acompana");
        }
        return new SobrePersona(comando.personaId(), comando.semanaDesde());
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDeObservaciones observaciones(UserId actorId, UserId mentorId, Instant antesDe) {
        acceso.requireLiderazgoActivo(actorId);
        acceso.requireMentor(mentorId);
        List<ObservacionDeMentor> filas = leer.deMentor(mentorId, antesDe, TAMANO_PAGINA + 1);
        boolean hayMas = filas.size() > TAMANO_PAGINA;
        List<ObservacionDeMentor> pagina = hayMas ? filas.subList(0, TAMANO_PAGINA) : filas;
        return new PaginaDeObservaciones(pagina, hayMas ? pagina.getLast().creadoEn() : null, nombresDe(pagina));
    }

    /** Una sola lectura de cuentas para toda la página, nunca una por observación. */
    private Map<UserId, String> nombresDe(List<ObservacionDeMentor> pagina) {
        List<UserId> personas = pagina.stream().map(ObservacionDeMentor::personaId).filter(Objects::nonNull)
                .distinct().toList();
        Map<UserId, String> nombres = new HashMap<>();
        if (!personas.isEmpty()) {
            usuarios.findByIds(personas).forEach((id, perfil) -> nombres.put(id, perfil.fullName()));
        }
        return nombres;
    }

    /** Un reintento devuelve lo ya creado; la misma clave para OTRO mentor es un error del cliente. */
    private static ObservacionDeMentor mismaOperacion(ObservacionDeMentor existente, RegistrarObservacionCommand comando) {
        if (!existente.mentorId().equals(comando.mentorId())
                || !Objects.equals(existente.personaId(), comando.personaId())) {
            throw new IllegalArgumentException("Esa clave de operacion ya se uso para otra observacion");
        }
        return existente;
    }
}
