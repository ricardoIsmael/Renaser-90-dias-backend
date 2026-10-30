package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.CambioDeDia;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveMiRadarPort;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;
import com.renaser.os.habits.domain.model.radar.DiaDelRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Spec 019: leer Mi Radar con las horas que salen del Despertar y el Dormir de cada dia, encender, apagar y cambiar el modo
 * y las veces; hoy y lo pasado no se editan. Solo la propia persona.
 *
 * <p>Reloj: viernes 30 de octubre de 2026, 10:00 en Lima. Lunes a viernes tienen candado; sabado y domingo se editan.
 */
@ExtendWith(MockitoExtension.class)
class MiRadarServiceTest {

    private static final FixedClock VIERNES_LIMA_10_00 = FixedClock.at(Instant.parse("2026-10-30T15:00:00Z"));

    @Mock
    private LoadMiRadarPort loadPort;
    @Mock
    private SaveMiRadarPort savePort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private HorarioDelDiaFinder horarios;

    private MiRadarService service;
    private UserId persona;

    @BeforeEach
    void setUp() {
        service = new MiRadarService(new AccesoAlRadar(progresoPort), loadPort, savePort,
                new AgendaDelRadar(horarios, VIERNES_LIMA_10_00));
        persona = UserId.of(UUID.randomUUID());
        lenient().when(loadPort.deParticipante(persona)).thenReturn(MiRadar.apagado());
    }

