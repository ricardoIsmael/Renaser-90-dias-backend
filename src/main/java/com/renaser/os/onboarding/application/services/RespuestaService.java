package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.cuestionario.LoadCuestionarioPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class RespuestaService implements GuardarRespuestaUseCase {

    private final LoadCuestionarioPort loadCuestionarioPort;
    private final LoadRespuestaPort loadRespuestaPort;
    private final SaveRespuestaPort saveRespuestaPort;
    private final ConsultarActorPort actorPort;
    private final Clock clock;

    public RespuestaService(LoadCuestionarioPort loadCuestionarioPort, LoadRespuestaPort loadRespuestaPort,
                             SaveRespuestaPort saveRespuestaPort, ConsultarActorPort actorPort, Clock clock) {
        this.loadCuestionarioPort = loadCuestionarioPort;
        this.loadRespuestaPort = loadRespuestaPort;
        this.saveRespuestaPort = saveRespuestaPort;
        this.actorPort = actorPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Respuesta guardar(GuardarRespuestaCommand command) {
        requireActorActivo(command.usuarioId());
        Pregunta pregunta = loadCuestionarioPort.porId(command.preguntaId())
                .orElseThrow(() -> new NoSuchElementException("Pregunta no encontrada: " + command.preguntaId()));

        Optional<Respuesta> existente = loadRespuestaPort.porUsuarioYPregunta(command.usuarioId(),
                command.preguntaId());

        Respuesta respuesta = existente
                .map(r -> r.actualizarValor(pregunta.tipo(), command.valorTexto(), command.valorNumero(),
                        command.valorBooleano(), command.valorEscala(), command.valorJson(), command.mediaId(),
                        clock))
                .orElseGet(() -> Respuesta.crear(pregunta.tipo(), command.usuarioId(), command.preguntaId(),
                        command.valorTexto(), command.valorNumero(), command.valorBooleano(), command.valorEscala(),
                        command.valorJson(), command.mediaId(), clock));

        return saveRespuestaPort.guardar(respuesta);
    }

    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
