package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.FrecuenciaRecurrencia;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas.OcurrenciaDeseada;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OcurrenciasDeseadasTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-11-05T15:00:00Z"));
    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");

    private static Evento evento(Instant inicia, Recurrencia recurrencia, Integer duracion) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Clase", null, inicia, duracion, LIMA, TipoUbicacion.MEET, null, TipoAudiencia.TODOS,
                null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, recurrencia, Set.of(), List.of(), UserId.of(UUID.randomUUID()),
                RELOJ, Formacion.ESCUELA, AccesoSalaMeet.ABIERTA);
    }

    // spec 040 FR-017, FR-018
    @Test
    @DisplayName("FR-018: un evento de un solo día tiene una fecha: la del día en SU zona, no la del servidor")
    void unEventoSoloTieneUnaFecha() {
        // 23:30 del martes en Lima = 04:30 UTC del miércoles.
        Evento evento = evento(Instant.parse("2026-11-11T04:30:00Z"), null, 60);

        Map<LocalDate, OcurrenciaDeseada> deseadas = OcurrenciasDeseadas.de(evento, List.of(), AHORA, 60);

        assertThat(deseadas).containsOnlyKeys(LocalDate.of(2026, 11, 10));
        assertThat(deseadas.get(LocalDate.of(2026, 11, 10)).inicio()).isEqualTo(Instant.parse("2026-11-11T04:30:00Z"));
    }

    // spec 040 FR-002
    @Test
    @DisplayName("FR-002: si el evento no dice cuánto dura se supone una hora, porque Google necesita un fin")
    void sinDuracionSeSuponeUnaHora() {
        Evento evento = evento(Instant.parse("2026-11-11T00:00:00Z"), null, null);

        OcurrenciaDeseada deseada = OcurrenciasDeseadas.de(evento, List.of(), AHORA, 60).values().iterator().next();

        assertThat(deseada.fin()).isEqualTo(deseada.inicio().plusSeconds(3600));
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: una serie semanal da una fecha por ocurrencia dentro de la ventana de días, y ninguna más lejos")
    void unaSerieDaUnaFechaPorOcurrenciaEnLaVentana() {
        Recurrencia martes = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null, Set.of(DayOfWeek.TUESDAY));
        Evento evento = evento(Instant.parse("2026-11-11T00:00:00Z"), martes, 60);

        Map<LocalDate, OcurrenciaDeseada> ventana60 = OcurrenciasDeseadas.de(evento, List.of(), AHORA, 60);
        Map<LocalDate, OcurrenciaDeseada> ventana14 = OcurrenciasDeseadas.de(evento, List.of(), AHORA, 14);

        assertThat(ventana60).hasSize(8).containsKeys(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 29));
        assertThat(ventana14).containsOnlyKeys(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17));
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: una ocurrencia cancelada no tiene sala")
    void unaOcurrenciaCanceladaNoTieneSala() {
        Recurrencia martes = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null, Set.of(DayOfWeek.TUESDAY));
        Evento evento = evento(Instant.parse("2026-11-11T00:00:00Z"), martes, 60);
        Excepcion cancelada = Excepcion.cancelar(UUID.randomUUID(), evento.id(), Instant.parse("2026-11-18T00:00:00Z"));

        Map<LocalDate, OcurrenciaDeseada> deseadas = OcurrenciasDeseadas.de(evento, List.of(cancelada), AHORA, 60);

        assertThat(deseadas).hasSize(7).doesNotContainKey(LocalDate.of(2026, 11, 17));
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: las ocurrencias de hoy que ya empezaron siguen contando; las de días anteriores, no")
    void hoyCuentaAunqueYaHayaEmpezado() {
        // Hoy (jueves 5, 10:00 Lima) hay una sesión a las 08:00 Lima, y el jueves anterior ya pasó.
        Recurrencia jueves = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null, Set.of(DayOfWeek.THURSDAY));
        Evento evento = evento(Instant.parse("2026-10-29T13:00:00Z"), jueves, 60);

        Map<LocalDate, OcurrenciaDeseada> deseadas = OcurrenciasDeseadas.de(evento, List.of(), AHORA, 14);

        assertThat(deseadas).containsKey(LocalDate.of(2026, 11, 5)).doesNotContainKey(LocalDate.of(2026, 10, 29));
    }
}
