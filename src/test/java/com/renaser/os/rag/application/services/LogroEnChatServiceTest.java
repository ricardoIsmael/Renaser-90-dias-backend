package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.logro.CelebrarLogroEnChatUseCase.LogroEnChatCommand;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.rag.domain.model.logro.LogrosEnChat;
import com.renaser.os.rag.domain.model.logro.TipoLogro;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
 * La celebracion de logros en el chat, sobre puertos mockeados. Igual que en
 * {@code AvisoHabitoEnChatServiceTest}, el guardado es un fake con memoria ({@link #guardados}):
 * la prueba de duplicados tiene que encontrar lo que guardo la primera entrega.
 *
 * <p>Reloj a las 02:00 UTC (regla 02 §3), aunque aca no se dice ninguna hora: el texto no depende
 * del dia local.
 */
@ExtendWith(MockitoExtension.class)
class LogroEnChatServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-23T02:00:00Z"));
    private static final String RACHA = "¡Lo lograste! Completaste tu día sin celular.";
    private static final String ROCA = "¡Roca completada!";

    @Mock
    private LoadMensajeRenasiaPort loadMensajePort;
    @Mock
    private SaveMensajeRenasiaPort saveMensajePort;
    @Mock
    private LoadConversacionRenasiaPort loadConversacionPort;
    @Mock
    private SaveConversacionRenasiaPort saveConversacionPort;

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final UUID rachaId = UUID.randomUUID();
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

    private static LogrosEnChat prendidos() {
        return new LogrosEnChat(true, Set.of(TipoLogro.values()),
                Map.of(TipoLogro.RACHA_SIN_CELULAR, RACHA, TipoLogro.ROCA_COMPLETADA, ROCA));
    }

    private LogroEnChatService servicio(LogrosEnChat logros) {
        return new LogroEnChatService(logros, loadMensajePort, saveMensajePort, loadConversacionPort,
                saveConversacionPort, CLOCK);
    }

    private LogroEnChatCommand logro(TipoLogro tipo, UUID clave) {
        return new LogroEnChatCommand(aprendiz, tipo, clave);
    }

    @Test
    void conElInterruptorApagadoNoTocaNingunPuerto() {
        boolean escrito = servicio(LogrosEnChat.apagados()).celebrarEnElChat(logro(TipoLogro.RACHA_SIN_CELULAR, rachaId));

        assertThat(escrito).isFalse();
        verifyNoInteractions(loadMensajePort, saveMensajePort, loadConversacionPort, saveConversacionPort);
    }

    @Test
    void unLogroDejaUnMensajeDelAcompananteConSuPlantilla() {
        boolean escrito = servicio(prendidos()).celebrarEnElChat(logro(TipoLogro.RACHA_SIN_CELULAR, rachaId));

        assertThat(escrito).isTrue();
        assertThat(guardados).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.agente()).isEqualTo(AgenteConversacional.COMPANION);
            assertThat(mensaje.rol()).isEqualTo(RolMensaje.ASISTENTE);
            assertThat(mensaje.usuarioId()).isEqualTo(aprendiz);
            assertThat(mensaje.fuentes()).isEmpty();
            assertThat(mensaje.contenido()).isEqualTo(RACHA);
        });
    }

    @Test
    void elMismoLogroReentregadoDejaUnSoloMensaje() {
        var servicio = servicio(prendidos());

        servicio.celebrarEnElChat(logro(TipoLogro.RACHA_SIN_CELULAR, rachaId));
        boolean segundo = servicio.celebrarEnElChat(logro(TipoLogro.RACHA_SIN_CELULAR, rachaId));

        assertThat(segundo).isFalse();
        assertThat(guardados).hasSize(1);
    }

    @Test
    void dosLogrosDistintosDejanDosMensajes() {
        var servicio = servicio(prendidos());

        servicio.celebrarEnElChat(logro(TipoLogro.RACHA_SIN_CELULAR, rachaId));
        servicio.celebrarEnElChat(logro(TipoLogro.ROCA_COMPLETADA, UUID.randomUUID()));

        assertThat(guardados).extracting(MensajeRenasia::contenido).containsExactly(RACHA, ROCA);
    }

    @Test
    void unTipoNoElegidoEnLaConfiguracionNoSeCelebra() {
        var soloRacha = new LogrosEnChat(true, Set.of(TipoLogro.RACHA_SIN_CELULAR), prendidos().plantillas());

        boolean escrito = servicio(soloRacha).celebrarEnElChat(logro(TipoLogro.ROCA_COMPLETADA, UUID.randomUUID()));

        assertThat(escrito).isFalse();
        verifyNoInteractions(saveMensajePort);
    }

    /** D-132: la celebracion no puede hacer pasar por respondido un pedido que no tuvo respuesta. */
    @Test
    void siLoUltimoEsUnPedidoSinRespuestaNoEscribe() {
        var pedido = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), aprendiz,
                AgenteConversacional.COMPANION, "marca meditar", CLOCK.now().minusSeconds(60));
        lenient().when(loadMensajePort.pagina(aprendiz, AgenteConversacional.COMPANION, null, 1))
                .thenReturn(List.of(pedido));

        boolean escrito = servicio(prendidos()).celebrarEnElChat(logro(TipoLogro.RACHA_SIN_CELULAR, rachaId));

        assertThat(escrito).isFalse();
        verify(saveMensajePort, never()).save(any());
    }

    @Test
    void siNuncaAbrioElChatCreaLaConversacionAntesDelMensaje() {
        lenient().when(loadConversacionPort.porUsuarioId(aprendiz)).thenReturn(Optional.empty());

        servicio(prendidos()).celebrarEnElChat(logro(TipoLogro.ROCA_COMPLETADA, UUID.randomUUID()));

        verify(saveConversacionPort).save(any());
        assertThat(guardados).singleElement().satisfies(mensaje -> assertThat(mensaje.contenido()).isEqualTo(ROCA));
    }

    @Test
    void elIdEsDeterministicoPorTipoYClaveYNoChocaConElDeLosAvisos() {
        MensajeRenasiaId id = LogroEnChatService.idDelLogro(TipoLogro.RACHA_SIN_CELULAR, rachaId);

        assertThat(id)
                .isEqualTo(LogroEnChatService.idDelLogro(TipoLogro.RACHA_SIN_CELULAR, rachaId))
                .isNotEqualTo(LogroEnChatService.idDelLogro(TipoLogro.ROCA_COMPLETADA, rachaId))
                .isNotEqualTo(AvisoHabitoEnChatService.idDelAviso(rachaId));
    }
}
