package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.CompartirPublicacionUseCase.CompartirPublicacionCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.community.api.PublicacionParaCompartir;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
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

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las dos puertas por las que una clave del Muro puede entrar a un mensaje de chat, contra los
 * objetos REALES y no contra dobles: {@code CompartirPublicacionService} montado sobre el
 * {@code MensajeService} de verdad.
 *
 * <p><b>Por que una clase aparte.</b> El agujero del 2026-09-21 no estaba en ninguna de las dos
 * clases por separado —cada una, leida sola, se defendia— sino en que eran DOS caminos al mismo
 * efecto con DOS reglas distintas: {@code paraCompartir} miraba la visibilidad y
 * {@code POST .../messages} miraba el prefijo. Una prueba que mockee {@code enviar} no puede ver
 * eso, porque lo que falla es la composicion.
 */
@ExtendWith(MockitoExtension.class)
class MediaDelMuroEnElChatTest {

    private static final Instant AHORA = Instant.parse("2026-09-21T15:00:00Z");

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private EsParticipantePort esParticipantePort;
    @Mock
    private PertenenciaVigentePort pertenenciaVigentePort;
    @Mock
    private MarcarLeidoPort marcarLeidoPort;
    @Mock
    private SaveMensajePort saveMensajePort;
    @Mock
    private LoadMensajePort loadMensajePort;
    @Mock
    private PublicarMensajeFanoutPort publicarMensajeFanoutPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private PublicacionMuroFinder publicacionMuroFinder;

    private MensajeService mensajeService;
    private CompartirPublicacionService compartirService;

    private final UserId actor = UserId.of(UUID.randomUUID());
    private final UserId autor = UserId.of(UUID.randomUUID());
    private final ConversacionId conversacionId = ConversacionId.of(UUID.randomUUID());
    private final UUID publicacionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mensajeService = new MensajeService(loadConversacionPort, esParticipantePort, pertenenciaVigentePort,
                marcarLeidoPort, saveMensajePort, loadMensajePort, publicarMensajeFanoutPort, userSummaryFinder,
                almacenamientoPort, FixedClock.at(AHORA), UUID::randomUUID);
        compartirService = new CompartirPublicacionService(mensajeService, publicacionMuroFinder, userSummaryFinder);

        lenient().when(userSummaryFinder.findById(actor)).thenReturn(
                Optional.of(new UserSummary(actor, "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(autor)).thenReturn(
                Optional.of(new UserSummary(autor, "Maria Quispe", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(loadConversacionPort.porId(conversacionId))
                .thenReturn(Optional.of(Conversacion.crearGlobal(conversacionId, AHORA)));
        lenient().when(esParticipantePort.esParticipante(conversacionId, actor)).thenReturn(true);
        lenient().when(saveMensajePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void dadaLaPublicacionVisible(String ruta) {
        when(publicacionMuroFinder.paraCompartir(publicacionId)).thenReturn(
                Optional.of(new PublicacionParaCompartir(publicacionId, autor, "Dia 12", "muro", ruta,
                        "image/jpeg")));
    }

    // ─── La funcion sigue viva ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("compartir una publicacion visible guarda el mensaje con la media del Muro")
    void compartirSigueFuncionando() {
        String portada = "muro/fotos/" + autor.value() + "/" + UUID.randomUUID();
        dadaLaPublicacionVisible(portada);

        Mensaje compartido = compartirService.compartir(
                new CompartirPublicacionCommand(actor, conversacionId, publicacionId));

        assertThat(compartido.tipo()).isEqualTo(TipoMensaje.IMAGEN);
        // La referencia al objeto, no una copia: es el punto de compartir.
        assertThat(compartido.mediaRuta()).isEqualTo(portada);
        assertThat(compartido.texto()).contains("Maria Quispe");
        verify(saveMensajePort).save(any());
    }

    @Test
    @DisplayName("compartir una publicacion creada desde una Roca tambien pasa (clave `rocas/`)")
    void compartirUnaPublicacionDeRocasSigueFuncionando() {
        String evidencia = "rocas/" + autor.value() + "/" + UUID.randomUUID();
        dadaLaPublicacionVisible(evidencia);

        Mensaje compartido = compartirService.compartir(
                new CompartirPublicacionCommand(actor, conversacionId, publicacionId));

        assertThat(compartido.mediaRuta()).isEqualTo(evidencia);
        verify(saveMensajePort).save(any());
    }

    // ─── La puerta de al lado, cerrada ────────────────────────────────────────────────────

    /**
     * La MISMA clave que el test de arriba deja pasar, ahora tecleada por el atacante en el cuerpo
     * de {@code POST .../messages}. Es el hallazgo entero en dos lineas: lo que cambia no es la
     * clave, es quien la eligio.
     */
    @Test
    @DisplayName("esa misma clave, pegada a mano en el POST, no entra")
    void laMismaClavePegadaAManoNoEntra() {
        String portada = "muro/fotos/" + autor.value() + "/" + UUID.randomUUID();

        assertThatThrownBy(() -> mensajeService.enviar(new EnviarMensajeCommand(actor, conversacionId,
                TipoMensaje.IMAGEN, null, "muro", portada, "image/jpeg", null, null, null,
                OrigenMedia.CLIENTE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tiene que ser de esta conversacion");

        verify(saveMensajePort, never()).save(any());
    }

    /**
     * La puerta de visibilidad vive en {@code paraCompartir} y es la UNICA: una publicacion oculta
     * —lo que deja "borrar mi publicacion" y la moderacion— no vuelve del finder, asi que no hay
     * ruta que marcar como derivada por el servidor y no se guarda nada.
     */
    @Test
    @DisplayName("una publicacion oculta no se comparte, y no deja mensaje atras")
    void unaPublicacionOcultaNoLlegaAlChat() {
        when(publicacionMuroFinder.paraCompartir(publicacionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> compartirService.compartir(
                new CompartirPublicacionCommand(actor, conversacionId, publicacionId)))
                .isInstanceOf(NoSuchElementException.class);

        verify(saveMensajePort, never()).save(any());
    }
}
