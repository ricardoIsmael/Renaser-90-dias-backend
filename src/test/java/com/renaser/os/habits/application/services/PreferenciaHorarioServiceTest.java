package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.ResultadoEdicionPreferencia;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SaveCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenciaHorarioServiceTest {

    /** 2026-08-24 09:00 UTC. */
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
    private SavePreferenciaHorarioPort savePreferenciaPort;
    @Mock
    private SaveCambioHorarioPendientePort saveCambioPendientePort;
    @Mock
    private HistorialCambioHorarioPort historialPort;
    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;

    private PreferenciaHorarioService service;

    @BeforeEach
    void setUp() {
        service = new PreferenciaHorarioService(progresoPort, loadHabitoPort, loadHorarioPort, loadPreferenciaPort,
                savePreferenciaPort, saveCambioPendientePort, historialPort, loadRegistroPort, CLOCK);
        lenient().when(savePreferenciaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Habito habito() {
        return Habito.crearDeSistema("Jugo verde", TipoHabito.CHECKBOX, "MENTE", ExigenciaEvidencia.OPCIONAL,
                CLOCK.now());
    }

    @Test
    void rechazaSuspendido() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.editar(new EditarPreferenciaHorarioCommand(actor, HabitoId.newId(),
                LocalTime.of(7, 0), LocalTime.of(9, 0), true, null))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void rechazaHoraLimiteAntesQueHoraDisparo() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habito();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(3, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.editar(new EditarPreferenciaHorarioCommand(actor, habito.id(),
                LocalTime.of(9, 0), LocalTime.of(7, 0), true, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enSemanaLibreAplicaInmediatoSinConsultarHistorial() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habito();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(3, "UTC", RolParticipante.TRAINEE, false))); // dia 3 <= 7
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(any(), any(), any())).thenReturn(Optional.empty());
        when(loadPreferenciaPort.porParticipanteYHabito(actor, habito.id())).thenReturn(Optional.empty());

        ResultadoEdicionPreferencia resultado = service.editar(new EditarPreferenciaHorarioCommand(actor, habito.id(),
                LocalTime.of(7, 0), LocalTime.of(9, 0), true, 15));

        assertThat(resultado.diferido()).isFalse();
        assertThat(resultado.periodo()).isEqualTo("FREE");
        assertThat(resultado.cambiosUsados()).isZero();
        verify(savePreferenciaPort).save(any(PreferenciaHorario.class));
        verify(historialPort).registrar(any(), any(), any(), any(), any(), any());
        verify(saveCambioPendientePort).borrar(actor, habito.id());
        verify(historialPort, never()).distintosHabitosCambiadosDesde(any(), any());
    }

    @Test
    void pasadaLaSemanaLibreConCupoAgotadoLanza() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habito();
        Habito habitoYaTocado = habito();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false))); // dia 10 > 7
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(any(), any(), any())).thenReturn(Optional.empty());
        when(historialPort.distintosHabitosCambiadosDesde(any(), any())).thenReturn(
                List.of(HabitoId.newId(), HabitoId.newId(), habitoYaTocado.id()));

        assertThatThrownBy(() -> service.editar(new EditarPreferenciaHorarioCommand(actor, habito.id(),
                LocalTime.of(7, 0), LocalTime.of(9, 0), true, null))).isInstanceOf(IllegalStateException.class);
        verify(savePreferenciaPort, never()).save(any());
    }

    @Test
    void reeditarUnHabitoYaTocadoNoConsumeCupoNuevo() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habito();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadRegistroPort.porParticipanteHabitoYFecha(any(), any(), any())).thenReturn(Optional.empty());
        when(loadPreferenciaPort.porParticipanteYHabito(actor, habito.id())).thenReturn(Optional.empty());
        when(historialPort.distintosHabitosCambiadosDesde(any(), any())).thenReturn(
                List.of(HabitoId.newId(), HabitoId.newId(), habito.id())); // ya tocado, cupo "lleno" pero es el mismo

        ResultadoEdicionPreferencia resultado = service.editar(new EditarPreferenciaHorarioCommand(actor, habito.id(),
                LocalTime.of(7, 0), LocalTime.of(9, 0), true, null));

        assertThat(resultado.diferido()).isFalse();
        verify(savePreferenciaPort).save(any(PreferenciaHorario.class));
    }

    @Test
    void siLaVentanaDeHoyYaArrancoElCambioQuedaDiferidoParaManana() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habito();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(3, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        RegistroHabito registroDeHoy = RegistroHabito.generar(actor, habito.id(), LocalDate.of(2026, 8, 24), 3,
                TipoDia.DISCIPLINA, false, CLOCK.now());
        when(loadRegistroPort.porParticipanteHabitoYFecha(actor, habito.id(), LocalDate.of(2026, 8, 24)))
                .thenReturn(Optional.of(registroDeHoy));
        // preferencia actual: dispara a las 08:00, y son las 09:00 -> la ventana de hoy ya arranco
        PreferenciaHorario prefActual = PreferenciaHorario.crear(actor, habito.id(), LocalTime.of(8, 0),
                LocalTime.of(10, 0), CLOCK.now());
        when(loadPreferenciaPort.porParticipanteYHabito(actor, habito.id())).thenReturn(Optional.of(prefActual));

        ResultadoEdicionPreferencia resultado = service.editar(new EditarPreferenciaHorarioCommand(actor, habito.id(),
                LocalTime.of(7, 0), LocalTime.of(9, 0), true, null));

        assertThat(resultado.diferido()).isTrue();
        assertThat(resultado.fechaEfectivaDiferido()).isEqualTo(LocalDate.of(2026, 8, 25));
        verify(saveCambioPendientePort).save(any());
        verify(savePreferenciaPort, never()).save(any());
        verify(historialPort, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    /**
     * E-54: sin fila en `preferencias_horario` el INSERT del pendiente viola la FK compuesta.
     * La fila padre se crea con lo VIGENTE HOY (el default del catalogo), no con lo pedido — el
     * dia en curso no se toca; las horas pedidas las escribe la promocion nocturna.
     */
    @Test
    void elPrimerCambioDiferidoDeUnHabitoNuncaEditadoCreaLaFilaPadreConLoVigenteHoy() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habito();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(3, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        RegistroHabito registroDeHoy = RegistroHabito.generar(actor, habito.id(), LocalDate.of(2026, 8, 24), 3,
                TipoDia.DISCIPLINA, false, CLOCK.now());
        when(loadRegistroPort.porParticipanteHabitoYFecha(actor, habito.id(), LocalDate.of(2026, 8, 24)))
                .thenReturn(Optional.of(registroDeHoy));
        when(loadPreferenciaPort.porParticipanteYHabito(actor, habito.id())).thenReturn(Optional.empty());
        // catalogo: dispara 08:00, y son las 09:00 -> la ventana de hoy ya arranco
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of(HorarioHabito.crear(habito.id(), 1, null,
                TipoDia.TODOS, LocalTime.of(8, 0), LocalTime.of(10, 0), CLOCK.now())));

        ResultadoEdicionPreferencia resultado = service.editar(new EditarPreferenciaHorarioCommand(actor, habito.id(),
                LocalTime.of(7, 0), LocalTime.of(9, 0), true, null));

        assertThat(resultado.diferido()).isTrue();
        ArgumentCaptor<PreferenciaHorario> padre = ArgumentCaptor.forClass(PreferenciaHorario.class);
        verify(savePreferenciaPort).save(padre.capture());
        assertThat(padre.getValue().horaDisparo()).isEqualTo(LocalTime.of(8, 0));
        assertThat(padre.getValue().horaLimite()).isEqualTo(LocalTime.of(10, 0));
        verify(saveCambioPendientePort).save(any());
        verify(historialPort, never()).registrar(any(), any(), any(), any(), any(), any());
    }
}
