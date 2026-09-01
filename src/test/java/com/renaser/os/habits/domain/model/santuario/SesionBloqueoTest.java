package com.renaser.os.habits.domain.model.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** blocking.ts traducido 1:1 — ver docs/MODULO_HABITS.md paso 0. */
class SesionBloqueoTest {

    private static final Instant INICIO = Instant.parse("2026-08-24T20:00:00Z");

    private static SesionBloqueo nuevaActiva(Instant inicio) {
        return SesionBloqueo.iniciar(RegistroHabitoId.of(UUID.randomUUID()), inicio);
    }

    @Test
    void iniciarUsaElMinimoDefaultDe30Minutos() {
        SesionBloqueo s = nuevaActiva(INICIO);
        assertThat(s.duracionMinimaMin()).isEqualTo(SesionBloqueo.DURACION_MINIMA_DEFAULT_MIN);
        assertThat(s.estaActiva()).isTrue();
    }

    @Test
    @DisplayName("completar antes del minimo -> rechazado (blocking.ts:167-175)")
    void completarAntesDelMinimoFalla() {
        SesionBloqueo s = nuevaActiva(INICIO);
        Instant ahora = INICIO.plus(Duration.ofMinutes(29));
        assertThatThrownBy(() -> s.completar(ahora, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("30");
    }

    @Test
    void completarJustoAlMinimoOk() {
        SesionBloqueo s = nuevaActiva(INICIO);
        Instant ahora = INICIO.plus(Duration.ofMinutes(30));
        s.completar(ahora, null);
        assertThat(s.estaCompletada()).isTrue();
        assertThat(s.terminadaEn()).isEqualTo(ahora);
    }

    @Test
    @DisplayName("pasada la gracia de 5 min tras el limite del horario -> rechazado (blocking.ts:177-183)")
    void completarPasadaLaGraciaFalla() {
        SesionBloqueo s = nuevaActiva(INICIO);
        Instant limite = INICIO.plus(Duration.ofMinutes(40));
        Instant ahora = limite.plus(SesionBloqueo.GRACIA_COMPLETAR).plusSeconds(1);
        assertThatThrownBy(() -> s.completar(ahora, limite)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerro");
    }

    @Test
    void completarDentroDeLaGraciaOk() {
        SesionBloqueo s = nuevaActiva(INICIO);
        Instant limite = INICIO.plus(Duration.ofMinutes(40));
        Instant ahora = limite.plus(SesionBloqueo.GRACIA_COMPLETAR);
        s.completar(ahora, limite);
        assertThat(s.estaCompletada()).isTrue();
    }

    @Test
    void romperMarcaPenalizacionAplicadaYGuardaMotivo() {
        SesionBloqueo s = nuevaActiva(INICIO);
        Instant ahora = INICIO.plus(Duration.ofMinutes(10));
        s.romper(MotivoSalidaBloqueo.VIOLACION_APP_USADA, "bucket", "ruta", ahora);
        assertThat(s.estaRota()).isTrue();
        assertThat(s.penalizacionAplicada()).isTrue();
        assertThat(s.motivoSalida()).isEqualTo(MotivoSalidaBloqueo.VIOLACION_APP_USADA);
        assertThat(s.terminadaEn()).isEqualTo(ahora);
    }

    @Test
    void noSePuedeCompletarUnaSesionYaRota() {
        SesionBloqueo s = nuevaActiva(INICIO);
        s.romper(MotivoSalidaBloqueo.MANUAL, null, null, INICIO.plus(Duration.ofMinutes(5)));
        assertThatThrownBy(() -> s.completar(INICIO.plus(Duration.ofHours(1)), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noSePuedeRomperUnaSesionYaCompletada() {
        SesionBloqueo s = nuevaActiva(INICIO);
        s.completar(INICIO.plus(Duration.ofMinutes(30)), null);
        assertThatThrownBy(() -> s.romper(MotivoSalidaBloqueo.MANUAL, null, null, INICIO.plus(Duration.ofHours(1))))
                .isInstanceOf(IllegalStateException.class);
    }
}
