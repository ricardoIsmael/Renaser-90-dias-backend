package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.ListarMensajesUseCase.PaginaMensajes;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.lectura.MarcasDeLecturaPort;
import com.renaser.os.chat.application.ports.out.lectura.PublicarLecturaFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.EstadoDeEntrega;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La marca de cada mensaje propio en el listado ({@code GET .../messages}, D-208): ✓ o ✓✓ según lo
 * hayan leído, con el {@link LecturaService} real y solo los puertos de salida simulados. Contra el
 * código viejo el listado no decía nada de lectura: todas estas pruebas fallan.
 */
@ExtendWith(MockitoExtension.class)
class MarcaDeLeidoEnElListadoTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T01:00:00Z");
    /** 19:00 en Lima del 26: en UTC ya es el 27 (regla 02 §3). */
    private static final Instant ESCRIBIO_ANA = Instant.parse("2026-09-27T00:00:00Z");

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
    private MarcasDeLecturaPort marcasDeLecturaPort;
    @Mock
    private PublicarLecturaFanoutPort publicarLecturaFanoutPort;

    private MensajeService servicio;

    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId luis = UserId.of(UUID.randomUUID());
    private final UserId marta = UserId.of(UUID.randomUUID());
    private final UserId kelin = UserId.of(UUID.randomUUID());
    private final UserId rita = UserId.of(UUID.randomUUID());
    private final Map<UserId, UserSummary> cuentas = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        servicio = new MensajeService(loadConversacionPort, esParticipantePort, pertenenciaVigentePort,
                marcarLeidoPort, saveMensajePort, loadMensajePort, publicarMensajeFanoutPort, userSummaryFinder,
                almacenamientoPort, new LecturaService(marcasDeLecturaPort, publicarLecturaFanoutPort, userSummaryFinder),
                FixedClock.at(AHORA), UUID::randomUUID);
        cuenta(ana, "Ana Pérez", UserRole.TRAINEE, UserStatus.ACTIVE);
        cuenta(luis, "Luis Soto", UserRole.TRAINEE, UserStatus.ACTIVE);
        cuenta(marta, "Marta Díaz", UserRole.TRAINEE, UserStatus.ACTIVE);
        cuenta(kelin, "Kelin Rojas", UserRole.ADMIN, UserStatus.ACTIVE);
        cuenta(rita, "Rita Vega", UserRole.ADMIN, UserStatus.SUSPENDED);
        lenient().when(userSummaryFinder.findById(any())).thenAnswer(inv -> Optional.ofNullable(cuentas.get(inv.getArgument(0))));
        lenient().when(userSummaryFinder.findByIds(any())).thenAnswer(inv -> {
            Map<UserId, UserSummary> pedidas = new LinkedHashMap<>();
            for (UserId id : inv.<Collection<UserId>>getArgument(0)) {
                if (cuentas.containsKey(id)) {
                    pedidas.put(id, cuentas.get(id));
                }
            }
            return pedidas;
        });
    }

    private void cuenta(UserId id, String nombre, UserRole rol, UserStatus estado) {
        cuentas.put(id, new UserSummary(id, nombre, null, rol, estado));
    }

    /** La conversación existe, quien mira participa, y estos son sus mensajes (del más nuevo al más viejo). */
    private void conversacion(Conversacion conversacion, List<UserId> participantes, List<Mensaje> mensajes) {
        when(loadConversacionPort.porId(conversacion.id())).thenReturn(Optional.of(conversacion));
        lenient().when(esParticipantePort.esParticipante(eq(conversacion.id()), any()))
                .thenAnswer(inv -> participantes.contains(inv.<UserId>getArgument(1)));
        when(loadMensajePort.pagina(eq(conversacion.id()), any(), anyInt())).thenReturn(mensajes);
    }

    private void marcas(Conversacion conversacion, Participante... participantes) {
        when(marcasDeLecturaPort.participantesDe(conversacion.id())).thenReturn(List.of(participantes));
    }

    private static Participante marca(Conversacion conversacion, UserId quien, Instant leyoHasta) {
        return Participante.rehydrate(conversacion.id(), quien, leyoHasta, ESCRIBIO_ANA.minusSeconds(3600));
    }

    private static Mensaje texto(Conversacion conversacion, UserId quien, Instant cuando) {
        return Mensaje.escribir(MensajeId.of(UUID.randomUUID()), conversacion.id(), quien, TipoMensaje.TEXTO, "hola",
                null, null, null, null, null, null, cuando);
    }

    private static EstadoDeEntrega marcaDe(PaginaMensajes pagina, Mensaje mensaje) {
        return pagina.mensajes().stream().filter(e -> e.mensaje().equals(mensaje)).findFirst()
                .map(MensajeEnriquecido::estadoDeEntrega).orElseThrow();
    }

    private Conversacion directa() {
        return Conversacion.crearDirecta(ConversacionId.of(UUID.randomUUID()), Conversacion.claveDirectaDe(ana, luis),
                ESCRIBIO_ANA.minusSeconds(7200));
    }

    @Test
    @DisplayName("1 a 1: lo de Ana que Luis ya leyó es ✓✓; lo que escribió después, ✓; lo de Luis no lleva marca para Ana")
    void enUnUnoAUno() {
        Conversacion directa = directa();
        Mensaje leido = texto(directa, ana, ESCRIBIO_ANA);
        Mensaje deLuis = texto(directa, luis, ESCRIBIO_ANA.plusSeconds(120));
        Mensaje sinLeer = texto(directa, ana, ESCRIBIO_ANA.plusSeconds(600));
        conversacion(directa, List.of(ana, luis), List.of(sinLeer, deLuis, leido));
        // Ana quedó marcada al escribir el último; Luis leyó (y escribió) a los 2 minutos.
        marcas(directa, marca(directa, ana, sinLeer.creadoEn()), marca(directa, luis, deLuis.creadoEn()));

        PaginaMensajes pagina = servicio.listar(ana, directa.id(), null, 30);

        assertThat(marcaDe(pagina, leido)).isEqualTo(EstadoDeEntrega.LEIDO);
        assertThat(marcaDe(pagina, sinLeer)).isEqualTo(EstadoDeEntrega.ENVIADO);
        assertThat(pagina.mensajes()).filteredOn(e -> e.mensaje().equals(deLuis))
                .singleElement().extracting(MensajeEnriquecido::estadoDeEntrega).isNull();
    }

    @Test
    @DisplayName("grupo: ✓ mientras Marta no leyó, ✓✓ cuando leyeron todos")
    void enUnGrupoHacenFaltaTodos() {
        UUID celula = UUID.randomUUID();
        Conversacion grupo = Conversacion.crearCelula(ConversacionId.of(UUID.randomUUID()), celula,
                ESCRIBIO_ANA.minusSeconds(7200));
        Mensaje deAna = texto(grupo, ana, ESCRIBIO_ANA);
        conversacion(grupo, List.of(), List.of(deAna));
        when(pertenenciaVigentePort.perteneceAlGrupo(celula, ana)).thenReturn(true);
        Participante anaAlEscribir = marca(grupo, ana, ESCRIBIO_ANA);
        Participante luisLeyo = marca(grupo, luis, ESCRIBIO_ANA.plusSeconds(60));
        when(marcasDeLecturaPort.participantesDe(grupo.id()))
                .thenReturn(List.of(anaAlEscribir, luisLeyo, marca(grupo, marta, ESCRIBIO_ANA.minusSeconds(60))))
                .thenReturn(List.of(anaAlEscribir, luisLeyo, marca(grupo, marta, ESCRIBIO_ANA.plusSeconds(300))));

        assertThat(marcaDe(servicio.listar(ana, grupo.id(), null, 30), deAna)).isEqualTo(EstadoDeEntrega.ENVIADO);
        assertThat(marcaDe(servicio.listar(ana, grupo.id(), null, 30), deAna)).isEqualTo(EstadoDeEntrega.LEIDO);
    }

    @Test
    @DisplayName("soporte: una admin suspendida que nunca leyó no frena el ✓✓ de la aprendiz")
    void enElSoporteNoCuentaUnaCuentaSuspendida() {
        Conversacion soporte = Conversacion.crearSoporte(ConversacionId.of(UUID.randomUUID()), ana,
                "Ana – Formación Renaser", ESCRIBIO_ANA.minusSeconds(7200));
        Mensaje deAna = texto(soporte, ana, ESCRIBIO_ANA);
        conversacion(soporte, List.of(ana, kelin, rita), List.of(deAna));
        marcas(soporte, marca(soporte, ana, ESCRIBIO_ANA), marca(soporte, kelin, ESCRIBIO_ANA.plusSeconds(60)),
                marca(soporte, rita, null));

        assertThat(marcaDe(servicio.listar(ana, soporte.id(), null, 30), deAna)).isEqualTo(EstadoDeEntrega.LEIDO);
    }

    @Test
    @DisplayName("comunidad: aunque lo hayan leído todos queda ✓, y ni se consultan las marcas")
    void enLaComunidadNoHayDobleMarca() {
        Conversacion comunidad = Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()),
                ESCRIBIO_ANA.minusSeconds(7200));
        Mensaje deAna = texto(comunidad, ana, ESCRIBIO_ANA);
        conversacion(comunidad, List.of(ana, luis), List.of(deAna));

        assertThat(marcaDe(servicio.listar(ana, comunidad.id(), null, 30), deAna)).isEqualTo(EstadoDeEntrega.ENVIADO);
        verify(marcasDeLecturaPort, never()).participantesDe(any());
    }

    @Test
    @DisplayName("sin N+1: una sola lectura de marcas por página aunque haya varios mensajes propios, y ninguna si no hay propios")
    void unaSolaLecturaPorPagina() {
        Conversacion directa = directa();
        List<Mensaje> propios = List.of(texto(directa, ana, ESCRIBIO_ANA.plusSeconds(3)),
                texto(directa, ana, ESCRIBIO_ANA.plusSeconds(2)), texto(directa, ana, ESCRIBIO_ANA.plusSeconds(1)));
        conversacion(directa, List.of(ana, luis), propios);
        marcas(directa, marca(directa, ana, ESCRIBIO_ANA.plusSeconds(3)), marca(directa, luis, AHORA));

        servicio.listar(ana, directa.id(), null, 30);
        servicio.listar(luis, directa.id(), null, 30);

        verify(marcasDeLecturaPort, times(1)).participantesDe(directa.id());
    }

    @Test
    @DisplayName("la bienvenida del programa guardada a nombre de Ana no lleva marca para ella")
    void elMensajeDelProgramaNoLlevaMarca() {
        Conversacion soporte = Conversacion.crearSoporte(ConversacionId.of(UUID.randomUUID()), ana,
                "Ana – Formación Renaser", ESCRIBIO_ANA.minusSeconds(7200));
        Mensaje bienvenida = Mensaje.delPrograma(MensajeId.of(UUID.randomUUID()), soporte.id(), ana,
                ContenidoDelPrograma.texto("Te damos la bienvenida"), ESCRIBIO_ANA);
        conversacion(soporte, List.of(ana, kelin), List.of(bienvenida));

        PaginaMensajes pagina = servicio.listar(ana, soporte.id(), null, 30);

        assertThat(pagina.mensajes()).singleElement().extracting(MensajeEnriquecido::estadoDeEntrega).isNull();
        verify(marcasDeLecturaPort, never()).participantesDe(any());
    }

    @Test
    @DisplayName("autorización negativa: quien no participa y una cuenta suspendida no ven la marca de nadie")
    void nadieDeAfueraVeLaMarca() {
        Conversacion directa = directa();
        when(loadConversacionPort.porId(directa.id())).thenReturn(Optional.of(directa));
        when(esParticipantePort.esParticipante(directa.id(), marta)).thenReturn(false);
        cuenta(luis, "Luis Soto", UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.listar(marta, directa.id(), null, 30)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.listar(luis, directa.id(), null, 30)).isInstanceOf(NotAuthorizedException.class);
        verify(marcasDeLecturaPort, never()).participantesDe(any());
    }
}
