package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.GruposEnCursoPort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ConfirmacionDeLectura;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Responder a un mensaje desde el caso de uso (D-251): qué devuelve enviar, qué se rechaza y qué muestra el
 * listado cuando lo citado ya no está. Las reglas en sí se prueban en {@code CitaTest}; acá, que el servicio
 * las use en el orden correcto y que el resumen no filtre nada.
 */
@ExtendWith(MockitoExtension.class)
class RespuestasEnElChatTest {

    /** 03:00 UTC: en Lima todavía es el día anterior. Nada de esto depende del día, pero no se esconde. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-05T03:00:00Z"));

    @Mock private LoadConversacionPort loadConversacionPort;
    @Mock private EsParticipantePort esParticipantePort;
    @Mock private PertenenciaVigentePort pertenenciaVigentePort;
    @Mock private MarcarLeidoPort marcarLeidoPort;
    @Mock private SaveMensajePort saveMensajePort;
    @Mock private LoadMensajePort loadMensajePort;
    @Mock private PublicarMensajeFanoutPort publicarMensajeFanoutPort;
    @Mock private UserSummaryFinder userSummaryFinder;
    @Mock private AlmacenamientoPort almacenamientoPort;
    @Mock private IdGenerator idGenerator;

    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId luis = UserId.of(UUID.randomUUID());
    private final ConversacionId chat = ConversacionId.of(UUID.randomUUID());
    private final ConversacionId otroChat = ConversacionId.of(UUID.randomUUID());
    private final List<Object> publicados = new ArrayList<>();
    private MensajeService service;

    @BeforeEach
    void setUp() {
        service = new MensajeService(loadConversacionPort, esParticipantePort,
                new AccesoAChatsDeGrupo(pertenenciaVigentePort, mock(GruposEnCursoPort.class), userSummaryFinder),
                marcarLeidoPort, saveMensajePort, loadMensajePort, publicarMensajeFanoutPort, userSummaryFinder,
                almacenamientoPort, conversacion -> ConfirmacionDeLectura.sinDobleMarca(), CLOCK, idGenerator,
                publicados::add, new MetricasDelChatEnMemoria());
        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        lenient().when(userSummaryFinder.findById(ana)).thenReturn(Optional.of(persona(ana, "Ana Pérez")));
        lenient().when(userSummaryFinder.findById(luis)).thenReturn(Optional.of(persona(luis, "Luis Soto")));
        lenient().when(loadConversacionPort.porId(chat))
                .thenReturn(Optional.of(Conversacion.crearGlobal(chat, CLOCK.now())));
        lenient().when(esParticipantePort.esParticipante(chat, ana)).thenReturn(true);
        lenient().when(saveMensajePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(almacenamientoPort.firmarLectura(any(), any()))
                .thenAnswer(inv -> URI.create("https://firmada/" + inv.getArgument(0)));
    }

    // ── Enviar ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("responder a un mensaje ajeno: la respuesta de enviar ya trae el resumen, con su autor y sin «Tú»")
    void respuestaAUnMensajeAjeno() {
        Mensaje deLuis = texto(luis, chat, "¿Vamos mañana a correr?");
        when(loadMensajePort.porId(deLuis.id())).thenReturn(Optional.of(deLuis));

        MensajeEnriquecido enviado = service.enviar(respondiendo(deLuis.id()));

        assertThat(enviado.mensaje().respuestaAId()).isEqualTo(deLuis.id());
        var resumen = enviado.respuestaPreview();
        assertThat(resumen.id()).isEqualTo(deLuis.id());
        assertThat(resumen.nombreEmisor()).isEqualTo("Luis Soto");
        assertThat(resumen.tipo()).isEqualTo(TipoMensaje.TEXTO);
        assertThat(resumen.previewTexto()).isEqualTo("¿Vamos mañana a correr?");
        assertThat(resumen.deQuienMira()).isFalse();
        assertThat(enviado.citaNoDisponible()).isFalse();
        // Lo demás sigue como antes de D-251: quien envía ya lo tiene.
        assertThat(enviado.nombreEmisor()).isNull();
        assertThat(enviado.mediaUrl()).isNull();
    }

    @Test
    @DisplayName("responder a un mensaje propio: el resumen dice que es de quien mira")
    void respuestaAUnMensajePropio() {
        Mensaje deAna = texto(ana, chat, "Mañana a las 6");
        when(loadMensajePort.porId(deAna.id())).thenReturn(Optional.of(deAna));

        assertThat(service.enviar(respondiendo(deAna.id())).respuestaPreview().deQuienMira()).isTrue();
    }

    @Test
    @DisplayName("citar un mensaje de otra conversación o uno que no existe: 400 igual, nada guardado ni avisado")
    void citaDeOtraConversacionSeRechaza() {
        Mensaje deOtroChat = texto(luis, otroChat, "lo que se dijo en otro chat");
        MensajeId inexistente = MensajeId.of(UUID.randomUUID());
        when(loadMensajePort.porId(deOtroChat.id())).thenReturn(Optional.of(deOtroChat));
        when(loadMensajePort.porId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enviar(respondiendo(deOtroChat.id())))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje que quieres responder no está en esta conversación");
        assertThatThrownBy(() -> service.enviar(respondiendo(inexistente)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje que quieres responder no está en esta conversación");
        verify(saveMensajePort, never()).save(any());
        assertThat(publicados).isEmpty();
    }

    @Test
    @DisplayName("quien no participa recibe 403 ANTES de que se busque el citado: no se le cuenta nada de él")
    void sinParticipacionNiSeBuscaElCitado() {
        UserId ajena = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(ajena)).thenReturn(Optional.of(persona(ajena, "Kelin")));
        when(esParticipantePort.esParticipante(chat, ajena)).thenReturn(false);
        MensajeId cualquiera = MensajeId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.enviar(comando(ajena, cualquiera))).isInstanceOf(NotAuthorizedException.class);
        verify(loadMensajePort, never()).porId(any());
    }

    @Test
    @DisplayName("una cuenta suspendida no responde, aunque cite algo válido")
    void suspendidaNoResponde() {
        UserId suspendida = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(suspendida)).thenReturn(Optional.of(
                new UserSummary(suspendida, "Sus", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.enviar(comando(suspendida, MensajeId.of(UUID.randomUUID()))))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveMensajePort, never()).save(any());
    }

    @Test
    @DisplayName("sin replyToId (la app vieja) todo sigue igual: sin resumen, sin «eliminado», sin buscar nada")
    void laAppViejaSinCita() {
        MensajeEnriquecido enviado = service.enviar(comando(ana, null));

        assertThat(enviado.mensaje().esRespuesta()).isFalse();
        assertThat(enviado.respuestaPreview()).isNull();
        assertThat(enviado.citaNoDisponible()).isFalse();
        verify(loadMensajePort, never()).porId(any());
    }

    // ── Listar ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listado: si el citado se borró (con la cuenta de su autor) la respuesta dice «eliminado» y la página no se rompe")
    void citadoBorradoNoRompe() {
        Mensaje respuesta = respuestaGuardadaA(MensajeId.of(UUID.randomUUID()));
        when(loadMensajePort.pagina(chat, null, 31)).thenReturn(List.of(respuesta));
        when(loadMensajePort.porIds(any())).thenReturn(Map.of());
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(ana, persona(ana, "Ana Pérez")));

        MensajeEnriquecido enListado = service.listar(ana, chat, null, 30).mensajes().get(0);

        assertThat(enListado.respuestaPreview()).isNull();
        assertThat(enListado.citaNoDisponible()).isTrue();
        assertThat(enListado.nombreEmisor()).isEqualTo("Ana Pérez");
    }

    @Test
    @DisplayName("listado: un citado de OTRA conversación (fila mal escrita) o borrado por su autor no muestra su texto")
    void citadoAjenoOBorradoNoSeMuestra() {
        Mensaje deOtroChat = guardado(luis, otroChat, "secreto de otro chat", null);
        Mensaje borrado = guardado(luis, chat, "lo que Luis borró", CLOCK.now());
        Mensaje aOtroChat = respuestaGuardadaA(deOtroChat.id());
        Mensaje aBorrado = respuestaGuardadaA(borrado.id());
        when(loadMensajePort.pagina(chat, null, 31)).thenReturn(List.of(aOtroChat, aBorrado));
        when(loadMensajePort.porIds(any())).thenReturn(Map.of(deOtroChat.id(), deOtroChat, borrado.id(), borrado));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(ana, persona(ana, "Ana"), luis, persona(luis, "Luis")));

        List<MensajeEnriquecido> pagina = service.listar(ana, chat, null, 30).mensajes();

        assertThat(pagina).allSatisfy(m -> {
            assertThat(m.respuestaPreview()).isNull();
            assertThat(m.citaNoDisponible()).isTrue();
        });
    }

    @Test
    @DisplayName("listado: el sticker citado trae su MIME y su miniatura firmada; el audio, su duración; «Tú» según quien mira")
    void stickerYAudioCitados() {
        Mensaje sticker = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), chat, luis, TipoMensaje.IMAGEN,
                "Sticker Renaser: Muy bien", "chat", "chat/" + chat.value() + "/fotos/s", "image/webp", 38_608, null,
                null, CLOCK.now());
        Mensaje audio = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), chat, ana, TipoMensaje.AUDIO, null, "chat",
                "chat/" + chat.value() + "/audios/a", "audio/m4a", 9_000, (short) 12, null, CLOCK.now());
        Mensaje aSticker = respuestaGuardadaA(sticker.id());
        Mensaje aAudio = respuestaGuardadaA(audio.id());
        when(loadMensajePort.pagina(chat, null, 31)).thenReturn(List.of(aSticker, aAudio));
        when(loadMensajePort.porIds(any())).thenReturn(Map.of(sticker.id(), sticker, audio.id(), audio));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(ana, persona(ana, "Ana"), luis, persona(luis, "Luis")));

        List<MensajeEnriquecido> pagina = service.listar(ana, chat, null, 30).mensajes();

        var deSticker = pagina.get(0).respuestaPreview();
        assertThat(deSticker.tipo()).isEqualTo(TipoMensaje.IMAGEN);
        assertThat(deSticker.mediaMime()).isEqualTo("image/webp");
        assertThat(deSticker.previewTexto()).isEqualTo("Sticker Renaser: Muy bien");
        assertThat(deSticker.mediaUrl()).isEqualTo("https://firmada/chat/" + chat.value() + "/fotos/s");
        assertThat(deSticker.deQuienMira()).isFalse();
        var deAudio = pagina.get(1).respuestaPreview();
        assertThat(deAudio.tipo()).isEqualTo(TipoMensaje.AUDIO);
        assertThat(deAudio.previewTexto()).isNull();
        assertThat(deAudio.mediaDuracionS()).isEqualTo((short) 12);
        assertThat(deAudio.mediaUrl()).as("un audio no tiene miniatura").isNull();
        assertThat(deAudio.deQuienMira()).isTrue();
    }

    @Test
    @DisplayName("el extracto no parte un emoji por la mitad y queda en un renglón")
    void extractoSinEmojiPartido() {
        String largo = "a".repeat(79) + "💪🏽 sigue\ny sigue";

        String extracto = MensajesParaMostrar.extracto(largo);

        assertThat(extracto).isEqualTo("a".repeat(79) + "💪…");
        assertThat(Character.isHighSurrogate(extracto.charAt(extracto.length() - 2))).isFalse();
        assertThat(MensajesParaMostrar.extracto("una\n\nlínea   y otra")).isEqualTo("una línea y otra");
        assertThat(MensajesParaMostrar.extracto("   ")).isNull();
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private EnviarMensajeCommand respondiendo(MensajeId citado) {
        return comando(ana, citado);
    }

    private EnviarMensajeCommand comando(UserId quien, MensajeId citado) {
        return new EnviarMensajeCommand(quien, chat, TipoMensaje.TEXTO, "¡Sí, yo también!", null, null, null, null,
                null, citado, OrigenMedia.CLIENTE);
    }

    private Mensaje texto(UserId quien, ConversacionId donde, String texto) {
        return Mensaje.escribir(MensajeId.of(UUID.randomUUID()), donde, quien, TipoMensaje.TEXTO, texto, null, null,
                null, null, null, null, CLOCK.now());
    }

    private Mensaje guardado(UserId quien, ConversacionId donde, String texto, Instant eliminadoEn) {
        return Mensaje.rehydrate(MensajeId.of(UUID.randomUUID()), donde, quien, TipoMensaje.TEXTO, texto, null, null,
                null, null, null, false, eliminadoEn, null, CLOCK.now());
    }

    /** Una respuesta de Ana como la lee el adaptador: con el id que guardó, esté o no el citado. */
    private Mensaje respuestaGuardadaA(MensajeId citado) {
        return Mensaje.rehydrate(MensajeId.of(UUID.randomUUID()), chat, ana, TipoMensaje.TEXTO, "¡Sí!", null, null,
                null, null, null, false, null, citado, CLOCK.now());
    }

    private static UserSummary persona(UserId id, String nombre) {
        return new UserSummary(id, nombre, null, UserRole.TRAINEE, UserStatus.ACTIVE);
    }
}
