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
        HabitoDelDia ritual = habito("RITUAL TIERRA", "PENDIENTE", 0, AHORA.minusSeconds(3600 * 10), LocalTime.of(6, 30), "Espíritu");
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
                .contains("RITUAL TIERRA | Espíritu | estado=PENDIENTE | ya_no_da_puntos")
                .contains("solo los 2 o 3 primeros").contains("Total en juego: 20 puntos en 2 habito(s)")
                .doesNotContainIgnoringCase("vencio").doesNotContainIgnoringCase("vencieron");
    }

    /** Spec 038 (D-259): un registro EXPIRADO es un dia cerrado: no se cuenta entre lo que falta ni se le deja boton. */
    // spec 038 FR-010
    @Test
    @DisplayName("spec 038: un EXPIRADO es un dia cerrado: no cuenta entre los que faltan, no lleva marca de evidencia y se dice que no se registra")
    void unDiaCerradoNoSeRegistra() {
        HabitoDelDia cerrado = new HabitoDelDia(UUID.randomUUID(), "LECTURA", "EXPIRADO", null, 10, null, true, List.of(), null, null,
                LocalTime.of(6, 0), "Mente");
        HabitoDelDia pendiente = habito("Caminar", "PENDIENTE", 10, AHORA.plusSeconds(3600), LocalTime.of(21, 0), "Cuerpo");

        String texto = LoQueLeFaltaHoy.texto(List.of(cerrado, pendiente), AHORA);

        assertThat(texto).startsWith("Le faltan 1 de 2 habitos de hoy. Por dimension: Cuerpo 1.")
                .contains("Ademas 1 son de un dia que ya cerro: no se pueden registrar ni cuentan entre los que le faltan")
                .contains("LECTURA | Mente | estado=EXPIRADO | dia_cerrado (ese dia ya termino: no se puede registrar")
                .doesNotContain("estado=EXPIRADO | ya_no_da_puntos").doesNotContain("exige_evidencia");
        assertThat(LoQueLeFaltaHoy.texto(List.of(cerrado), AHORA))
                .startsWith("Hoy no le queda ninguno por registrar (1 en total): 1 son de un dia que ya cerro");
    }

    @Test
    @DisplayName("spec 038: un ritual dice que se registra escribiendo; el jugo verde, solo que exige evidencia")
    void unRitualSeEscribe() {
        HabitoDelDia ritual = new HabitoDelDia(UUID.randomUUID(), "RITUAL DE LA MAÑANA", "PENDIENTE", 10, 10,
                AHORA.plusSeconds(3600), true, List.of(), "RITUAL_MORNING", null, LocalTime.of(6, 0), "Espíritu");
        HabitoDelDia jugo = new HabitoDelDia(UUID.randomUUID(), "JUGO VERDE", "PENDIENTE", 10, 10,
                AHORA.plusSeconds(3600), true, List.of(), "GREEN_JUICE", null, LocalTime.of(7, 0), "Cuerpo");

        String texto = LoQueLeFaltaHoy.texto(List.of(ritual, jugo), AHORA);

        assertThat(texto).contains("RITUAL DE LA MAÑANA | Espíritu | estado=PENDIENTE | puntos_en_juego=10 de 10 | sus_puntos_terminan_en=1 h 0 min "
                        + "| exige_evidencia=si | registro=escribir (ritual: sin foto, escribe como se siente)")
                .contains("JUGO VERDE | Cuerpo | estado=PENDIENTE | puntos_en_juego=10 de 10 | sus_puntos_terminan_en=1 h 0 min | exige_evidencia=si")
                .doesNotContain("JUGO VERDE | Cuerpo | estado=PENDIENTE | puntos_en_juego=10 de 10 | sus_puntos_terminan_en=1 h 0 min | exige_evidencia=si | registro");
    }

    @Test
    @DisplayName("con todo hecho lo dice en una linea, sin reparto")
    void todoHecho() {
        String texto = LoQueLeFaltaHoy.texto(List.of(habito("Leer", "COMPLETADO", null, null, null, "Mente")), AHORA);

        assertThat(texto).startsWith("Hoy ya hizo todos sus habitos (1).").doesNotContain("Le faltan");
    }

    private static HabitoDelDia kilometros(String estado, java.math.BigDecimal lleva, String origen, boolean conPasosConectados) {
        var medicion = new com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.MedicionDelHabito("KILOMETROS",
                new java.math.BigDecimal("5.00"), lleva, origen, null, conPasosConectados);
        return new HabitoDelDia(UUID.randomUUID(), "KILOMETROS DIARIOS", estado, estado.equals("PENDIENTE") ? 10 : null, 10, null, true,
                List.of(), "DAILY_KM", null, LocalTime.of(17, 0), "Cuerpo", null, null, medicion);
    }

    // spec 037 (T021 de la 038): consultar_habitos_del_dia suma mide=km, la meta y lo que lleva
    @Test
    @DisplayName("spec 037: el habito de km sale con mide=km, la meta y lo que lleva; sin pasos conectados sigue marcado exige_evidencia")
    void kilometrosSinPasosConectados() {
        String texto = LoQueLeFaltaHoy.texto(List.of(kilometros("PENDIENTE", null, null, false)), AHORA);

        assertThat(texto).contains("KILOMETROS DIARIOS | Cuerpo | estado=PENDIENTE").contains("| mide=km | meta=5 | lleva=0")
                .contains("| exige_evidencia=si").doesNotContain("registro=automatico");
    }

    @Test
    @DisplayName("spec 037: con los pasos conectados dice registro=automatico y no pide foto ni la deja como exige_evidencia")
    void kilometrosConPasosConectados() {
        String texto = LoQueLeFaltaHoy.texto(List.of(kilometros("PENDIENTE", null, null, true)), AHORA);

        assertThat(texto).contains("| mide=km | meta=5 | lleva=0").contains("registro=automatico")
                .contains("No pidas foto ni captura").contains("proponer_registrar_con_foto")
                .contains("«Registrar» en la fila de Kilometros diarios en Training").doesNotContain("exige_evidencia=si");
    }

    @Test
    @DisplayName("spec 037: ya registrado dice cuanto llevo y de donde salio (pasos o captura), sin ceros de mas")
    void kilometrosYaRegistrados() {
        String porPasos = LoQueLeFaltaHoy.texto(List.of(kilometros("COMPLETADO", new java.math.BigDecimal("5.40"), "HEALTH_CONNECT", true)), AHORA);
        String porCaptura = LoQueLeFaltaHoy.texto(List.of(kilometros("COMPLETADO", new java.math.BigDecimal("7.00"), "MANUAL", false)), AHORA);

        assertThat(porPasos).contains("| mide=km | meta=5 | lleva=5.4 | origen=pasos");
        assertThat(porCaptura).contains("| mide=km | meta=5 | lleva=7 | origen=captura");
    }

    // spec 042 FR-022
    @Test
    @DisplayName("spec 042: un dia cerrado por carreras con GPS dice origen=carrera, no captura")
    void kilometrosPorCarreras() {
        String porCarrera = LoQueLeFaltaHoy.texto(List.of(kilometros("COMPLETADO", new java.math.BigDecimal("6.20"), "GPS", false)), AHORA);

        assertThat(porCarrera).contains("| mide=km | meta=5 | lleva=6.2 | origen=carrera");
    }

    @Test
    @DisplayName("spec 037: un habito que no mide nada no lleva mide, meta ni lleva")
    void otrosHabitosNoLlevanMedicion() {
        assertThat(LoQueLeFaltaHoy.texto(List.of(habito("Leer", "PENDIENTE", 10, AHORA.plusSeconds(3600), LocalTime.of(20, 30), "Mente")), AHORA))
                .doesNotContain("mide=").doesNotContain("lleva=");
    }
}
