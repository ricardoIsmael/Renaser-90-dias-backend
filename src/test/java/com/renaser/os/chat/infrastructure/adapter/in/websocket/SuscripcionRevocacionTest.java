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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las dos mitades de la revocacion en el canal en vivo, y la regla de autorizacion compartida.
 * Auditoria del 2026-09-18, ampliada el 2026-09-21. <b>Los casos negativos fallan contra el codigo
 * viejo.</b>
 *
 * <p>Se arma la cadena real —{@link AutorizacionDeConversacionService} de verdad, no un doble—
 * porque justamente lo que se quiere fijar es <i>a quien le pregunta</i> el interceptor. Con el
 * caso de uso mockeado, el test pasaria con el codigo viejo y con el nuevo.
 *
 * <p>Las dos formas de revocar no se pisan y por eso se prueban por separado: matar la sesion
 * (suspension, robo de token, reset de contrasena) y apagar la pertenencia dejando la sesion
 * intacta (rotacion, traslado, baja de staff). El agujero que motivo la segunda tanda es que solo
 * la primera cortaba la entrega.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuscripcionRevocacionTest {

    private static final UUID CELULA = UUID.randomUUID();
    private static final ConversacionId CONVERSACION = ConversacionId.of(UUID.randomUUID());
    private static final UserId EX_MENTOR = UserId.of(UUID.randomUUID());
    private static final String ID_SESION = "sesion-abc";
    private static final String SOCKET = "socket-1";

    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());
    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UserId EX_ADMIN = UserId.of(UUID.randomUUID());
    private static final String ID_SESION_ADMIN = "sesion-def";
    private static final String SOCKET_ADMIN = "socket-2";

    @Mock
    private LoadConversacionPort conversaciones;
    @Mock
    private EsParticipantePort proyeccion;
    @Mock
    private PertenenciaVigentePort pertenenciaVigente;
    @Mock
    private UserSummaryFinder usuarios;

    private MapSessionRepository sesiones;
    private Clock reloj;
    private SesionViva sesionViva;
    private AutorizacionViva autorizacionViva;
    private SubscripcionAutorizadaInterceptor interceptor;
    private EntregaAutorizadaInterceptor entrega;
    @Mock
    private MessageChannel canal;

    @BeforeEach
    void preparar() {
        when(conversaciones.porId(CONVERSACION))
                .thenReturn(Optional.of(Conversacion.crearCelula(CONVERSACION, CELULA, Instant.EPOCH)));
        when(conversaciones.porId(SOPORTE)).thenReturn(Optional.of(
                Conversacion.crearSoporte(SOPORTE, APRENDIZ, "Soporte de Ana", Instant.EPOCH)));
        when(usuarios.findById(EX_MENTOR)).thenReturn(Optional.of(
                new UserSummary(EX_MENTOR, "Ex mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(usuarios.findById(EX_ADMIN)).thenReturn(Optional.of(
                new UserSummary(EX_ADMIN, "Ex admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        // Conserva su fila en la proyeccion y sigue siendo integrante vigente: el caso normal.
        when(proyeccion.esParticipante(CONVERSACION, EX_MENTOR)).thenReturn(true);
        when(proyeccion.esParticipante(SOPORTE, EX_ADMIN)).thenReturn(true);
        when(pertenenciaVigente.perteneceAlGrupo(CELULA, EX_MENTOR)).thenReturn(true);

        sesiones = new MapSessionRepository(new ConcurrentHashMap<>());
        sesiones.save(new MapSession(ID_SESION));
        sesiones.save(new MapSession(ID_SESION_ADMIN));
        reloj = relojQueAvanza();
        armarCanales(reloj);
    }

    private void armarCanales(Clock reloj) {
        AutorizacionDeConversacionService regla =
                new AutorizacionDeConversacionService(conversaciones, proyeccion, pertenenciaVigente, usuarios);
        sesionViva = new SesionViva(sesiones, reloj);
        autorizacionViva = new AutorizacionViva(regla, reloj);
        interceptor = new SubscripcionAutorizadaInterceptor(regla, usuarios, sesionViva, autorizacionViva);
        entrega = new EntregaAutorizadaInterceptor(sesionViva, autorizacionViva);
    }

    // ---------------------------------------------------------------- canal de entrada (SUBSCRIBE)

    /**
     * El caso que motivo el arreglo del 18: quien roto de grupo conserva su fila en la proyeccion
     * —esa tabla se reconcilia por evento, y para un grupo cuyo periodo termino no se reconcilia
     * nunca— pero ya no es integrante vigente. El codigo viejo preguntaba a la proyeccion y lo
     * dejaba entrar a leer en vivo el chat de gente que ya no acompana.
     */
    @Test
    @DisplayName("un ex integrante con fila en la proyeccion no se puede suscribir al grupo")
    void laProyeccionViejaYaNoAlcanzaParaSuscribirse() {
        // La fila vieja sigue ahi; la pertenencia vigente ya no.
        when(pertenenciaVigente.perteneceAlGrupo(CELULA, EX_MENTOR)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(suscripcionA(CONVERSACION), canal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("No eres participante");
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

    // ------------------------------------------------------------------- canal de salida (MESSAGE)

    @Nested
    @DisplayName("la entrega a una suscripcion YA registrada")
    class Entrega {

        @BeforeEach
        void suscribirElSocket() {
            suscribir(SOCKET, ID_SESION, EX_MENTOR, CONVERSACION);
        }

        /**
         * La segunda mitad, la que el canal de entrada no puede cubrir: a una suscripcion YA
         * registrada el broker le sigue escribiendo sin preguntarle nada a nadie. Sin el
         * interceptor de salida, suspender a alguien conectado no le quitaba el chat en vivo.
         */
        @Test
        @DisplayName("se corta cuando la sesion detras del socket ya no existe")
        void seCortaAlRevocarLaSesion() {
            Message<byte[]> aviso = entregaA(SOCKET, CONVERSACION);

            assertThat(entrega.preSend(aviso, canal)).as("con la sesion viva, entrega").isSameAs(aviso);

            sesiones.deleteById(ID_SESION);
            assertThat(entrega.preSend(entregaA(SOCKET, CONVERSACION), canal))
                    .as("revocada la sesion, el mensaje se descarta para ese suscriptor")
                    .isNull();
        }

        /**
         * <b>La regresion del 2026-09-21.</b> Este es el caso que no existia y por el que el
         * agujero sobrevivio al arreglo anterior: la pertenencia se apaga <i>sin tocar la sesion</i>
         * —rotar al mentor, trasladar al aprendiz—, el grupo sigue operativo y los que se quedaron
         * siguen escribiendo. El codigo viejo solo preguntaba si la sesion existia, que es la
         * pregunta equivocada: existe, y por eso le entregaba el texto completo de cada mensaje
         * nuevo a alguien a quien el mismo sistema le responde 403 por REST.
         */
        @Test
        @DisplayName("se corta cuando la pertenencia se revoca aunque la sesion siga viva")
        void seCortaAlRevocarLaPertenenciaConLaSesionIntacta() {
            Message<byte[]> antes = entregaA(SOCKET, CONVERSACION);
            assertThat(entrega.preSend(antes, canal)).as("siendo integrante vigente, entrega").isSameAs(antes);

            // Rotacion o traslado: se apaga la pertenencia y NADA MAS. La sesion sigue en el
            // repositorio, el socket abierto y la suscripcion registrada en el broker.
            when(pertenenciaVigente.perteneceAlGrupo(CELULA, EX_MENTOR)).thenReturn(false);

            assertThat(sesiones.findById(ID_SESION)).as("la sesion no se toco").isNotNull();
            assertThat(entrega.preSend(entregaA(SOCKET, CONVERSACION), canal))
                    .as("revocada la pertenencia, el mensaje se descarta para ese suscriptor")
                    .isNull();
        }

        /**
         * La misma regla, por la otra rama que el modulo arreglo el mismo dia: un SOPORTE se gana
         * por ROL de staff vigente, no por la fila de participante. Degradar a un administrador le
         * cortaba el acceso por REST desde el arreglo del soporte, pero el canal en vivo seguia
         * entregandole el chat privado de cada aprendiz. La entrega no repite esa regla: le
         * pregunta al mismo caso de uso, asi que la hereda.
         */
        @Test
        @DisplayName("se corta cuando alguien deja de ser staff, en el chat de soporte")
        void seCortaAlPerderElRolDeStaff() {
            suscribir(SOCKET_ADMIN, ID_SESION_ADMIN, EX_ADMIN, SOPORTE);
            Message<byte[]> antes = entregaA(SOCKET_ADMIN, SOPORTE);
            assertThat(entrega.preSend(antes, canal)).as("siendo ADMIN, entrega").isSameAs(antes);

            // Degradado: conserva la fila de participante hasta que el listener la borre.
            when(usuarios.findById(EX_ADMIN)).thenReturn(Optional.of(
                    new UserSummary(EX_ADMIN, "Ex admin", null, UserRole.MENTOR, UserStatus.ACTIVE)));

            assertThat(entrega.preSend(entregaA(SOCKET_ADMIN, SOPORTE), canal))
                    .as("sin el rol vigente, el mensaje se descarta")
                    .isNull();
        }

        /**
         * Los frames del propio socket —CONNECTED, RECEIPT, ERROR— no nombran ninguna conversacion
         * y tienen que pasar: rechazarlos romperia toda conexion legitima. Es el hueco que
         * {@link SesionViva#sigueViva(String)} deja abierto a proposito, y la razon por la que la
         * pregunta de pertenencia se hace solo cuando hay un destino de conversacion.
         */
        @Test
        @DisplayName("no le pide autorizacion a los frames de control del socket")
        void losFramesDeControlPasan() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
            accessor.setSessionId(SOCKET);
            Message<byte[]> conectado = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThat(entrega.preSend(conectado, canal)).isSameAs(conectado);
        }

        /**
         * Al reves que la sesion, un socket desconocido NO recibe contenido de una conversacion: en
         * este hueco no cae ningun frame legitimo —a un destino de conversacion no se le entrega
         * nada antes de que el cliente se suscriba, y no puede suscribirse antes del CONNECTED—,
         * asi que lo unico que queda es un socket del que no se sabe quien es.
         */
        @Test
        @DisplayName("un socket del que no se sabe quien es no recibe nada de una conversacion")
        void elSocketDesconocidoNoRecibe() {
            autorizacionViva.alDesconectarse(new SessionDisconnectEvent(
                    this, entregaA(SOCKET, CONVERSACION), SOCKET, CloseStatus.NORMAL));

            assertThat(entrega.preSend(entregaA(SOCKET, CONVERSACION), canal)).isNull();
        }
    }

    /**
     * El canal de salida pasa una vez por mensaje <b>y por suscriptor</b>, y preguntar la
     * pertenencia cuesta tres consultas. Sin reuso, un grupo de diez pagaria treinta por mensaje:
     * la respuesta se recuerda por (socket, conversacion) durante {@link SesionViva#GRACIA}, igual
     * que la de la sesion. Si alguien saca esa memoria, este test avisa antes que la base.
     */
    @Test
    @DisplayName("no vuelve a preguntar la pertenencia en cada mensaje, dentro de la gracia")
    void laRespuestaSeReusaDentroDeLaGracia() {
        armarCanales(relojDetenido());
        suscribir(SOCKET, ID_SESION, EX_MENTOR, CONVERSACION);
        clearInvocations(conversaciones, pertenenciaVigente); // lo que costo el SUBSCRIBE no se cuenta

        for (int i = 0; i < 5; i++) {
            assertThat(entrega.preSend(entregaA(SOCKET, CONVERSACION), canal)).isNotNull();
        }

        verify(conversaciones, times(1)).porId(CONVERSACION);
        verify(pertenenciaVigente, times(1)).perteneceAlGrupo(CELULA, EX_MENTOR);
    }

    /**
     * Lo de arriba se prueba contra frames armados a mano; esto lo prueba contra el broker de
     * verdad. Se monta un {@link SimpleBrokerMessageHandler} con los tres canales reales, se
     * registra la suscripcion como la registra un cliente y se publica como publica
     * {@code RedisChatSubscriberConfig}, con la guarda puesta en el canal de salida.
     *
     * <p><b>Por que hace falta.</b> Toda la guarda depende de como el broker arma el frame de
     * entrega —que cabeceras trae y con que accessor—, y eso no es algo que el modulo decida: es
     * comportamiento de Spring. Un fixture hecho a mano puede coincidir con lo que uno cree y no
     * con lo que pasa, que es exactamente lo que ocurrio la vez anterior.
     */
    @Test
    @DisplayName("contra el broker de verdad, la entrega se corta al revocar la pertenencia")
    void elBrokerDeVerdadTampocoEntregaDespuesDeRevocar() {
        ExecutorSubscribableChannel haciaElCliente = new ExecutorSubscribableChannel();
        ExecutorSubscribableChannel desdeElCliente = new ExecutorSubscribableChannel();
        ExecutorSubscribableChannel canalDelBroker = new ExecutorSubscribableChannel();
        List<Message<?>> recibidosPorElCliente = new ArrayList<>();
        haciaElCliente.addInterceptor(entrega);
        haciaElCliente.subscribe(recibidosPorElCliente::add);
        SimpleBrokerMessageHandler broker = new SimpleBrokerMessageHandler(
                desdeElCliente, haciaElCliente, canalDelBroker, List.of("/topic"));
        broker.start();

        desdeElCliente.addInterceptor(interceptor); // la guarda de entrada, tambien la de verdad
        desdeElCliente.send(frameDelCliente(SimpMessageType.CONNECT));
        desdeElCliente.send(suscripcionDe(SOCKET, ID_SESION, EX_MENTOR, CONVERSACION));
        recibidosPorElCliente.clear(); // el CONNECT_ACK, que no nombra conversacion y pasa

        canalDelBroker.send(publicacionEn(CONVERSACION));
        assertThat(recibidosPorElCliente).as("siendo integrante vigente, el broker le entrega").hasSize(1);

        // Rotacion o traslado: se apaga la pertenencia y nada mas. La suscripcion sigue registrada
        // en el broker y la sesion sigue en el repositorio.
        when(pertenenciaVigente.perteneceAlGrupo(CELULA, EX_MENTOR)).thenReturn(false);
        recibidosPorElCliente.clear();

        canalDelBroker.send(publicacionEn(CONVERSACION));
        assertThat(recibidosPorElCliente).as("revocada la pertenencia, no le llega nada").isEmpty();
        broker.stop();
    }

    // --------------------------------------------------------------------------------- utilidades

    private static String destinoDe(ConversacionId conversacionId) {
        return "/topic/conversaciones/" + conversacionId.value();
    }

    /** Un frame del cliente hacia el broker, con el socket que lo manda. */
    private static Message<byte[]> frameDelCliente(SimpMessageType tipo) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(tipo);
        accessor.setSessionId(SOCKET);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /**
     * Lo que publica el servidor: sin socket destinatario, porque va a la conversacion entera. El
     * broker lo copia una vez por suscriptor y es ahi donde aparece el socket de cada uno.
     */
    private static Message<byte[]> publicacionEn(ConversacionId conversacionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destinoDe(conversacionId));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /**
     * Prepara el socket <b>suscribiendose de verdad</b>, por el canal de entrada, que es como se
     * puebla en produccion: es el SUBSCRIBE el ultimo frame que trae los atributos del handshake.
     * Hacerlo con un {@code SessionConnectedEvent} no serviria — ese evento llega sin atributos.
     */
    private void suscribir(String socketId, String idSesion, UserId actor, ConversacionId conversacionId) {
        assertThat(interceptor.preSend(suscripcionDe(socketId, idSesion, actor, conversacionId), canal))
                .as("el SUBSCRIBE de preparacion tiene que pasar").isNotNull();
    }

    private static Message<byte[]> suscripcionA(ConversacionId conversacionId) {
        return suscripcionDe(SOCKET, ID_SESION, EX_MENTOR, conversacionId);
    }

    private static Message<byte[]> suscripcionDe(String socketId, String idSesion, UserId actor,
                                                 ConversacionId conversacionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destinoDe(conversacionId));
        accessor.setSessionId(socketId);
        accessor.setSubscriptionId("sub-0");
        accessor.setSessionAttributes(atributosDe(idSesion, actor));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /**
     * Un frame de entrega tal como lo arma el broker, <b>hasta en el tipo del accessor</b>.
     *
     * <p>Antes este helper usaba {@code StompHeaderAccessor.create(StompCommand.MESSAGE)} y eso
     * hacia pasar un test que en produccion no probaba nada:
     * {@code SimpleBrokerMessageHandler.sendMessageToSubscribers} arma cada copia con un
     * {@code SimpMessageHeaderAccessor} —no un STOMP—, asi que un interceptor que pidiera
     * {@code getAccessor(message, StompHeaderAccessor.class)} recibia {@code null} justo para los
     * mensajes que tenia que filtrar. Si alguien vuelve a leer las cabeceras por el tipo del
     * accessor, estos casos avisan.
     *
     * <p>Tampoco lleva los atributos del handshake, que es lo otro que el broker no arrastra y por
     * lo que el actor tiene que salir de un registro propio.
     */
    private static Message<byte[]> entregaA(String socketId, ConversacionId conversacionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination("/topic/conversaciones/" + conversacionId.value());
        accessor.setSessionId(socketId);
        accessor.setSubscriptionId("sub-0");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Map<String, Object> atributosDe(String idSesion, UserId actor) {
        Map<String, Object> atributos = new HashMap<>();
        atributos.put(ActorHandshakeInterceptor.ATRIBUTO_ACTOR_ID, actor.value());
        atributos.put(ActorHandshakeInterceptor.ATRIBUTO_ID_SESION, idSesion);
        return atributos;
    }

    /**
     * Avanza a proposito: la ventana de gracia se mide contra este reloj, y con el corriendo la
     * comprobacion no se reusa entre un caso y el siguiente por accidente de tiempo real. Cada
     * llamada vuelve a preguntar salvo que el test lo quiera distinto.
     */
    private static Clock relojQueAvanza() {
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

    /** Detenido: todo cae DENTRO de la gracia, que es lo que hace visible el reuso. */
    private static Clock relojDetenido() {
        return new Clock() {
            @Override
            public Instant now() {
                return Instant.EPOCH;
            }

            @Override
            public LocalDate today() {
                return LocalDate.EPOCH;
            }
        };
    }
}
