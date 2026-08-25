package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.cuestionario.ObtenerCuestionarioUseCase.ObtenerCuestionarioQuery;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.cuestionario.LoadCuestionarioPort;
import com.renaser.os.onboarding.domain.model.cuestionario.OpcionPregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuestionarioServiceTest {

    @Mock
    private LoadCuestionarioPort loadCuestionarioPort;
    @Mock
    private ConsultarActorPort actorPort;

    private CuestionarioService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new CuestionarioService(loadCuestionarioPort, actorPort);
        actorId = UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("obtener(): arma secciones -> preguntas -> opciones en un solo arbol")
    void obtenerArmaElArbolCompleto() {
        when(actorPort.deActor(actorId)).thenReturn(Optional.of(new ActorOnboarding(actorId, false)));
        Seccion seccion = new Seccion((short) 1, "v90", "intro", "Introduccion", null, (short) 0, Instant.now());
        when(loadCuestionarioPort.seccionesDeFlujo("v90")).thenReturn(List.of(seccion));
        Pregunta pregunta = new Pregunta(10, (short) 1, "clave", "texto", TipoPreguntaOnboarding.SELECCION_UNICA,
                null, true, (short) 0, null, null, Instant.now());
        when(loadCuestionarioPort.preguntasDeSeccion((short) 1)).thenReturn(List.of(pregunta));
        OpcionPregunta opcion = new OpcionPregunta(10, (short) 0, "si", "Si");
        when(loadCuestionarioPort.opcionesDePregunta(10)).thenReturn(List.of(opcion));

        var resultado = service.obtener(new ObtenerCuestionarioQuery(actorId, "v90"));

        assertThat(resultado.flujo()).isEqualTo("v90");
        assertThat(resultado.secciones()).hasSize(1);
        assertThat(resultado.secciones().get(0).preguntas()).hasSize(1);
        assertThat(resultado.secciones().get(0).preguntas().get(0).opciones()).containsExactly(opcion);
    }

    @Test
    @DisplayName("obtener(): actor suspendido -> NotAuthorizedException")
    void obtenerConActorSuspendido() {
        when(actorPort.deActor(actorId)).thenReturn(Optional.of(new ActorOnboarding(actorId, true)));

        assertThatThrownBy(() -> service.obtener(new ObtenerCuestionarioQuery(actorId, "v90")))
                .isInstanceOf(NotAuthorizedException.class);
    }
}
