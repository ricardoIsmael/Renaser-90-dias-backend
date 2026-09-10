package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase.PlanDesbloqueo;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.desbloqueo.SaveDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** NOTA: pruebas escritas en esta pasada, no verificadas con {@code ./mvnw} (regla del encargo). */
@ExtendWith(MockitoExtension.class)
class DesbloqueoHabitoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadDesbloqueoHabitoPort loadPort;
    @Mock
    private SaveDesbloqueoHabitoPort savePort;
    @Mock
    private LoadHabitoPort loadHabitoPort;

    private DesbloqueoHabitoService service;

    @BeforeEach
    void setUp() {
        service = new DesbloqueoHabitoService(progresoPort, loadPort, savePort, loadHabitoPort, CLOCK);
    }

    private static Habito habitoDeSistemaActivo() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Meditar", TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    private static Habito habitoPersonalDe(UserId dueno) {
        return Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), dueno, "Mi reto", TipoHabito.CHECKBOX,
                "CUERPO", PlantillaHabitoPersonal.OTRO, "etiqueta", CLOCK.now());
    }

    // ---- consultar (comportamiento preexistente, sin cambios de contrato) ----

    @Test
    void suspendidoRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true, false)));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void sinDesbloqueosDevuelveEnabledFalso() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadPort.deParticipante(actor)).thenReturn(List.of());

        PlanDesbloqueo plan = service.consultar(actor);

        assertThat(plan.enabled()).isFalse();
        assertThat(plan.items()).isEmpty();
    }

    @Test
    void conDesbloqueosDevuelveEnabledVerdaderoYLosItems() {
        UserId actor = UserId.of(UUID.randomUUID());
        HabitoId habito = HabitoId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadPort.deParticipante(actor)).thenReturn(
                List.of(DesbloqueoHabito.rehydrate(actor, habito, 5, CLOCK.now(), CLOCK.now(), CLOCK.now())));

        PlanDesbloqueo plan = service.consultar(actor);

        assertThat(plan.enabled()).isTrue();
        assertThat(plan.items()).hasSize(1);
        assertThat(plan.items().get(0).diaDesbloqueo()).isEqualTo(5);
    }

    // ---- elegir (nuevo) ----

    /** D-103: antes el dia 0 daba 403. Quien empieza manana arma su plan hoy, y lo elegido arranca el dia 1. */
    @Test
    void elegirEnDiaCeroDesbloqueaParaElDiaUno() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(0, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadPort.deParticipanteYHabito(actor, habito.id())).thenReturn(Optional.of(
                DesbloqueoHabito.rehydrate(actor, habito.id(), 1, CLOCK.now(), CLOCK.now(), CLOCK.now())));

        service.elegir(new ElegirHabitoCommand(actor, habito.id(), null));

        verify(savePort).elegirSiFalta(actor, habito.id(), 1, CLOCK.now(), CLOCK.now());
    }

    // ---- habitos PERSONAL en el plan (E-138) ----

    /**
     * E-138. Este metodo se llamaba {@code elegirHabitoPersonalRechazado} y afirmaba lo contrario:
     * que un habito PERSONAL siempre se rechazaba con
     * {@code IllegalArgumentException("Solo se eligen habitos del catalogo, no habitos personales")}.
     * Ese rechazo dejaba al interruptor ACTIVO/PAUSADO sin backend para los habitos propios,
     * porque el movil asegura la fila con este caso de uso antes de mandar el PATCH (D-99) y
     * `desbloqueos_habito` es la UNICA tabla donde vive la pausa.
     */
    @Test
    void elegirElHabitoPersonalPropioLoAgregaAlPlan() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito personal = habitoPersonalDe(actor);
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(personal.id())).thenReturn(Optional.of(personal));
        DesbloqueoHabito esperado = DesbloqueoHabito.rehydrate(actor, personal.id(), 10, CLOCK.now(), CLOCK.now(),
                CLOCK.now());
        when(loadPort.deParticipanteYHabito(actor, personal.id())).thenReturn(Optional.of(esperado));

        DesbloqueoHabito resultado = service.elegir(new ElegirHabitoCommand(actor, personal.id(), null));

        assertThat(resultado).isEqualTo(esperado);
        verify(savePort).elegirSiFalta(actor, personal.id(), 10, CLOCK.now(), CLOCK.now());
    }

    /**
     * Lo que el rechazo viejo si protegia, ahora explicito: el habito propio de OTRO aprendiz.
     * 404 y no 403 a proposito — un 403 confirmaria que ese id existe.
     */
    @Test
    void elegirElHabitoPersonalDeOtroAprendizNoSeEncuentra() {
        UserId actor = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());
        Habito ajeno = habitoPersonalDe(otro);
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(ajeno.id())).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.elegir(new ElegirHabitoCommand(actor, ajeno.id(), null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    /** Baja logica de un habito propio (`habitos.activo = false`): no vuelve a entrar al plan. */
    @Test
    void elegirUnHabitoPersonalDadoDeBajaRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito personal = habitoPersonalDe(actor);
        personal.desactivar(CLOCK.now());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(personal.id())).thenReturn(Optional.of(personal));

        assertThatThrownBy(() -> service.elegir(new ElegirHabitoCommand(actor, personal.id(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * La secuencia EXACTA que dispara el interruptor del Plan sobre un habito propio: PUT
     * ({@code elegir}, asegura la fila) y despues PATCH ({@code cambiarEstado}). Contra el codigo
     * viejo se caia en el primer paso con un 400.
     */
    @Test
    void elInterruptorPausaUnHabitoPersonalPropioHastaUnaFecha() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito personal = habitoPersonalDe(actor);
        LocalDate hastaElDomingo = LocalDate.of(2026, 8, 30);
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(personal.id())).thenReturn(Optional.of(personal));
        DesbloqueoHabito fila = DesbloqueoHabito.rehydrate(actor, personal.id(), 10, CLOCK.now(), CLOCK.now(),
                CLOCK.now());
        when(loadPort.deParticipanteYHabito(actor, personal.id())).thenReturn(Optional.of(fila));
        when(savePort.save(fila)).thenReturn(fila);

        service.elegir(new ElegirHabitoCommand(actor, personal.id(), null));
        DesbloqueoHabito pausado = service.cambiarEstado(
                new CambiarEstadoHabitoCommand(actor, personal.id(), false, hastaElDomingo));

        assertThat(pausado.estaPausado()).isTrue();
        assertThat(pausado.pausadoHasta()).isEqualTo(hastaElDomingo);
        // Sigue pausado el ultimo dia (inclusive) y vuelve solo al siguiente, igual que un habito
        // de catalogo: la regla de V31 no cambia por ser un habito propio.
        assertThat(pausado.estaPausadoEl(hastaElDomingo, ZoneId.of("America/Lima"))).isTrue();
        assertThat(pausado.estaPausadoEl(hastaElDomingo.plusDays(1), ZoneId.of("America/Lima"))).isFalse();
        verify(savePort).save(fila);
    }

    /** Contraparte: el mismo interruptor lo vuelve a encender. */
    @Test
    void elInterruptorReactivaUnHabitoPersonalPausado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito personal = habitoPersonalDe(actor);
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(personal.id())).thenReturn(Optional.of(personal));
        DesbloqueoHabito fila = DesbloqueoHabito.rehydrate(actor, personal.id(), 10, CLOCK.now(), CLOCK.now(),
                CLOCK.now(), CLOCK.now(), LocalDate.of(2026, 8, 30));
        when(loadPort.deParticipanteYHabito(actor, personal.id())).thenReturn(Optional.of(fila));
        when(savePort.save(fila)).thenReturn(fila);

        DesbloqueoHabito activo = service.cambiarEstado(
                new CambiarEstadoHabitoCommand(actor, personal.id(), true, null));

        assertThat(activo.estaPausado()).isFalse();
        assertThat(activo.pausadoHasta()).isNull();
    }

    @Test
    void elegirHabitoInactivoRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        habito.desactivar(CLOCK.now());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.elegir(new ElegirHabitoCommand(actor, habito.id(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eligeUnHabitoDeCatalogoYDevuelveElDesbloqueoAsegurado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        DesbloqueoHabito esperado = DesbloqueoHabito.rehydrate(actor, habito.id(), 10, CLOCK.now(), CLOCK.now(),
                CLOCK.now());
        when(loadPort.deParticipanteYHabito(actor, habito.id())).thenReturn(Optional.of(esperado));

        DesbloqueoHabito resultado = service.elegir(new ElegirHabitoCommand(actor, habito.id(), null));

        assertThat(resultado).isEqualTo(esperado);
        verify(savePort).elegirSiFalta(actor, habito.id(), 10, CLOCK.now(), CLOCK.now());
    }

    @Test
    void elegirElMismoHabitoDosVecesEsIdempotente() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        DesbloqueoHabito primero = DesbloqueoHabito.rehydrate(actor, habito.id(), 10, CLOCK.now(), CLOCK.now(),
                CLOCK.now());
        // Ambas llamadas relean el mismo estado canonico persistido (DO NOTHING preserva el primer valor).
        when(loadPort.deParticipanteYHabito(actor, habito.id())).thenReturn(Optional.of(primero));

        DesbloqueoHabito resultado1 = service.elegir(new ElegirHabitoCommand(actor, habito.id(), null));
        DesbloqueoHabito resultado2 = service.elegir(new ElegirHabitoCommand(actor, habito.id(), null));

        assertThat(resultado1).isEqualTo(resultado2).isEqualTo(primero);
        verify(savePort, times(2)).elegirSiFalta(actor, habito.id(), 10, CLOCK.now(), CLOCK.now());
    }

    // ---- elegir con dia pedido (el aprendiz agenda el habito para mas adelante) ----

    @Test
    void elegirParaUnDiaFuturoGuardaEseDiaYNoElActual() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(1, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        DesbloqueoHabito esperado = DesbloqueoHabito.rehydrate(actor, habito.id(), 2, CLOCK.now(), CLOCK.now(),
                CLOCK.now());
        when(loadPort.deParticipanteYHabito(actor, habito.id())).thenReturn(Optional.of(esperado));

        DesbloqueoHabito resultado = service.elegir(new ElegirHabitoCommand(actor, habito.id(), 2));

        assertThat(resultado.diaDesbloqueo()).isEqualTo(2);
        verify(savePort).elegirSiFalta(actor, habito.id(), 2, CLOCK.now(), CLOCK.now());
    }

    @Test
    void elegirParaElMismoDiaActualEsValido() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadPort.deParticipanteYHabito(actor, habito.id())).thenReturn(Optional.of(
                DesbloqueoHabito.rehydrate(actor, habito.id(), 10, CLOCK.now(), CLOCK.now(), CLOCK.now())));

        service.elegir(new ElegirHabitoCommand(actor, habito.id(), 10));

        verify(savePort).elegirSiFalta(actor, habito.id(), 10, CLOCK.now(), CLOCK.now());
    }

    @Test
    void elegirParaUnDiaYaVividoRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.elegir(new ElegirHabitoCommand(actor, habito.id(), 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dia 10");
    }

    @Test
    void elegirFueraDelRangoDelProgramaRechazadoPorElComando() {
        UserId actor = UserId.of(UUID.randomUUID());
        HabitoId habitoId = HabitoId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new ElegirHabitoCommand(actor, habitoId, 91))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> new ElegirHabitoCommand(actor, habitoId, 0))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
