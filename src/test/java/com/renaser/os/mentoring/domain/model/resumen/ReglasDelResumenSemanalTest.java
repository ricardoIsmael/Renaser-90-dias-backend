package com.renaser.os.mentoring.domain.model.resumen;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La ventana del resumen del sábado, sin Spring. Los relojes se fijan a propósito cerca de la
 * medianoche de Lima (regla 02 §3): una hora UTC "cómoda" como las 10:00 cae el mismo día en Lima y
 * esconde justo el error que esta regla existe para evitar.
 */
class ReglasDelResumenSemanalTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Viernes 25 de setiembre de 2026: cierra la semana que abrió el sábado 19. */
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);

    private static Instant utc(String instante) {
        return Instant.parse(instante);
    }

    @Test
    @DisplayName("sabado 04:30 UTC en Lima es viernes 23:30: la semana no cerro y no toca")
    void aLas0430UtcDelSabadoTodaviaEsViernesEnLima() {
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-09-26T04:30:00Z"), LIMA)).isEmpty();
    }

    @Test
    @DisplayName("sabado 05:40 UTC es sabado 00:40 en Lima: toca la semana que termino el viernes")
    void aLas0540UtcDelSabadoToca() {
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-09-26T05:40:00Z"), LIMA)).contains(VIERNES);
    }

    @Test
    @DisplayName("la medianoche local ya cuenta: sabado 00:00:00 en Lima toca")
    void laMedianocheLocalYaEstaDentro() {
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-09-26T05:00:00Z"), LIMA)).contains(VIERNES);
    }

    @Test
    @DisplayName("sabado 05:40 en Lima es la ultima corrida de la ventana")
    void laUltimaCorridaDeLaVentana() {
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-09-26T10:40:00Z"), LIMA)).contains(VIERNES);
    }

    @Test
    @DisplayName("desde el sabado 06:00 en Lima ya no toca: sin avisos tardios")
    void pasadaLaVentanaNoToca() {
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-09-26T11:00:00Z"), LIMA)).isEmpty();
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-09-26T11:40:00Z"), LIMA)).isEmpty();
    }

    @Test
    @DisplayName("el domingo y el resto de la semana no tocan, aunque la ultima semana cerrada siga siendo la misma")
    void elRestoDeLaSemanaNoToca() {
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-09-27T05:40:00Z"), LIMA)).isEmpty();
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-09-30T15:40:00Z"), LIMA)).isEmpty();
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-10-03T04:40:00Z"), LIMA)).isEmpty();
    }

    @Test
    @DisplayName("la semana siguiente toca su propio viernes")
    void laSemanaSiguienteTocaSuViernes() {
        assertThat(ReglasDelResumenSemanal.semanaQueToca(utc("2026-10-03T05:40:00Z"), LIMA))
                .contains(VIERNES.plusWeeks(1));
    }

    @Test
    @DisplayName("manda la zona del grupo: el viernes 22:40 UTC ya es sabado en Madrid y todavia viernes en Lima")
    void mandaLaZonaDelGrupo() {
        Instant viernes2240Utc = utc("2026-09-25T22:40:00Z");

        assertThat(ReglasDelResumenSemanal.semanaQueToca(viernes2240Utc, ZoneId.of("Europe/Madrid"))).contains(VIERNES);
        assertThat(ReglasDelResumenSemanal.semanaQueToca(viernes2240Utc, LIMA)).isEmpty();
    }

    @Test
    @DisplayName("el semaforo del grupo esta listo con una sola semana cerrada, y no antes")
    void semaforoListoConUnaSemanaCerrada() {
        UserId ana = UserId.of(UUID.randomUUID());
        UserId beto = UserId.of(UUID.randomUUID());
        UserId sinPrograma = UserId.of(UUID.randomUUID());
        List<UserId> aprendices = List.of(ana, beto, sinPrograma);

        assertThat(ReglasDelResumenSemanal.semaforoListo(aprendices,
                Map.of(ana, semana(false), beto, semana(false)))).isFalse();
        assertThat(ReglasDelResumenSemanal.semaforoListo(aprendices,
                Map.of(ana, semana(false), beto, semana(true)))).isTrue();
        assertThat(ReglasDelResumenSemanal.semaforoListo(List.of(), Map.of())).isFalse();
    }

    @Test
    @DisplayName("una semana cerrada de alguien que no es del grupo no lo vuelve listo")
    void soloCuentanLosAprendicesDelGrupo() {
        UserId ana = UserId.of(UUID.randomUUID());
        UserId ajeno = UserId.of(UUID.randomUUID());

        assertThat(ReglasDelResumenSemanal.semaforoListo(List.of(ana), Map.of(ajeno, semana(true)))).isFalse();
    }

    @Test
    @DisplayName("las claves son deterministas, con el formato del contrato, una por grupo y semana")
    void clavesDelContrato() {
        UUID grupo = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otroGrupo = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(ReglasDelResumenSemanal.claveDelGrupo(grupo, VIERNES))
                .isEqualTo(UUID.nameUUIDFromBytes(("semaforo-grupo:" + grupo + ":2026-09-25")
                        .getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ReglasDelResumenSemanal.claveDelGrupo(grupo, VIERNES))
                .isNotEqualTo(ReglasDelResumenSemanal.claveDelGrupo(otroGrupo, VIERNES))
                .isNotEqualTo(ReglasDelResumenSemanal.claveDelGrupo(grupo, VIERNES.plusWeeks(1)));
        assertThat(ReglasDelResumenSemanal.claveGeneral(VIERNES))
                .isEqualTo(UUID.nameUUIDFromBytes("semaforo-general:2026-09-25".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(ReglasDelResumenSemanal.claveGeneral(VIERNES.plusWeeks(1)));
    }

    /** Coherente: verde con su porcentaje y sus siete días con datos. */
    private static VentanaDelSemaforo semana(boolean cerrada) {
        return new VentanaDelSemaforo(VIERNES.minusDays(6), VIERNES, new BigDecimal("85.0"), ColorSemaforo.VERDE, 7,
                cerrada, List.of());
    }
}
