package com.renaser.os.onboarding.domain.model.caja;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La trazabilidad del aprendiz (D-220): de los pasos guardados, qué envíos salieron, por dónde, en qué terminó
 * cada uno y cuándo. Sin Spring: pasos a mano.
 */
class EnvioSalidoTest {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-00000000a0a0"));
    private static final UserId KELIN = UserId.of(UUID.fromString("00000000-0000-0000-0000-00000000ad01"));

    @Test
    @DisplayName("Olva se perdió, Shalom en camino: dos envíos, cada uno con su código, su resultado y su fecha")
    void unoPerdidoYOtroEnCamino() {
        List<PasoDeCaja> pasos = List.of(
                paso(1, TipoPasoCaja.ARMANDO, "2026-09-26T15:00:00Z", Map.of()),
                paso(1, TipoPasoCaja.ENVIADA, "2026-09-27T15:00:00Z", envio("Olva Courier", "Olva", "OLV-7777", "12.5")),
                paso(1, TipoPasoCaja.CON_PROBLEMA, "2026-09-28T15:00:00Z", Map.of(PasoDeCaja.MOTIVO, "PERDIDA",
                        PasoDeCaja.NOTA, "El courier no la encuentra")),
                paso(2, TipoPasoCaja.ARMANDO, "2026-09-29T15:00:00Z", Map.of()),
                paso(2, TipoPasoCaja.ENVIADA, "2026-09-30T15:00:00Z", envio("Shalom", "Shalom", "SH-7777", null)));

        List<EnvioSalido> envios = EnvioSalido.de(pasos);

        assertThat(envios).hasSize(2);
        EnvioSalido primero = envios.get(0);
        assertThat(primero.envio()).isEqualTo(1);
        assertThat(primero.datos().codigo()).isEqualTo("OLV-7777");
        assertThat(primero.datos().rastreoUrl()).hasValue(DatosDelEnvio.RASTREO_OLVA);
        assertThat(primero.resultado()).isEqualTo(EstadoCaja.CON_PROBLEMA);
        assertThat(primero.motivo()).isEqualTo(MotivoProblema.PERDIDA);
        assertThat(primero.en()).isEqualTo(Instant.parse("2026-09-28T15:00:00Z"));
        EnvioSalido segundo = envios.get(1);
        assertThat(segundo.resultado()).isEqualTo(EstadoCaja.ENVIADA);
        assertThat(segundo.en()).as("en camino desde que salió").isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
        assertThat(segundo.motivo()).isNull();
    }

    @Test
    @DisplayName("entregada y después dañada: termina con problema; reapareció después del problema: entregada")
    void elUltimoPasoDelEnvioManda() {
        List<PasoDeCaja> danada = List.of(
                paso(1, TipoPasoCaja.ENVIADA, "2026-09-27T15:00:00Z", envio("Olva", "Olva", "OLV-1", null)),
                paso(1, TipoPasoCaja.ENTREGADA, "2026-09-28T15:00:00Z", Map.of()),
                paso(1, TipoPasoCaja.CON_PROBLEMA, "2026-09-29T15:00:00Z", Map.of(PasoDeCaja.MOTIVO, "DANADA")));
        List<PasoDeCaja> reaparecio = List.of(
                paso(1, TipoPasoCaja.ENVIADA, "2026-09-27T15:00:00Z", envio("Olva", "Olva", "OLV-1", null)),
                paso(1, TipoPasoCaja.CON_PROBLEMA, "2026-09-28T15:00:00Z", Map.of(PasoDeCaja.MOTIVO, "PERDIDA")),
                paso(1, TipoPasoCaja.ENTREGADA, "2026-09-29T15:00:00Z", Map.of()));

        assertThat(EnvioSalido.de(danada)).singleElement().satisfies(e -> {
            assertThat(e.resultado()).isEqualTo(EstadoCaja.CON_PROBLEMA);
            assertThat(e.motivo()).isEqualTo(MotivoProblema.DANADA);
        });
        assertThat(EnvioSalido.de(reaparecio)).singleElement().satisfies(e -> {
            assertThat(e.resultado()).isEqualTo(EstadoCaja.ENTREGADA);
            assertThat(e.motivo()).as("el problema quedó atrás").isNull();
        });
    }

    @Test
    @DisplayName("sin envío que haya salido no hay nada que seguir: armando, o «ya se envió antes» sin datos")
    void sinSalidaNoHayEnvios() {
        assertThat(EnvioSalido.de(List.of(paso(1, TipoPasoCaja.ARMANDO, "2026-09-26T15:00:00Z", Map.of())))).isEmpty();
        assertThat(EnvioSalido.de(List.of(paso(1, TipoPasoCaja.ENTREGADA, "2026-09-26T15:00:00Z",
                Map.of(PasoDeCaja.PREVIA, "true"))))).isEmpty();
    }

    @Test
    @DisplayName("un motivo que este código no conoce se lee vacío, sin romper la lectura")
    void motivoDesconocido() {
        List<PasoDeCaja> pasos = List.of(
                paso(1, TipoPasoCaja.ENVIADA, "2026-09-27T15:00:00Z", envio("Olva", "Olva", "OLV-1", null)),
                paso(1, TipoPasoCaja.CON_PROBLEMA, "2026-09-28T15:00:00Z", Map.of(PasoDeCaja.MOTIVO, "ROBADA")));

        assertThat(EnvioSalido.de(pasos)).singleElement().satisfies(e -> {
            assertThat(e.resultado()).isEqualTo(EstadoCaja.CON_PROBLEMA);
            assertThat(e.motivo()).isNull();
        });
    }

    private static Map<String, String> envio(String medio, String courier, String codigo, String costo) {
        java.util.HashMap<String, String> detalle = new java.util.HashMap<>(Map.of(PasoDeCaja.MEDIO, medio,
                PasoDeCaja.COURIER, courier, PasoDeCaja.CODIGO, codigo));
        if (costo != null) {
            detalle.put(PasoDeCaja.COSTO, costo);
        }
        return detalle;
    }

    private static PasoDeCaja paso(int envio, TipoPasoCaja tipo, String en, Map<String, String> detalle) {
        return new PasoDeCaja(ANA, envio, tipo, Instant.parse(en), KELIN, detalle);
    }
}
