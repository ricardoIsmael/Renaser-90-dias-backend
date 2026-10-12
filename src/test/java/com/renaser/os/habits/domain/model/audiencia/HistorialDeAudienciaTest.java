package com.renaser.os.habits.domain.model.audiencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vigencia estricta (clarify 2026-10-12): para el día local D rige la versión guardada antes del día D en la zona de
 * la persona; la primera versión rige desde la creación. Los relojes caen en la madrugada UTC a propósito: es el día
 * local ANTERIOR en Lima (regla 02 §3).
 */
class HistorialDeAudienciaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final HabitoId HABITO = HabitoId.of(UUID.randomUUID());
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final Audiencia MENTORES = Audiencia.de(Set.of(CodigoDeRol.of("MENTOR")));
    private static final Audiencia APRENDICES = Audiencia.de(Set.of(CodigoDeRol.of("TRAINEE")));

    @Test
    @DisplayName("sin versiones es para todos (los hábitos de antes de la función)")
    void sinVersionesEsParaTodos() {
        assertThat(HistorialDeAudiencia.vacio().vigenteEl(LocalDate.parse("2026-10-12"), LIMA))
                .isEqualTo(Audiencia.PARA_TODOS);
    }

    @Test
    @DisplayName("la audiencia con la que se crea rige desde la creación, el mismo día")
    void primeraVersionRigeDesdeLaCreacion() {
        var historial = historial(version(MENTORES, "2026-10-12T15:00:00Z"));

        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-12"), LIMA)).isEqualTo(MENTORES);
    }

    @Test
    @DisplayName("un cambio hecho hoy rige desde mañana: hoy sigue la versión anterior")
    void cambioDeHoyRigeManana() {
        var historial = historial(version(APRENDICES, "2026-10-01T15:00:00Z"),
                version(MENTORES, "2026-10-12T15:00:00Z"));

        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-12"), LIMA)).isEqualTo(APRENDICES);
        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-13"), LIMA)).isEqualTo(MENTORES);
    }

    @Test
    @DisplayName("un cambio a las 23:30 de Lima (04:30 UTC del día siguiente) rige recién el día local siguiente")
    void cambioDeMadrugadaUtc() {
        // 2026-10-13T04:30Z = 2026-10-12 23:30 en Lima: el cambio es del día 12 local.
        var historial = historial(version(APRENDICES, "2026-10-01T15:00:00Z"),
                version(MENTORES, "2026-10-13T04:30:00Z"));

        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-12"), LIMA)).isEqualTo(APRENDICES);
        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-13"), LIMA)).isEqualTo(MENTORES);
    }

    @Test
    @DisplayName("restringir un hábito que existía sin audiencia rige mañana, gracias a la versión base desde siempre")
    void habitoAnteriorConVersionBase() {
        var historial = historial(version(Audiencia.PARA_TODOS, "1970-01-01T00:00:00Z"),
                version(MENTORES, "2026-10-12T15:00:00Z"));

        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-12"), LIMA)).isEqualTo(Audiencia.PARA_TODOS);
        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-13"), LIMA)).isEqualTo(MENTORES);
    }

    @Test
    @DisplayName("varios cambios el mismo día: mañana rige el último")
    void variosCambiosElMismoDia() {
        var historial = historial(version(Audiencia.PARA_TODOS, "2026-10-01T15:00:00Z"),
                version(APRENDICES, "2026-10-12T14:00:00Z"), version(MENTORES, "2026-10-12T16:00:00Z"));

        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-12"), LIMA)).isEqualTo(Audiencia.PARA_TODOS);
        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-13"), LIMA)).isEqualTo(MENTORES);
    }

    @Test
    @DisplayName("la última guardada es la que ve el administrador, aunque todavía no rija")
    void ultimaGuardada() {
        var vieja = version(APRENDICES, "2026-10-01T15:00:00Z");
        var nueva = version(MENTORES, "2026-10-12T15:00:00Z");

        assertThat(historial(nueva, vieja).ultima()).contains(nueva);
        assertThat(HistorialDeAudiencia.vacio().ultima()).isEmpty();
    }

    @Test
    @DisplayName("la audiencia con la que nace un hábito no queda «pendiente»; un cambio de hoy sí, hasta mañana (E2E)")
    void cambioPendiente() {
        Instant hoyALas10DeLima = Instant.parse("2026-10-12T15:00:00Z");
        Instant mananaALas10DeLima = Instant.parse("2026-10-13T15:00:00Z");
        var recienCreado = historial(version(MENTORES, "2026-10-12T14:00:00Z"));
        var cambiadoHoy = historial(version(Audiencia.PARA_TODOS, "1970-01-01T00:00:00Z"),
                version(MENTORES, "2026-10-12T14:00:00Z"));

        assertThat(recienCreado.cambioPendienteHasta(hoyALas10DeLima, LIMA)).isFalse();
        assertThat(cambiadoHoy.cambioPendienteHasta(hoyALas10DeLima, LIMA)).isTrue();
        assertThat(cambiadoHoy.cambioPendienteHasta(mananaALas10DeLima, LIMA)).isFalse();
        assertThat(HistorialDeAudiencia.vacio().cambioPendienteHasta(hoyALas10DeLima, LIMA)).isFalse();
    }

    private static HistorialDeAudiencia historial(VersionDeAudiencia... versiones) {
        return HistorialDeAudiencia.de(List.of(versiones));
    }

    private static VersionDeAudiencia version(Audiencia audiencia, String instante) {
        Instant cuando = Instant.parse(instante);
        return new VersionDeAudiencia(HABITO, audiencia, cuando, ADMIN, cuando);
    }
}
