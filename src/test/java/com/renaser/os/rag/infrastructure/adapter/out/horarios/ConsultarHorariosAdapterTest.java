package com.renaser.os.rag.infrastructure.adapter.out.horarios;

import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.api.HorarioDelDiaFinder.CambioHorarioProgramado;
import com.renaser.os.habits.api.HorarioDelDiaFinder.CuotaCambiosHorario;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Traduccion campo a campo del contrato de {@code habits} al puerto de {@code rag}, sin perder nada. */
class ConsultarHorariosAdapterTest {

    @Test
    void traduceCadaCampoYDejaPasarElNullDeHoy() {
        HorarioDelDiaFinder finder = mock(HorarioDelDiaFinder.class);
        UserId aprendiz = UserId.of(UUID.randomUUID());
        UUID habitoId = UUID.randomUUID();
        LocalDate fecha = LocalDate.of(2026, 9, 24);
        when(finder.deFecha(aprendiz, null)).thenReturn(new HorarioDelDiaFinder.HorariosDelDia(fecha, 26,
                List.of(new HorarioResuelto(habitoId, "Meditar", LocalTime.of(6, 0), LocalTime.of(8, 0), true, false,
                        true, true, new CambioHorarioProgramado(LocalTime.of(7, 0), null, fecha.plusDays(2)))),
                new CuotaCambiosHorario(1, 2, 3, false)));

        ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
        when(participaciones.deParticipante(aprendiz)).thenReturn(Optional.empty());

        HorariosDelDia dia = new ConsultarHorariosAdapter(finder, participaciones).deFecha(aprendiz, null);

        assertThat(dia.fecha()).isEqualTo(fecha);
        assertThat(dia.diaPrograma()).isEqualTo(26);
        HorarioDeHabito horario = dia.habitos().get(0);
        assertThat(horario.habitoId()).isEqualTo(habitoId);
        assertThat(horario.titulo()).isEqualTo("Meditar");
        assertThat(horario.horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(horario.horaLimite()).isEqualTo(LocalTime.of(8, 0));
        assertThat(horario.personalizado()).isTrue();
        assertThat(horario.apagado()).isFalse();
        assertThat(horario.pausado()).isTrue();
        assertThat(horario.obligatorio()).isTrue();
        assertThat(horario.cambioProgramado().desde()).isEqualTo(fecha.plusDays(2));
        assertThat(dia.cuota().usados()).isEqualTo(1);
        assertThat(dia.cuota().restantes()).isEqualTo(2);
        assertThat(dia.cuota().limite()).isEqualTo(3);
        assertThat(dia.cuota().semanaDeAcomodoLibre()).isFalse();
        // Sin fila de programa no se sabe cuánto dura: los 90 de siempre.
        assertThat(dia.duracionDias()).isEqualTo(90);
    }

    @Test
    void lleva_los_dias_de_su_programa() {
        HorarioDelDiaFinder finder = mock(HorarioDelDiaFinder.class);
        ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
        UserId aprendiz = UserId.of(UUID.randomUUID());
        LocalDate fecha = LocalDate.of(2027, 3, 1);
        when(finder.deFecha(aprendiz, null)).thenReturn(new HorarioDelDiaFinder.HorariosDelDia(fecha, 200, List.of(),
                new CuotaCambiosHorario(0, 3, 3, true)));
        when(participaciones.deParticipante(aprendiz)).thenReturn(Optional.of(new ParticipacionPrograma(aprendiz, true, 200,
                fecha.minusDays(199), ZoneId.of("America/Lima"), FasePrograma.PHASE_3_ALCHEMIST_WARRIOR, null, null, UserRole.TRAINEE,
                false, true, Formacion.DIPLOMADO, 365)));

        assertThat(new ConsultarHorariosAdapter(finder, participaciones).deFecha(aprendiz, null).duracionDias()).isEqualTo(365);
    }
}
