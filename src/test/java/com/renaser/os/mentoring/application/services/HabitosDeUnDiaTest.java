package com.renaser.os.mentoring.application.services;

import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.HorarioDelDiaFinder.CuotaCambiosHorario;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorariosDelDia;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder.RegistroDeHabito;
import com.renaser.os.mentoring.domain.model.huecos.Cumplible;
import com.renaser.os.mentoring.domain.model.huecos.HabitoConHora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué hábitos ocupan tiempo un día (FR-003, FR-004 y FR-011): los que de verdad le tocaron si ya pasó, los que le
 * tocarán según su plan si viene.
 */
class HabitosDeUnDiaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate AYER = HOY.minusDays(1);
    private static final LocalDate MANANA = HOY.plusDays(1);

    private static HorarioResuelto horario(UUID id, String clave, LocalTime disparo, LocalTime limite) {
        return new HorarioResuelto(id, "Habito", disparo, limite, false, false, false, false, null, false, clave);
    }

    private static HorarioResuelto conMarcas(UUID id, boolean apagado, boolean pausado, boolean sinTocarAun) {
        return new HorarioResuelto(id, "Habito", LocalTime.of(7, 0), null, false, apagado, pausado, false, null,
                sinTocarAun, null);
    }

    private static HorariosDelDia dia(LocalDate fecha, int diaPrograma, HorarioResuelto... habitos) {
        return new HorariosDelDia(fecha, diaPrograma, List.of(habitos), new CuotaCambiosHorario(0, 3, 3, false));
    }

    private static RegistroDeHabito registro(UUID habito, LocalDate fecha, EstadoObligacion estado, Instant en,
                                             boolean opcional) {
        return new RegistroDeHabito(habito, fecha, estado, opcional, en);
    }

    @Test
    @DisplayName("un dia por venir usa el plan: no cuentan apagados, pausados, los que todavia no empiezan ni los sin hora")
    void diaPorVenir() {
        UUID bueno = UUID.randomUUID();
        UUID sinHora = UUID.randomUUID();
        HorariosDelDia horarios = dia(MANANA, 15, horario(bueno, HabitoConHora.DESPERTAR, LocalTime.of(6, 0), null),
                horario(sinHora, null, null, null), conMarcas(UUID.randomUUID(), true, false, false),
                conMarcas(UUID.randomUUID(), false, true, false), conMarcas(UUID.randomUUID(), false, false, true));

        HabitosDeUnDia habitos = HabitosDeUnDia.de(HOY, horarios, List.of());

        assertThat(habitos.ocupan()).containsExactly(new HabitoConHora(HabitoConHora.DESPERTAR, LocalTime.of(6, 0)));
        assertThat(habitos.cumplibles()).isEmpty();
    }

    @Test
    @DisplayName("antes del Dia 1 y despues del 90 no hay habitos del programa que inventar")
    void fueraDelPrograma() {
        UUID id = UUID.randomUUID();

        assertThat(HabitosDeUnDia.de(HOY, dia(MANANA, 0, horario(id, null, LocalTime.of(7, 0), null)), List.of())
                .ocupan()).isEmpty();
        assertThat(HabitosDeUnDia.de(HOY, dia(MANANA, 91, horario(id, null, LocalTime.of(7, 0), null)), List.of())
                .ocupan()).isEmpty();
        assertThat(HabitosDeUnDia.de(HOY, dia(MANANA, 1, horario(id, null, LocalTime.of(7, 0), null)), List.of())
                .ocupan()).hasSize(1);
        assertThat(HabitosDeUnDia.de(HOY, dia(MANANA, 90, horario(id, null, LocalTime.of(7, 0), null)), List.of())
                .ocupan()).hasSize(1);
    }

    @Test
    @DisplayName("un dia ya vivido usa los registros: solo ocupan los que tuvieron registro, aunque hoy figuren apagados")
    void diaVivido() {
        UUID conRegistro = UUID.randomUUID();
        UUID sinRegistro = UUID.randomUUID();
        HorariosDelDia horarios = dia(AYER, 14, conMarcas(conRegistro, true, false, false),
                horario(sinRegistro, null, LocalTime.of(9, 0), null));
        Instant a = Instant.parse("2026-10-27T12:00:00Z");

        HabitosDeUnDia habitos = HabitosDeUnDia.de(HOY, horarios,
                List.of(registro(conRegistro, AYER, EstadoObligacion.COMPLETADO, a, false)));

        assertThat(habitos.ocupan()).containsExactly(new HabitoConHora(null, LocalTime.of(7, 0)));
        assertThat(habitos.cumplibles()).containsExactly(
                new Cumplible(AYER, LocalTime.of(7, 0), null, a, false));
    }

    @Test
    @DisplayName("un dia ya vivido sin ningun registro no tiene habitos")
    void diaVividoSinRegistros() {
        HabitosDeUnDia habitos = HabitosDeUnDia.de(HOY,
                dia(AYER, 14, horario(UUID.randomUUID(), null, LocalTime.of(7, 0), null)), List.of());

        assertThat(habitos.ocupan()).isEmpty();
        assertThat(habitos.cumplibles()).isEmpty();
    }

    @Test
    @DisplayName("lo real: solo COMPLETADO trae instante; un registro sin horario conocido igual cuenta para lo real")
    void loReal() {
        UUID cumplido = UUID.randomUUID();
        UUID vencido = UUID.randomUUID();
        UUID desaparecido = UUID.randomUUID();
        Instant a = Instant.parse("2026-10-27T12:00:00Z");

        HabitosDeUnDia habitos = HabitosDeUnDia.de(HOY, dia(AYER, 14, horario(cumplido, null, LocalTime.of(7, 0),
                        LocalTime.of(9, 0)), horario(vencido, null, LocalTime.of(8, 0), null)),
                List.of(registro(cumplido, AYER, EstadoObligacion.COMPLETADO, a, false),
                        registro(vencido, AYER, EstadoObligacion.EXPIRADO, null, false),
                        registro(desaparecido, AYER, EstadoObligacion.FALLIDO, null, true)));

        assertThat(habitos.cumplibles()).containsExactly(
                new Cumplible(AYER, LocalTime.of(7, 0), LocalTime.of(9, 0), a, false),
                new Cumplible(AYER, LocalTime.of(8, 0), null, null, false),
                new Cumplible(AYER, null, null, null, true));
        assertThat(habitos.ocupan()).hasSize(2);
    }

    @Test
    @DisplayName("hoy sin registros todavia (el generador no corrio) se planea; con registros se usan los registros")
    void hoy() {
        UUID id = UUID.randomUUID();
        HorariosDelDia horarios = dia(HOY, 15, horario(id, null, LocalTime.of(7, 0), null),
                horario(UUID.randomUUID(), null, LocalTime.of(8, 0), null));

        HabitosDeUnDia sinRegistros = HabitosDeUnDia.de(HOY, horarios, List.of());
        HabitosDeUnDia conRegistros = HabitosDeUnDia.de(HOY, horarios,
                List.of(registro(id, HOY, EstadoObligacion.PENDIENTE, null, false)));

        assertThat(sinRegistros.ocupan()).hasSize(2);
        assertThat(sinRegistros.cumplibles()).isEmpty();
        assertThat(conRegistros.ocupan()).hasSize(1);
        assertThat(conRegistros.cumplibles()).hasSize(1);
    }
}
