package com.renaser.os.chat.application.services;

import com.renaser.os.chat.api.RecordatorioDeEventoEnElChat.Resultado;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.NombreDelChat;
import com.renaser.os.chat.domain.model.evento.RecordatorioDeEventoEnSoporte;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El recordatorio de un evento como mensaje del programa en el chat de soporte (spec 041, FR-001, FR-002, FR-004, FR-005, FR-032, SC-001, SC-009). Se prueba con el
 * caso de uso de mensajes del programa REAL en el ultimo eslabon de memoria: el id del mensaje es el que decide si un mensaje se repite.
 */
class RecordatorioDeEventoEnSoporteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T23:50:00Z");
    private static final String TEXTO = "Clase de manifestación\nEmpieza en 10 min, a las 19:00.\nEntra aquí: https://meet.google.com/abc-defg-hij";

    private final LoadConversacionPort conversaciones = mock(LoadConversacionPort.class);
    private final AlcanceDeFormacionFinder formaciones = mock(AlcanceDeFormacionFinder.class);
    private final EnviarMensajeDelProgramaUseCase delPrograma = mock(EnviarMensajeDelProgramaUseCase.class);
    private final Interruptores interruptores = mock(Interruptores.class);
    private final UserId rosa = UserId.of(UUID.randomUUID());
    private final Set<UUID> yaEscritos = new HashSet<>();
    private RecordatorioDeEventoEnSoporteService servicio;

    @BeforeEach
    void armar() {
        when(interruptores.prendida("chat.soporte")).thenReturn(true);
        // enviarUnaVez de verdad: devuelve cuantas piezas SALIERON ahora, y una pieza con el mismo id no vuelve a salir.
        when(delPrograma.enviarUnaVez(any())).thenAnswer(inv -> {
            EntregaDelPrograma entrega = inv.getArgument(0);
            return (int) entrega.piezas().stream().filter(p -> yaEscritos.add(p.id().value())).count();
        });
        servicio = new RecordatorioDeEventoEnSoporteService(new SoporteVigenteDelAprendiz(conversaciones, formaciones), delPrograma, interruptores);
    }

    private ConversacionId soporteDe(UserId persona, Formacion formacion) {
        ConversacionId id = ConversacionId.of(UUID.randomUUID());
        when(formaciones.formacionDe(persona)).thenReturn(Optional.of(formacion));
        when(conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(persona, formacion))).thenReturn(Optional.of(
                Conversacion.crearSoporte(id, persona, NombreDelChat.deSoporte("Rosa Quispe", NombreDelChat.delPrograma(formacion)), AHORA, formacion)));
        return id;
    }

    // spec 041 FR-001, FR-002
    @Test
    @DisplayName("un mensaje del programa en el soporte de la persona, a su nombre, con el texto armado y SIN segundo push")
    void escribeElMensaje() {
        ConversacionId soporte = soporteDe(rosa, Formacion.ESCUELA);

        Resultado resultado = servicio.escribir(rosa, 100L, TEXTO);

        assertThat(resultado).isEqualTo(Resultado.ESCRITO);
        ArgumentCaptor<EntregaDelPrograma> entrega = ArgumentCaptor.forClass(EntregaDelPrograma.class);
        verify(delPrograma).enviarUnaVez(entrega.capture());
        assertThat(entrega.getValue().conversacionId()).isEqualTo(soporte);
        assertThat(entrega.getValue().sobreQuien()).isEqualTo(rosa);
        assertThat(entrega.getValue().piezas()).singleElement().satisfies(pieza -> {
            assertThat(pieza.contenido().texto()).isEqualTo(TEXTO);
            assertThat(pieza.aviso()).as("el aviso de la app ya fue el push").isEqualTo(AvisoDeLaPieza.SIN_AVISO);
            assertThat(pieza.id()).isEqualTo(new RecordatorioDeEventoEnSoporte(100L, TEXTO).idDelMensaje());
        });
    }

    // spec 041 FR-004, SC-001
    @Test
    @DisplayName("reentregar el mismo recordatorio diez veces deja UN solo mensaje: el id sale de la fila de la cola")
    void reentregarNoDuplica() {
        soporteDe(rosa, Formacion.ESCUELA);

        Resultado primero = servicio.escribir(rosa, 100L, TEXTO);
        for (int i = 0; i < 9; i++) {
            assertThat(servicio.escribir(rosa, 100L, TEXTO)).isEqualTo(Resultado.YA_ESTABA);
        }

        assertThat(primero).isEqualTo(Resultado.ESCRITO);
        assertThat(yaEscritos).hasSize(1);
    }

    // spec 041 FR-004
    @Test
    @DisplayName("cada uno de los cuatro avisos es otra fila de la cola y por tanto otro mensaje")
    void cadaAvisoSuMensaje() {
        soporteDe(rosa, Formacion.ESCUELA);

        servicio.escribir(rosa, 100L, TEXTO);
        servicio.escribir(rosa, 101L, TEXTO);
        servicio.escribir(rosa, 102L, TEXTO);
        servicio.escribir(rosa, 103L, TEXTO);

        assertThat(yaEscritos).hasSize(4);
    }

    // spec 041 FR-004
    @Test
    @DisplayName("el id del mensaje es el mismo siempre para la misma fila (no depende del texto ni de la hora)")
    void idDeterminista() {
        assertThat(new RecordatorioDeEventoEnSoporte(7L, "uno").idDelMensaje()).isEqualTo(new RecordatorioDeEventoEnSoporte(7L, "otro").idDelMensaje());
        assertThat(new RecordatorioDeEventoEnSoporte(7L, "uno").idDelMensaje()).isNotEqualTo(new RecordatorioDeEventoEnSoporte(8L, "uno").idDelMensaje());
    }

    // spec 041 FR-005
    @Test
    @DisplayName("quien no tiene chat de soporte (el personal) no recibe mensaje y no se le crea uno")
    void sinChatDeSoporte() {
        when(formaciones.formacionDe(rosa)).thenReturn(Optional.of(Formacion.ESCUELA));
        when(conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(rosa, Formacion.ESCUELA))).thenReturn(Optional.empty());

        assertThat(servicio.escribir(rosa, 100L, TEXTO)).isEqualTo(Resultado.SIN_CHAT_DE_SOPORTE);

        verify(delPrograma, never()).enviarUnaVez(any());
    }

    // spec 041 FR-006
    @Test
    @DisplayName("con el chat de soporte apagado por Soporte TI no se escribe nada")
    void chatApagado() {
        soporteDe(rosa, Formacion.ESCUELA);
        when(interruptores.prendida("chat.soporte")).thenReturn(false);

        assertThat(servicio.escribir(rosa, 100L, TEXTO)).isEqualTo(Resultado.CHAT_APAGADO);

        verify(delPrograma, never()).enviarUnaVez(any());
    }

    // spec 041 FR-032, SC-009, Escenario 1 de la historia 1
    @ParameterizedTest
    @EnumSource(Formacion.class)
    @DisplayName("el mensaje va al soporte de la formacion en que esta la persona hoy, firmado con el nombre de ESA formacion, y nunca al de otra")
    void cadaFormacionEnSuSoporte(Formacion formacion) {
        ConversacionId suSoporte = soporteDe(rosa, formacion);
        // Su soporte de las otras dos formaciones existe, pero no es el vigente.
        for (Formacion otra : Formacion.values()) {
            if (otra != formacion) {
                when(conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(rosa, otra))).thenReturn(Optional.of(
                        Conversacion.crearSoporte(ConversacionId.of(UUID.randomUUID()), rosa, "Rosa – " + otra, AHORA, otra)));
            }
        }

        servicio.escribir(rosa, 100L, TEXTO);

        ArgumentCaptor<EntregaDelPrograma> entrega = ArgumentCaptor.forClass(EntregaDelPrograma.class);
        verify(delPrograma).enviarUnaVez(entrega.capture());
        assertThat(entrega.getValue().conversacionId()).isEqualTo(suSoporte);
        Conversacion conversacion = conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(rosa, formacion)).orElseThrow();
        assertThat(conversacion.formacion()).isEqualTo(formacion);
        assertThat(NombreDelChat.delPrograma(conversacion.formacion())).isEqualTo(switch (formacion) {
            case ESCUELA -> "Formación Renaser";
            case DIPLOMADO -> "Diplomado Renaser";
            case CERTIFICACION -> "Certificación Renaser";
        });
    }

    // spec 041 FR-001
    @Test
    @DisplayName("un recordatorio sin texto no se escribe")
    void sinTexto() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new RecordatorioDeEventoEnSoporte(1L, "  "));
    }
}
