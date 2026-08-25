package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase.GuardarRespuestaCommand;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.cuestionario.LoadCuestionarioPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
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
}
