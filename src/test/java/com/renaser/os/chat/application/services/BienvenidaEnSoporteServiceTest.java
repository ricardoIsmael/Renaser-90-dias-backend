package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.MarcaDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
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
 * La bienvenida automática del procedimiento OPE-01-01 (D-174), idempotente por destinatario (G-2),
 * con los textos del recurso versionado (D-190), firmada por el programa y apagada por defecto (D-199).
 *
 * <p>Sin Spring: {@code PlatformTransactionManager} sin stubbing hace que {@code TransactionTemplate}
 * ejecute el callback directo, mismo criterio que {@code ConversacionSoporteServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class BienvenidaEnSoporteServiceTest {

    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.fromString("55555555-5555-4555-8555-555555555555"));
    private static final UUID ID_FOTO = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final byte[] TARJETA = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
    private static final MensajeId ID_MENSAJE_1 =
            MensajeId.of(UUID.fromString("77777777-7777-4777-8777-777777777777"));
    private static final TextosDeBienvenidaPort TEXTOS =
            new TextosFijos("Esta tarjeta es para ti, {nombre}", "Hola {nombre}, tu ingreso está confirmado");

    @Mock
    private DibujarBienvenidaPort dibujarPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private EnviarMensajeDelProgramaUseCase delPrograma;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private MarcaDeBienvenidaPort marcaPort;
    @Mock
    private PlatformTransactionManager transactionManager;

    /** Encendida (BIENVENIDA_ACTIVA=true): el resto de las pruebas mira lo que pasa cuando SÍ sale. */
    private BienvenidaEnSoporteService servicio() {
        return servicio(TEXTOS, true);
    }

    private BienvenidaEnSoporteService servicio(TextosDeBienvenidaPort textos, boolean activa) {
        return new BienvenidaEnSoporteService(dibujarPort, almacenamientoPort, delPrograma, userSummaryFinder,
                marcaPort, textos, idGenerator, transactionManager, activa);
    }

    @Test
    @DisplayName("D-199: la firma el programa: los tres en orden (tarjeta, el que la acompaña, formal), como SISTEMA a nombre de la aprendiz")
    void losTresSalenDelProgramaEnOrden() {
        preparar("maría josé ñahui");

        servicio().darBienvenida(SOPORTE, ANA);

        verify(dibujarPort).dibujar("María");
        String ruta = "chat/" + SOPORTE.value() + "/fotos/" + ID_FOTO;
        verify(almacenamientoPort).subir(ruta, TARJETA, "image/jpeg");
        ArgumentCaptor<ContenidoDelPrograma> enviados = ArgumentCaptor.forClass(ContenidoDelPrograma.class);
        verify(delPrograma, times(3)).enviarDelPrograma(eq(SOPORTE), eq(ANA), enviados.capture());
        assertThat(enviados.getAllValues()).containsExactly(
                ContenidoDelPrograma.imagen(ruta, "image/jpeg", TARJETA.length),
                ContenidoDelPrograma.texto("Esta tarjeta es para ti, María"),
                ContenidoDelPrograma.texto("Hola María, tu ingreso está confirmado"));
    }

    @Test
    @DisplayName("D-199: apagada (el default): no mira nada, no dibuja, no sube, no manda, no marca")
    void apagadaNoHaceNada() {
        servicio(TEXTOS, false).darBienvenida(SOPORTE, ANA);

        verifyNoInteractions(userSummaryFinder, dibujarPort, almacenamientoPort, delPrograma, marcaPort);
    }

    @Test
    @DisplayName("D-199: ya no hay remitente que configurar: sin ninguna cuenta de staff, igual sale")
    void noNecesitaRemitente() {
        preparar("Ana");

        servicio().darBienvenida(SOPORTE, ANA);

        verify(userSummaryFinder, never()).findByEmail(anyString());
        verify(delPrograma, times(3)).enviarDelPrograma(eq(SOPORTE), eq(ANA), any());
    }

    @Test
    @DisplayName("con los dos textos vacíos en el recurso, manda solo la tarjeta")
    void sinTextosSoloLaTarjeta() {
        preparar("Ana Perez");

        servicio(new TextosFijos("", ""), true).darBienvenida(SOPORTE, ANA);

        verify(delPrograma, times(1)).enviarDelPrograma(any(), any(), any());
    }

    @Test
    @DisplayName("si subir la tarjeta falla, no manda una foto que no existe y LANZA para que el outbox reintente (G-2)")
    void unFalloSeReintenta() {
        preparar("Ana");
        doThrow(new IllegalStateException("S3 caído")).when(almacenamientoPort).subir(anyString(), any(), anyString());

        assertThatThrownBy(() -> servicio().darBienvenida(SOPORTE, ANA)).isInstanceOf(IllegalStateException.class);
        verify(delPrograma, never()).enviarDelPrograma(any(), any(), any());
        verify(marcaPort, never()).marcar(any(), any());
    }

    @Test
    @DisplayName("G-2: deja la marca con el primer mensaje (la tarjeta), en la misma transacción")
    void dejaLaMarcaConLaTarjeta() {
        preparar("Ana");

        servicio().darBienvenida(SOPORTE, ANA);

        verify(marcaPort).marcar(eq(ANA), eq(ID_MENSAJE_1));
    }

    @Test
    @DisplayName("G-2: una reentrega del outbox con la marca ya puesta no dibuja, no sube y no manda nada")
    void reentregaNoDuplica() {
        when(marcaPort.yaSeDio(ANA)).thenReturn(true);

        servicio().darBienvenida(SOPORTE, ANA);

        verifyNoInteractions(dibujarPort, almacenamientoPort, delPrograma);
        verify(marcaPort, never()).marcar(any(), any());
    }

    @Test
    @DisplayName("G-2: si otra entrega cruzada dejó la marca primero, esta se deshace sin error")
    void carreraConOtraEntrega() {
        preparar("Ana");
        when(marcaPort.yaSeDio(ANA)).thenReturn(false, true);
        doThrow(new DuplicateKeyException("mensajes_bienvenida_pkey")).when(marcaPort).marcar(any(), any());

        assertThatCode(() -> servicio().darBienvenida(SOPORTE, ANA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("G-5: con almacenamiento noop no manda una imagen inexistente ni el texto que la acompaña: solo el formal, y marca con él")
    void sinAlmacenamientoRealSoloElTexto() {
        preparar("Ana");
        when(almacenamientoPort.guardaObjetos()).thenReturn(false);

        servicio().darBienvenida(SOPORTE, ANA);

        verify(dibujarPort, never()).dibujar(anyString());
        verify(almacenamientoPort, never()).subir(anyString(), any(), anyString());
        verify(delPrograma, times(1)).enviarDelPrograma(SOPORTE, ANA,
                ContenidoDelPrograma.texto("Hola Ana, tu ingreso está confirmado"));
        verify(marcaPort).marcar(eq(ANA), eq(ID_MENSAJE_1));
    }

    private void preparar(String nombreAprendiz) {
        when(userSummaryFinder.findById(ANA)).thenReturn(Optional.of(
                new UserSummary(ANA, nombreAprendiz, null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(dibujarPort.dibujar(anyString())).thenReturn(TARJETA);
        lenient().when(idGenerator.newId()).thenReturn(ID_FOTO);
        lenient().when(almacenamientoPort.guardaObjetos()).thenReturn(true);
        // Cada envío devuelve el mensaje guardado: el primero es el que queda en la marca.
        lenient().when(delPrograma.enviarDelPrograma(any(), any(), any())).thenAnswer(new org.mockito.stubbing.Answer<Mensaje>() {
            private int enviados;

            @Override
            public Mensaje answer(org.mockito.invocation.InvocationOnMock inv) {
                MensajeId id = ++enviados == 1 ? ID_MENSAJE_1 : MensajeId.of(UUID.randomUUID());
                return Mensaje.delPrograma(id, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        Instant.parse("2026-09-27T15:00:00Z"));
            }
        });
    }

    private record TextosFijos(String soporteConLaTarjeta, String soporteFormal) implements TextosDeBienvenidaPort {
        @Override
        public String grupo() {
            return "";
        }
    }
}
