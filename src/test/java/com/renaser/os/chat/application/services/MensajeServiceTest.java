package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensajeServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private EsParticipantePort esParticipantePort;
    @Mock
    private MarcarLeidoPort marcarLeidoPort;
    @Mock
    private SaveMensajePort saveMensajePort;
    @Mock
    private LoadMensajePort loadMensajePort;
    @Mock
    private PublicarMensajeFanoutPort publicarMensajeFanoutPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private MensajeService service;

    private final UserId activo = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());
    private final ConversacionId conversacionId = ConversacionId.newId();

    @BeforeEach
    void setUp() {
        service = new MensajeService(loadConversacionPort, esParticipantePort, marcarLeidoPort, saveMensajePort,
                loadMensajePort, publicarMensajeFanoutPort, userSummaryFinder, CLOCK);
        lenient().when(userSummaryFinder.findById(activo)).thenReturn(
                Optional.of(new UserSummary(activo, "Activo", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendido)).thenReturn(
                Optional.of(new UserSummary(suspendido, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));
        lenient().when(loadConversacionPort.porId(conversacionId))
                .thenReturn(Optional.of(Conversacion.crearGlobal(CLOCK.now())));
        lenient().when(saveMensajePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private EnviarMensajeCommand comandoDeTexto(UserId actorId) {
        return new EnviarMensajeCommand(actorId, conversacionId, TipoMensaje.TEXTO, "hola", null, null, null, null,
                null, null);
    }

    @Test
    void enviarRechazaAQuienNoEsParticipante() {
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(false);

        assertThatThrownBy(() -> service.enviar(comandoDeTexto(activo))).isInstanceOf(NotAuthorizedException.class);

        verify(saveMensajePort, never()).save(any());
    }

    @Test
    void enviarRechazaAUnActorSuspendido() {
        assertThatThrownBy(() -> service.enviar(comandoDeTexto(suspendido)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveMensajePort, never()).save(any());
    }

    @Test
    void enviarGuardaElMensajeYMarcaLeidoAlEmisor() {
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(true);

        Mensaje enviado = service.enviar(comandoDeTexto(activo));

        assertThat(enviado.texto()).isEqualTo("hola");
        verify(saveMensajePort).save(any());
        verify(marcarLeidoPort).marcarLeido(conversacionId, activo, CLOCK.now());
    }

    @Test
    void enviarPublicaElFanoutDespuesDeGuardar() {
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(true);

        service.enviar(comandoDeTexto(activo));

        // Sin transaccion Spring activa en el test (unit puro): el fanout se dispara
        // sincrono, tras el save — nunca antes.
        verify(publicarMensajeFanoutPort).publicar(any());
    }

    @Test
    void listarRechazaAQuienNoEsParticipante() {
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(false);

        assertThatThrownBy(() -> service.listar(activo, conversacionId, null, 30))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void listarRechazaAUnActorSuspendido() {
        assertThatThrownBy(() -> service.listar(suspendido, conversacionId, null, 30))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void listarIndicaHayMasCuandoLaPaginaExcedeElLimite() {
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(true);
        Mensaje m1 = Mensaje.escribir(conversacionId, activo, TipoMensaje.TEXTO, "1", null, null, null, null, null,
                null, CLOCK.now());
        Mensaje m2 = Mensaje.escribir(conversacionId, activo, TipoMensaje.TEXTO, "2", null, null, null, null, null,
                null, CLOCK.now());
        when(loadMensajePort.pagina(conversacionId, null, 2)).thenReturn(java.util.List.of(m1, m2));

        var pagina = service.listar(activo, conversacionId, null, 1);

        assertThat(pagina.mensajes()).hasSize(1);
        assertThat(pagina.hayMas()).isTrue();
        assertThat(pagina.siguienteCursor()).isEqualTo(m1.creadoEn());
    }
}
