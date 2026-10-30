package com.renaser.os.habits.domain.model.radar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 012: cuando se puede registrar el Codigo Renaser y a que hora estaba prevista. Los dias 0 a 7 se comportan
 * EXACTAMENTE como antes (FR-001); desde el 8, solo con Mi Radar encendido, en un dia de la semana que tenga Radar y de
 * 03:00 a 23:59 (FR-008a). Funciones puras: el momento local entra como parametro, asi que el reloj en la madrugada UTC
 * se prueba tambien en {@code RadarServiceTest}.
 */
class HorarioDelRadarTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Un lunes: el dia de la semana se suma desde aca. */
    private static final LocalDate UN_LUNES = LocalDate.of(2026, 10, 19);

    private static final MiRadar APAGADO = MiRadar.apagado();
    private static final MiRadar ENCENDIDO = MiRadar.encendido(List.of(9, 14, 20));
    /** 9, 14 y 20 todos los dias, salvo el lunes (solo la 15) y el domingo (sin Radar). */
    private static final MiRadar POR_DIA = MiRadar.encendido(List.of(9, 14, 20), List.of(
            new MiRadar.DiaPropio(1, List.of(15)), new MiRadar.DiaPropio(7, List.of())));

    private static ZonedDateTime local(DayOfWeek dia, int hora) {
        return UN_LUNES.plusDays(dia.getValue() - 1L).atTime(hora, 30).atZone(LIMA);
    }

    // ── dias 0 a 7: como hoy ────────────────────────────────────────────────

    @Test
    @DisplayName("dias 0 a 7: siempre se puede registrar, a cualquier hora y cualquier dia, con o sin Mi Radar")
    void primeraSemanaSiempreSePuedeRegistrar() {
        for (int dia : new int[]{0, 1, 4, 7}) {
            for (DayOfWeek semana : DayOfWeek.values()) {
                for (int hora = 0; hora <= 23; hora++) {
                    ZonedDateTime momento = local(semana, hora);
                    assertThat(HorarioDelRadar.puedeRegistrar(dia, APAGADO, momento)).as("dia %d %s %d", dia, semana,
                            hora).isTrue();
                    assertThat(HorarioDelRadar.puedeRegistrar(dia, ENCENDIDO, momento)).as("dia %d %s %d", dia, semana,
                            hora).isTrue();
                    assertThat(HorarioDelRadar.puedeRegistrar(dia, POR_DIA, momento)).as("dia %d %s %d", dia, semana,
                            hora).isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("dias 0 a 7: la hora prevista es la hora local si cae entre las 08 y las 19, y nada fuera de ahi")
    void primeraSemanaHoraPrevista() {
        for (int dia : new int[]{0, 3, 7}) {
            for (DayOfWeek semana : DayOfWeek.values()) {
                for (int hora = 0; hora <= 23; hora++) {
                    OptionalInt esperada = hora >= 8 && hora <= 19 ? OptionalInt.of(hora) : OptionalInt.empty();
                    assertThat(HorarioDelRadar.horaPrevista(dia, APAGADO, local(semana, hora)))
                            .as("dia %d %s %d", dia, semana, hora).isEqualTo(esperada);
                }
            }
        }
    }

    @Test
    @DisplayName("dias 0 a 7: las horas de Mi Radar, las de todos los dias y las de un dia, no cuentan aunque esten guardadas")
    void primeraSemanaIgnoraMiRadar() {
        // Mi Radar encendido con las 9, 14 y 20: la 20 NO es prevista el dia 7 (la semana 1 llega hasta las 19)...
        assertThat(HorarioDelRadar.horaPrevista(7, ENCENDIDO, local(DayOfWeek.WEDNESDAY, 20))).isEmpty();
        // ...y la 11 SI lo es, aunque no este en sus horas.
        assertThat(HorarioDelRadar.horaPrevista(7, ENCENDIDO, local(DayOfWeek.WEDNESDAY, 11))).hasValue(11);
        // El domingo «Sin Radar» tampoco cuenta en la semana 1: se registra y las franjas son las de siempre.
        assertThat(HorarioDelRadar.puedeRegistrar(7, POR_DIA, local(DayOfWeek.SUNDAY, 12))).isTrue();
        assertThat(HorarioDelRadar.horaPrevista(7, POR_DIA, local(DayOfWeek.SUNDAY, 12))).hasValue(12);
        // El lunes tiene solo la 15, pero la semana 1 sigue en 08-19.
        assertThat(HorarioDelRadar.horaPrevista(5, POR_DIA, local(DayOfWeek.MONDAY, 9))).hasValue(9);
    }

    // ── desde el dia 8: apagado y encendido igual todos los dias ────────────

    @Test
    @DisplayName("desde el dia 8 con Mi Radar apagado no se puede registrar a ninguna hora ni en ningun dia, ni el dia 90")
    void apagadoNoRegistra() {
        for (int dia : new int[]{8, 9, 30, 90}) {
            for (DayOfWeek semana : DayOfWeek.values()) {
                for (int hora = 0; hora <= 23; hora++) {
                    assertThat(HorarioDelRadar.puedeRegistrar(dia, APAGADO, local(semana, hora)))
                            .as("dia %d %s %d", dia, semana, hora).isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("desde el dia 8 encendido (sin dias propios) se registra de 03:00 a 23:59 en cualquier dia, y no de 00:00 a 02:59")
    void encendidoRegistraDe03a2359() {
        for (int dia : new int[]{8, 10, 90}) {
            for (DayOfWeek semana : DayOfWeek.values()) {
                for (int hora = 0; hora <= 23; hora++) {
                    assertThat(HorarioDelRadar.puedeRegistrar(dia, ENCENDIDO, local(semana, hora)))
                            .as("dia %d %s %d", dia, semana, hora).isEqualTo(hora >= 3);
                }
            }
        }
    }

    @Test
    @DisplayName("desde el dia 8 la hora prevista es la hora local solo si es una de las elegidas")
    void encendidoHoraPrevista() {
        assertThat(HorarioDelRadar.horaPrevista(10, ENCENDIDO, local(DayOfWeek.TUESDAY, 9))).hasValue(9);
        assertThat(HorarioDelRadar.horaPrevista(10, ENCENDIDO, local(DayOfWeek.TUESDAY, 14))).hasValue(14);
        assertThat(HorarioDelRadar.horaPrevista(10, ENCENDIDO, local(DayOfWeek.TUESDAY, 20))).hasValue(20);
        // Una hora libre (entre 03:00 y 23:59) se registra, pero sin hora prevista.
        assertThat(HorarioDelRadar.horaPrevista(10, ENCENDIDO, local(DayOfWeek.TUESDAY, 15))).isEmpty();
        assertThat(HorarioDelRadar.horaPrevista(10, ENCENDIDO, local(DayOfWeek.TUESDAY, 4))).isEmpty();
    }

    @Test
    @DisplayName("el dia 8 es el primero de Mi Radar y el 7 el ultimo de la primera semana")
    void elLimiteEsElDia8() {
        assertThat(HorarioDelRadar.PRIMER_DIA_DE_MI_RADAR).isEqualTo(8);
        assertThat(HorarioDelRadar.puedeRegistrar(7, APAGADO, local(DayOfWeek.WEDNESDAY, 1))).isTrue();
        assertThat(HorarioDelRadar.puedeRegistrar(8, APAGADO, local(DayOfWeek.WEDNESDAY, 12))).isFalse();
    }

    @Test
    @DisplayName("apagado desde el dia 8 no tiene ninguna hora prevista")
    void apagadoSinHoraPrevista() {
        for (DayOfWeek semana : DayOfWeek.values()) {
            for (int hora = 0; hora <= 23; hora++) {
                assertThat(HorarioDelRadar.horaPrevista(12, APAGADO, local(semana, hora))).isEmpty();
            }
        }
    }

    // ── desde el dia 8: por dia de la semana ────────────────────────────────

    @Test
    @DisplayName("un dia con horas propias: la prevista es una de las suyas y NO las de todos los dias; lo demas se registra libre")
    void diaConHorasPropias() {
        // El lunes (dia 1) tiene solo la 15.
        assertThat(HorarioDelRadar.horaPrevista(10, POR_DIA, local(DayOfWeek.MONDAY, 15))).hasValue(15);
        assertThat(HorarioDelRadar.horaPrevista(10, POR_DIA, local(DayOfWeek.MONDAY, 9))).isEmpty();
        assertThat(HorarioDelRadar.horaPrevista(10, POR_DIA, local(DayOfWeek.MONDAY, 14))).isEmpty();
        // Un registro libre del lunes (a las 9) se puede hacer: es un dia con Radar y es de 03:00 a 23:59.
        assertThat(HorarioDelRadar.puedeRegistrar(10, POR_DIA, local(DayOfWeek.MONDAY, 9))).isTrue();
        assertThat(HorarioDelRadar.puedeRegistrar(10, POR_DIA, local(DayOfWeek.MONDAY, 2))).isFalse();
    }

    @Test
    @DisplayName("los dias que no estan en dias siguen las horas de todos los dias")
    void diaSinHorasPropiasSigueLasDeTodos() {
        for (DayOfWeek semana : new DayOfWeek[]{DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY}) {
            assertThat(HorarioDelRadar.horaPrevista(10, POR_DIA, local(semana, 9))).as("%s 9", semana).hasValue(9);
            assertThat(HorarioDelRadar.horaPrevista(10, POR_DIA, local(semana, 14))).as("%s 14", semana).hasValue(14);
            assertThat(HorarioDelRadar.horaPrevista(10, POR_DIA, local(semana, 15))).as("%s 15", semana).isEmpty();
            assertThat(HorarioDelRadar.puedeRegistrar(10, POR_DIA, local(semana, 15))).as("%s 15", semana).isTrue();
        }
    }

    @Test
    @DisplayName("«Sin Radar ese dia» se comporta como apagado ese dia: ni se registra ni tiene hora prevista, a ninguna hora")
    void diaSinRadar() {
        for (int dia : new int[]{8, 10, 90}) {
            for (int hora = 0; hora <= 23; hora++) {
                ZonedDateTime domingo = local(DayOfWeek.SUNDAY, hora);
                assertThat(HorarioDelRadar.puedeRegistrar(dia, POR_DIA, domingo)).as("dia %d domingo %d", dia, hora)
                        .isFalse();
                assertThat(HorarioDelRadar.horaPrevista(dia, POR_DIA, domingo)).as("dia %d domingo %d", dia, hora)
                        .isEmpty();
            }
        }
        // Pero Mi Radar sigue encendido el resto de la semana.
        assertThat(HorarioDelRadar.puedeRegistrar(10, POR_DIA, local(DayOfWeek.SATURDAY, 10))).isTrue();
    }

    @Test
    @DisplayName("el dia de la semana es el del momento local que se pasa: el mismo instante cae en domingo o en lunes segun la zona")
    void elDiaDeLaSemanaEsElDelMomentoLocal() {
        // 2026-10-26T03:00Z es lunes en UTC y domingo 22:00 en Lima (UTC-5).
        Instant instante = Instant.parse("2026-10-26T03:00:00Z");
        ZonedDateTime enLima = instante.atZone(LIMA);
        ZonedDateTime enUtc = instante.atZone(ZoneId.of("UTC"));

        assertThat(enLima.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(enUtc.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        // POR_DIA: el domingo no tiene Radar y el lunes tiene solo la 15. A las 22:00 de Lima NO se puede registrar...
        assertThat(HorarioDelRadar.puedeRegistrar(10, POR_DIA, enLima)).isFalse();
        // ...y mirado con el reloj de UTC (03:00 del lunes) si se podria: por eso el servidor SIEMPRE pasa la zona de la persona.
        assertThat(HorarioDelRadar.puedeRegistrar(10, POR_DIA, enUtc)).isTrue();
    }
}
