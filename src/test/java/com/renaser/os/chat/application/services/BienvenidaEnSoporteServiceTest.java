package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** La bienvenida automática del procedimiento OPE-01-01 (D-174). */
@ExtendWith(MockitoExtension.class)
class BienvenidaEnSoporteServiceTest {

    private static final String KELIN = "kelin@renaser.test";
    private static final UserId KELIN_ID = UserId.of(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"));
    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.fromString("55555555-5555-4555-8555-555555555555"));
    private static final UUID ID_FOTO = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final byte[] TARJETA = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

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

    private BienvenidaEnSoporteService servicio(String remitente, String texto) {
        return new BienvenidaEnSoporteService(dibujarPort, almacenamientoPort, enviarMensaje, userSummaryFinder,
                idGenerator, remitente, texto);
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
    @DisplayName("si subir la tarjeta falla, no lanza ni manda un mensaje con una foto que no existe")
    void unFalloNoRompeNada() {
        preparar("Ana", UserStatus.ACTIVE);
        org.mockito.Mockito.doThrow(new IllegalStateException("S3 caído"))
                .when(almacenamientoPort).subir(anyString(), any(), anyString());

        assertThatCode(() -> servicio(KELIN, "Hola").darBienvenida(SOPORTE, ANA)).doesNotThrowAnyException();
        verify(enviarMensaje, never()).enviar(any());
    }

    private void preparar(String nombreAprendiz, UserStatus estadoKelin) {
        when(userSummaryFinder.findByEmail(KELIN)).thenReturn(Optional.of(
                new UserSummary(KELIN_ID, "Kelin", KELIN, UserRole.ALCHEMIST, estadoKelin)));
        when(userSummaryFinder.findById(ANA)).thenReturn(Optional.of(
                new UserSummary(ANA, nombreAprendiz, null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(dibujarPort.dibujar(anyString())).thenReturn(TARJETA);
        when(idGenerator.newId()).thenReturn(ID_FOTO);
    }
}
