package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveMiRadarPort;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar.DiaPropio;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Spec 012: leer, encender, apagar y cambiar las horas de Mi Radar. Solo la propia persona. */
@ExtendWith(MockitoExtension.class)
class MiRadarServiceTest {

    @Mock
    private LoadMiRadarPort loadPort;
    @Mock
    private SaveMiRadarPort savePort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;

    private MiRadarService service;
    private UserId persona;

    @BeforeEach
    void setUp() {
        service = new MiRadarService(new AccesoAlRadar(progresoPort), loadPort, savePort);
        persona = UserId.of(UUID.randomUUID());
    }

    private void conProgramaActivado(RolParticipante rol) {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(10, "America/Lima", rol, false, true)));
    }

    private ConfigurarMiRadarCommand encender(Integer... horas) {
        return new ConfigurarMiRadarCommand(persona, persona, true, Arrays.asList(horas), List.of());
    }

    // ─── leer ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("consultar devuelve lo que hay guardado: apagado si no hay horas")
    void consultarApagado() {
        conProgramaActivado(RolParticipante.TRAINEE);
        when(loadPort.deParticipante(persona)).thenReturn(MiRadar.apagado());

        assertThat(service.consultar(persona, persona).estaEncendido()).isFalse();
    }

    @Test
    @DisplayName("consultar devuelve las horas encendidas")
    void consultarEncendido() {
        conProgramaActivado(RolParticipante.TRAINEE);
        when(loadPort.deParticipante(persona)).thenReturn(MiRadar.encendido(List.of(9, 14, 20)));

        assertThat(service.consultar(persona, persona).horas()).containsExactly(9, 14, 20);
    }

    @Test
    @DisplayName("FR-019: lo puede usar el personal con su programa activado, no solo el aprendiz")
    void personalConProgramaActivado() {
        for (RolParticipante rol : new RolParticipante[]{RolParticipante.MENTOR, RolParticipante.MENTOR_LEAD,
                RolParticipante.ADMIN, RolParticipante.ALCHEMIST, RolParticipante.TRAINEE_GUIDE}) {
            conProgramaActivado(rol);
            when(loadPort.deParticipante(persona)).thenReturn(MiRadar.apagado());

            assertThat(service.consultar(persona, persona).estaEncendido()).as("rol %s", rol).isFalse();
        }
    }

    // ─── configurar ────────────────────────────────────────────────────────

    @Test
    @DisplayName("encender guarda las horas ordenadas y devuelve lo guardado")
    void encenderGuarda() {
        conProgramaActivado(RolParticipante.TRAINEE);

        MiRadar guardado = service.configurar(encender(20, 9, 14));

        assertThat(guardado.horas()).containsExactly(9, 14, 20);
        verify(savePort).guardar(persona, MiRadar.encendido(List.of(9, 14, 20)));
    }

    @Test
    @DisplayName("apagar guarda apagado (sin horas) e ignora las horas que lleguen")
    void apagarIgnoraLasHoras() {
        conProgramaActivado(RolParticipante.TRAINEE);

        MiRadar guardado = service.configurar(new ConfigurarMiRadarCommand(persona, persona, false, List.of(9, 99, 9), List.of()));

        assertThat(guardado.estaEncendido()).isFalse();
        verify(savePort).guardar(persona, MiRadar.apagado());
    }

    @Test
    @DisplayName("encendido sin horas (vacias o nulas) es 400 y no guarda")
    void encendidoSinHoras() {
        conProgramaActivado(RolParticipante.TRAINEE);

        assertThatThrownBy(() -> service.configurar(new ConfigurarMiRadarCommand(persona, persona, true, List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Elige al menos una hora");
        assertThatThrownBy(() -> service.configurar(new ConfigurarMiRadarCommand(persona, persona, true, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(savePort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("SC-008: horas fuera de las reglas (13, fuera de 03-23, repetidas) son 400 y no guardan")
    void horasFueraDeLasReglas() {
        conProgramaActivado(RolParticipante.TRAINEE);
        Integer[] trece = IntStream.rangeClosed(8, 20).boxed().toArray(Integer[]::new);

        assertThatThrownBy(() -> service.configurar(encender(trece))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(encender(2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(encender(24))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(encender(9, 9))).isInstanceOf(IllegalArgumentException.class);

        verify(savePort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("el comando exige el campo «encendido»: sin el, es una violacion de validacion (400)")
    void comandoSinEncendido() {
        assertThatThrownBy(() -> new ConfigurarMiRadarCommand(persona, persona, null, List.of(9), List.of()))
                .isInstanceOf(ConstraintViolationException.class);
    }

    // ─── autorizacion ──────────────────────────────────────────────────────

    @Test
    @DisplayName("solo la propia persona: un actor distinto recibe 403 al leer y al configurar, y no se toca la base")
    void actorAjeno() {
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.consultar(otro, persona)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.configurar(new ConfigurarMiRadarCommand(otro, persona, true, List.of(9), List.of())))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(loadPort, savePort, progresoPort);
    }

    @Test
    @DisplayName("la cuenta suspendida recibe 403")
    void suspendida() {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(10, "America/Lima", RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> service.consultar(persona, persona)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.configurar(encender(9))).isInstanceOf(NotAuthorizedException.class);

        verify(savePort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("el personal sin su programa activado recibe 403, como en el resto del Codigo Renaser")
    void personalSinProgramaActivado() {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(0, "America/Lima", RolParticipante.MENTOR, false, false)));

        assertThatThrownBy(() -> service.configurar(encender(9))).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("quien no tiene programa es 404")
    void sinPrograma() {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(persona, persona)).isInstanceOf(NoSuchElementException.class);
    }

    // ─── por dia de la semana (cuarta ronda) ───────────────────────────────

    private ConfigurarMiRadarCommand encenderConDias(List<Integer> horas, DiaPropio... dias) {
        return new ConfigurarMiRadarCommand(persona, persona, true, horas, List.of(dias));
    }

    @Test
    @DisplayName("consultar devuelve tambien los dias con horas propias")
    void consultarConDias() {
        conProgramaActivado(RolParticipante.TRAINEE);
        when(loadPort.deParticipante(persona)).thenReturn(MiRadar.encendido(List.of(9, 14),
                List.of(new DiaPropio(7, List.of()), new DiaPropio(6, List.of(11)))));

        MiRadar leido = service.consultar(persona, persona);

        assertThat(leido.horas()).containsExactly(9, 14);
        assertThat(leido.dias()).extracting(DiaPropio::dia).containsExactly(6, 7);
    }

    @Test
    @DisplayName("encender con dias guarda las horas de todos los dias y los dias propios, ordenados, y devuelve lo guardado")
    void encenderConDiasGuarda() {
        conProgramaActivado(RolParticipante.TRAINEE);

        MiRadar guardado = service.configurar(encenderConDias(List.of(20, 9),
                new DiaPropio(7, List.of()), new DiaPropio(6, List.of(11, 10))));

        assertThat(guardado.horas()).containsExactly(9, 20);
        assertThat(guardado.dias()).extracting(DiaPropio::dia).containsExactly(6, 7);
        assertThat(guardado.horasDelDia(java.time.DayOfWeek.SATURDAY)).containsExactly(10, 11);
        verify(savePort).guardar(persona, guardado);
    }

    @Test
    @DisplayName("encender sin dias (nulos o vacios) es lo mismo: todos los dias siguen las horas")
    void encenderSinDiasEsLoMismoQueDiasVacio() {
        conProgramaActivado(RolParticipante.TRAINEE);

        MiRadar conNulos = service.configurar(new ConfigurarMiRadarCommand(persona, persona, true, List.of(9), null));
        MiRadar conVacios = service.configurar(encender(9));

        assertThat(conNulos).isEqualTo(conVacios).isEqualTo(MiRadar.encendido(List.of(9)));
        assertThat(conNulos.dias()).isEmpty();
    }

    @Test
    @DisplayName("apagar ignora tambien los dias que lleguen y guarda apagado, sin dias viejos")
    void apagarIgnoraLosDias() {
        conProgramaActivado(RolParticipante.TRAINEE);

        MiRadar guardado = service.configurar(new ConfigurarMiRadarCommand(persona, persona, false, List.of(9),
                List.of(new DiaPropio(6, List.of(11)))));

        assertThat(guardado).isEqualTo(MiRadar.apagado());
        verify(savePort).guardar(persona, MiRadar.apagado());
    }

    @Test
    @DisplayName("SC-008: dias con horas propias fuera de las reglas, un dia repetido o dias sin horas de todos los dias son 400 y no guardan")
    void diasFueraDeLasReglas() {
        conProgramaActivado(RolParticipante.TRAINEE);
        List<Integer> trece = IntStream.rangeClosed(8, 20).boxed().toList();

        assertThatThrownBy(() -> service.configurar(encenderConDias(List.of(9), new DiaPropio(6, trece))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(encenderConDias(List.of(9), new DiaPropio(6, List.of(2)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(encenderConDias(List.of(9), new DiaPropio(6, List.of(9, 9)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurar(encenderConDias(List.of(9), new DiaPropio(6, List.of(10)),
                new DiaPropio(6, List.of(11))))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No repitas el mismo día");
        assertThatThrownBy(() -> service.configurar(encenderConDias(List.of(), new DiaPropio(6, List.of(10)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Elige al menos una hora");

        verify(savePort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("un dia fuera de 1 a 7 no llega ni a armarse: es 400 desde el dominio")
    void diaFueraDeRango() {
        assertThatThrownBy(() -> new DiaPropio(0, List.of(9))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiaPropio(8, List.of(9))).isInstanceOf(IllegalArgumentException.class);
    }
}
