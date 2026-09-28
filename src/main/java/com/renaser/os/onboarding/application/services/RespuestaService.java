package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase;
import com.renaser.os.onboarding.application.ports.in.respuesta.ObtenerRespuestasUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.cuestionario.LoadCuestionarioPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.caja.CajaRenaser;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RespuestaService implements GuardarRespuestaUseCase, ObtenerRespuestasUseCase {

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
        requireNoEsDeLaCaja(pregunta);

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

    @Override
    public List<SeccionConRespuestas> obtener(ObtenerRespuestasQuery query) {
        requireActorActivo(query.actorId());
        Map<Integer, Respuesta> respuestasPorPregunta = loadRespuestaPort.todasDeUsuario(query.actorId()).stream()
                .collect(Collectors.toMap(Respuesta::preguntaId, Function.identity()));

        return loadCuestionarioPort.seccionesDeFlujo(query.flujo()).stream()
                .map(seccion -> conRespuestasDeSeccion(seccion, respuestasPorPregunta))
                .filter(s -> !s.preguntas().isEmpty())
                .toList();
    }

    private SeccionConRespuestas conRespuestasDeSeccion(Seccion seccion, Map<Integer, Respuesta> respuestasPorPregunta) {
        List<PreguntaConRespuesta> preguntas = loadCuestionarioPort.preguntasDeSeccion(seccion.id()).stream()
                .filter(pregunta -> respuestasPorPregunta.containsKey(pregunta.id()))
                .map(pregunta -> new PreguntaConRespuesta(pregunta, respuestasPorPregunta.get(pregunta.id())))
                .toList();
        return new SeccionConRespuestas(seccion, preguntas);
    }

    /**
     * Las preguntas de la Caja Renaser (flujo {@code caja_renaser}, V82, D-219) NO se responden por acá. Este
     * endpoint acepta cualquier pregunta del propio actor, y sin este cierre un aprendiz se marcaba solo el
     * checklist de su caja ({@code caja_contenido}), que es lo que el Admin exige completo antes de enviarla.
     * El destino lo escribe su propio caso de uso ({@code MiCajaService}), que además mira el estado de la
     * caja; el contenido, solo el Admin.
     */
    private void requireNoEsDeLaCaja(Pregunta pregunta) {
        boolean deLaCaja = loadCuestionarioPort.seccionesDeFlujo(CajaRenaser.FLUJO).stream()
                .anyMatch(seccion -> seccion.id() == pregunta.seccionId());
        if (deLaCaja) {
            throw new NotAuthorizedException("Las preguntas de la Caja Renaser no se responden por acá");
        }
    }

    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
