package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TracksDelDiaProyeccionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T09:00:00Z");

    @Mock
    private ConsultarTracksDelDiaUseCase consultarTracksUseCase;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private LoadGuiaHabitoPort loadGuiaPort;

    private TracksDelDiaProyeccionService service;

    @BeforeEach
    void setUp() {
        service = new TracksDelDiaProyeccionService(consultarTracksUseCase, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, loadGuiaPort);
    }

    private static Habito habito(String titulo) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), titulo, TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, AHORA);
    }

    @Test
    void sinRegistrosNoConsultaNingunPuertoDeCatalogo() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(consultarTracksUseCase.consultar(actor, actor, LocalDate.of(2026, 8, 24))).thenReturn(List.of());

        List<TrackDelDiaConCatalogo> resultado = service.consultar(actor, actor, LocalDate.of(2026, 8, 24));

        assertThat(resultado).isEmpty();
        verify(loadHabitoPort, org.mockito.Mockito.never()).porIds(any());
    }

    @Test
    void resuelveTituloTipoGuiaYHorarioConUnaSolaConsultaPorTabla() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habito("Jugo verde");
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), actor, habito.id(),
                LocalDate.of(2026, 8, 24), 10, TipoDia.DISCIPLINA, false, AHORA);
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null,
                TipoDia.TODOS, LocalTime.of(7, 0), LocalTime.of(9, 0), AHORA);
        GuiaHabito guiaTemprana = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habito.id(), 1, AHORA);
        guiaTemprana.actualizarContenido("hacer esto", "asi", null, null, null, null, AHORA);
        GuiaHabito guiaTardia = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habito.id(), 8, AHORA);
        guiaTardia.actualizarContenido("hacer lo nuevo", "asi de nuevo", null, null, null, null, AHORA);

        when(consultarTracksUseCase.consultar(actor, actor, registro.fechaEjecucion())).thenReturn(List.of(registro));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of(horario));
        when(loadGuiaPort.porHabitos(any())).thenReturn(List.of(guiaTemprana, guiaTardia));
        when(loadPreferenciaPort.porParticipanteYHabitos(any(), any())).thenReturn(List.of());

        List<TrackDelDiaConCatalogo> resultado = service.consultar(actor, actor, registro.fechaEjecucion());

        assertThat(resultado).hasSize(1);
        TrackDelDiaConCatalogo vista = resultado.get(0);
        assertThat(vista.tituloHabito()).isEqualTo("Jugo verde");
        assertThat(vista.tipoHabito()).isEqualTo(TipoHabito.CHECKBOX);
        assertThat(vista.guia().queHacer()).isEqualTo("hacer lo nuevo"); // dia 8 es la mas especifica que aplica (diaPrograma=10)
        assertThat(vista.horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(vista.horaLimite()).isEqualTo(LocalTime.of(9, 0));
        verify(loadHabitoPort, times(1)).porIds(any());
        verify(loadHorarioPort, times(1)).porHabitos(any());
        verify(loadGuiaPort, times(1)).porHabitos(any());
        verify(loadPreferenciaPort, times(1)).porParticipanteYHabitos(any(), any());
    }

    @Test
    void laPreferenciaDelParticipanteGanaAlHorarioDelCatalogo() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habito("Meditacion");
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), actor, habito.id(),
                LocalDate.of(2026, 8, 24), 10, TipoDia.DISCIPLINA, false, AHORA);
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null,
                TipoDia.TODOS, LocalTime.of(7, 0), LocalTime.of(9, 0), AHORA);
        PreferenciaHorario preferencia = PreferenciaHorario.crear(actor, habito.id(), LocalTime.of(6, 30), null,
                AHORA);

        when(consultarTracksUseCase.consultar(actor, actor, registro.fechaEjecucion())).thenReturn(List.of(registro));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of(horario));
        when(loadGuiaPort.porHabitos(any())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteYHabitos(any(), any())).thenReturn(List.of(preferencia));

        TrackDelDiaConCatalogo vista = service.consultar(actor, actor, registro.fechaEjecucion()).get(0);

        assertThat(vista.horaDisparo()).isEqualTo(LocalTime.of(6, 30)); // preferencia gana
        assertThat(vista.horaLimite()).isEqualTo(LocalTime.of(9, 0)); // sin override, el del catalogo
        assertThat(vista.guia()).isNull();
    }
}
