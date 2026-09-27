package com.renaser.os.notifications.domain.model.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Texto, ruta, clave y vigencia del aviso de un recordatorio de evento (D-182). Sin Spring.
 *
 * <p><b>Los relojes estan a proposito entre 00:00 y 05:00 UTC</b> (regla 03): a esa hora la
 * fecha UTC y la de Lima difieren, y "hoy"/"mañana" calculados con la fecha UTC saldrian al reves.
 */
class AvisoDeEventoTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UUID EVENTO = UUID.fromString("7b0c6f5e-2f6a-4c55-9d7e-0a1b2c3d4e5f");

    private static AvisoDeEvento recordatorio(Instant inicio) {
        return new AvisoDeEvento(41L, EVENTO, "Semana de Manifestacion", inicio, LIMA, false);
    }

    @Test
    @DisplayName("a 10 min del inicio dice 'empieza en 10 min' con la hora de Lima, no la UTC")
    void cuentaRegresivaConHoraLocal() {
        Instant inicio = Instant.parse("2026-09-29T00:30:00Z");   // 19:30 del 28 en Lima
        Instant ahora = Instant.parse("2026-09-29T00:20:00Z");    // 19:20 del 28 en Lima

        assertThat(recordatorio(inicio).cuerpo(ahora)).isEqualTo("Empieza en 10 min, a las 19:30.");
    }

    @Test
    @DisplayName("regla 02: a las 21:00 de Lima (02:00 UTC) un evento de las 05:00 de Lima es 'mañana', aunque en UTC sea el mismo dia")
    void mananaSeCalculaEnLaZonaDelEvento() {
        Instant inicio = Instant.parse("2026-09-29T10:00:00Z");   // 05:00 del 29 en Lima
        Instant ahora = Instant.parse("2026-09-29T02:00:00Z");    // 21:00 del 28 en Lima

        assertThat(recordatorio(inicio).cuerpo(ahora)).isEqualTo("Es mañana a las 05:00.");
    }

    @Test
    @DisplayName("la alarma de 04:50 de Lima (09:50 UTC) por un evento de las 19:30 del mismo dia dice 'hoy'")
    void alarmaDeLaMadrugadaDiceHoy() {
        Instant inicio = Instant.parse("2026-09-29T00:30:00Z");   // 19:30 del 28 en Lima
        Instant alarma = Instant.parse("2026-09-28T09:50:00Z");   // 04:50 del 28 en Lima

        assertThat(recordatorio(inicio).cuerpo(alarma)).isEqualTo("Es hoy a las 19:30.");
    }

    @Test
    @DisplayName("mas de un dia antes nombra el dia de la semana y la fecha")
    void masAdelanteNombraElDia() {
        Instant inicio = Instant.parse("2026-10-02T00:30:00Z");   // jueves 1 de octubre, 19:30 en Lima
        Instant ahora = Instant.parse("2026-09-28T15:00:00Z");

        assertThat(recordatorio(inicio).cuerpo(ahora)).isEqualTo("Es el jueves 1 de octubre a las 19:30.");
    }

    @Test
    @DisplayName("el anuncio de un evento nuevo lleva su propio titulo y no hace cuenta regresiva")
    void anuncio() {
        Instant inicio = Instant.parse("2026-09-29T00:30:00Z");
        AvisoDeEvento anuncio = new AvisoDeEvento(7L, EVENTO, "Clase abierta", inicio, LIMA, true);

        assertThat(anuncio.titulo()).isEqualTo("Nuevo evento: Clase abierta");
        assertThat(anuncio.cuerpo(Instant.parse("2026-09-29T00:10:00Z")))
                .isEqualTo("Es hoy a las 19:30. Toca para ver el detalle.");
    }

    @Test
    @DisplayName("un recordatorio que llega con la ocurrencia ya empezada no sirve; el anuncio si")
    void vigencia() {
        Instant inicio = Instant.parse("2026-09-29T00:30:00Z");

        assertThat(recordatorio(inicio).yaNoSirve(inicio.minusSeconds(1))).isFalse();
        assertThat(recordatorio(inicio).yaNoSirve(inicio)).isTrue();
        assertThat(new AvisoDeEvento(7L, EVENTO, "X", inicio, LIMA, true).yaNoSirve(inicio.plusSeconds(60)))
                .isFalse();
    }

    @Test
    @DisplayName("una clave por fila de la cola: la misma fila da la misma clave, otra fila otra")
    void claveDeterministicaPorRecordatorio() {
        Instant inicio = Instant.parse("2026-09-29T00:30:00Z");
        AvisoDeEvento a = recordatorio(inicio);
        AvisoDeEvento otraFila = new AvisoDeEvento(42L, EVENTO, "Semana de Manifestacion", inicio, LIMA, false);

        assertThat(a.claveDeduplicacion()).isEqualTo(recordatorio(inicio).claveDeduplicacion());
        assertThat(a.claveDeduplicacion()).isNotEqualTo(otraFila.claveDeduplicacion());
        assertThat(a.rutaApp()).isEqualTo("/eventos/" + EVENTO);
    }
}
