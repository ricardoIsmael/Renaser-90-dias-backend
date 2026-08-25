package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.santuario.CerrarRachaUseCase.CerrarRachaCommand;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase.IniciarRachaCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.santuario.LoadRachaSinCelularPort;
import com.renaser.os.habits.application.ports.out.santuario.SaveRachaSinCelularPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.santuario.EstadoRacha;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RachaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T09:00:00Z"));

    @Mock
    private LoadRachaSinCelularPort loadRachaPort;
    @Mock
    private SaveRachaSinCelularPort saveRachaPort;
    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private RachaService service;

    @BeforeEach
    void setUp() {
        service = new RachaService(loadRachaPort, saveRachaPort, loadRegistroPort, saveRegistroPort, loadHabitoPort,
                progresoPort, ajustarPuntosPort, events, CLOCK);
        lenient().when(saveRachaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveRegistroPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Habito habitoSinCelular() {
        return Habito.crearDeSistema("Dia sin celular", TipoHabito.CHECKBOX, "MENTE", ExigenciaEvidencia.OPCIONAL,
                CLOCK.now());
        // claveSistema no se puede fijar por el factory publico (no expone ese setter) — se usa
        // reflexion minima via rehydrate en el test que lo necesita.
    }

    private static Habito habitoSinCelularConClave() {
        Habito h = habitoSinCelular();
        return Habito.rehydrate(h.id(), h.ambito(), null, h.titulo(), null, h.tipo(), h.categoriaClave(), null,
                RachaService.CLAVE_SISTEMA_SIN_CELULAR, h.exigenciaEvidencia(), false, false, false, null, null,
                null, true, CLOCK.now(), CLOCK.now());
    }

    @Test
    void iniciarRechazaHabitoQueNoEsDiaSinCelular() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito otroHabito = habitoSinCelular(); // sin claveSistema
        RegistroHabito registro = RegistroHabito.generar(participante, otroHabito.id(), LocalDate.of(2026, 8, 24), 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(otroHabito.id())).thenReturn(Optional.of(otroHabito));

        assertThatThrownBy(() -> service.iniciar(new IniciarRachaCommand(participante, registro.id(), 24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iniciarRechazaSiYaHayUnaRachaActiva() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito habito = habitoSinCelularConClave();
        RegistroHabito registro = RegistroHabito.generar(participante, habito.id(), LocalDate.of(2026, 8, 24), 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDe(participante)).thenReturn(Optional.of(
                RachaSinCelular.iniciar(participante, registro.id(), 24, CLOCK.now())));

        assertThatThrownBy(() -> service.iniciar(new IniciarRachaCommand(participante, registro.id(), 24)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cerrarRechazaActorSinRachaActiva() {
        UserId participante = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDeParaEscritura(participante)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cerrar(new CerrarRachaCommand(participante)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void cerrarSuspendidoRechazado() {
        UserId participante = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.cerrar(new CerrarRachaCommand(participante)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void cerrarConCicloCompletoOtorgaDiezPuntosYCompletaElRegistro() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito habito = habitoSinCelularConClave();
        RegistroHabito registro = RegistroHabito.generar(participante, habito.id(), LocalDate.of(2026, 8, 24), 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
        registro.iniciar(CLOCK.now());
        RachaSinCelular racha = RachaSinCelular.iniciar(participante, registro.id(), 24,
                CLOCK.now().minus(Duration.ofHours(24)));

        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDeParaEscritura(participante)).thenReturn(Optional.of(racha));
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));
        when(ajustarPuntosPort.ajustar(any(), any(), anyInt(), any()))
                .thenReturn(new ResumenAjustePuntos(participante, 10, 110));

        RachaSinCelular resultado = service.cerrar(new CerrarRachaCommand(participante));

        assertThat(resultado.estado()).isEqualTo(EstadoRacha.COMPLETADA);
        assertThat(registro.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        verify(ajustarPuntosPort).ajustar(eq(participante), eq(MotivoPuntos.HABIT_COMPLETED), eq(10), any());
    }

    @Test
    void romperNuncaPenalizaPuntos() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito habito = habitoSinCelularConClave();
        RegistroHabito registro = RegistroHabito.generar(participante, habito.id(), LocalDate.of(2026, 8, 24), 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
        registro.iniciar(CLOCK.now());
        RachaSinCelular racha = RachaSinCelular.iniciar(participante, registro.id(), 24,
                CLOCK.now().minus(Duration.ofHours(2)));

        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDeParaEscritura(participante)).thenReturn(Optional.of(racha));
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));

        service.romper(new com.renaser.os.habits.application.ports.in.santuario.RomperRachaUseCase.RomperRachaCommand(
                participante, "me distraje"));

        assertThat(racha.estado()).isEqualTo(EstadoRacha.ROTA);
        assertThat(registro.estado()).isEqualTo(EstadoRegistro.PENDIENTE); // liberado, es de hoy
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
    }
}
