package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.MarcaDeBienvenidaPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La bienvenida automática del procedimiento OPE-01-01 (D-174), idempotente por destinatario (G-2).
 *
 * <p>Sin Spring: {@code PlatformTransactionManager} sin stubbing hace que {@code TransactionTemplate}
 * ejecute el callback directo, mismo criterio que {@code ConversacionSoporteServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class BienvenidaEnSoporteServiceTest {

    private static final String KELIN = "kelin@renaser.test";
    private static final UserId KELIN_ID = UserId.of(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"));
    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.fromString("55555555-5555-4555-8555-555555555555"));
    private static final UUID ID_FOTO = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final byte[] TARJETA = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
    private static final MensajeId ID_MENSAJE_1 =
            MensajeId.of(UUID.fromString("77777777-7777-4777-8777-777777777777"));

    @Mock
    private DibujarBienvenidaPort dibujarPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private EnviarMensajeUseCase enviarMensaje;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private MarcaDeBienvenidaPort marcaPort;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BienvenidaEnSoporteService servicio(String remitente, String texto) {
        return new BienvenidaEnSoporteService(dibujarPort, almacenamientoPort, enviarMensaje, userSummaryFinder,
                marcaPort, idGenerator, transactionManager, remitente, texto);
    }

    @Test
    @DisplayName("manda la tarjeta con el primer nombre y después el texto, firmados por Kelin")
    void mandaTarjetaYTextoDesdeKelin() {
        preparar("maría josé ñahui", UserStatus.ACTIVE);

        servicio(KELIN, "¡Bienvenida, {nombre}! 💚").darBienvenida(SOPORTE, ANA);

        verify(dibujarPort).dibujar("María");
        String ruta = "chat/" + SOPORTE.value() + "/fotos/" + ID_FOTO;
        verify(almacenamientoPort).subir(ruta, TARJETA, "image/jpeg");
        ArgumentCaptor<EnviarMensajeCommand> enviados = ArgumentCaptor.forClass(EnviarMensajeCommand.class);
        verify(enviarMensaje, times(2)).enviar(enviados.capture());
        EnviarMensajeCommand foto = enviados.getAllValues().get(0);
        EnviarMensajeCommand texto = enviados.getAllValues().get(1);
        assertThat(foto.tipo()).isEqualTo(TipoMensaje.IMAGEN);
        assertThat(foto.mediaRuta()).isEqualTo(ruta);
        assertThat(foto.mediaBytes()).isEqualTo(TARJETA.length);
        assertThat(texto.tipo()).isEqualTo(TipoMensaje.TEXTO);
        assertThat(texto.texto()).isEqualTo("¡Bienvenida, María! 💚");
        assertThat(enviados.getAllValues()).allSatisfy(c -> {
            assertThat(c.actorId()).isEqualTo(KELIN_ID);
            assertThat(c.conversacionId()).isEqualTo(SOPORTE);
        });
    }

    @Test
    @DisplayName("sin texto configurado, manda solo la tarjeta")
    void sinTextoSoloLaTarjeta() {
        preparar("Ana Perez", UserStatus.ACTIVE);

        servicio(KELIN, "  ").darBienvenida(SOPORTE, ANA);

        verify(enviarMensaje, times(1)).enviar(any());
    }

    @Test
    @DisplayName("sin remitente configurado está apagada: no dibuja, no sube, no manda")
    void apagadaSinRemitente() {
        servicio("", "Hola").darBienvenida(SOPORTE, ANA);

        verifyNoInteractions(userSummaryFinder, dibujarPort, almacenamientoPort, enviarMensaje);
    }

    @Test
    @DisplayName("si la cuenta remitente está suspendida o no existe, no manda nada")
    void remitenteSuspendidoNoManda() {
        when(userSummaryFinder.findByEmail(KELIN)).thenReturn(Optional.of(
                new UserSummary(KELIN_ID, "Kelin", KELIN, UserRole.ALCHEMIST, UserStatus.SUSPENDED)));

        servicio(KELIN, "Hola").darBienvenida(SOPORTE, ANA);

        verify(almacenamientoPort, never()).subir(anyString(), any(), anyString());
        verify(enviarMensaje, never()).enviar(any());
    }

    @Test
    @DisplayName("si subir la tarjeta falla, no manda una foto que no existe y LANZA para que el outbox reintente (G-2)")
    void unFalloSeReintenta() {
        preparar("Ana", UserStatus.ACTIVE);
        doThrow(new IllegalStateException("S3 caído")).when(almacenamientoPort).subir(anyString(), any(), anyString());

        assertThatThrownBy(() -> servicio(KELIN, "Hola").darBienvenida(SOPORTE, ANA))
                .isInstanceOf(IllegalStateException.class);
        verify(enviarMensaje, never()).enviar(any());
        verify(marcaPort, never()).marcar(any(), any());
    }

    @Test
    @DisplayName("G-2: deja la marca con el primer mensaje (la tarjeta), en la misma transacción")
    void dejaLaMarcaConLaTarjeta() {
        preparar("Ana", UserStatus.ACTIVE);

        servicio(KELIN, "Hola {nombre}").darBienvenida(SOPORTE, ANA);

        verify(marcaPort).marcar(eq(ANA), eq(ID_MENSAJE_1));
    }

    @Test
    @DisplayName("G-2: una reentrega del outbox con la marca ya puesta no dibuja, no sube y no manda nada")
    void reentregaNoDuplica() {
        when(marcaPort.yaSeDio(ANA)).thenReturn(true);

        servicio(KELIN, "Hola").darBienvenida(SOPORTE, ANA);

        verifyNoInteractions(dibujarPort, almacenamientoPort, enviarMensaje);
        verify(marcaPort, never()).marcar(any(), any());
    }

    @Test
    @DisplayName("G-2: si otra entrega cruzada dejó la marca primero, esta se deshace sin error")
    void carreraConOtraEntrega() {
        preparar("Ana", UserStatus.ACTIVE);
        when(marcaPort.yaSeDio(ANA)).thenReturn(false, true);
        doThrow(new DuplicateKeyException("mensajes_bienvenida_pkey")).when(marcaPort).marcar(any(), any());

        assertThatCode(() -> servicio(KELIN, "Hola").darBienvenida(SOPORTE, ANA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("G-5: con almacenamiento noop no manda una imagen inexistente: solo el texto, y marca con él")
    void sinAlmacenamientoRealSoloElTexto() {
        preparar("Ana", UserStatus.ACTIVE);
        when(almacenamientoPort.guardaObjetos()).thenReturn(false);

        servicio(KELIN, "Hola {nombre}").darBienvenida(SOPORTE, ANA);

        verify(dibujarPort, never()).dibujar(anyString());
        verify(almacenamientoPort, never()).subir(anyString(), any(), anyString());
        ArgumentCaptor<EnviarMensajeCommand> enviados = ArgumentCaptor.forClass(EnviarMensajeCommand.class);
        verify(enviarMensaje, times(1)).enviar(enviados.capture());
        assertThat(enviados.getValue().tipo()).isEqualTo(TipoMensaje.TEXTO);
        assertThat(enviados.getValue().texto()).isEqualTo("Hola Ana");
        verify(marcaPort).marcar(eq(ANA), eq(ID_MENSAJE_1));
    }

    private void preparar(String nombreAprendiz, UserStatus estadoKelin) {
        when(userSummaryFinder.findByEmail(KELIN)).thenReturn(Optional.of(
                new UserSummary(KELIN_ID, "Kelin", KELIN, UserRole.ALCHEMIST, estadoKelin)));
        when(userSummaryFinder.findById(ANA)).thenReturn(Optional.of(
                new UserSummary(ANA, nombreAprendiz, null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(dibujarPort.dibujar(anyString())).thenReturn(TARJETA);
        lenient().when(idGenerator.newId()).thenReturn(ID_FOTO);
        lenient().when(almacenamientoPort.guardaObjetos()).thenReturn(true);
        // Cada envío devuelve un mensaje guardado: el primero es el que queda en la marca.
        lenient().when(enviarMensaje.enviar(any())).thenAnswer(new org.mockito.stubbing.Answer<Mensaje>() {
            private int enviados;

            @Override
            public Mensaje answer(org.mockito.invocation.InvocationOnMock inv) {
                EnviarMensajeCommand c = inv.getArgument(0);
                MensajeId id = ++enviados == 1 ? ID_MENSAJE_1 : MensajeId.of(UUID.randomUUID());
                return Mensaje.escribir(id, c.conversacionId(), c.actorId(), c.tipo(), c.texto(), c.mediaBucket(),
                        c.mediaRuta(), c.mediaMime(), c.mediaBytes(), null, null, Instant.parse("2026-09-26T15:00:00Z"));
            }
        });
    }
}
