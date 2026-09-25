package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.aviso.DejarAvisoHabitoEnChatUseCase.AvisoHabitoEnChatCommand;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.domain.model.aviso.AvisosEnChat;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * El aviso de habito que el acompanante deja en el chat, sobre puertos mockeados. El guardado de
 * mensajes es un fake con memoria ({@link #guardados}) para que la prueba de duplicados sea real:
 * la segunda entrega tiene que encontrar lo que guardo la primera, no un {@code thenReturn(true)}.
 *
 * <p><b>Reloj a las 02:00 UTC</b> (regla 02 §3): en Lima son las 21:00 del dia ANTERIOR. Un
 * {@code {hora}} dicho en la zona del servidor diria 02:29 en vez de 21:29.
 */
@ExtendWith(MockitoExtension.class)
class AvisoHabitoEnChatServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-23T02:00:00Z"));
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** El plazo real es 02:29:00Z; habits calculo a las 01:59:30Z → faltaban 29m30s → "30". */
    private static final Instant CALCULADO_EN = Instant.parse("2026-09-23T01:59:30Z");

    @Mock
    private LoadMensajeRenasiaPort loadMensajePort;
    @Mock
    private SaveMensajeRenasiaPort saveMensajePort;
    @Mock
    private LoadConversacionRenasiaPort loadConversacionPort;
    @Mock
    private SaveConversacionRenasiaPort saveConversacionPort;
    @Mock
    private ConsultarAgendaHabitosPort agendaPort;

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final UUID claveEvento = UUID.randomUUID();
    private final List<MensajeRenasia> guardados = new ArrayList<>();

    @BeforeEach
    void mensajesConMemoria() {
        lenient().when(loadMensajePort.existe(any())).thenAnswer(inv -> guardados.stream()
                .anyMatch(mensaje -> mensaje.id().equals(inv.getArgument(0, MensajeRenasiaId.class))));
        lenient().when(saveMensajePort.save(any())).thenAnswer(inv -> {
            guardados.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(loadMensajePort.pagina(aprendiz, AgenteConversacional.COMPANION, null, 1)).thenReturn(List.of());
        lenient().when(agendaPort.zonaDe(aprendiz)).thenReturn(LIMA);
        lenient().when(loadConversacionPort.porUsuarioId(aprendiz))
                .thenReturn(Optional.of(ConversacionRenasia.iniciar(aprendiz, CLOCK.now())));
    }

    private static AvisosEnChat prendidos() {
        return new AvisosEnChat(true, Set.of("INICIO", "POR_VENCER"), Map.of(
                "INICIO", "Tu habito {habito} empieza a las {hora}. Hay {puntos} puntos en juego.",
                "POR_VENCER", "Se te vence {habito} a las {hora}. Si lo haces ahora sumas {puntos} puntos."));
    }

    private AvisoHabitoEnChatService servicio(AvisosEnChat avisos, FixedClock clock) {
        return new AvisoHabitoEnChatService(avisos, loadMensajePort, saveMensajePort, loadConversacionPort,
                saveConversacionPort, agendaPort, clock);
    }

    private AvisoHabitoEnChatCommand aviso(String tipo, Instant calculadoEn, long minutos) {
        return new AvisoHabitoEnChatCommand(aprendiz, "Meditar", tipo, minutos, 10, claveEvento, calculadoEn);
    }

    @Test
    void conElInterruptorApagadoNoTocaNingunPuerto() {
        boolean escrito = servicio(AvisosEnChat.apagados(), CLOCK).dejarEnElChat(aviso("POR_VENCER", CALCULADO_EN, 30));

        assertThat(escrito).isFalse();
        verifyNoInteractions(loadMensajePort, saveMensajePort, loadConversacionPort, saveConversacionPort, agendaPort);
    }

    @Test
    void conElInterruptorPrendidoDejaUnMensajeDelAcompananteConLaHoraEnSuZona() {
        boolean escrito = servicio(prendidos(), CLOCK).dejarEnElChat(aviso("POR_VENCER", CALCULADO_EN, 30));

        assertThat(escrito).isTrue();
        assertThat(guardados).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.agente()).isEqualTo(AgenteConversacional.COMPANION);
            assertThat(mensaje.rol()).isEqualTo(RolMensaje.ASISTENTE);
            assertThat(mensaje.usuarioId()).isEqualTo(aprendiz);
            assertThat(mensaje.fuentes()).isEmpty();
            // 02:29 UTC = 21:29 del dia anterior en Lima.
            assertThat(mensaje.contenido())
                    .isEqualTo("Se te vence Meditar a las 21:29. Si lo haces ahora sumas 10 puntos.");
        });
    }

    @Test
    void elMismoAvisoEntregadoDosVecesDejaUnSoloMensaje() {
        var servicio = servicio(prendidos(), CLOCK);

        servicio.dejarEnElChat(aviso("POR_VENCER", CALCULADO_EN, 30));
        // El barrido siguiente republica el mismo aviso con otro instante y otros minutos.
        boolean segundo = servicio.dejarEnElChat(aviso("POR_VENCER", CALCULADO_EN.plusSeconds(300), 25));

        assertThat(segundo).isFalse();
        assertThat(guardados).hasSize(1);
    }

    @Test
    void unTipoDeAvisoDesconocidoSeFiltra() {
        boolean escrito = servicio(prendidos(), CLOCK).dejarEnElChat(aviso("OTRO", CALCULADO_EN, 30));

        assertThat(escrito).isFalse();
        verifyNoInteractions(saveMensajePort);
    }

    @Test
    void unTipoNoElegidoEnLaConfiguracionSeFiltra() {
        var soloVencimiento = new AvisosEnChat(true, Set.of("POR_VENCER"), prendidos().plantillas());

        boolean escrito = servicio(soloVencimiento, CLOCK).dejarEnElChat(aviso("INICIO", CALCULADO_EN, 30));

        assertThat(escrito).isFalse();
        verifyNoInteractions(saveMensajePort);
    }

    @Test
    void unAvisoQueLlegaDespuesDelMomentoAvisadoNoSeEscribe() {
        var tarde = FixedClock.at(Instant.parse("2026-09-23T02:29:00Z"));

        boolean escrito = servicio(prendidos(), tarde).dejarEnElChat(aviso("POR_VENCER", CALCULADO_EN, 30));

        assertThat(escrito).isFalse();
        verify(saveMensajePort, never()).save(any());
    }

    /** D-132: el aviso no puede hacer pasar por respondido un pedido que no tuvo respuesta. */
    @Test
    void siLoUltimoEsUnPedidoSinRespuestaNoEscribe() {
        var pedido = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), aprendiz,
                AgenteConversacional.COMPANION, "marca meditar", CLOCK.now().minusSeconds(60));
        lenient().when(loadMensajePort.pagina(aprendiz, AgenteConversacional.COMPANION, null, 1))
                .thenReturn(List.of(pedido));

        boolean escrito = servicio(prendidos(), CLOCK).dejarEnElChat(aviso("POR_VENCER", CALCULADO_EN, 30));

        assertThat(escrito).isFalse();
        verify(saveMensajePort, never()).save(any());
    }

    @Test
    void siNuncaAbrioElChatCreaLaConversacionAntesDelMensaje() {
        lenient().when(loadConversacionPort.porUsuarioId(aprendiz)).thenReturn(Optional.empty());

        servicio(prendidos(), CLOCK).dejarEnElChat(aviso("INICIO", CALCULADO_EN, 30));

        verify(saveConversacionPort).save(any());
        assertThat(guardados).singleElement().satisfies(mensaje -> assertThat(mensaje.contenido())
                .isEqualTo("Tu habito Meditar empieza a las 21:29. Hay 10 puntos en juego."));
    }

    @Test
    void elIdDelMensajeEsDeterministicoPorClaveDeAviso() {
        assertThat(AvisoHabitoEnChatService.idDelAviso(claveEvento))
                .isEqualTo(AvisoHabitoEnChatService.idDelAviso(claveEvento))
                .isNotEqualTo(AvisoHabitoEnChatService.idDelAviso(UUID.randomUUID()));
    }
}
