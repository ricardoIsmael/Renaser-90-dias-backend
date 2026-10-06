package com.renaser.os.habits.domain.model.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-534 — cuando termina el dia de un participante, en SU zona. Dominio puro: sin Spring, sin base.
 *
 * <p>Regla 02 §3: todos los relojes de esta prueba estan entre las 00:00 y las 05:00 UTC, o justo en los bordes de
 * la medianoche local, que es donde la fecha UTC y la del participante no coinciden.
 */
class CorteDeExpiracionTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    @ParameterizedTest(name = "{0} a las {1}: hoy es {2}")
    @CsvSource({
            // Lima (UTC−5): su medianoche es las 05:00 UTC, la hora del barrido de antes.
            "America/Lima, 2026-11-10T00:00:00Z, 2026-11-09",
            "America/Lima, 2026-11-10T03:00:00Z, 2026-11-09",
            "America/Lima, 2026-11-10T04:59:59Z, 2026-11-09",
            "America/Lima, 2026-11-10T05:00:00Z, 2026-11-10",
            // Los Angeles en noviembre (UTC−8): a las 05:00 UTC todavia son las 21:00 del dia anterior.
            "America/Los_Angeles, 2026-11-10T05:00:00Z, 2026-11-09",
            "America/Los_Angeles, 2026-11-10T07:59:59Z, 2026-11-09",
            "America/Los_Angeles, 2026-11-10T08:00:00Z, 2026-11-10",
            // Lagos (UTC+1): su dia empieza a las 23:00 UTC del dia anterior.
            "Africa/Lagos, 2026-11-09T22:59:59Z, 2026-11-09",
            "Africa/Lagos, 2026-11-09T23:00:00Z, 2026-11-10",
            "Africa/Lagos, 2026-11-10T03:00:00Z, 2026-11-10"
    })
    void elHoyEsElDeSuZona(String zona, String instante, String hoy) {
        assertThat(CorteDeExpiracion.para(ZoneId.of(zona), Instant.parse(instante)).hoyEnSuZona())
                .isEqualTo(LocalDate.parse(hoy));
    }

    @Test
    @DisplayName("a las 03:00 UTC del 10: en Lima termino el 8 pero no el 9; con la fecha UTC habria terminado el 9")
    void deMadrugadaUtcLimaSigueEnSuDia() {
        Instant tresUtc = Instant.parse("2026-11-10T03:00:00Z");
        CorteDeExpiracion lima = CorteDeExpiracion.para(LIMA, tresUtc);

        assertThat(lima.yaTermino(LocalDate.of(2026, 11, 8))).isTrue();
        assertThat(lima.yaTermino(LocalDate.of(2026, 11, 9))).isFalse();
        // La fecha UTC del mismo instante ya es el 10: es el corte que vencia lo de HOY al oeste (E-534).
        assertThat(CorteDeExpiracion.para(ZoneOffset.UTC, tresUtc).yaTermino(LocalDate.of(2026, 11, 9))).isTrue();
    }

    @Test
    @DisplayName("Los Angeles a las 05:00 UTC (21:00 suyas): su dia sigue abierto; Lagos a las 23:00 UTC ya cerro el suyo")
    void alOesteYAlEste() {
        LocalDate nueve = LocalDate.of(2026, 11, 9);

        assertThat(CorteDeExpiracion.para(LOS_ANGELES, Instant.parse("2026-11-10T05:00:00Z")).yaTermino(nueve))
                .isFalse();
        assertThat(CorteDeExpiracion.para(LAGOS, Instant.parse("2026-11-09T23:00:00Z")).yaTermino(nueve)).isTrue();
    }

    @Test
    @DisplayName("el tope de busqueda nunca deja afuera a nadie: es >= al hoy de cualquier zona")
    void fechaMasTardiaPosibleCubreTodasLasZonas() {
        Instant ahora = Instant.parse("2026-11-10T03:00:00Z");
        LocalDate tope = CorteDeExpiracion.fechaMasTardiaPosible(ahora);

        for (String zona : ZoneId.getAvailableZoneIds()) {
            assertThat(CorteDeExpiracion.para(ZoneId.of(zona), ahora).hoyEnSuZona()).as(zona).isBeforeOrEqualTo(tope);
        }
        assertThat(tope).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    @DisplayName("solo vence un PENDIENTE de un dia que termino: ni el de hoy, ni lo que ya no esta pendiente")
    void soloVenceLoPendienteDeUnDiaTerminado() {
        CorteDeExpiracion corte = new CorteDeExpiracion(LocalDate.of(2026, 11, 10));

        assertThat(corte.vence(registro(LocalDate.of(2026, 11, 9), null))).isTrue();
        assertThat(corte.vence(registro(LocalDate.of(2026, 11, 10), null))).as("hoy").isFalse();
        assertThat(corte.vence(registro(LocalDate.of(2026, 11, 9), EstadoRegistro.EN_CURSO))).isFalse();
        assertThat(corte.vence(registro(LocalDate.of(2026, 11, 9), EstadoRegistro.COMPLETADO))).isFalse();
        assertThat(corte.vence(registro(LocalDate.of(2026, 11, 9), EstadoRegistro.EXPIRADO))).isFalse();
    }

    private static RegistroHabito registro(LocalDate fecha, EstadoRegistro estado) {
        Instant creado = Instant.parse("2026-11-01T12:00:00Z");
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), fecha, 10, TipoDia.DISCIPLINA, false,
                creado);
        if (estado == EstadoRegistro.EN_CURSO) {
            registro.iniciar(creado);
        } else if (estado == EstadoRegistro.COMPLETADO) {
            registro.completar(10, null, null, null, creado);
        } else if (estado == EstadoRegistro.EXPIRADO) {
            registro.expirar(creado);
        }
        return registro;
    }
}
