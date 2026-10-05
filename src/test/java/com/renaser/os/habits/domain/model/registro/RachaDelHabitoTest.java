package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.renaser.os.habits.domain.model.registro.EstadoRegistro.COMPLETADO;
import static com.renaser.os.habits.domain.model.registro.EstadoRegistro.EN_CURSO;
import static com.renaser.os.habits.domain.model.registro.EstadoRegistro.EXPIRADO;
import static com.renaser.os.habits.domain.model.registro.EstadoRegistro.FALLIDO;
import static com.renaser.os.habits.domain.model.registro.EstadoRegistro.PENDIENTE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-254: la racha de un habito, con las reglas 1A/2A del dueño y los supuestos S-1..S-5 escritos en
 * {@link RachaDelHabito}. Dominio puro: sin Spring, sin base, sin reloj.
 *
 * <p>Las fechas son de un participante de {@code America/Lima}; "hoy" es el lunes 5 de octubre de 2026.
 */
class RachaDelHabitoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 5);
    private static final LocalDate SIN_INICIO = null;

    private static DiaProgramado hace(int dias, EstadoRegistro estado) {
        return new DiaProgramado(HOY.minusDays(dias), estado, false);
    }

    private static RachaDelHabito racha(List<DiaProgramado> dias) {
        return RachaDelHabito.derivar(dias, HOY, SIN_INICIO);
    }

    @Test
    @DisplayName("sin ningun dia programado no hay racha, y dias mas viejos podrian traerla")
    void sinDias() {
        assertThat(racha(List.of())).isEqualTo(new RachaDelHabito(0, false));
        assertThat(RachaDelHabito.derivar(null, HOY, SIN_INICIO).dias()).isZero();
    }

    @Test
    @DisplayName("tres dias programados cumplidos seguidos, terminando hoy: 3")
    void tresSeguidosHastaHoy() {
        assertThat(racha(List.of(hace(0, COMPLETADO), hace(1, COMPLETADO), hace(2, COMPLETADO))).dias())
                .isEqualTo(3);
    }

    @Nested
    @DisplayName("hoy")
    class Hoy {

        @Test
        @DisplayName("hoy pendiente no corta: cuenta hasta ayer")
        void hoyPendienteNoCorta() {
            assertThat(racha(List.of(hace(0, PENDIENTE), hace(1, COMPLETADO), hace(2, COMPLETADO))).dias())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("hoy cumplido suma uno")
        void hoyCumplidoSuma() {
            assertThat(racha(List.of(hace(0, COMPLETADO), hace(1, COMPLETADO))).dias()).isEqualTo(2);
        }

        @Test
        @DisplayName("S-2: hoy EN_CURSO (Santuario abierto) no corta")
        void hoyEnCursoNoCorta() {
            assertThat(racha(List.of(hace(0, EN_CURSO), hace(1, COMPLETADO))).dias()).isEqualTo(1);
        }

        @Test
        @DisplayName("S-2: hoy EXPIRADO todavia se puede completar tarde, asi que no corta")
        void hoyExpiradoNoCorta() {
            assertThat(racha(List.of(hace(0, EXPIRADO), hace(1, COMPLETADO))).dias()).isEqualTo(1);
        }

        @Test
        @DisplayName("S-2: hoy FALLIDO (Santuario roto) es un veredicto: corta, y ya no importa lo de antes")
        void hoyFallidoCorta() {
            assertThat(racha(List.of(hace(0, FALLIDO), hace(1, COMPLETADO), hace(2, COMPLETADO))))
                    .isEqualTo(new RachaDelHabito(0, true));
        }
    }

    @Nested
    @DisplayName("dias ya terminados")
    class DiasTerminados {

        @Test
        @DisplayName("ayer vencido sin cumplir corta: 0 aunque antes hubiera racha")
        void ayerVencidoCorta() {
            assertThat(racha(List.of(hace(0, PENDIENTE), hace(1, EXPIRADO), hace(2, COMPLETADO))))
                    .isEqualTo(new RachaDelHabito(0, true));
        }

        @Test
        @DisplayName("S-3: ayer todavia PENDIENTE (el barrido no paso) corta igual: el dia termino sin cumplirse")
        void ayerPendienteCorta() {
            assertThat(racha(List.of(hace(1, PENDIENTE), hace(2, COMPLETADO))).dias()).isZero();
        }

        @Test
        @DisplayName("S-3: una sesion de ayer que quedo EN_CURSO corta")
        void ayerEnCursoCorta() {
            assertThat(racha(List.of(hace(1, EN_CURSO), hace(2, COMPLETADO))).dias()).isZero();
        }

        @Test
        @DisplayName("el corte deja la racha en lo cumplido DESPUES de el")
        void cuentaSoloLoPosteriorAlCorte() {
            assertThat(racha(List.of(hace(0, COMPLETADO), hace(1, COMPLETADO), hace(2, FALLIDO),
                    hace(3, COMPLETADO), hace(4, COMPLETADO), hace(5, COMPLETADO))))
                    .isEqualTo(new RachaDelHabito(2, true));
        }

        @Test
        @DisplayName("un dia vencido que despues se completo tarde ya es COMPLETADO y cuenta (se deriva, no se guarda)")
        void completadoTardeCuenta() {
            assertThat(racha(List.of(hace(0, PENDIENTE), hace(1, COMPLETADO), hace(2, COMPLETADO))).dias())
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("1A: solo cuentan los dias en que le toca")
    class DiasNoProgramados {

        @Test
        @DisplayName("lunes, miercoles y viernes: los dias del medio no tienen registro y no cortan")
        void diasSinRegistroNoCortan() {
            // Hoy es lunes 5: le toco el viernes 2, el miercoles 30/09 y el lunes 28/09. Hoy no hay registro.
            List<DiaProgramado> lmv = List.of(hace(3, COMPLETADO), hace(5, COMPLETADO), hace(7, COMPLETADO));

            assertThat(HOY.minusDays(3).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
            assertThat(racha(lmv)).isEqualTo(new RachaDelHabito(3, false));
        }

        @Test
        @DisplayName("una semanal (audioterapia): cuatro semanas cumplidas seguidas son 4")
        void semanal() {
            assertThat(racha(List.of(hace(0, PENDIENTE), hace(7, COMPLETADO), hace(14, COMPLETADO),
                    hace(21, COMPLETADO), hace(28, COMPLETADO), hace(35, EXPIRADO))).dias()).isEqualTo(4);
        }

        @Test
        @DisplayName("cambio de dias programados: lo que le toco cada dia quedo escrito en su registro")
        void cambioDeDiasProgramados() {
            // Hasta hace 10 dias iba lunes-miercoles-viernes; desde entonces, martes y jueves.
            List<DiaProgramado> dias = new ArrayList<>();
            for (int haceDias = 3; haceDias <= 30; haceDias++) {
                DayOfWeek dia = HOY.minusDays(haceDias).getDayOfWeek();
                boolean antes = haceDias > 10 && (dia == DayOfWeek.MONDAY || dia == DayOfWeek.WEDNESDAY
                        || dia == DayOfWeek.FRIDAY);
                boolean despues = haceDias <= 10 && (dia == DayOfWeek.TUESDAY || dia == DayOfWeek.THURSDAY);
                if (antes || despues) {
                    dias.add(hace(haceDias, COMPLETADO));
                }
            }

            // 2 martes/jueves recientes (29/09 y 1/10) + 8 lunes/miercoles/viernes del 7/09 al 23/09.
            assertThat(dias).hasSize(2 + 8);
            assertThat(racha(dias).dias()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("2A: la pausa congela")
    class Pausa {

        @Test
        @DisplayName("pausa en el medio: no corta ni suma, y la racha sigue al reanudar")
        void pausaEnElMedio() {
            // Cumplio del 25/09 al 27/09, pauso del 28/09 al 2/10 (sin registros) y reanudo el 3.
            List<DiaProgramado> dias = List.of(hace(10, COMPLETADO), hace(9, COMPLETADO), hace(8, COMPLETADO),
                    hace(2, COMPLETADO), hace(1, COMPLETADO), hace(0, PENDIENTE));

            assertThat(racha(dias)).isEqualTo(new RachaDelHabito(5, false));
        }

        @Test
        @DisplayName("en pausa hoy (sin registro de hoy): la racha queda congelada en lo que tenia")
        void enPausaHoy() {
            assertThat(racha(List.of(hace(4, COMPLETADO), hace(5, COMPLETADO))).dias()).isEqualTo(2);
        }

        @Test
        @DisplayName("pausar DESPUES de fallar no limpia la racha: lo ya vencido se queda (RetirarObligacionesPausadas)")
        void pausarDespuesDeFallarNoLimpia() {
            // El 3 vencio sin cumplirse y ese mismo dia lo pauso: pausar solo borra lo PENDIENTE.
            List<DiaProgramado> dias = List.of(hace(5, COMPLETADO), hace(4, COMPLETADO), hace(2, EXPIRADO),
                    hace(0, PENDIENTE));

            assertThat(racha(dias)).isEqualTo(new RachaDelHabito(0, true));
        }
    }

    @Nested
    @DisplayName("bordes")
    class Bordes {

        @Test
        @DisplayName("habito nuevo: solo su registro de hoy, pendiente = 0; cumplido = 1")
        void habitoNuevo() {
            assertThat(racha(List.of(hace(0, PENDIENTE))).dias()).isZero();
            assertThat(racha(List.of(hace(0, COMPLETADO))).dias()).isEqualTo(1);
        }

        @Test
        @DisplayName("S-4: los dias anteriores al inicio del programa no cuentan")
        void antesDelInicioNoCuenta() {
            List<DiaProgramado> dias = List.of(hace(0, COMPLETADO), hace(1, COMPLETADO), hace(2, COMPLETADO),
                    hace(3, COMPLETADO), hace(4, COMPLETADO));

            assertThat(RachaDelHabito.derivar(dias, HOY, HOY.minusDays(2)).dias()).isEqualTo(3);
        }

        @Test
        @DisplayName("S-4: un programa que todavia no empezo no tiene racha")
        void programaSinEmpezar() {
            assertThat(RachaDelHabito.derivar(List.of(hace(0, COMPLETADO)), HOY, HOY.plusDays(1)))
                    .isEqualTo(new RachaDelHabito(0, true));
        }

        @Test
        @DisplayName("una fecha futura es un dato corrupto: no suma ni corta")
        void fechaFuturaSeIgnora() {
            assertThat(racha(List.of(new DiaProgramado(HOY.plusDays(1), COMPLETADO, false),
                    new DiaProgramado(HOY.plusDays(2), EXPIRADO, false), hace(0, COMPLETADO))).dias()).isEqualTo(1);
        }

        @Test
        @DisplayName("el orden de entrada no importa")
        void ordenDeEntrada() {
            List<DiaProgramado> dias = new ArrayList<>(List.of(hace(0, PENDIENTE), hace(1, COMPLETADO),
                    hace(3, COMPLETADO), hace(4, EXPIRADO), hace(5, COMPLETADO)));
            Collections.shuffle(dias, new Random(254));

            assertThat(racha(dias)).isEqualTo(new RachaDelHabito(2, true));
        }

        @Test
        @DisplayName("dos filas de la misma fecha (no deberia pasar por la UNIQUE): gana la mejor")
        void mismaFechaDosVeces() {
            assertThat(racha(List.of(hace(1, EXPIRADO), hace(1, COMPLETADO))).dias()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("S-1: dias opcionales")
    class Opcionales {

        @Test
        @DisplayName("un dia opcional (ciclo de intoxicacion) que no se cumplio corta igual: tenia registro, le tocaba")
        void opcionalSinCumplirCorta() {
            List<DiaProgramado> dias = List.of(new DiaProgramado(HOY.minusDays(1), EXPIRADO, true),
                    hace(2, COMPLETADO));

            assertThat(racha(dias).dias()).isZero();
        }

        @Test
        @DisplayName("un dia opcional cumplido suma como cualquier otro")
        void opcionalCumplidoSuma() {
            assertThat(racha(List.of(new DiaProgramado(HOY.minusDays(1), COMPLETADO, true), hace(2, COMPLETADO)))
                    .dias()).isEqualTo(2);
        }
    }

    /**
     * Regla 02 §3: el caso que E-91 escondia. A las 03:00 UTC del 5 en Lima todavia son las 22:00 del 4.
     * El registro del 4 es el de HOY para la persona y sigue pendiente; si "hoy" se sacara de la fecha
     * UTC, ese pendiente pasaria a ser un dia terminado y la racha caeria a 0 toda la noche.
     */
    @Test
    @DisplayName("reloj a las 03:00 UTC: para alguien de Lima hoy es el 4, y su pendiente no corta")
    void madrugadaUtcEsAyerEnLima() {
        Instant ahora = Instant.parse("2026-10-05T03:00:00Z");
        LocalDate hoyEnLima = ahora.atZone(ZoneId.of("America/Lima")).toLocalDate();
        List<DiaProgramado> dias = List.of(new DiaProgramado(LocalDate.of(2026, 10, 4), PENDIENTE, false),
                new DiaProgramado(LocalDate.of(2026, 10, 3), COMPLETADO, false),
                new DiaProgramado(LocalDate.of(2026, 10, 2), COMPLETADO, false));

        assertThat(hoyEnLima).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(RachaDelHabito.derivar(dias, hoyEnLima, SIN_INICIO).dias()).isEqualTo(2);
        // Con la fecha del servidor (UTC) el mismo dato daria 0: es exactamente el error a evitar.
        assertThat(RachaDelHabito.derivar(dias, ahora.atZone(ZoneOffset.UTC).toLocalDate(), SIN_INICIO).dias())
                .isZero();
    }
}