    private void conProgramaActivado(RolParticipante rol) {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(10, "America/Lima", rol, false, true)));
    }

    private void conHorario(LocalTime despertar, LocalTime dormir) {
        HorarioDeRadarDePrueba.siempre(horarios, despertar, dormir);
    }

    private ConfigurarMiRadarCommand pedido(boolean encendido, CambioDeDia... dias) {
        return new ConfigurarMiRadarCommand(persona, persona, encendido, List.of(dias));
    }

    private static CambioDeDia cambio(int dia, Modo modo, Integer veces, Boolean sinRadar) {
        return new CambioDeDia(dia, modo, veces, sinRadar);
    }

    private MiRadar guardado() {
        ArgumentCaptor<MiRadar> captor = ArgumentCaptor.forClass(MiRadar.class);
        verify(savePort).guardar(any(), captor.capture());
        return captor.getValue();
    }

    // ─── leer ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("consultar arma los siete dias con las horas de su Despertar y su Dormir y dice cuales se pueden editar")
    void consultarLaSemana() {
        conProgramaActivado(RolParticipante.TRAINEE);
        when(loadPort.deParticipante(persona)).thenReturn(new MiRadar(true, List.of()));
        conHorario(LocalTime.of(5, 30), LocalTime.of(22, 30));

        PlanDeMiRadar plan = service.consultar(persona, persona);

        assertThat(plan.encendido()).isTrue();
        assertThat(plan.hoy()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(plan.dias()).hasSize(7);
        assertThat(plan.dias()).extracting(DiaDelRadar::dia).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(plan.dias()).extracting(DiaDelRadar::fecha).first().isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(plan.dias()).extracting(DiaDelRadar::veces).containsOnly(17);
        assertThat(plan.dias()).extracting(DiaDelRadar::editable)
                .containsExactly(false, false, false, false, false, true, true);
    }

    @Test
    @DisplayName("consultar apagado tambien trae los dias: la pantalla los muestra al encender")
    void consultarApagado() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(8, 0), LocalTime.of(23, 0));

        PlanDeMiRadar plan = service.consultar(persona, persona);

        assertThat(plan.encendido()).isFalse();
        assertThat(plan.dias()).extracting(DiaDelRadar::veces).containsOnly(15);
    }

    @Test
    @DisplayName("cada dia usa su propio horario: un sabado sin Despertar no tiene Radar y los demas si")
    void unDiaSinDespertar() {
        conProgramaActivado(RolParticipante.TRAINEE);
        HorarioDeRadarDePrueba.porFecha(horarios, (fecha, dia) -> HorarioDeRadarDePrueba.delDia(fecha,
                dia == 6 ? null : LocalTime.of(6, 0), LocalTime.of(22, 0)));

        PlanDeMiRadar plan = service.consultar(persona, persona);

        assertThat(plan.dias().get(5).horas()).isEmpty();
        assertThat(plan.dias().get(5).motivo()).isEqualTo(DiaDelRadar.MotivoSinHorario.SIN_DESPERTAR);
        assertThat(plan.dias().get(4).veces()).isEqualTo(16);
    }

    @Test
    @DisplayName("FR-019: lo puede usar el personal con su programa activado, no solo el aprendiz")
    void personalConProgramaActivado() {
        conHorario(LocalTime.of(6, 0), LocalTime.of(22, 0));
        for (RolParticipante rol : new RolParticipante[]{RolParticipante.MENTOR, RolParticipante.MENTOR_LEAD,
                RolParticipante.ADMIN, RolParticipante.ALCHEMIST, RolParticipante.TRAINEE_GUIDE}) {
            conProgramaActivado(rol);

            assertThat(service.consultar(persona, persona).dias()).as("rol %s", rol).hasSize(7);
        }
    }

    // ─── configurar ────────────────────────────────────────────────────────

    @Test
    @DisplayName("encender rige en el acto, aunque sea viernes, y no toca los dias")
    void encender() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(6, 0), LocalTime.of(22, 0));

        PlanDeMiRadar plan = service.configurar(pedido(true));

        assertThat(plan.encendido()).isTrue();
        assertThat(guardado()).isEqualTo(new MiRadar(true, List.of()));
    }

    @Test
    @DisplayName("apagar guarda apagado y conserva los dias como estaban")
    void apagarConservaLosDias() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(6, 0), LocalTime.of(22, 0));
        MiRadar antes = new MiRadar(true, List.of(new DiaDeMiRadar(6, Modo.PERSONALIZADO, 4, false)));
        when(loadPort.deParticipante(persona)).thenReturn(antes);

        service.configurar(pedido(false));

        assertThat(guardado()).isEqualTo(antes.conEncendido(false));
    }

    @Test
    @DisplayName("pasar sabado y domingo a Personalizado con 3 veces: se guarda solo eso y la respuesta ya trae las horas repartidas")
    void personalizadoElFinDeSemana() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(5, 30), LocalTime.of(22, 30));

        PlanDeMiRadar plan = service.configurar(pedido(true, cambio(6, Modo.PERSONALIZADO, 3, null),
                cambio(7, Modo.PERSONALIZADO, 3, null)));

        assertThat(guardado().dias()).containsExactly(new DiaDeMiRadar(6, Modo.PERSONALIZADO, 3, false),
                new DiaDeMiRadar(7, Modo.PERSONALIZADO, 3, false));
        assertThat(plan.dias().get(5).horas()).containsExactly(6, 14, 22);
        assertThat(plan.dias().get(4).veces()).as("el viernes no cambio").isEqualTo(17);
    }

    @Test
    @DisplayName("Personalizado sin veces propone 6")
    void porDefectoSeis() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(5, 30), LocalTime.of(22, 30));

        PlanDeMiRadar plan = service.configurar(pedido(true, cambio(6, Modo.PERSONALIZADO, null, null)));

        assertThat(plan.dias().get(5).horas()).containsExactly(6, 9, 12, 16, 19, 22);
    }

    @Test
    @DisplayName("«Sin Radar» para el domingo apaga ese dia y nada mas")
    void sinRadarElDomingo() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(6, 0), LocalTime.of(22, 0));

        PlanDeMiRadar plan = service.configurar(pedido(true, cambio(7, null, null, true)));

        assertThat(plan.dias().get(6).horas()).isEmpty();
        assertThat(plan.dias().get(6).sinRadar()).isTrue();
        assertThat(plan.dias().get(5).veces()).isEqualTo(16);
    }

    // ─── hoy y lo pasado no se editan ──────────────────────────────────────

    @Test
    @DisplayName("FR-005: cambiar hoy (viernes) o un dia pasado es 409 con un motivo claro y no guarda nada")
    void hoyYLoPasadoNoSeEditan() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(6, 0), LocalTime.of(22, 0));

        for (int dia = 1; dia <= 5; dia++) {
            int cual = dia;
            assertThatThrownBy(() -> service.configurar(pedido(true, cambio(cual, Modo.PERSONALIZADO, 3, null))))
                    .as("dia %d", dia)
                    .isInstanceOf(IllegalStateException.class).isNotInstanceOf(NotAuthorizedException.class)
                    .hasMessage("Hoy y los días que pasaron no se pueden cambiar");
        }
        assertThatThrownBy(() -> service.configurar(pedido(true, cambio(5, null, null, true))))
                .isInstanceOf(IllegalStateException.class);
        verify(savePort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("un pedido que mezcla un dia editable con hoy se rechaza entero")
    void todoONada() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(6, 0), LocalTime.of(22, 0));

        assertThatThrownBy(() -> service.configurar(pedido(true, cambio(6, Modo.PERSONALIZADO, 3, null),
                cambio(5, Modo.PERSONALIZADO, 3, null)))).isInstanceOf(IllegalStateException.class);
        verify(savePort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("repetir hoy tal como esta no cambia nada y se acepta")
    void repetirHoyTalComoEsta() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(6, 0), LocalTime.of(22, 0));
        when(loadPort.deParticipante(persona)).thenReturn(new MiRadar(true,
                List.of(new DiaDeMiRadar(5, Modo.PERSONALIZADO, 3, false))));

        service.configurar(pedido(true, cambio(5, Modo.PERSONALIZADO, 3, false), cambio(1, Modo.AUTOMATICO, null, false)));

        assertThat(guardado().dia(5).veces()).isEqualTo(3);
    }

    // ─── lo que no cumple las reglas ───────────────────────────────────────

    @Test
    @DisplayName("SC-008: veces fuera de 1 a 21, un dia que no existe, un dia repetido o mas veces de las que caben son 400 y no guardan")
    void fueraDeLasReglas() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThatThrownBy(() -> service.configurar(pedido(true, cambio(6, Modo.PERSONALIZADO, 0, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(pedido(true, cambio(6, Modo.PERSONALIZADO, 22, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(pedido(true, cambio(9, Modo.PERSONALIZADO, 3, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(pedido(true, cambio(6, null, null, true), cambio(6, null, null, false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("No repitas el mismo día");
        assertThatThrownBy(() -> service.configurar(pedido(true, cambio(6, Modo.PERSONALIZADO, 5, null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Ese día caben 4 veces");

        verify(savePort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("un dia sin horario acepta Personalizado: no hay tope que comparar y el dia sigue sin Radar")
    void diaSinHorarioAceptaElModo() {
        conProgramaActivado(RolParticipante.TRAINEE);
        conHorario(null, LocalTime.of(22, 0));

        PlanDeMiRadar plan = service.configurar(pedido(true, cambio(6, Modo.PERSONALIZADO, 10, null)));

        assertThat(plan.dias().get(5).horas()).isEmpty();
    }

    // ─── quien puede ───────────────────────────────────────────────────────

    @Test
    @DisplayName("solo la propia persona: otro actor, una cuenta suspendida y un cuerpo sin encendido se rechazan antes de leer nada")
    void soloLaPropiaPersona() {
        UserId otro = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(10, "America/Lima", RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> service.consultar(otro, persona)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.configurar(new ConfigurarMiRadarCommand(otro, persona, true, List.of())))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.consultar(persona, persona)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> new ConfigurarMiRadarCommand(persona, persona, null, List.of()))
                .isInstanceOf(ConstraintViolationException.class);

        verifyNoInteractions(horarios);
        verify(savePort, never()).guardar(any(), any());
    }
}
