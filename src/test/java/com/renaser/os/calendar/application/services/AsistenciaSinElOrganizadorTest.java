package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.asistencia.PasarListaUseCase.MarcarAsistenciaCommand;
import com.renaser.os.calendar.application.ports.in.asistencia.PersonaConvocada;
import com.renaser.os.calendar.application.ports.out.asistencia.LoadListaDeAsistenciaPort;
import com.renaser.os.calendar.application.ports.out.asistencia.SaveListaDeAsistenciaPort;
import com.renaser.os.calendar.application.ports.out.audiencia.AudienciaElegidaPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.HistorialDeRespuestasPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort.Persona;
import com.renaser.os.calendar.domain.model.asistencia.EstadoAsistencia;
import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.evento.audiencia.AudienciaElegida;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decisión del dueño (2026-11-06): la asistencia de un evento NO cuenta a quien lo creó. En «Rescate de rojos» la tarjeta decía
 * «59 no respondieron» junto a «Llega a 58»: el organizador estaba en la lista y no en «Llega a». Se prueba con la audiencia
 * elegida de verdad ({@link MundoDeAudiencia}) para fijar que las dos cifras salen del mismo conjunto: van + no van + no
 * respondieron = «Llega a N».
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsistenciaSinElOrganizadorTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant INICIO = MundoDeAudiencia.AHORA.plusSeconds(3_600);
    /** Dentro de la ventana de pasar lista (abre 30 min antes del inicio). */
    private static final Instant DENTRO_DE_LA_VENTANA = INICIO.minusSeconds(600);

    @Mock private ConsultarProgresoParticipanteCalendarPort progresoPort;
    @Mock private LoadNivelMembresiaPort nivelPort;
    @Mock private LoadEventoPort loadEventoPort;
    @Mock private LoadExcepcionPort loadExcepcionPort;
    @Mock private ResolverAudienciaMasivaPort audienciaMasivaPort;
    @Mock private ResolverAudienciaCursoPort cursoPort;
    @Mock private LoadConfirmacionPort confirmacionPort;
    @Mock private HistorialDeRespuestasPort historialPort;
    @Mock private LoadListaDeAsistenciaPort loadLista;
    @Mock private SaveListaDeAsistenciaPort saveLista;
    @Mock private ConsultarPersonasPort personasPort;
    @Mock private AudienciaElegidaPort audienciaPort;

    private final MundoDeAudiencia m = new MundoDeAudiencia();
    private final EventoId eventoId = EventoId.of(UUID.randomUUID());

    @BeforeEach
    void preparar() {
        m.roles.forEach((persona, rol) -> when(progresoPort.deParticipante(persona))
                .thenReturn(Optional.of(new ProgresoParticipanteCalendar(10, LIMA, rol, false, null))));
        when(loadExcepcionPort.porEvento(eventoId)).thenReturn(List.of());
        when(personasPort.porIds(anyCollection())).thenAnswer(inv -> {
            Map<UserId, Persona> fichas = new HashMap<>();
            for (Object o : (Collection<?>) inv.getArgument(0)) {
                UserId id = (UserId) o;
                fichas.put(id, new Persona(id, "Persona " + id.value().toString().substring(0, 4), null));
            }
            return fichas;
        });
        when(confirmacionPort.deOcurrencia(any(), any())).thenReturn(List.of());
        when(confirmacionPort.estadoDe(any(), any(), any())).thenReturn(Optional.empty());
        when(historialPort.deOcurrencia(any(), any())).thenReturn(List.of());
        when(loadLista.marcas(any(), any())).thenReturn(List.of());
        when(loadLista.marcaDe(any(), any(), any())).thenReturn(Optional.empty());
        when(loadLista.cierre(any(), any())).thenReturn(Optional.empty());
    }

    // decisión del dueño 2026-11-06: el organizador no cuenta en la asistencia
    @Test
    @DisplayName("el líder que invita a los rojos no está en la lista: van + no van + no respondieron = «Llega a N»")
    void elLiderNoCuentaYLasCifrasCuadran() {
        Evento evento = eventoDe(m.lider, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));
        responde(evento, m.lider, EstadoConfirmacion.ASISTE);
        responde(evento, m.rojo1, EstadoConfirmacion.ASISTE);
        responde(evento, m.rojo2, EstadoConfirmacion.NO_ASISTE);

        List<PersonaConvocada> lista = servicio(DENTRO_DE_LA_VENTANA).ver(m.admin, eventoId, INICIO).personas();

        assertThat(lista).extracting(PersonaConvocada::id).doesNotContain(m.lider)
                .containsExactlyInAnyOrder(m.rojo1, m.rojo2, m.deOtroGrupo);
        assertThat(cuentas(lista)).containsExactly(1L, 1L, 1L);
        assertThat(lista).hasSize(llegaA(evento));
    }

    // decisión del dueño 2026-11-06: el organizador que también está en la audiencia elegida tampoco cuenta
    @Test
    @DisplayName("la administradora que invita a «Todo el personal» (y a los rojos) está en la audiencia y aun así no cuenta")
    void laOrganizadoraDentroDeLaAudienciaTampocoCuenta() {
        Evento evento = eventoDe(m.admin, List.of(ParteDeAudiencia.sola(TipoDeParte.PERSONAL),
                ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));
        responde(evento, m.admin, EstadoConfirmacion.ASISTE);
        responde(evento, m.alquimista, EstadoConfirmacion.ASISTE);

        List<PersonaConvocada> lista = servicio(DENTRO_DE_LA_VENTANA).ver(m.alquimista, eventoId, INICIO).personas();

        assertThat(lista).extracting(PersonaConvocada::id).doesNotContain(m.admin)
                .containsExactlyInAnyOrder(m.alquimista, m.rojo1, m.rojo2, m.deOtroGrupo);
        assertThat(lista).hasSize(llegaA(evento));
        assertThat(cuentas(lista)).containsExactly(1L, 0L, 3L);
    }

    // decisión del dueño 2026-11-06: tampoco cuenta con una marca de antes
    @Test
    @DisplayName("una marca vieja del organizador no lo devuelve a la lista")
    void unaMarcaViejaNoLoDevuelve() {
        Evento evento = eventoDe(m.lider, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));
        when(loadLista.marcas(eventoId, INICIO)).thenReturn(List.of(
                new MarcaDeAsistencia(eventoId, INICIO, m.lider, EstadoAsistencia.A_TIEMPO, m.admin, DENTRO_DE_LA_VENTANA)));

        List<PersonaConvocada> lista = servicio(DENTRO_DE_LA_VENTANA).ver(m.admin, eventoId, INICIO).personas();

        assertThat(lista).extracting(PersonaConvocada::id).doesNotContain(m.lider);
        assertThat(lista).hasSize(llegaA(evento));
    }

    // decisión del dueño 2026-11-06: pasar lista no lo cuenta
    @Test
    @DisplayName("pasar lista al organizador es 400 y no escribe nada")
    void alOrganizadorNoSeLePasaLista() {
        eventoDe(m.lider, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));

        assertThatThrownBy(() -> servicio(DENTRO_DE_LA_VENTANA).marcar(
                new MarcarAsistenciaCommand(m.admin, eventoId, INICIO, m.lider, EstadoAsistencia.A_TIEMPO)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(saveLista, never()).guardar(any());
        verify(saveLista, never()).quitar(any(), any(), any());

        servicio(DENTRO_DE_LA_VENTANA).marcar(new MarcarAsistenciaCommand(m.admin, eventoId, INICIO, m.rojo1, EstadoAsistencia.A_TIEMPO));
        verify(saveLista).guardar(any());
    }

    // S5 (spec 044) sigue: los recordatorios le llegan al organizador
    @Test
    @DisplayName("los recordatorios siguen llegándole al organizador (S5): solo la asistencia lo deja afuera")
    void susRecordatoriosNoCambian() {
        Evento evento = eventoDe(m.lider, List.of(ParteDeAudiencia.color(ColorDeAudiencia.ROJO)));

        assertThat(audiencia().destinatarios(evento)).contains(m.lider);
    }

    private Evento eventoDe(UserId creador, List<ParteDeAudiencia> partes) {
        RolUsuario rol = m.roles.get(creador);
        AudienciaElegida audiencia = m.servicio.armar(creador, rol, Formacion.ESCUELA, partes, null, AudienciaElegida.vacia(), true,
                MundoDeAudiencia.AHORA);
        Evento evento = Evento.crear(eventoId, "Rescate de rojos", null, INICIO, 60, LIMA, TipoUbicacion.MEET, "https://meet.google.com/abc",
                TipoAudiencia.SELECCION, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), creador,
                FixedClock.at(MundoDeAudiencia.AHORA), Formacion.ESCUELA, null);
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento));
        when(audienciaPort.de(eventoId)).thenReturn(audiencia);
        return evento;
    }

    private final List<Confirmacion> respuestas = new ArrayList<>();

    private void responde(Evento evento, UserId persona, EstadoConfirmacion estado) {
        respuestas.add(new Confirmacion(evento.id(), INICIO, persona, estado, MundoDeAudiencia.AHORA, MundoDeAudiencia.AHORA));
        when(confirmacionPort.deOcurrencia(eventoId, INICIO)).thenReturn(List.copyOf(respuestas));
    }

    /** «Llega a N personas» del detalle del evento: el mismo número que muestra la app. */
    private int llegaA(Evento evento) {
        return m.servicio.resumen(evento, audienciaPort.de(eventoId)).total();
    }

    /** [van, no van, no respondieron], como los agrupa la tarjeta de la app. */
    private static List<Long> cuentas(List<PersonaConvocada> lista) {
        long van = lista.stream().filter(p -> p.respuesta() == EstadoConfirmacion.ASISTE).count();
        long noVan = lista.stream().filter(p -> p.respuesta() == EstadoConfirmacion.NO_ASISTE).count();
        return List.of(van, noVan, lista.size() - van - noVan);
    }

    private AudienciaDelEventoService audiencia() {
        return new AudienciaDelEventoService(nivelPort, progresoPort, audienciaMasivaPort, m.celulas, cursoPort, (u, t) -> true,
                new AlcanceDeFormacionPermisivo(), audienciaPort, m.servicio);
    }

    private ListaDeAsistenciaService servicio(Instant ahora) {
        Clock reloj = new Clock() {
            @Override
            public Instant now() {
                return ahora;
            }

            @Override
            public LocalDate today() {
                throw new AssertionError("la asistencia no depende de la fecha del servidor");
            }
        };
        var accesoEvento = new AccesoEventoService(progresoPort, nivelPort, cursoPort, (u, t) -> false, (c, u) -> false,
                new AlcanceDeFormacionPermisivo());
        var audiencia = audiencia();
        var acceso = new AccesoALaListaService(accesoEvento, loadEventoPort, loadExcepcionPort);
        var personas = new PersonasConvocadasService(audiencia, confirmacionPort, historialPort, loadLista, personasPort);
        return new ListaDeAsistenciaService(acceso, personas, audiencia, loadLista, saveLista, personasPort, reloj);
    }
}
