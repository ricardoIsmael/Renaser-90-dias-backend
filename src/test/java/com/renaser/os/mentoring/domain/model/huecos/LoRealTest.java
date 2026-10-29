package com.renaser.os.mentoring.domain.model.huecos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo real frente a lo planificado: a tiempo, tarde y no cumplido (FR-014). Los instantes se escriben en UTC y la zona
 * es Lima (UTC-5): una hora local de las 21:00 es la 02:00 UTC del dia siguiente.
 */
class LoRealTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDate MARTES = LUNES.plusDays(1);
    private static final LocalDate MIERCOLES = LUNES.plusDays(2);

    private static Instant lima(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto).atZone(LIMA).toInstant();
    }

    private static Cumplible cumplible(LocalDate dia, LocalTime inicio, LocalTime fin, Instant cumplidoEn,
                                       boolean opcional) {
        return new Cumplible(dia, inicio, fin, cumplidoEn, opcional);
    }

    @Test
    @DisplayName("a tiempo es hasta el limite si lo tiene, y hasta el inicio si no; despues es tarde")
    void aTiempoYTarde() {
        LocalTime siete = LocalTime.of(7, 0);
        LocalTime nueve = LocalTime.of(9, 0);

        LoReal real = LoReal.de(LIMA, MIERCOLES, List.of(
                cumplible(LUNES, siete, nueve, lima(LUNES, 8, 59), false),   // dentro del plazo: a tiempo
                cumplible(LUNES, siete, nueve, lima(LUNES, 9, 1), false),    // pasado el plazo: tarde
                cumplible(LUNES, siete, null, lima(LUNES, 7, 0), false),     // justo a su hora: a tiempo
                cumplible(LUNES, siete, null, lima(LUNES, 7, 5), false)));   // sin plazo y pasada su hora: tarde

        assertThat(real).isEqualTo(new LoReal(2, 2, 0));
    }

    @Test
    @DisplayName("sin ninguna hora (Despertar) siempre esta a tiempo")
    void sinHora() {
        LoReal real = LoReal.de(LIMA, MIERCOLES, List.of(cumplible(LUNES, null, null, lima(LUNES, 11, 0), false)));

        assertThat(real).isEqualTo(new LoReal(1, 0, 0));
    }

    @Test
    @DisplayName("cumplirlo otro dia es tarde")
    void otroDia() {
        LoReal real = LoReal.de(LIMA, MIERCOLES, List.of(
                cumplible(LUNES, LocalTime.of(7, 0), LocalTime.of(9, 0), lima(MARTES, 7, 0), false)));

        assertThat(real).isEqualTo(new LoReal(0, 1, 0));
    }

    @Test
    @DisplayName("un plazo que cierra pasada la medianoche (22:00 a 02:00) vale hasta las 02:00 del dia siguiente")
    void plazoQueCruza() {
        Instant aLasUnaDeLaMadrugada = lima(MARTES, 1, 0);
        Instant aLasTres = lima(MARTES, 3, 0);

        LoReal real = LoReal.de(LIMA, MIERCOLES, List.of(
                cumplible(LUNES, LocalTime.of(22, 0), LocalTime.of(2, 0), aLasUnaDeLaMadrugada, false),
                cumplible(LUNES, LocalTime.of(22, 0), LocalTime.of(2, 0), aLasTres, false)));

        assertThat(real).isEqualTo(new LoReal(1, 1, 0));
    }

    @Test
    @DisplayName("el instante se lee en la hora de la persona: las 02:00 UTC son las 21:00 del dia anterior en Lima")
    void madrugadaUtc() {
        // 2026-10-27T02:00Z = lunes 26 a las 21:00 en Lima.
        Instant madrugadaUtc = Instant.parse("2026-10-27T02:00:00Z");

        LoReal aTiempo = LoReal.de(LIMA, MIERCOLES, List.of(
                cumplible(LUNES, LocalTime.of(21, 0), null, madrugadaUtc, false)));
        LoReal tarde = LoReal.de(LIMA, MIERCOLES, List.of(
                cumplible(LUNES, LocalTime.of(20, 0), null, madrugadaUtc, false)));

        assertThat(aTiempo).isEqualTo(new LoReal(1, 0, 0));
        assertThat(tarde).isEqualTo(new LoReal(0, 1, 0));
    }

    @Test
    @DisplayName("no cumplido: solo un dia ya terminado y no opcional; hoy, lo opcional y lo futuro no cuentan como falta")
    void noCumplidos() {
        LoReal real = LoReal.de(LIMA, MARTES, List.of(
                cumplible(LUNES, LocalTime.of(7, 0), null, null, false),     // ayer, sin cumplir: no cumplido
                cumplible(LUNES, LocalTime.of(7, 0), null, null, true),      // opcional: no es falta
                cumplible(MARTES, LocalTime.of(7, 0), null, null, false),    // hoy: todavia corre
                cumplible(MIERCOLES, LocalTime.of(7, 0), null, null, false))); // manana: no existe

        assertThat(real).isEqualTo(new LoReal(0, 0, 1));
    }

    @Test
    @DisplayName("sin nada que cumplir, todo en cero: un dia sin registros no es falta")
    void sinNada() {
        assertThat(LoReal.de(LIMA, MARTES, List.of())).isEqualTo(LoReal.NADA);
    }

    @Test
    @DisplayName("un cumplimiento de hoy se cuenta; uno de un dia por venir se ignora")
    void hoyYFuturo() {
        LoReal real = LoReal.de(LIMA, MARTES, List.of(
                cumplible(MARTES, LocalTime.of(7, 0), null, lima(MARTES, 6, 0), false),
                cumplible(MIERCOLES, LocalTime.of(7, 0), null, lima(MARTES, 6, 0), false)));

        assertThat(real).isEqualTo(new LoReal(1, 0, 0));
    }
}
