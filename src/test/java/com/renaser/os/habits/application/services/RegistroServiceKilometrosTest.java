package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaClaseDiaria;
import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.politica.PoliticaSantuario;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.medicion.OrigenMedicion;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-226: completar KILÓMETROS DIARIOS por el gesto genérico ({@code POST /habit-tracks/{id}/complete})
 * con el número del día. Las políticas son las reales: la de km decide, y las demás siguen como estaban.
 */
@ExtendWith(MockitoExtension.class)
class RegistroServiceKilometrosTest {

    /** 03:00 UTC = 22:00 del día ANTERIOR en Lima (regla 02 §3): la fecha es la del registro, no la del servidor. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-30T03:00:00Z"));

    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;
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
    private com.renaser.os.community.api.PublicacionMuroFinder publicacionMuroFinder;
    @Mock
    private com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private IdGenerator idGenerator;

    private RegistroService service;
    private UserId dueno;

    @BeforeEach
    void setUp() {
        service = new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, progresoPort, ajustarPuntosPort, publicacionMuroFinder, loadDesbloqueoPort, events,
                CLOCK, idGenerator, List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad(),
                        new PoliticaClaseDiaria(), new PoliticaKilometros()));
        dueno = UserId.of(UUID.randomUUID());
        lenient().when(saveRegistroPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(loadHorarioPort.porHabito(any())).thenReturn(List.of());
        lenient().when(loadPreferenciaPort.porParticipanteHabitoYFecha(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(12, "America/Lima", RolParticipante.TRAINEE, false, false)));
    }

    /** El hábito real de V9/V84: CHECKBOX, captura obligatoria, opcional y con la clave DAILY_KM. */
    private static Habito habitoKilometros() {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, "KILÓMETROS DIARIOS",
                null, TipoHabito.CHECKBOX, "CUERPO", "WALKING", PoliticaKilometros.CLAVE_SISTEMA,
                ExigenciaEvidencia.OBLIGATORIA, true, false, true, false, null, 90, null, null, true, CLOCK.now(),
                CLOCK.now());
    }

    private static Habito habitoComun() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Meditar", TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    private RegistroHabito pendienteDe(Habito habito) {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), dueno, habito.id(),
                LocalDate.of(2026, 9, 29), 12, TipoDia.TODOS, true, CLOCK.now());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        return registro;
    }

    private RegistroHabito completarCon(RegistroHabito registro, String km) {
        return service.completar(CompletarRegistroCommand.conValorManual(dueno, registro.id(), null, null,
                km == null ? null : new BigDecimal(km)));
    }

    @Test
    @DisplayName("con km se completa y guarda el número con dos decimales y origen MANUAL")
    void conKmSeCompletaYGuardaElNumero() {
        RegistroHabito registro = pendienteDe(habitoKilometros());

        RegistroHabito resultado = completarCon(registro, "5.254");

        assertThat(resultado.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        assertThat(resultado.medicion().valor()).isEqualByComparingTo("5.25");
        assertThat(resultado.medicion().origen()).isEqualTo(OrigenMedicion.MANUAL);
        verify(saveRegistroPort).save(registro);
    }

    @Test
    @DisplayName("sin km es un 400 que dice qué falta, y no guarda ni paga")
    void sinKmNoSeCompleta() {
        RegistroHabito registro = pendienteDe(habitoKilometros());

        assertThatThrownBy(() -> completarCon(registro, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("km");

        assertThat(registro.estado()).isEqualTo(EstadoRegistro.PENDIENTE);
        verify(saveRegistroPort, never()).save(any());
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("cero km o más del tope no se completan")
    void ceroOMasDelTopeNoSeCompletan() {
        RegistroHabito cero = pendienteDe(habitoKilometros());
        assertThatThrownBy(() -> completarCon(cero, "0")).isInstanceOf(IllegalArgumentException.class);

        RegistroHabito demasiado = pendienteDe(habitoKilometros());
        assertThatThrownBy(() -> completarCon(demasiado, "150")).isInstanceOf(IllegalArgumentException.class);

        verify(saveRegistroPort, never()).save(any());
    }

    @Test
    @DisplayName("un número en un hábito que no mide nada es un 400: no se guarda un dato que nadie lee")
    void numeroEnHabitoComunSeRechaza() {
        RegistroHabito registro = pendienteDe(habitoComun());

        assertThatThrownBy(() -> completarCon(registro, "3"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no registra un número");
        verify(saveRegistroPort, never()).save(any());
    }

    @Test
    @DisplayName("un hábito común se sigue completando sin número, como siempre")
    void habitoComunSinNumeroSigueIgual() {
        RegistroHabito registro = pendienteDe(habitoComun());

        RegistroHabito resultado = completarCon(registro, null);

        assertThat(resultado.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        assertThat(resultado.medicion()).isNull();
    }

    @Test
    @DisplayName("otro aprendiz no puede registrar km en el registro de alguien más")
    void otroAprendizNoRegistraKmAjenos() {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), dueno,
                HabitoId.of(UUID.randomUUID()), LocalDate.of(2026, 9, 29), 12, TipoDia.TODOS, true, CLOCK.now());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.completar(CompletarRegistroCommand.conValorManual(otro, registro.id(), null,
                null, new BigDecimal("5")))).isInstanceOf(NotAuthorizedException.class);
        verify(saveRegistroPort, never()).save(any());
    }
}
