package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-455 (2026-09-30): a "¿que habitos me faltan hoy?" el acompanante dijo "el agua tibia y el ritual
 * de la manana ya vencieron" de dos habitos PENDIENTE, sin decir cuantos faltaban ni en que
 * dimension, y sin ordenarlos por el horario de la persona.
 *
 * <p>El reloj, a las 01:00 UTC: son las 20:00 del dia ANTERIOR en Lima (regla 03). Los plazos son
 * instantes y la hora de cada habito ya viene resuelta en su zona, asi que el texto no depende de
 * la zona; el caso queda igual fijado.
 */
class LoQueLeFaltaHoyTest {

    private static final Instant AHORA = Instant.parse("2026-09-30T01:00:00Z");

    private static HabitoDelDia habito(String titulo, String estado, Integer puntos, Instant plazo, LocalTime hora,
                                       String dimension) {
        return new HabitoDelDia(UUID.randomUUID(), titulo, estado, puntos, 10, plazo, false, List.of(), null, null,
                hora, dimension);
    }

    @Test
    @DisplayName("cuenta lo que falta por dimension y lo ordena: primero lo que da puntos, cada grupo por su hora")
    void cuentaYOrdena() {
        HabitoDelDia aguaTibia = habito("AGUA TIBIA", "PENDIENTE", 0, AHORA.minusSeconds(3600 * 12),
                LocalTime.of(6, 0), "Cuerpo");
        HabitoDelDia ritual = habito("RITUAL TIERRA", "EXPIRADO", null, null, LocalTime.of(6, 30), "Espíritu");
        HabitoDelDia caminar = habito("Caminar 40 minutos", "PENDIENTE", 10, AHORA.plusSeconds(3600 * 2),
                LocalTime.of(21, 0), "Cuerpo");
        HabitoDelDia leer = habito("Leer", "PENDIENTE", 10, AHORA.plusSeconds(3600), LocalTime.of(20, 30), "Mente");
        HabitoDelDia jugo = habito("JUGO VERDE", "COMPLETADO", null, null, LocalTime.of(7, 0), "Cuerpo");

        String texto = LoQueLeFaltaHoy.texto(List.of(jugo, caminar, aguaTibia, ritual, leer), AHORA);

        assertThat(texto).startsWith("Le faltan 4 de 5 habitos de hoy. Por dimension: Cuerpo 2, Mente 1, Espíritu 1. "
                + "2 todavia dan puntos y 2 ya no dan puntos porque paso su hora, pero igual puede hacerlos.");
        assertThat(texto.indexOf("| Leer")).isLessThan(texto.indexOf("| Caminar 40 minutos"));
        assertThat(texto.indexOf("| Caminar 40 minutos")).isLessThan(texto.indexOf("| AGUA TIBIA"));
        assertThat(texto.indexOf("| AGUA TIBIA")).isLessThan(texto.indexOf("| RITUAL TIERRA"));
        assertThat(texto.indexOf("| RITUAL TIERRA")).isLessThan(texto.indexOf("| JUGO VERDE"));
        assertThat(texto).contains("hora=20:30 | Leer | Mente | estado=PENDIENTE | puntos_en_juego=10 de 10 "
                        + "| sus_puntos_terminan_en=1 h 0 min")
                .contains("hora=06:00 | AGUA TIBIA | Cuerpo | estado=PENDIENTE | ya_no_da_puntos (paso su hora; "
                        + "igual puede hacerlo)")
                .contains("RITUAL TIERRA | Espíritu | estado=EXPIRADO | ya_no_da_puntos")
                .contains("solo los 2 o 3 primeros").contains("Total en juego: 20 puntos en 2 habito(s)")
                .doesNotContainIgnoringCase("vencio").doesNotContainIgnoringCase("vencieron");
    }

    @Test
    @DisplayName("con todo hecho lo dice en una linea, sin reparto")
    void todoHecho() {
        String texto = LoQueLeFaltaHoy.texto(List.of(habito("Leer", "COMPLETADO", null, null, null, "Mente")), AHORA);

        assertThat(texto).startsWith("Hoy ya hizo todos sus habitos (1).").doesNotContain("Le faltan");
    }
}
