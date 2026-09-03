package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.santuario.CompletarSesionBloqueoUseCase.CompletarSesionBloqueoCommand;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase.IniciarSesionBloqueoCommand;
import com.renaser.os.habits.application.ports.in.santuario.RomperSesionBloqueoUseCase.RomperSesionBloqueoCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.santuario.LoadSesionBloqueoPort;
import com.renaser.os.habits.application.ports.out.santuario.SaveSesionBloqueoPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.MotivoSalidaBloqueo;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.api.ResumenAjustePuntos;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SantuarioServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T20:05:00Z"));

    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;
    @Mock
    private LoadSesionBloqueoPort loadSesionPort;
    @Mock
    private SaveSesionBloqueoPort saveSesionPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private SantuarioService service;

    @BeforeEach
    void setUp() {
        service = new SantuarioService(loadRegistroPort, saveRegistroPort, loadSesionPort, saveSesionPort,
                loadHabitoPort, loadHorarioPort, loadPreferenciaPort, progresoPort, ajustarPuntosPort, events, CLOCK);
        lenient().when(saveRegistroPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveSesionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Habito habitoBloqueo() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Santuario", TipoHabito.BLOQUEO, "MENTE",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    private static RegistroHabito registroPendiente(UserId participanteId, Habito habito) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participanteId, habito.id(),
                LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
    }

    @Test
    void iniciarRechazaActorQueNoEsElDueno() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());
        Habito habito = habitoBloqueo();
        RegistroHabito registro = registroPendiente(dueno, habito);
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));

        assertThatThrownBy(() -> service.iniciar(new IniciarSesionBloqueoCommand(otro, registro.id())))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void iniciarRechazaHabitoQueNoEsBloqueo() {
        UserId dueno = UserId.of(UUID.randomUUID());
        Habito checkbox = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Meditar", TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        RegistroHabito registro = registroPendiente(dueno, checkbox);
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(checkbox.id())).thenReturn(Optional.of(checkbox));
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));

        assertThatThrownBy(() -> service.iniciar(new IniciarSesionBloqueoCommand(dueno, registro.id())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iniciarSinHorarioConfiguradoSiempreSePuede() {
        UserId dueno = UserId.of(UUID.randomUUID());
        Habito habito = habitoBloqueo();
        RegistroHabito registro = registroPendiente(dueno, habito);
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadSesionPort.porRegistro(registro.id())).thenReturn(Optional.empty());
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteYHabito(dueno, habito.id())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));

        SesionBloqueo sesion = service.iniciar(new IniciarSesionBloqueoCommand(dueno, registro.id()));

        assertThat(sesion.estaActiva()).isTrue();
        assertThat(registro.estado()).isEqualTo(EstadoRegistro.EN_CURSO);
    }

    @Test
    void romperAplicaPenalizacionDeDiezPuntosYMarcaRegistroFallido() {
        UserId dueno = UserId.of(UUID.randomUUID());
        Habito habito = habitoBloqueo();
        RegistroHabito registro = registroPendiente(dueno, habito);
        registro.iniciar(CLOCK.now());
        SesionBloqueo sesion = SesionBloqueo.iniciar(registro.id(), CLOCK.now().minusSeconds(600));

        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadSesionPort.porRegistro(registro.id())).thenReturn(Optional.of(sesion));
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(ajustarPuntosPort.ajustar(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(new ResumenAjustePuntos(dueno, -10, 90));

        service.romper(new RomperSesionBloqueoCommand(dueno, registro.id(), MotivoSalidaBloqueo.SALIDA_TEMPRANA,
                null, null));

        assertThat(registro.estado()).isEqualTo(EstadoRegistro.FALLIDO);
        verify(ajustarPuntosPort).ajustar(eq(dueno), eq(MotivoPuntos.SANCTUARY_BREAK),
                eq(-SesionBloqueo.PENALIZACION_ROTURA_PUNTOS), any());
    }
}
