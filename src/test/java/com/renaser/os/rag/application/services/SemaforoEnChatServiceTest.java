package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.semaforo.DejarSemaforoEnChatUseCase.SemaforoEnChatCommand;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.rag.domain.model.logro.TipoLogro;
import com.renaser.os.rag.domain.model.semaforo.ColorDeLaSemana;
import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat;
import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat.CierreDeSemana;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * El cierre semanal del semáforo contado en el chat, sobre puertos mockeados. Igual que en
 * {@code LogroEnChatServiceTest}, el guardado es un fake con memoria ({@link #guardados}): la prueba
 * de duplicados tiene que encontrar lo que guardó la primera entrega.
 *
 * <p>Reloj a las 05:25 UTC de un sábado (00:25 en Lima), la hora real del cierre (regla 02 §3); el
 * texto no depende del día local.
 */
@ExtendWith(MockitoExtension.class)
class SemaforoEnChatServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-26T05:25:00Z"));
    private static final String VERDE = "Cerraste la semana en verde, {etiqueta}: {porcentaje} %.";
    private static final String SIN_DATOS = "Tu semana cerró sin datos. ¿Planificamos juntos esta semana?";

    @Mock
    private LoadMensajeRenasiaPort loadMensajePort;
    @Mock
    private SaveMensajeRenasiaPort saveMensajePort;
    @Mock
    private LoadConversacionRenasiaPort loadConversacionPort;
    @Mock
    private SaveConversacionRenasiaPort saveConversacionPort;

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final UUID claveDeLaSemana = UUID.randomUUID();
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
        lenient().when(loadConversacionPort.porUsuarioId(aprendiz))
                .thenReturn(Optional.of(ConversacionRenasia.iniciar(aprendiz, CLOCK.now())));
    }

    private static SemaforoEnChat prendido() {
        return new SemaforoEnChat(true, Map.of(ColorDeLaSemana.VERDE, VERDE, ColorDeLaSemana.SIN_DATOS, SIN_DATOS));
    }

    private SemaforoEnChatService servicio(SemaforoEnChat semaforoEnChat) {
        return new SemaforoEnChatService(semaforoEnChat, loadMensajePort, saveMensajePort, loadConversacionPort,
                saveConversacionPort, CLOCK);
    }

    private SemaforoEnChatCommand cierre(ColorDeLaSemana color, String etiqueta, BigDecimal porcentaje) {
        return new SemaforoEnChatCommand(aprendiz, new CierreDeSemana(color, etiqueta, porcentaje), claveDeLaSemana);
    }

    private SemaforoEnChatCommand verde() {
        return cierre(ColorDeLaSemana.VERDE, "Al día", new BigDecimal("86.0"));
    }

    @Test
    void conElInterruptorApagadoNoEscribeNiTocaNingunPuerto() {
        boolean escrito = servicio(SemaforoEnChat.apagado()).dejarEnElChat(verde());

        assertThat(escrito).isFalse();
        verifyNoInteractions(loadMensajePort, saveMensajePort, loadConversacionPort, saveConversacionPort);
    }

    @Test
    void elCierreDejaUnMensajeDelAcompananteConColorPalabraYPorcentaje() {
        boolean escrito = servicio(prendido()).dejarEnElChat(verde());

        assertThat(escrito).isTrue();
        assertThat(guardados).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.agente()).isEqualTo(AgenteConversacional.COMPANION);
            assertThat(mensaje.rol()).isEqualTo(RolMensaje.ASISTENTE);
            assertThat(mensaje.usuarioId()).isEqualTo(aprendiz);
            assertThat(mensaje.fuentes()).isEmpty();
            assertThat(mensaje.contenido()).isEqualTo("Cerraste la semana en verde, Al día: 86 %.");
        });
    }

    @Test
    void sinPorcentajeElMensajeNoDiceNingunNumero() {
        boolean escrito = servicio(prendido()).dejarEnElChat(cierre(ColorDeLaSemana.SIN_DATOS, "Sin datos", null));

        assertThat(escrito).isTrue();
        assertThat(guardados).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.contenido()).isEqualTo(SIN_DATOS);
            assertThat(mensaje.contenido()).doesNotContainPattern("\\d");
        });
    }

    @Test
    void laMismaSemanaReentregadaDejaUnSoloMensaje() {
        var servicio = servicio(prendido());

        servicio.dejarEnElChat(verde());
        boolean segundo = servicio.dejarEnElChat(verde());

        assertThat(segundo).isFalse();
        assertThat(guardados).hasSize(1);
    }

    @Test
    void unColorSinTextoNoSeEscribe() {
        boolean escrito = servicio(prendido()).dejarEnElChat(
                cierre(ColorDeLaSemana.ROJO, "Con problemas", new BigDecimal("40.0")));

        assertThat(escrito).isFalse();
        verifyNoInteractions(saveMensajePort);
    }

    /** D-132: el mensaje no puede hacer pasar por respondido un pedido que no tuvo respuesta. */
    @Test
    void siLoUltimoEsUnPedidoSinRespuestaNoEscribe() {
        var pedido = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), aprendiz,
                AgenteConversacional.COMPANION, "marca meditar", CLOCK.now().minusSeconds(60));
        lenient().when(loadMensajePort.pagina(aprendiz, AgenteConversacional.COMPANION, null, 1))
                .thenReturn(List.of(pedido));

        boolean escrito = servicio(prendido()).dejarEnElChat(verde());

        assertThat(escrito).isFalse();
        verify(saveMensajePort, never()).save(any());
    }

    @Test
    void siNuncaAbrioElChatCreaLaConversacionAntesDelMensaje() {
        lenient().when(loadConversacionPort.porUsuarioId(aprendiz)).thenReturn(Optional.empty());

        servicio(prendido()).dejarEnElChat(verde());

        verify(saveConversacionPort).save(any());
        assertThat(guardados).hasSize(1);
    }

    @Test
    void elIdEsDeterministicoPorSemanaYNoChocaConLosDeLogrosNiAvisos() {
        MensajeRenasiaId id = SemaforoEnChatService.idDelCierre(claveDeLaSemana);

        assertThat(id)
                .isEqualTo(SemaforoEnChatService.idDelCierre(claveDeLaSemana))
                .isNotEqualTo(SemaforoEnChatService.idDelCierre(UUID.randomUUID()))
                .isNotEqualTo(AvisoHabitoEnChatService.idDelAviso(claveDeLaSemana))
                .isNotEqualTo(LogroEnChatService.idDelLogro(TipoLogro.RACHA_SIN_CELULAR, claveDeLaSemana));
    }
}
