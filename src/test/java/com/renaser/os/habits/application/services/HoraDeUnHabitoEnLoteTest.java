package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La hora de UN habito para VARIAS personas en lote (auditoria de consultas del 2026-11-02, H-01): la precedencia es la de siempre (la preferencia gana al
 * catalogo) y, sobre todo, el numero de lecturas NO depende de cuantas personas haya.
 */
class HoraDeUnHabitoEnLoteTest {

    /** Lunes 2026-08-24 a las 09:00 UTC (04:00 en Lima). */
    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-08-24T09:00:00Z"));
    private static final LocalDate HOY = LocalDate.of(2026, 8, 24);

    private final ConsultarProgresoParticipanteHabitsPort progresoPort = mock(ConsultarProgresoParticipanteHabitsPort.class);
    private final LoadHabitoPort habitoPort = mock(LoadHabitoPort.class);
    private final LoadHorarioHabitoPort horarioPort = mock(LoadHorarioHabitoPort.class);
    private final LoadPreferenciaHorarioPort preferenciaPort = mock(LoadPreferenciaHorarioPort.class);
    private final LoadRegistroHabitoPort registroPort = mock(LoadRegistroHabitoPort.class);
    private final HoraDeUnHabitoEnLote lote = new HoraDeUnHabitoEnLote(progresoPort, habitoPort, horarioPort, preferenciaPort, registroPort, RELOJ);

    private Habito despertar;

    @BeforeEach
    void catalogo() {
        despertar = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Despertar", TipoHabito.CHECKBOX, "CUERPO", ExigenciaEvidencia.OPCIONAL, RELOJ.now());
        when(habitoPort.byId(despertar.id())).thenReturn(Optional.of(despertar));
        when(horarioPort.porHabitos(any())).thenReturn(List.of(HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), despertar.id(), 1, null,
                TipoDia.TODOS, LocalTime.of(6, 0), LocalTime.of(22, 0), RELOJ.now())));
        when(preferenciaPort.horaDeUnHabitoDeVarios(any(), any(), any())).thenReturn(Map.of());
        when(registroPort.diaProgramaMasAltoGeneradoDeVarios(any(), any())).thenReturn(Map.of());
    }

    private Map<UserId, ProgresoParticipanteHabits> personas(int cuantas, boolean suspendida) {
        Map<UserId, ProgresoParticipanteHabits> progresos = new HashMap<>();
        for (int i = 0; i < cuantas; i++) {
            progresos.put(UserId.of(UUID.randomUUID()), new ProgresoParticipanteHabits(21, "America/Lima", RolParticipante.TRAINEE, suspendida, true));
        }
        when(progresoPort.deVarios(any())).thenReturn(progresos);
        return progresos;
    }

    @Test
    @DisplayName("sin preferencia rige el horario del catalogo; con una preferencia propia gana la de la persona, campo por campo")
    void laPreferenciaGanaAlCatalogo() {
        var progresos = personas(2, false);
        List<UserId> ids = new ArrayList<>(progresos.keySet());
        UserId conPreferencia = ids.get(0);
        UserId sinPreferencia = ids.get(1);
        when(preferenciaPort.horaDeUnHabitoDeVarios(any(), any(), any())).thenReturn(Map.of(conPreferencia,
                PreferenciaHorario.crear(conPreferencia, despertar.id(), LocalTime.of(7, 30), null, RELOJ.now())));

        Map<UserId, LocalTime> horas = lote.de(ids, despertar.id(), HOY);

        assertThat(horas.get(conPreferencia)).isEqualTo(LocalTime.of(7, 30));
        assertThat(horas.get(sinPreferencia)).isEqualTo(LocalTime.of(6, 0));
    }

    @Test
    @DisplayName("una persona suspendida o sin programa no tiene hora; el habito que no existe, nadie")
    void suspendidaSinProgramaOHabitoInexistente() {
        var progresos = personas(2, false);
        UserId suspendida = UserId.of(UUID.randomUUID());
        UserId sinPrograma = UserId.of(UUID.randomUUID());
        progresos.put(suspendida, new ProgresoParticipanteHabits(21, "America/Lima", RolParticipante.TRAINEE, true, true));
        List<UserId> todas = new ArrayList<>(progresos.keySet());
        todas.add(sinPrograma);

        Map<UserId, LocalTime> horas = lote.de(todas, despertar.id(), HOY);

        assertThat(horas).doesNotContainKeys(suspendida, sinPrograma).hasSize(2);
        assertThat(lote.de(todas, HabitoId.of(UUID.randomUUID()), HOY)).isEmpty();
        assertThat(lote.de(List.of(), despertar.id(), HOY)).isEmpty();
    }

    @Test
    @DisplayName("las lecturas son las mismas con 2 personas que con 40: una del progreso, una del habito, una de sus horarios y una de las capas de preferencia")
    void lasLecturasNoDependenDeCuantasPersonas() {
        var pocas = personas(2, false);
        lote.de(pocas.keySet(), despertar.id(), HOY);
        verify(progresoPort, times(1)).deVarios(any());
        verify(habitoPort, times(1)).byId(any());
        verify(horarioPort, times(1)).porHabitos(any());
        verify(preferenciaPort, times(1)).horaDeUnHabitoDeVarios(any(), any(), any());

        var muchas = personas(40, false);
        lote.de(muchas.keySet(), despertar.id(), HOY);
        verify(progresoPort, times(2)).deVarios(any());
        verify(habitoPort, times(2)).byId(any());
        verify(horarioPort, times(2)).porHabitos(any());
        verify(preferenciaPort, times(2)).horaDeUnHabitoDeVarios(any(), any(), any());
        // Nunca por persona.
        verify(progresoPort, never()).deParticipante(any());
        verify(preferenciaPort, never()).porParticipanteHabitosYFecha(any(), any(), any());
        verify(preferenciaPort, never()).porParticipanteHabitosYFechaSinAvisoDelDia(any(), any(), any());
    }
}
