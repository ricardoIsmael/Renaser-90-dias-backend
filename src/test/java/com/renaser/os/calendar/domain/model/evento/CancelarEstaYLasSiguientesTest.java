package com.renaser.os.calendar.domain.model.evento;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 053 FR-014 (D8 del dueño): «esta y las siguientes» termina la serie antes de la fecha elegida y deja intactas las pasadas. */
class CancelarEstaYLasSiguientesTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant LUNES_16 = Instant.parse("2026-11-17T00:00:00Z"); // lunes 16, 19:00 en Lima
    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-11-14T15:00:00Z"));

    private static Evento serie(Recurrencia recurrencia) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Sesión", null, LUNES_16, 60, LIMA, TipoUbicacion.DIRECCION, "Av. Lima 1",
                TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_PERSONALIZADA, false, false, false, recurrencia, Set.of(), List.of(),
                UserId.of(UUID.randomUUID()), RELOJ, Formacion.POR_DEFECTO, null);
    }

    private static List<Ocurrencia> ocurrencias(Evento e) {
        return ExpansorOcurrencias.expandir(e.iniciaEn(), e.duracionMinutos(), e.timezone(), e.recurrencia(), LUNES_16,
                Instant.parse("2027-01-31T00:00:00Z"), List.of());
    }

    // spec 053 FR-014
    @Test
    @DisplayName("FR-014: lunes y jueves por 4 semanas; cancelar desde el jueves 26 deja las 3 fechas anteriores y quita el resto")
    void terminaLaSerieAntesDeLaFechaElegida() {
        Evento e = serie(new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, 8, Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)));
        List<Ocurrencia> antes = ocurrencias(e);
        assertThat(antes).hasSize(8);
        Instant jueves26 = antes.get(3).inicioOcurrencia(); // lun 16, jue 19, lun 23, jue 26

        e.terminarLaSerieAntesDe(jueves26, RELOJ);

        List<Ocurrencia> despues = ocurrencias(e);
        assertThat(despues).extracting(Ocurrencia::inicioOcurrencia).containsExactlyElementsOf(antes.subList(0, 3).stream().map(Ocurrencia::inicioOcurrencia).toList());
        assertThat(e.recurrencia().repeticiones()).isNull();
        assertThat(e.recurrencia().diasSemana()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
    }

    // spec 053 FR-014
    @Test
    @DisplayName("FR-014: un evento que no se repite no se puede cortar")
    void sinRepeticionNoSeCorta() {
        Evento e = serie(null);
        assertThatThrownBy(() -> e.terminarLaSerieAntesDe(LUNES_16, RELOJ)).isInstanceOf(IllegalStateException.class);
    }
}
