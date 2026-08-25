package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.cuestionario.ObtenerCuestionarioUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.cuestionario.LoadCuestionarioPort;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.List;

@Service
public class CuestionarioService implements ObtenerCuestionarioUseCase {

    private final LoadCuestionarioPort loadCuestionarioPort;
    private final ConsultarActorPort actorPort;

    public CuestionarioService(LoadCuestionarioPort loadCuestionarioPort, ConsultarActorPort actorPort) {
        this.loadCuestionarioPort = loadCuestionarioPort;
        this.actorPort = actorPort;
    }

    @Override
    public Cuestionario obtener(ObtenerCuestionarioQuery query) {
        requireActorActivo(query.actorId());
        List<Seccion> secciones = loadCuestionarioPort.seccionesDeFlujo(query.flujo());
        List<SeccionConPreguntas> resultado = secciones.stream().map(this::conPreguntas).toList();
        return new Cuestionario(query.flujo(), resultado);
    }

    private SeccionConPreguntas conPreguntas(Seccion seccion) {
        List<PreguntaConOpciones> preguntas = loadCuestionarioPort.preguntasDeSeccion(seccion.id()).stream()
                .map(this::conOpciones)
                .toList();
        return new SeccionConPreguntas(seccion, preguntas);
    }

    private PreguntaConOpciones conOpciones(Pregunta pregunta) {
        return new PreguntaConOpciones(pregunta, loadCuestionarioPort.opcionesDePregunta(pregunta.id()));
    }

    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
