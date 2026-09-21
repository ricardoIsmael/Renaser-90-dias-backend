package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.MarcarLeidoUseCase.MarcarLeidoCommand;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.application.ports.out.participante.ConversacionesDeUsuarioPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.ports.out.participante.QuitarParticipantePort;
import com.renaser.os.chat.application.ports.out.presencia.PresenciaPort;
import com.renaser.os.chat.application.ports.out.presencia.PublicarPresenciaFanoutPort;
import com.renaser.os.chat.application.services.AutorizacionDeConversacionService;
import com.renaser.os.chat.application.services.ConversacionService;
import com.renaser.os.chat.application.services.ConversacionSoporteService;
import com.renaser.os.chat.application.services.MensajeService;
import com.renaser.os.chat.application.services.PresenciaService;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * <b>La regresion del hallazgo</b>: a un administrador le bajan el rol y deja de ver el chat de
 * soporte de los aprendices ajenos — por REST, por el canal en vivo, por la presencia y en la
 * bandeja. <b>Los casos negativos fallan contra el codigo viejo</b>, donde la baja de rol no
 * borraba ninguna fila y la fila era toda la prueba que la regla de acceso pedia.
 *
 * <p><b>Por que se arma la cadena de verdad</b> y no con dobles: lo que se quiere fijar es
 * justamente <i>quien le pregunta a quien</i>. El listener real, el servicio de soporte real y los
 * CUATRO guardas reales —{@code MensajeService} (leer y enviar), {@code ConversacionService}
 * (marcar leido), {@code PresenciaService} (leer y repartir el aviso de conexion) y
 * {@code AutorizacionDeConversacionService} (la suscripcion STOMP)—, porque cada uno guarda su
 * propia copia de la regla y arreglar dos deja la mitad de la superficie abierta. Con el caso de
 * uso mockeado, esto pasaria con el codigo viejo y con el nuevo.
 *
 * <p><b>Por que vive en este paquete</b> y no junto a los servicios: {@code
 * RolDeUsuarioCambiadoSoporteListener} es package-private, y ensancharle la visibilidad para que
 * un test lo alcance seria cambiar el codigo por el test. Lo unico que aporta el paquete es poder
 * construirlo.
 *
 * <p>Sin Spring y sin Postgres. {@code participantes_conversacion} se simula con un mapa en
 * memoria: es la proyeccion cuya obsolescencia es el hallazgo, asi que tiene que ser un dato que
 * el test pueda dejar viejo a proposito.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevocacionDeSoportePorBajaDeRolTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-21T10:00:00Z"));

    /** La aprendiz dueña del soporte. Nunca tiene que perder nada. */
    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    /** El que sube a ADMIN y despues baja. */
    private static final UserId BRUNO = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));

    private static final Conversacion SOPORTE_DE_ANA = Conversacion.crearSoporte(
            ConversacionId.of(UUID.fromString("33333333-3333-4333-8333-333333333333")), ANA,
            "Soporte - Ana Perez", CLOCK.now());
    /** Un DM entre los dos: el cinturon no puede recortar de mas. Nadie pierde un DM por cambiar
     * de rol, y eso tiene que seguir siendo verdad despues del arreglo. */
    private static final Conversacion DM_ANA_BRUNO = Conversacion.crearDirecta(
            ConversacionId.of(UUID.fromString("44444444-4444-4444-8444-444444444444")), "a:b", CLOCK.now());

    /** La proyeccion `participantes_conversacion`, que es la que se queda vieja. */
    private final Map<ConversacionId, Set<UserId>> proyeccion = new HashMap<>();
    /** Los canales a los que salio un aviso de presencia. */
    private final List<ConversacionId> avisosDePresencia = new ArrayList<>();
    /** El rol VIGENTE de Bruno en `usuarios`, que el test mueve como lo haria el panel. */
    private UserRole rolDeBruno = UserRole.MENTOR;

    @Mock
    private LoadConversacionPort conversaciones;
    @Mock
    private SaveConversacionPort guardarConversacion;
    @Mock
    private AgregarParticipantePort agregarParticipante;
    @Mock
    private QuitarParticipantePort quitarParticipante;
    @Mock
    private EsParticipantePort esParticipante;
    @Mock
    private PertenenciaVigentePort pertenenciaVigente;
    @Mock
    private UserSummaryFinder usuarios;
    @Mock
    private ParticipacionProgramaFinder participaciones;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private PlatformTransactionManager transacciones;
    @Mock
    private MarcarLeidoPort marcarLeidoPort;
    @Mock
    private ContarNoLeidosPort contarNoLeidos;
    @Mock
    private ListarUsuariosDeConversacionPort roster;
    @Mock
    private LoadMensajePort cargarMensajes;
    @Mock
    private SaveMensajePort guardarMensaje;
    @Mock
    private PublicarMensajeFanoutPort fanoutMensajes;
    @Mock
    private AlmacenamientoPort almacenamiento;
    @Mock
    private PresenciaPort presenciaPort;
    @Mock
    private PublicarPresenciaFanoutPort fanoutPresencia;
    @Mock
    private ConversacionesDeUsuarioPort conversacionesDeUsuario;

    private RolDeUsuarioCambiadoSoporteListener listener;
    private ConversacionSoporteService soporte;
    private AutorizacionDeConversacionService autorizacion;
    private MensajeService mensajes;
    private ConversacionService bandeja;
    private PresenciaService presencia;

    @BeforeEach
    void preparar() {
        proyeccion.clear();
        avisosDePresencia.clear();
        rolDeBruno = UserRole.MENTOR;
        // La conversacion nace con la aprendiz adentro; el DM, con los dos.
        proyeccion.put(SOPORTE_DE_ANA.id(), new LinkedHashSet<>(List.of(ANA)));
        proyeccion.put(DM_ANA_BRUNO.id(), new LinkedHashSet<>(List.of(ANA, BRUNO)));

        when(conversaciones.porId(any())).thenAnswer(inv -> porId(inv.getArgument(0)));
        when(conversaciones.deSoporte()).thenReturn(List.of(SOPORTE_DE_ANA));
        when(conversaciones.porClaveDirecta(anyString())).thenAnswer(inv ->
                Conversacion.claveSoporteDe(ANA).equals(inv.getArgument(0))
                        ? Optional.of(SOPORTE_DE_ANA) : Optional.empty());
        when(conversaciones.misConversaciones(any())).thenAnswer(inv -> List.of(SOPORTE_DE_ANA, DM_ANA_BRUNO)
                .stream().filter(c -> participantesDe(c.id()).contains((UserId) inv.getArgument(0))).toList());

        when(esParticipante.esParticipante(any(), any()))
                .thenAnswer(inv -> participantesDe(inv.getArgument(0)).contains((UserId) inv.getArgument(1)));
        doAnswer(inv -> {
            Participante p = inv.getArgument(0);
            participantesDe(p.conversacionId()).add(p.usuarioId());
            return null;
        }).when(agregarParticipante).agregar(any());
        doAnswer(inv -> {
            participantesDe(inv.getArgument(0)).remove((UserId) inv.getArgument(1));
            return null;
        }).when(quitarParticipante).quitar(any(), any());

        when(usuarios.findById(any())).thenAnswer(inv -> perfilDe(inv.getArgument(0)));
        when(participaciones.deParticipante(any())).thenAnswer(inv -> {
            UserId id = inv.getArgument(0);
            // Bruno es staff: tiene cuenta pero nunca entro al programa de 90 dias (inscrito=false).
            // Ana si. El fixture tiene que ser coherente o tapa lo que dice verificar.
            return Optional.of(participacion(id, rolVigenteDe(id), id.equals(ANA)));
        });

        when(conversacionesDeUsuario.conversacionesDe(any())).thenAnswer(inv -> List.of(SOPORTE_DE_ANA, DM_ANA_BRUNO)
                .stream().filter(c -> participantesDe(c.id()).contains((UserId) inv.getArgument(0)))
                .map(Conversacion::id).toList());
        when(roster.usuariosDe(any())).thenAnswer(inv -> List.copyOf(participantesDe(inv.getArgument(0))));
        when(presenciaPort.enLineaDe(any())).thenReturn(Set.of());
        doAnswer(inv -> {
            avisosDePresencia.addAll(inv.getArgument(2));
            return null;
        }).when(fanoutPresencia).publicar(any(), anyBoolean(), any());
        when(cargarMensajes.pagina(any(), any(), anyInt())).thenReturn(List.of());

        soporte = new ConversacionSoporteService(conversaciones, guardarConversacion, agregarParticipante,
                quitarParticipante, esParticipante, usuarios, participaciones, CLOCK, idGenerator, transacciones);
        listener = new RolDeUsuarioCambiadoSoporteListener(soporte, soporte);
        autorizacion = new AutorizacionDeConversacionService(conversaciones, esParticipante,
                pertenenciaVigente, usuarios);
        mensajes = new MensajeService(conversaciones, esParticipante, pertenenciaVigente, marcarLeidoPort,
                guardarMensaje, cargarMensajes, fanoutMensajes, usuarios, almacenamiento, CLOCK, idGenerator);
        bandeja = new ConversacionService(conversaciones, guardarConversacion, agregarParticipante,
                esParticipante, pertenenciaVigente, marcarLeidoPort, contarNoLeidos, cargarMensajes, roster,
                usuarios, CLOCK, idGenerator, transacciones);
        presencia = new PresenciaService(presenciaPort, fanoutPresencia, conversacionesDeUsuario, roster,
                conversaciones, esParticipante, pertenenciaVigente, usuarios);
    }

    /**
     * El recorrido completo del hallazgo, tal como lo pide el registro de la auditoria: ascender a
     * ADMIN, comprobar que ve el soporte de una aprendiz ajena, degradar, y comprobar que ya no.
     */
    @Test
    @DisplayName("bajarle el rol a un administrador lo saca del chat de soporte de un aprendiz ajeno")
    void bajarleElRolLoSacaDelSoporteAjeno() {
        ascenderABrunoAAdmin();

        assertThat(participantesDe(SOPORTE_DE_ANA.id())).as("el ascenso le escribe la fila").contains(BRUNO);
        assertThat(autorizacion.puedeVer(SOPORTE_DE_ANA.id(), BRUNO)).as("suscripcion STOMP").isTrue();
        assertThatNoException().as("leer por REST")
                .isThrownBy(() -> mensajes.listar(BRUNO, SOPORTE_DE_ANA.id(), null, 30));
        assertThatNoException().as("marcar leido")
                .isThrownBy(() -> bandeja.marcarLeido(new MarcarLeidoCommand(BRUNO, SOPORTE_DE_ANA.id())));
        assertThatNoException().as("presencia")
                .isThrownBy(() -> presencia.enLineaEn(SOPORTE_DE_ANA.id(), BRUNO));
        assertThat(conversacionesEnLaBandejaDe(BRUNO)).contains(SOPORTE_DE_ANA.id());

        degradarABrunoATrainee();

        assertThat(participantesDe(SOPORTE_DE_ANA.id()))
                .as("la baja de rol borra la fila; contra el codigo viejo no la borraba nadie")
                .doesNotContain(BRUNO);
        assertThat(autorizacion.puedeVer(SOPORTE_DE_ANA.id(), BRUNO)).as("suscripcion STOMP").isFalse();
        assertThatThrownBy(() -> mensajes.listar(BRUNO, SOPORTE_DE_ANA.id(), null, 30))
                .as("leer por REST").isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> bandeja.marcarLeido(new MarcarLeidoCommand(BRUNO, SOPORTE_DE_ANA.id())))
                .as("marcar leido").isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> presencia.enLineaEn(SOPORTE_DE_ANA.id(), BRUNO))
                .as("presencia").isInstanceOf(NotAuthorizedException.class);
        assertThat(conversacionesEnLaBandejaDe(BRUNO)).as("bandeja").doesNotContain(SOPORTE_DE_ANA.id());

        presencia.seConecto(BRUNO);
        assertThat(avisosDePresencia).as("su aviso de conexion ya no sale al canal del soporte")
                .doesNotContain(SOPORTE_DE_ANA.id());
    }

    /**
     * El cinturon. La revocacion del listener corre en el outbox de Modulith: puede no haber
     * corrido todavia, haberse perdido o reentregarse tarde. Aca la fila vieja SIGUE en la
     * proyeccion a proposito y el acceso tiene que estar cerrado igual, porque la autoridad la da
     * el rol vigente y no la proyeccion — la misma leccion que el modulo ya habia aprendido para
     * los grupos.
     *
     * <p><b>Limitacion conocida y deliberada:</b> la bandeja ({@code ConversacionService.listar})
     * no tiene guarda, se arma con la proyeccion tal cual. Mientras la fila sobreviva, la
     * conversacion sigue <i>apareciendo</i> en el listado aunque no se pueda abrir. Quien la cierra
     * es la revocacion del listener, que es la que borra la fila.
     */
    @Test
    @DisplayName("aunque la fila vieja sobreviva, el ex staff ya no entra: manda el rol vigente")
    void elCinturonNoDependeDeQueLaRevocacionHayaCorrido() {
        ascenderABrunoAAdmin();
        assertThat(autorizacion.puedeVer(SOPORTE_DE_ANA.id(), BRUNO)).isTrue();

        rolDeBruno = UserRole.MENTOR; // lo degradan, pero el evento NUNCA llega al chat

        assertThat(participantesDe(SOPORTE_DE_ANA.id())).as("la fila vieja sigue ahi").contains(BRUNO);
        assertThat(autorizacion.puedeVer(SOPORTE_DE_ANA.id(), BRUNO)).as("suscripcion STOMP").isFalse();
        assertThatThrownBy(() -> mensajes.listar(BRUNO, SOPORTE_DE_ANA.id(), null, 30))
                .as("leer por REST").isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> bandeja.marcarLeido(new MarcarLeidoCommand(BRUNO, SOPORTE_DE_ANA.id())))
                .as("marcar leido").isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> presencia.enLineaEn(SOPORTE_DE_ANA.id(), BRUNO))
                .as("presencia").isInstanceOf(NotAuthorizedException.class);

        presencia.seConecto(BRUNO);
        assertThat(avisosDePresencia).as("el aviso de conexion no sale al canal del soporte")
                .doesNotContain(SOPORTE_DE_ANA.id());
    }

    /**
     * La excepcion a CH-11 es ACOTADA: revoca a quien dejo de cumplir la regla 1 y no mueve a nadie
     * mas. La aprendiz dueña no pierde su via de contacto con la casa (regla 4) y el DM entre los
     * dos sigue intacto — nadie pierde un mensaje directo porque a alguien le cambien el rol.
     */
    @Test
    @DisplayName("la revocacion no mueve a nadie mas: ni a la aprendiz dueña ni al mensaje directo")
    void laRevocacionNoMueveANadieMas() {
        ascenderABrunoAAdmin();
        degradarABrunoATrainee();

        assertThat(participantesDe(SOPORTE_DE_ANA.id())).as("la dueña se queda sola adentro")
                .containsExactly(ANA);
        assertThat(autorizacion.puedeVer(SOPORTE_DE_ANA.id(), ANA)).isTrue();
        assertThatNoException().isThrownBy(() -> mensajes.listar(ANA, SOPORTE_DE_ANA.id(), null, 30));

        assertThat(participantesDe(DM_ANA_BRUNO.id())).as("el DM no se toca").containsExactly(ANA, BRUNO);
        assertThat(autorizacion.puedeVer(DM_ANA_BRUNO.id(), BRUNO)).isTrue();
        assertThatNoException().isThrownBy(() -> mensajes.listar(BRUNO, DM_ANA_BRUNO.id(), null, 30));
    }

    /**
     * El outbox entrega al-menos-una-vez y sin orden garantizado. Si el evento de baja se
     * reentrega cuando la persona ya volvio al staff, no puede borrarle las filas a un
     * administrador legitimo: la decision se toma contra el rol vigente, no contra el del evento.
     */
    @Test
    @DisplayName("reentregar el evento de baja cuando ya volvio al staff no le quita nada")
    void reentregarLaBajaConElRolYaRepuestoNoQuitaNada() {
        ascenderABrunoAAdmin();
        degradarABrunoATrainee();
        ascenderABrunoAAdmin(); // lo reponen

        listener.on(new RolDeUsuarioCambiadoEvent(BRUNO, UserRole.ADMIN, UserRole.TRAINEE, CLOCK.now()));

        assertThat(participantesDe(SOPORTE_DE_ANA.id())).contains(BRUNO);
        assertThat(autorizacion.puedeVer(SOPORTE_DE_ANA.id(), BRUNO)).isTrue();
    }

    @Test
    @DisplayName("repetir la baja no falla: quitar una fila que ya no esta es el estado que se buscaba")
    void repetirLaBajaEsIdempotente() {
        ascenderABrunoAAdmin();
        degradarABrunoATrainee();

        assertThatNoException().isThrownBy(this::degradarABrunoATrainee);
        assertThat(participantesDe(SOPORTE_DE_ANA.id())).containsExactly(ANA);
    }

    // ── Ayudas ──────────────────────────────────────────────────────────────────────────────

    private void ascenderABrunoAAdmin() {
        UserRole anterior = rolDeBruno;
        rolDeBruno = UserRole.ADMIN;
        listener.on(new RolDeUsuarioCambiadoEvent(BRUNO, anterior, UserRole.ADMIN, CLOCK.now()));
    }

    private void degradarABrunoATrainee() {
        UserRole anterior = rolDeBruno;
        rolDeBruno = UserRole.TRAINEE;
        listener.on(new RolDeUsuarioCambiadoEvent(BRUNO, anterior, UserRole.TRAINEE, CLOCK.now()));
    }

    private Set<UserId> participantesDe(ConversacionId conversacionId) {
        return proyeccion.computeIfAbsent(conversacionId, id -> new LinkedHashSet<>());
    }

    private List<ConversacionId> conversacionesEnLaBandejaDe(UserId actorId) {
        return bandeja.listar(actorId).stream().map(resumen -> resumen.conversacion().id()).toList();
    }

    private static Optional<Conversacion> porId(ConversacionId id) {
        if (SOPORTE_DE_ANA.id().equals(id)) {
            return Optional.of(SOPORTE_DE_ANA);
        }
        return DM_ANA_BRUNO.id().equals(id) ? Optional.of(DM_ANA_BRUNO) : Optional.empty();
    }

    private Optional<UserSummary> perfilDe(UserId id) {
        if (ANA.equals(id)) {
            return Optional.of(new UserSummary(ANA, "Ana Perez", null, UserRole.TRAINEE, UserStatus.ACTIVE));
        }
        return BRUNO.equals(id)
                ? Optional.of(new UserSummary(BRUNO, "Bruno Diaz", null, rolDeBruno, UserStatus.ACTIVE))
                : Optional.empty();
    }

    private UserRole rolVigenteDe(UserId id) {
        return ANA.equals(id) ? UserRole.TRAINEE : rolDeBruno;
    }

    private static ParticipacionPrograma participacion(UserId id, UserRole rol, boolean inscrito) {
        return new ParticipacionPrograma(id, inscrito, inscrito ? 3 : 0,
                inscrito ? CLOCK.today().minusDays(3) : null, ZoneId.of("America/Lima"),
                FasePrograma.values()[0], null, null, rol, false, inscrito);
    }
}
