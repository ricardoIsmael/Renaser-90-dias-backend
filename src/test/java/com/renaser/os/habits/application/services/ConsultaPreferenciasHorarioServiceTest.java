package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.ResumenPreferenciasHorario;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaPreferenciasHorarioServiceTest {

    /** 2026-08-24 (lunes) 09:00 UTC — dia de DISCIPLINA, no domingo. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T09:00:00Z"));

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private LoadCambioHorarioPendientePort loadCambioPendientePort;
    @Mock
    private HistorialCambioHorarioPort historialPort;

    private ConsultaPreferenciasHorarioService service;
    private UserId actor;

    @BeforeEach
    void setUp() {
        service = new ConsultaPreferenciasHorarioService(progresoPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, loadCambioPendientePort, historialPort, CLOCK);
        actor = UserId.of(UUID.randomUUID());
        lenient().when(loadHabitoPort.personalesActivosDe(any())).thenReturn(List.of());
    }

    private static Habito habito(String titulo) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), titulo, TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    private void conProgreso(int diaPrograma, boolean suspendido) {
        when(progresoPort.deParticipante(actor)).thenReturn(
                java.util.Optional.of(new ProgresoParticipanteHabits(diaPrograma, "UTC", RolParticipante.TRAINEE,
                        suspendido)));
    }

    @Test
    void rechazaSuspendido() {
        conProgreso(10, true);

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void sinHabitosActivosDevuelveListaVaciaSinConsultarCatalogoDeHorarios() {
        conProgreso(3, false);
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of());

        ResumenPreferenciasHorario resumen = service.consultar(actor);

        assertThat(resumen.habitos()).isEmpty();
        assertThat(resumen.cuota().periodo()).isEqualTo("FREE");
        verify(loadHorarioPort, never()).porHabitos(any());
    }

    @Test
    void devuelveElHorarioDelCatalogoCuandoNoHayPreferenciaPropia() {
        conProgreso(3, false);
        Habito habito = habito("Meditar");
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of(
                HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null, TipoDia.TODOS,
                        LocalTime.of(6, 0), LocalTime.of(8, 0), CLOCK.now())));
        when(loadPreferenciaPort.porParticipanteYHabitos(any(), any())).thenReturn(List.of());
        when(loadCambioPendientePort.deParticipante(actor)).thenReturn(List.of());

        var vista = service.consultar(actor).habitos().get(0);

        assertThat(vista.titulo()).isEqualTo("Meditar");
        assertThat(vista.horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(vista.horaLimite()).isEqualTo(LocalTime.of(8, 0));
        assertThat(vista.personalizado()).isFalse();
        assertThat(vista.cambioProgramado()).isNull();
    }

    @Test
    void laPreferenciaPropiaGanaAlCatalogoYQuedaMarcadaComoPersonalizada() {
        conProgreso(3, false);
        Habito habito = habito("Meditar");
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of(
                HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null, TipoDia.TODOS,
                        LocalTime.of(6, 0), LocalTime.of(8, 0), CLOCK.now())));
        when(loadPreferenciaPort.porParticipanteYHabitos(any(), any())).thenReturn(
                List.of(PreferenciaHorario.crear(actor, habito.id(), LocalTime.of(7, 30), LocalTime.of(9, 30),
                        CLOCK.now())));
        when(loadCambioPendientePort.deParticipante(actor)).thenReturn(List.of());

        var vista = service.consultar(actor).habitos().get(0);

        assertThat(vista.horaDisparo()).isEqualTo(LocalTime.of(7, 30));
        assertThat(vista.horaLimite()).isEqualTo(LocalTime.of(9, 30));
        assertThat(vista.personalizado()).isTrue();
    }

    @Test
    void exponeElCambioProgramadoConSuFechaEfectiva() {
        conProgreso(3, false);
        Habito habito = habito("Meditar");
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteYHabitos(any(), any())).thenReturn(List.of());
        when(loadCambioPendientePort.deParticipante(actor)).thenReturn(
                List.of(CambioHorarioPendiente.programar(actor, habito.id(), LocalTime.of(5, 0), LocalTime.of(7, 0),
                        true, 10, LocalDate.of(2026, 8, 25), CLOCK.now())));

        var vista = service.consultar(actor).habitos().get(0);

        assertThat(vista.cambioProgramado()).isNotNull();
        assertThat(vista.cambioProgramado().horaDisparo()).isEqualTo(LocalTime.of(5, 0));
        assertThat(vista.cambioProgramado().horaLimite()).isEqualTo(LocalTime.of(7, 0));
        assertThat(vista.cambioProgramado().fechaEfectiva()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void enLaSemanaDeAcomodoNoConsultaElHistorial() {
        conProgreso(7, false);
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of());

        var cuota = service.consultar(actor).cuota();

        assertThat(cuota.periodo()).isEqualTo("FREE");
        assertThat(cuota.cambiosUsados()).isZero();
        assertThat(cuota.cambiosRestantes()).isEqualTo(3);
        verify(historialPort, never()).distintosHabitosCambiadosDesde(any(), any());
    }

    @Test
    void pasadaLaSemanaDeAcomodoInformaElCupoConsumido() {
        conProgreso(10, false);
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of());
        when(historialPort.distintosHabitosCambiadosDesde(actor, LocalDate.of(2026, 8, 22)))
                .thenReturn(List.of(habito("a").id(), habito("b").id()));

        var cuota = service.consultar(actor).cuota();

        assertThat(cuota.periodo()).isEqualTo("WEEK");
        assertThat(cuota.cambiosUsados()).isEqualTo(2);
        assertThat(cuota.cambiosRestantes()).isEqualTo(1);
        assertThat(cuota.cambiosLimite()).isEqualTo(3);
    }

    @Test
    void nuncaHaceNMasUnaConsulta() {
        conProgreso(3, false);
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito("uno"), habito("dos"), habito("tres")));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteYHabitos(any(), any())).thenReturn(List.of());
        when(loadCambioPendientePort.deParticipante(actor)).thenReturn(List.of());

        assertThat(service.consultar(actor).habitos()).hasSize(3);

        verify(loadHorarioPort, times(1)).porHabitos(any());
        verify(loadPreferenciaPort, times(1)).porParticipanteYHabitos(any(), any());
        verify(loadCambioPendientePort, times(1)).deParticipante(actor);
        verify(loadHorarioPort, never()).porHabito(any());
    }
}
