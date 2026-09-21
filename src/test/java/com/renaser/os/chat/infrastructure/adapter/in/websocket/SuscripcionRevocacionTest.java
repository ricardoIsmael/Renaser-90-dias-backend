package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.services.AutorizacionDeConversacionService;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.Clock;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Las dos mitades de la revocacion en el canal en vivo, y la regla de autorizacion compartida.
 * Auditoria del 2026-09-18. <b>Los tres casos negativos fallan contra el codigo viejo.</b>
 *
 * <p>Se arma la cadena real —{@link AutorizacionDeConversacionService} de verdad, no un doble—
 * porque justamente lo que se quiere fijar es <i>a quien le pregunta</i> el interceptor. Con el
 * caso de uso mockeado, el test pasaria con el codigo viejo y con el nuevo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuscripcionRevocacionTest {

    private static final UUID CELULA = UUID.randomUUID();
    private static final ConversacionId CONVERSACION = ConversacionId.of(UUID.randomUUID());
    private static final UserId EX_MENTOR = UserId.of(UUID.randomUUID());
    private static final String ID_SESION = "sesion-abc";
    private static final String SOCKET = "socket-1";

    @Mock
    private LoadConversacionPort conversaciones;
    @Mock
    private EsParticipantePort proyeccion;
    @Mock
    private PertenenciaVigentePort pertenenciaVigente;
    @Mock
    private UserSummaryFinder usuarios;

    private MapSessionRepository sesiones;
    private SesionViva sesionViva;
    private SubscripcionAutorizadaInterceptor interceptor;
    @Mock
    private MessageChannel canal;

    @BeforeEach
    void preparar() {
        when(conversaciones.porId(CONVERSACION))
                .thenReturn(Optional.of(Conversacion.crearCelula(CONVERSACION, CELULA, Instant.EPOCH)));
        when(usuarios.findById(EX_MENTOR)).thenReturn(Optional.of(
                new UserSummary(EX_MENTOR, "Ex mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        // Conserva su fila en la proyeccion y sigue siendo integrante vigente: el caso normal.
        when(proyeccion.esParticipante(CONVERSACION, EX_MENTOR)).thenReturn(true);
        when(pertenenciaVigente.perteneceAlGrupo(CELULA, EX_MENTOR)).thenReturn(true);

        sesiones = new MapSessionRepository(new ConcurrentHashMap<>());
        sesiones.save(new MapSession(ID_SESION));
        sesionViva = new SesionViva(sesiones, relojFijo());
        interceptor = new SubscripcionAutorizadaInterceptor(
                new AutorizacionDeConversacionService(conversaciones, proyeccion, pertenenciaVigente, usuarios),
                usuarios, sesionViva);
    }

    /**
     * El caso que motivo el arreglo: quien roto de grupo conserva su fila en la proyeccion —esa
     * tabla se reconcilia por evento, y para un grupo cuyo periodo termino no se reconcilia nunca—
     * pero ya no es integrante vigente. El codigo viejo preguntaba a la proyeccion y lo dejaba
     * entrar a leer en vivo el chat de gente que ya no acompana.
     */
    @Test
    @DisplayName("un ex integrante con fila en la proyeccion no se puede suscribir al grupo")
    void laProyeccionViejaYaNoAlcanzaParaSuscribirse() {
        // La fila vieja sigue ahi; la pertenencia vigente ya no.
        when(pertenenciaVigente.perteneceAlGrupo(CELULA, EX_MENTOR)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(suscripcionA(CONVERSACION), canal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("No sos participante");
    }

    @Test
    @DisplayName("un integrante vigente se suscribe sin problema")
    void elIntegranteVigenteSeSuscribe() {
        Message<byte[]> suscripcion = suscripcionA(CONVERSACION);

        assertThat(interceptor.preSend(suscripcion, canal)).isSameAs(suscripcion);
    }

    /**
     * La primera mitad de la revocacion. El handshake leia la sesion una sola vez: quien tuviera un
     * token robado conservaba el socket aunque la victima cambiara la contrasena, y podia
     * suscribirse a conversaciones NUEVAS despues de que {@code cerrarTodas()} corriera.
     */
    @Test
    @DisplayName("con la sesion ya cerrada no se puede registrar una suscripcion nueva")
    void laSesionCerradaNoRegistraSuscripcionesNuevas() {
        sesiones.deleteById(ID_SESION);

        assertThatThrownBy(() -> interceptor.preSend(suscripcionA(CONVERSACION), canal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Tu sesion ya no es valida");
    }

    /**
     * La segunda mitad, la que el canal de entrada no puede cubrir: a una suscripcion YA registrada
     * el broker le sigue escribiendo sin preguntarle nada a nadie. Sin este interceptor, suspender
     * a alguien conectado no le quitaba el chat en vivo.
     */
    @Test
    @DisplayName("la entrega se corta cuando la sesion detras del socket ya no existe")
    void laEntregaSeCortaAlRevocarLaSesion() {
        EntregaConSesionVivaInterceptor entrega = new EntregaConSesionVivaInterceptor(sesionViva);
        sesionViva.sigueViva(SOCKET, ID_SESION); // el socket queda registrado, como al conectarse
        Message<byte[]> aviso = mensajeDelBroker();

        assertThat(entrega.preSend(aviso, canal)).as("con la sesion viva, entrega").isSameAs(aviso);

        sesiones.deleteById(ID_SESION);
        assertThat(entrega.preSend(mensajeDelBroker(), canal))
                .as("revocada la sesion, el mensaje se descarta para ese suscriptor")
                .isNull();
    }

    private static Message<byte[]> suscripcionA(ConversacionId conversacionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversaciones/" + conversacionId.value());
        accessor.setSessionId(SOCKET);
        Map<String, Object> atributos = new HashMap<>();
        atributos.put(ActorHandshakeInterceptor.ATRIBUTO_ACTOR_ID, EX_MENTOR.value());
        atributos.put(ActorHandshakeInterceptor.ATRIBUTO_ID_SESION, ID_SESION);
        accessor.setSessionAttributes(atributos);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> mensajeDelBroker() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination("/topic/conversaciones/" + CONVERSACION.value());
        accessor.setSessionId(SOCKET);
        accessor.setSubscriptionId("sub-0");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /**
     * Fijo a proposito: la ventana de gracia de {@link SesionViva} se mide contra este reloj, y con
     * el detenido la comprobacion no se reusa entre un caso y el siguiente por accidente de tiempo
     * real. Cada llamada vuelve a preguntarle al repositorio salvo que el test lo quiera distinto.
     */
    private static Clock relojFijo() {
        return new Clock() {
            private long avance = 0;

            @Override
            public Instant now() {
                avance += 60_000; // cada consulta cae fuera de la gracia de 10 s
                return Instant.EPOCH.plusMillis(avance);
            }

            @Override
            public LocalDate today() {
                return LocalDate.EPOCH;
            }
        };
    }
}
