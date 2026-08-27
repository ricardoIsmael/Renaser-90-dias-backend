package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase.GuardarRespuestaCommand;
import com.renaser.os.onboarding.application.ports.in.respuesta.ObtenerRespuestasUseCase.ObtenerRespuestasQuery;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.cuestionario.LoadCuestionarioPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.FixedClock;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RespuestaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadCuestionarioPort loadCuestionarioPort;
    @Mock
    private LoadRespuestaPort loadRespuestaPort;
    @Mock
    private SaveRespuestaPort saveRespuestaPort;
    @Mock
    private ConsultarActorPort actorPort;

    private RespuestaService service;
    private UserId usuarioId;

    @BeforeEach
    void setUp() {
        service = new RespuestaService(loadCuestionarioPort, loadRespuestaPort, saveRespuestaPort, actorPort, CLOCK);
        usuarioId = UserId.of(UUID.randomUUID());
    }

    private void actorActivo() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
    }

    private Pregunta preguntaTexto() {
        return new Pregunta(1, (short) 1, "clave", "texto", TipoPreguntaOnboarding.TEXTO, null, false, (short) 0,
                null, null, Instant.now());
    }

    @Test
    @DisplayName("guardar(): pregunta inexistente -> NoSuchElementException (404), nunca guarda")
    void guardarConPreguntaInexistente() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.empty());

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null, null);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(NoSuchElementException.class);
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("guardar(): actor suspendido -> NotAuthorizedException, nunca consulta la pregunta")
    void guardarConActorSuspendido() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, true)));

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null, null);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(NotAuthorizedException.class);
        verify(loadCuestionarioPort, never()).porId(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("guardar(): tipo de valor incoherente con la pregunta -> IllegalArgumentException, propagada del dominio")
    void guardarConValorIncoherenteConElTipo() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.of(preguntaTexto()));

        // pregunta es TEXTO, pero se manda valorNumero -- incoherente
        var comando = new GuardarRespuestaCommand(usuarioId, 1, null, java.math.BigDecimal.TEN, null, null, null,
                null);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(IllegalArgumentException.class);
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("guardar(): primera vez -> crea (SaveRespuestaPort.guardar con id null)")
    void guardarPrimeraVezCrea() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.of(preguntaTexto()));
        when(loadRespuestaPort.porUsuarioYPregunta(usuarioId, 1)).thenReturn(Optional.empty());
        when(saveRespuestaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null, null);

        Respuesta resultado = service.guardar(comando);

        assertThat(resultado.id()).isNull();
        assertThat(resultado.valorTexto()).isEqualTo("hola");
    }

    @Test
    @DisplayName("guardar(): segunda vez sobre la misma pregunta -> actualiza la existente (mismo id), no crea otra")
    void guardarSegundaVezActualiza() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.of(preguntaTexto()));
        Respuesta existente = Respuesta.rehydrate(77L, usuarioId, 1, "primero", null, null, null, null, null, null,
                CLOCK.now(), CLOCK.now());
        when(loadRespuestaPort.porUsuarioYPregunta(usuarioId, 1)).thenReturn(Optional.of(existente));
        when(saveRespuestaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "segundo", null, null, null, null, null);

        Respuesta resultado = service.guardar(comando);

        assertThat(resultado.id()).isEqualTo(77L);
        assertThat(resultado.valorTexto()).isEqualTo("segundo");
    }

    // ── obtener() — GET /onboarding/answers ─────────────────────────────────────

    private Seccion seccion(short id, String claveSeccion) {
        return new Seccion(id, "diseno_destino", claveSeccion, "Titulo " + claveSeccion, null, (short) 0,
                Instant.now());
    }

    private Pregunta pregunta(int id, short seccionId, String clave) {
        return new Pregunta(id, seccionId, clave, "texto", TipoPreguntaOnboarding.AREA_TEXTO, null, false,
                (short) 0, null, null, Instant.now());
    }

    @Test
    @DisplayName("obtener(): actor suspendido -> NotAuthorizedException, nunca consulta el catalogo ni las respuestas")
    void obtenerConActorSuspendido() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, true)));

        var query = new ObtenerRespuestasQuery(usuarioId, "diseno_destino");

        assertThatThrownBy(() -> service.obtener(query)).isInstanceOf(NotAuthorizedException.class);
        verify(loadRespuestaPort, never()).todasDeUsuario(any());
        verify(loadCuestionarioPort, never()).seccionesDeFlujo(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("obtener(): agrupa las respuestas ya guardadas por seccion, en el orden del cuestionario")
    void obtenerAgrupaPorSeccion() {
        actorActivo();
        Seccion seccion1 = seccion((short) 1, "destino_90d");
        Pregunta pregunta1 = pregunta(10, (short) 1, "master_goal_90d");
        Pregunta pregunta2 = pregunta(11, (short) 1, "otra_pregunta");
        Respuesta respuesta1 = Respuesta.rehydrate(1L, usuarioId, 10, "mi meta", null, null, null, null, null,
                CLOCK.now(), CLOCK.now(), CLOCK.now());

        when(loadRespuestaPort.todasDeUsuario(usuarioId)).thenReturn(List.of(respuesta1));
        when(loadCuestionarioPort.seccionesDeFlujo("diseno_destino")).thenReturn(List.of(seccion1));
        when(loadCuestionarioPort.preguntasDeSeccion((short) 1)).thenReturn(List.of(pregunta1, pregunta2));

        var resultado = service.obtener(new ObtenerRespuestasQuery(usuarioId, "diseno_destino"));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).seccion()).isEqualTo(seccion1);
        // pregunta2 nunca fue respondida -> no aparece, ninguna placeholder
        assertThat(resultado.get(0).preguntas()).hasSize(1);
        assertThat(resultado.get(0).preguntas().get(0).pregunta()).isEqualTo(pregunta1);
        assertThat(resultado.get(0).preguntas().get(0).respuesta()).isEqualTo(respuesta1);
    }

    @Test
    @DisplayName("obtener(): una seccion sin ninguna pregunta respondida no aparece en el resultado")
    void obtenerOmiteSeccionesSinRespuestas() {
        actorActivo();
        Seccion seccionSinRespuestas = seccion((short) 2, "vacia");
        Pregunta preguntaSinResponder = pregunta(20, (short) 2, "sin_responder");

        when(loadRespuestaPort.todasDeUsuario(usuarioId)).thenReturn(List.of());
        when(loadCuestionarioPort.seccionesDeFlujo("diseno_destino")).thenReturn(List.of(seccionSinRespuestas));
        when(loadCuestionarioPort.preguntasDeSeccion((short) 2)).thenReturn(List.of(preguntaSinResponder));

        var resultado = service.obtener(new ObtenerRespuestasQuery(usuarioId, "diseno_destino"));

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("obtener(): solo trae las respuestas DEL ACTOR que llama — nunca lee otro usuario")
    void obtenerSoloLeeLasRespuestasDelActorQueLlama() {
        actorActivo();
        when(loadRespuestaPort.todasDeUsuario(usuarioId)).thenReturn(List.of());
        when(loadCuestionarioPort.seccionesDeFlujo("diseno_destino")).thenReturn(List.of());

        service.obtener(new ObtenerRespuestasQuery(usuarioId, "diseno_destino"));

        verify(loadRespuestaPort).todasDeUsuario(usuarioId);
        verify(loadRespuestaPort, never()).todasDeUsuario(org.mockito.ArgumentMatchers.argThat(id -> !id.equals(usuarioId)));
    }
}
