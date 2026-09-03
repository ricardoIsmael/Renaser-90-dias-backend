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
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensajeServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, escribir() ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

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
    @Mock
    private IdGenerator idGenerator;

    private MensajeService service;

    private final UserId activo = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());
    private final ConversacionId conversacionId = ConversacionId.of(UUID.randomUUID());

    /** Los mensajes de fixture llevan cada uno su id: la factoria ya no lo sortea. */
    private static MensajeId nuevoMensajeId() {
        return MensajeId.of(UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        service = new MensajeService(loadConversacionPort, esParticipantePort, marcarLeidoPort, saveMensajePort,
                loadMensajePort, publicarMensajeFanoutPort, userSummaryFinder, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(userSummaryFinder.findById(activo)).thenReturn(
                Optional.of(new UserSummary(activo, "Activo", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendido)).thenReturn(
                Optional.of(new UserSummary(suspendido, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));
        lenient().when(loadConversacionPort.porId(conversacionId))
                .thenReturn(Optional.of(Conversacion.crearGlobal(conversacionId, CLOCK.now())));
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

        assertThat(enviado.id()).isEqualTo(MensajeId.of(ID_GENERADO));
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
        Mensaje m1 = Mensaje.escribir(nuevoMensajeId(), conversacionId, activo, TipoMensaje.TEXTO, "1", null, null,
                null, null, null, null, CLOCK.now());
        Mensaje m2 = Mensaje.escribir(nuevoMensajeId(), conversacionId, activo, TipoMensaje.TEXTO, "2", null, null,
                null, null, null, null, CLOCK.now());
        when(loadMensajePort.pagina(conversacionId, null, 2)).thenReturn(java.util.List.of(m1, m2));

        var pagina = service.listar(activo, conversacionId, null, 1);

        assertThat(pagina.mensajes()).hasSize(1);
        assertThat(pagina.hayMas()).isTrue();
        assertThat(pagina.siguienteCursor()).isEqualTo(m1.creadoEn());
    }

    @Test
    void listarResuelveNombreYAvatarDelEmisorEnUnaSolaConsultaALaPagina() {
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(true);
        Mensaje m1 = Mensaje.escribir(nuevoMensajeId(), conversacionId, activo, TipoMensaje.TEXTO, "1", null, null,
                null, null, null, null, CLOCK.now());
        Mensaje m2 = Mensaje.escribir(nuevoMensajeId(), conversacionId, activo, TipoMensaje.TEXTO, "2", null, null,
                null, null, null, null, CLOCK.now());
        when(loadMensajePort.pagina(conversacionId, null, 31)).thenReturn(List.of(m1, m2));
        when(userSummaryFinder.findByIds(any())).thenReturn(
                Map.of(activo, new UserSummary(activo, "Activo", "http://avatar", UserRole.TRAINEE,
                        UserStatus.ACTIVE)));

        var pagina = service.listar(activo, conversacionId, null, 30);

        assertThat(pagina.mensajes()).hasSize(2);
        assertThat(pagina.mensajes().get(0).nombreEmisor()).isEqualTo("Activo");
        assertThat(pagina.mensajes().get(0).avatarEmisor()).isEqualTo("http://avatar");
        // Nunca N+1: UNA sola llamada en lote a users.api para toda la pagina, sin
        // importar cuantos mensajes tenga.
        verify(userSummaryFinder, times(1)).findByIds(any());
    }

    @Test
    void listarResuelvePreviewDeRespuestaTruncadoYNombreDelEmisorOriginal() {
        UserId otroActivo = UserId.of(UUID.randomUUID());
        lenient().when(userSummaryFinder.findById(otroActivo)).thenReturn(
                Optional.of(new UserSummary(otroActivo, "Otro", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(true);

        String textoLargo = "x".repeat(200);
        Mensaje original = Mensaje.escribir(nuevoMensajeId(), conversacionId, otroActivo, TipoMensaje.TEXTO,
                textoLargo, null, null, null, null, null, null, CLOCK.now());
        Mensaje respuesta = Mensaje.escribir(nuevoMensajeId(), conversacionId, activo, TipoMensaje.TEXTO,
                "respondo", null, null, null, null, null, original.id(), CLOCK.now());

        when(loadMensajePort.pagina(conversacionId, null, 31)).thenReturn(List.of(respuesta));
        when(loadMensajePort.porIds(List.of(original.id()))).thenReturn(Map.of(original.id(), original));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                activo, new UserSummary(activo, "Activo", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                otroActivo, new UserSummary(otroActivo, "Otro", null, UserRole.TRAINEE, UserStatus.ACTIVE)));

        var pagina = service.listar(activo, conversacionId, null, 30);

        var preview = pagina.mensajes().get(0).respuestaPreview();
        assertThat(preview).isNotNull();
        assertThat(preview.nombreEmisor()).isEqualTo("Otro");
        assertThat(preview.previewTexto()).hasSize(81); // 80 caracteres + el "…" de corte
        assertThat(preview.previewTexto()).endsWith("…");
        // Nunca N+1: una sola consulta en lote para resolver los mensajes originales
        // citados por toda la pagina, sin importar cuantas respuestas tenga.
        verify(loadMensajePort, times(1)).porIds(any());
    }

    @Test
    void listarNoConsultaMensajesOriginalesSiNingunoResponde() {
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(true);
        Mensaje m1 = Mensaje.escribir(nuevoMensajeId(), conversacionId, activo, TipoMensaje.TEXTO, "1", null, null,
                null, null, null, null, CLOCK.now());
        when(loadMensajePort.pagina(conversacionId, null, 31)).thenReturn(List.of(m1));

        var pagina = service.listar(activo, conversacionId, null, 30);

        assertThat(pagina.mensajes().get(0).respuestaPreview()).isNull();
        verify(loadMensajePort, never()).porIds(any());
    }
}
