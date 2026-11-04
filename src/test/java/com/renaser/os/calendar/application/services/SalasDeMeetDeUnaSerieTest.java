package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.OcurrenciaVista;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.FrecuenciaRecurrencia;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.LIMA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un evento que se repite tiene UN enlace distinto por fecha (spec 040, decisión 4 del dueño). Con la serie «cada martes a las 19:00» y el reloj en
 * el jueves 5 de noviembre, la ventana de 60 días llega al 4 de enero: ocho martes.
 */
class SalasDeMeetDeUnaSerieTest {

    private static final Instant PRIMER_MARTES_19H_LIMA = Instant.parse("2026-11-11T00:00:00Z");

    private MundoDeMeet m;

    @BeforeEach
    void setUp() {
        m = new MundoDeMeet();
        m.conectarLaCuenta();
    }

    // spec 040 FR-017, FR-002, SC-012
    @Test
    @DisplayName("SC-012: una serie con 8 fechas en la ventana deja 8 enlaces distintos, cada uno con su propio evento de Google")
    void ochoFechasDejanOchoEnlacesDistintos() {
        Evento serie = crearSerieSemanal();

        List<SalaMeet> salas = m.salas.porEvento(serie.id());
        assertThat(salas).hasSize(8).allMatch(s -> s.estado() == EstadoSalaMeet.CREADO);
        assertThat(salas).extracting(SalaMeet::enlace).doesNotHaveDuplicates();
        assertThat(m.google.salasCreadas()).isEqualTo(8);
        // Un evento de Google por fecha, con la fecha pegada al uuid sin guiones (FR-002).
        String base = serie.id().value().toString().replace("-", "");
        assertThat(m.google.eventos.keySet()).allMatch(id -> id.startsWith(base) && id.length() == base.length() + 8);
        assertThat(salas.get(0).idGoogle()).isEqualTo(base + "20261110");
        // Nunca una regla de repetición: la serie no viaja a Google.
        assertThat(m.google.ultimoPedido.values()).allSatisfy(p -> assertThat(p.fin()).isEqualTo(p.inicio().plus(Duration.ofMinutes(60))));
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: cada fecha abre SU enlace en el listado, y el evento guarda el de la próxima fecha")
    void cadaFechaAbreSuEnlace() {
        Evento serie = crearSerieSemanal();

        List<OcurrenciaVista> ocurrencias = m.servicio.listar(ADMIN, Instant.parse("2026-11-05T00:00:00Z"), Instant.parse("2026-12-10T00:00:00Z"), null);

        assertThat(ocurrencias).hasSize(5);
        assertThat(ocurrencias).extracting(o -> o.ubicacionDeMeet().enlace()).doesNotContainNull().doesNotHaveDuplicates();
        SalaMeet primera = m.salas.porEvento(serie.id()).get(0);
        assertThat(ocurrencias.get(0).ubicacionDeMeet().enlace()).isEqualTo(primera.enlace());
        assertThat(m.eventos.byId(serie.id()).orElseThrow().valorUbicacion()).isEqualTo(primera.enlace());
    }

    // spec 040 FR-005, FR-017, SC-012
    @Test
    @DisplayName("SC-012: cancelar una sola fecha borra su evento de Google y deja las otras 7 intactas")
    void cancelarUnaFechaDejaLasOtras() {
        Evento serie = crearSerieSemanal();
        List<String> enlacesAntes = m.salas.porEvento(serie.id()).stream().map(SalaMeet::enlace).toList();
        String enlaceDeLaCancelada = enlacesAntes.get(1);

        m.servicio.cancelarOcurrencia(ADMIN, serie.id(), Instant.parse("2026-11-18T00:00:00Z"));

        List<SalaMeet> despues = m.salas.porEvento(serie.id());
        assertThat(despues).filteredOn(s -> s.estado() == EstadoSalaMeet.BORRADO).singleElement()
                .extracting(SalaMeet::fechaOcurrencia).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(despues).filteredOn(s -> s.estado() == EstadoSalaMeet.CREADO).hasSize(7);
        assertThat(m.google.eventos.values()).doesNotContain(enlaceDeLaCancelada).hasSize(7);
        assertThat(despues).filteredOn(s -> s.estado() == EstadoSalaMeet.CREADO).extracting(SalaMeet::enlace)
                .containsExactlyElementsOf(enlacesAntes.stream().filter(enlace -> !enlace.equals(enlaceDeLaCancelada)).toList());
    }

    // spec 040 FR-017, SC-012
    @Test
    @DisplayName("SC-012: las fechas que entran a la ventana después reciben su sala solas, y las que ya pasaron no se tocan")
    void lasFechasNuevasReciebenSalaSinQueNadieHagaNada() {
        Evento serie = crearSerieSemanal();
        long llamadasAntes = m.google.llamadasDe("borrar:");

        m.reloj.avanzar(Duration.ofDays(14));
        m.trabajo.mantenimiento(m.reloj.now());

        List<SalaMeet> salas = m.salas.porEvento(serie.id());
        assertThat(salas).extracting(SalaMeet::fechaOcurrencia).contains(LocalDate.of(2027, 1, 5), LocalDate.of(2026, 12, 29));
        assertThat(salas).filteredOn(s -> s.estado() == EstadoSalaMeet.CREADO).extracting(SalaMeet::enlace).doesNotHaveDuplicates();
        assertThat(m.google.llamadasDe("borrar:")).as("las fechas pasadas no se borran").isEqualTo(llamadasAntes);
        // Y el evento ahora muestra el enlace de la próxima fecha, no el de la primera.
        String proxima = salas.stream().filter(s -> s.fechaOcurrencia().equals(LocalDate.of(2026, 11, 24))).findFirst().orElseThrow().enlace();
        assertThat(m.eventos.byId(serie.id()).orElseThrow().valorUbicacion()).isEqualTo(proxima);
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: si una fecha falla, esa queda FALLIDO con su motivo y las demás no se ven afectadas")
    void unaFechaQueFallaNoAfectaALasDemas() {
        m.google.fallaProxima = new com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException("Google no aceptó el pedido (código 400).", 400);

        EventoConMeetVista creado = crearSerieSemanalConVista();

        List<SalaMeet> salas = m.salas.porEvento(creado.evento().evento().id());
        assertThat(salas.get(0).estado()).isEqualTo(EstadoSalaMeet.FALLIDO);
        assertThat(salas.subList(1, 8)).allMatch(s -> s.estado() == EstadoSalaMeet.CREADO);
        assertThat(creado.meet().fechasConProblema()).isEqualTo(1);
        assertThat(creado.meet().fechasConSala()).isEqualTo(7);
    }

    // spec 040 FR-018
    @Test
    @DisplayName("FR-018: la fecha de una sala es la de la zona del evento: una clase de las 23:30 en Lima es del martes aunque en UTC ya sea miércoles")
    void laFechaEsLaDeLaZonaDelEvento() {
        Evento tarde = m.eventoSemanal(AccesoSalaMeet.ABIERTA, Instant.parse("2026-11-11T04:30:00Z"), DayOfWeek.TUESDAY);

        m.sincronizador.reconciliar(tarde.id(), ADMIN, "c", false, Duration.ofSeconds(8));

        SalaMeet primera = m.salas.porEvento(tarde.id()).get(0);
        assertThat(primera.fechaOcurrencia()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(primera.idGoogle()).endsWith("20261110");
    }

    // ─── Armado ─────────────────────────────────────────────────────────────────

    private Evento crearSerieSemanal() {
        return crearSerieSemanalConVista().evento().evento();
    }

    private EventoConMeetVista crearSerieSemanalConVista() {
        Recurrencia cadaMartes = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null, Set.of(DayOfWeek.TUESDAY));
        return m.servicio.crear(new CrearEventoCommand(ADMIN, "Mentoría semanal", null, PRIMER_MARTES_19H_LIMA, 60, LIMA, TipoUbicacion.MEET,
                null, TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, cadaMartes, Set.of(), List.of(),
                null, AccesoSalaMeet.ABIERTA));
    }
}
