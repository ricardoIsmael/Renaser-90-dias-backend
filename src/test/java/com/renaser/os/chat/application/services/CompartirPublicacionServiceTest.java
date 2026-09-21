package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.CompartirPublicacionUseCase.CompartirPublicacionCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.community.api.PublicacionParaCompartir;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El caso de uso no guarda nada por su cuenta: delega en {@code EnviarMensajeUseCase}. Asi que lo
 * que estas pruebas verifican es <b>el comando que le arma</b> — que es exactamente donde vivia el
 * bug: la foto viajaba como URL dentro del texto en vez de como media.
 */
@ExtendWith(MockitoExtension.class)
class CompartirPublicacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-14T10:00:00Z");
    /** El pin del encabezado, por codepoint. Ver {@link #elEncabezadoLlevaElPinDelMuro}. */
    private static final int CODEPOINT_PIN = 0x1F4CC;

    @Mock
    private EnviarMensajeUseCase enviarMensajeUseCase;
    @Mock
    private PublicacionMuroFinder publicacionMuroFinder;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private CompartirPublicacionService service;

    private final UserId actor = UserId.of(UUID.randomUUID());
    private final UserId autor = UserId.of(UUID.randomUUID());
    private final ConversacionId conversacionId = ConversacionId.of(UUID.randomUUID());
    private final UUID publicacionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CompartirPublicacionService(enviarMensajeUseCase, publicacionMuroFinder, userSummaryFinder);
        lenient().when(userSummaryFinder.findById(autor)).thenReturn(
                Optional.of(new UserSummary(autor, "Maria Quispe", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(enviarMensajeUseCase.enviar(any())).thenAnswer(inv -> mensajeDe(inv.getArgument(0)));
    }

    /** Lo que devolveria el {@code enviar} real, para que el servicio tenga algo que retornar. */
    private static Mensaje mensajeDe(EnviarMensajeCommand cmd) {
        return Mensaje.escribir(MensajeId.of(UUID.randomUUID()), cmd.conversacionId(), cmd.actorId(), cmd.tipo(),
                cmd.texto(), cmd.mediaBucket(), cmd.mediaRuta(), cmd.mediaMime(), cmd.mediaBytes(),
                cmd.mediaDuracionS(), cmd.respuestaAId(), AHORA);
    }

    private CompartirPublicacionCommand comando() {
        return new CompartirPublicacionCommand(actor, conversacionId, publicacionId);
    }

    private void dadaLaPublicacion(String texto, String bucket, String ruta, String mime) {
        when(publicacionMuroFinder.paraCompartir(publicacionId)).thenReturn(
                Optional.of(new PublicacionParaCompartir(publicacionId, autor, texto, bucket, ruta, mime)));
    }

    private EnviarMensajeCommand comandoCapturado() {
        ArgumentCaptor<EnviarMensajeCommand> captor = ArgumentCaptor.forClass(EnviarMensajeCommand.class);
        verify(enviarMensajeUseCase).enviar(captor.capture());
        return captor.getValue();
    }

    // ─── Con foto: el corazon del bug ─────────────────────────────────────────────────────

    @Test
    void publicacionConFotoViajaComoMensajeDeImagenConBucketYRuta() {
        dadaLaPublicacion("Dia 12 cumplido", "muro", "muro/abc/foto.jpg", "image/jpeg");

        service.compartir(comando());

        EnviarMensajeCommand enviado = comandoCapturado();
        assertThat(enviado.tipo()).isEqualTo(TipoMensaje.IMAGEN);
        assertThat(enviado.mediaBucket()).isEqualTo("muro");
        assertThat(enviado.mediaRuta()).isEqualTo("muro/abc/foto.jpg");
        assertThat(enviado.mediaMime()).isEqualTo("image/jpeg");
        assertThat(enviado.actorId()).isEqualTo(actor);
        assertThat(enviado.conversacionId()).isEqualTo(conversacionId);
    }

    /**
     * La prueba de regresion del bug: el texto persistido NO puede contener una URL. Antes
     * terminaba en {@code "\n📷 Ver foto: https://...X-Amz-Expires=900&X-Amz-Signature=..."}, que
     * moria a los quince minutos y quedaba guardado para siempre.
     */
    @Test
    void elTextoNoLlevaNingunaUrlFirmadaAdentro() {
        dadaLaPublicacion("Dia 12 cumplido", "muro", "muro/abc/foto.jpg", "image/jpeg");

        service.compartir(comando());

        String texto = comandoCapturado().texto();
        assertThat(texto).doesNotContain("http").doesNotContain("X-Amz").doesNotContain("Ver foto");
    }

    @Test
    void publicacionConFotoSinEpigrafeNoDejaComillasVacias() {
        dadaLaPublicacion("   ", "muro", "muro/abc/foto.jpg", "image/jpeg");

        service.compartir(comando());

        // Solo el encabezado: ni salto de linea ni un par de comillas colgando.
        assertThat(comandoCapturado().texto()).isEqualTo("📌 [Compartido del Muro por Maria Quispe]");
    }

    /**
     * La marca que hace que {@code enviar} acepte una ruta que no es del prefijo de la
     * conversacion. Es lo UNICO que separa compartir de que alguien pegue a mano la clave de la
     * foto de otro, asi que si se cae, compartir deja de funcionar (o, si se cae al reves y el
     * controller empieza a mandarla, vuelve el agujero del 2026-09-21).
     */
    @Test
    void elComandoMarcaLaRutaComoDerivadaPorElServidor() {
        dadaLaPublicacion("Dia 12 cumplido", "muro", "muro/abc/foto.jpg", "image/jpeg");

        service.compartir(comando());

        assertThat(comandoCapturado().origenMedia()).isEqualTo(OrigenMedia.MURO_COMPARTIDO);
    }

    // ─── Sin foto ─────────────────────────────────────────────────────────────────────────

    @Test
    void publicacionSinFotoViajaComoMensajeDeTextoSinMedia() {
        dadaLaPublicacion("Hoy me costo pero lo hice", null, null, null);

        service.compartir(comando());

        EnviarMensajeCommand enviado = comandoCapturado();
        assertThat(enviado.tipo()).isEqualTo(TipoMensaje.TEXTO);
        assertThat(enviado.mediaBucket()).isNull();
        assertThat(enviado.mediaRuta()).isNull();
        assertThat(enviado.mediaMime()).isNull();
        assertThat(enviado.texto())
                .isEqualTo("📌 [Compartido del Muro por Maria Quispe]\n\"Hoy me costo pero lo hice\"");
    }

    // ─── Formato del encabezado ───────────────────────────────────────────────────────────

    /**
     * El pin se verifica por <b>codepoint</b>, no comparando dos literales: si el archivo fuente
     * dejara de compilarse en UTF-8, la constante y el literal esperado se degradarian igual y una
     * comparacion de strings pasaria en verde con un encabezado roto en la base.
     */
    @Test
    void elEncabezadoLlevaElPinDelMuro() {
        dadaLaPublicacion("algo", null, null, null);

        service.compartir(comando());

        assertThat(comandoCapturado().texto().codePointAt(0)).isEqualTo(CODEPOINT_PIN);
    }

    /**
     * El nombre sale de {@code users.api}, no de lo que mande el cliente — el comando de entrada
     * ni siquiera tiene un campo donde ponerlo.
     */
    @Test
    void elNombreDelAutorSeResuelveContraUsers() {
        dadaLaPublicacion("algo", null, null, null);

        service.compartir(comando());

        verify(userSummaryFinder).findById(autor);
        assertThat(comandoCapturado().texto()).contains("Maria Quispe");
    }

    @Test
    void siElAutorNoSeResuelveUsaElMismoTextoQueYaMostrabaLaApp() {
        when(userSummaryFinder.findById(autor)).thenReturn(Optional.empty());
        dadaLaPublicacion("algo", null, null, null);

        service.compartir(comando());

        assertThat(comandoCapturado().texto()).startsWith("📌 [Compartido del Muro por Comunidad Renaser]");
    }

    // ─── Errores ──────────────────────────────────────────────────────────────────────────

    @Test
    void publicacionInexistenteNoEnviaNingunMensaje() {
        when(publicacionMuroFinder.paraCompartir(publicacionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compartir(comando()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(publicacionId.toString());

        verify(enviarMensajeUseCase, never()).enviar(any());
    }

    /**
     * Quien no participa de la conversacion recibe 403 y no se guarda nada. El guard no vive en
     * esta clase a proposito — es el de {@code enviar}, con su revalidacion de pertenencia de
     * grupo incluida (ver javadoc de {@link CompartirPublicacionService}); lo que se verifica aca
     * es que el caso de uso <b>no se lo traga ni lo traduce</b>.
     */
    @Test
    void actorQueNoParticipaDeLaConversacionRecibeElRechazoDeEnviar() {
        dadaLaPublicacion("algo", null, null, null);
        // `doThrow(...).when(...)` y no `when(...).thenThrow(...)`: la segunda forma INVOCA el
        // stub que ya dejo `setUp`, con un `null` como argumento, y revienta en el propio armado
        // de la prueba.
        doThrow(new NotAuthorizedException("No eres participante de esta conversación"))
                .when(enviarMensajeUseCase).enviar(any());

        assertThatThrownBy(() -> service.compartir(comando()))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("No eres participante");
    }

    @Test
    void devuelveElMensajeQueGuardoEnviar() {
        dadaLaPublicacion("algo", "muro", "muro/abc/foto.jpg", "image/jpeg");

        Mensaje devuelto = service.compartir(comando());

        assertThat(devuelto.conversacionId()).isEqualTo(conversacionId);
        assertThat(devuelto.emisorId()).isEqualTo(actor);
        assertThat(devuelto.tipo()).isEqualTo(TipoMensaje.IMAGEN);
        assertThat(devuelto.mediaRuta()).isEqualTo("muro/abc/foto.jpg");
    }
}
