package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.renaser.os.chat.api.MensajeDeChatGuardadoEvent;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.PiezaDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El programa escribe en un chat (D-199/D-204): SISTEMA, a nombre de la persona a quien se refiere, sin
 * pedir actor activo ni participante, y empujado en vivo. Sin transacción activa se publica al toque
 * (el camino con transacción es el mismo mecanismo de {@code MensajeService}).
 */
@ExtendWith(MockitoExtension.class)
class MensajeDelProgramaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UUID ID = UUID.randomUUID();

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private SaveMensajePort saveMensajePort;
    @Mock
    private PublicarMensajeFanoutPort fanout;

    /** D-221: lo que se publicó para el aviso push. */
    private final java.util.List<Object> publicados = new java.util.ArrayList<>();
    /** D-223: la tabla de mensajes vista por el INSERT que ignora el choque de ids. */
    private final Map<MensajeId, Mensaje> guardadosUnaVez = new LinkedHashMap<>();

    private MensajeDelProgramaService servicio() {
        return new MensajeDelProgramaService(loadConversacionPort, saveMensajePort, fanout, FixedClock.at(AHORA),
                () -> ID, publicados::add, m -> guardadosUnaVez.putIfAbsent(m.id(), m) == null);
    }

    @Test
    @DisplayName("D-221: un mensaje del programa también publica el aviso de mensaje nuevo; sin conversación, no")
    void publicaElAviso() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.of(Conversacion.crearGlobal(SOPORTE, AHORA)));
        when(saveMensajePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio().enviarDelPrograma(SOPORTE, ANA, ContenidoDelPrograma.texto("Te damos la bienvenida"));

        assertThat(publicados).containsExactly(
                new com.renaser.os.chat.api.MensajeDeChatGuardadoEvent(ID, SOPORTE.value()));
    }

    @Test
    @DisplayName("guarda un SISTEMA a nombre de la persona a quien se refiere y lo empuja en vivo")
    void guardaYEmpuja() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.of(Conversacion.crearGlobal(SOPORTE, AHORA)));
        when(saveMensajePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Mensaje guardado = servicio().enviarDelPrograma(SOPORTE, ANA, ContenidoDelPrograma.texto("Te damos la bienvenida"));

        assertThat(guardado.tipo()).isEqualTo(TipoMensaje.SISTEMA);
        assertThat(guardado.emisorId()).isEqualTo(ANA);
        assertThat(guardado.id().value()).isEqualTo(ID);
        assertThat(guardado.creadoEn()).isEqualTo(AHORA);
        assertThat(guardado.texto()).isEqualTo("Te damos la bienvenida");
        verify(fanout).publicar(guardado);
    }

    @Test
    @DisplayName("sin la conversación no guarda nada (la bienvenida lanza y el outbox reintenta)")
    void sinConversacionNoGuarda() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().enviarDelPrograma(SOPORTE, ANA, ContenidoDelPrograma.texto("hola")))
                .isInstanceOf(NoSuchElementException.class);
        verify(saveMensajePort, never()).save(any());
        verify(fanout, never()).publicar(any());
    }

    // ── D-223: piezas con id calculado ──────────────────────────────────────

    private static final MensajeId IMAGEN = MensajeId.of(UUID.randomUUID());
    private static final MensajeId TEXTO = MensajeId.of(UUID.randomUUID());

    private static EntregaDelPrograma tarjeta() {
        return new EntregaDelPrograma(SOPORTE, ANA, List.of(
                new PiezaDelPrograma(IMAGEN, ContenidoDelPrograma.imagen("semaforo/tarjetas/verde-v1.jpg", "image/jpeg", 1000),
                        AvisoDeLaPieza.SIN_AVISO),
                new PiezaDelPrograma(TEXTO, ContenidoDelPrograma.texto("Hoy llevas 85 % de tus hábitos."),
                        AvisoDeLaPieza.SOLO_A_QUIEN_SE_REFIERE)));
    }

    @Test
    @DisplayName("D-223: manda las piezas en orden (la imagen un milisegundo antes); la imagen sin aviso y el texto solo para la aprendiz")
    void mandaLasPiezasEnOrdenYConSuAviso() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.of(Conversacion.crearGlobal(SOPORTE, AHORA)));

        assertThat(servicio().enviarUnaVez(tarjeta())).isEqualTo(2);

        assertThat(guardadosUnaVez.get(IMAGEN).creadoEn()).isEqualTo(AHORA);
        assertThat(guardadosUnaVez.get(TEXTO).creadoEn()).isEqualTo(AHORA.plusMillis(1));
        assertThat(guardadosUnaVez.values()).allMatch(m -> m.tipo() == TipoMensaje.SISTEMA && m.emisorId().equals(ANA));
        assertThat(publicados).containsExactly(new MensajeDeChatGuardadoEvent(TEXTO.value(), SOPORTE.value(), ANA.value()));
        verify(fanout, org.mockito.Mockito.times(2)).publicar(any());
        verify(saveMensajePort, never()).save(any());
    }

    @Test
    @DisplayName("D-223: la segunda vez no guarda, no empuja ni avisa nada; si faltaba una pieza, manda solo esa")
    void laSegundaVezNoDuplica() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.of(Conversacion.crearGlobal(SOPORTE, AHORA)));
        servicio().enviarUnaVez(tarjeta());
        publicados.clear();
        org.mockito.Mockito.clearInvocations(fanout);

        assertThat(servicio().enviarUnaVez(tarjeta())).isZero();
        assertThat(publicados).isEmpty();
        verify(fanout, never()).publicar(any());

        guardadosUnaVez.remove(TEXTO);
        assertThat(servicio().enviarUnaVez(tarjeta())).isEqualTo(1);
        assertThat(publicados).hasSize(1);
    }

    @Test
    @DisplayName("D-223: A_TODOS avisa como siempre (sin destinatario único)")
    void aTodosAvisaComoSiempre() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.of(Conversacion.crearGlobal(SOPORTE, AHORA)));

        servicio().enviarUnaVez(new EntregaDelPrograma(SOPORTE, ANA,
                List.of(new PiezaDelPrograma(TEXTO, ContenidoDelPrograma.texto("hola"), AvisoDeLaPieza.A_TODOS))));

        assertThat(publicados).containsExactly(new MensajeDeChatGuardadoEvent(TEXTO.value(), SOPORTE.value()));
    }

    @Test
    @DisplayName("D-262: sin persona (el podio semanal) se guarda sin emisor, firmado por el programa, y avisa a todos")
    void sinPersona() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.of(Conversacion.crearGlobal(SOPORTE, AHORA)));

        servicio().enviarUnaVez(EntregaDelPrograma.sinPersona(SOPORTE,
                List.of(new PiezaDelPrograma(TEXTO, ContenidoDelPrograma.texto("podio"), AvisoDeLaPieza.A_TODOS))));

        assertThat(guardadosUnaVez.get(TEXTO).emisorId()).isNull();
        assertThat(guardadosUnaVez.get(TEXTO).remitentePublico()).isEqualTo(Mensaje.ID_PUBLICO_DEL_PROGRAMA);
        assertThat(guardadosUnaVez.get(TEXTO).escritoPor(ANA)).isFalse();
        assertThat(publicados).containsExactly(new MensajeDeChatGuardadoEvent(TEXTO.value(), SOPORTE.value()));
    }

    @Test
    @DisplayName("D-262: sin persona no se puede avisar «solo a quien se refiere»")
    void sinPersonaNoHayAQuienAvisarleSolo() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EntregaDelPrograma.sinPersona(SOPORTE,
                        List.of(new PiezaDelPrograma(TEXTO, ContenidoDelPrograma.texto("x"), AvisoDeLaPieza.SOLO_A_QUIEN_SE_REFIERE))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
