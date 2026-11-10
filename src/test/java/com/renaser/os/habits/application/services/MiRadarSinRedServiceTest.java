package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.shared.application.EntregaSinRedDePrueba;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mi Radar guardado sin red (spec 048, fase 3, FR-019): una sola cosa que se decide por su hora lógica (V191). */
class MiRadarSinRedServiceTest {

    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");
    private static final HechoSinRed SIN_RED = new HechoSinRed(HECHO, UUID.randomUUID(), OrigenDeLaHora.RELOJ_DEL_SISTEMA);

    private final ConsultarProgresoParticipanteHabitsPort progresoPort = mock(ConsultarProgresoParticipanteHabitsPort.class);
    private final LoadMiRadarPort miRadarPort = mock(LoadMiRadarPort.class);
    private final ConfigurarMiRadarUseCase configurar = mock(ConfigurarMiRadarUseCase.class);
    private final ConsultarMiRadarUseCase consultar = mock(ConsultarMiRadarUseCase.class);
    private final PlanDeMiRadar plan = new PlanDeMiRadar(true, LocalDate.of(2026, 11, 10), List.of());

    private EntregaSinRedDePrueba entrega;
    private MiRadarSinRedService servicio;
    private UserId persona;

    @BeforeEach
    void preparar() {
        FixedClock reloj = FixedClock.at(LLEGA);
        entrega = EntregaSinRedDePrueba.nueva(reloj);
        servicio = new MiRadarSinRedService(new AccesoAlRadar(progresoPort), miRadarPort, configurar, consultar, entrega.puerto(),
                reloj);
        persona = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(12, "America/Lima", RolParticipante.TRAINEE, false, true)));
        when(miRadarPort.actualizadoEn(persona)).thenReturn(Optional.empty());
        when(configurar.configurar(any(), any())).thenReturn(plan);
        when(consultar.consultar(persona, persona)).thenReturn(plan);
    }

    @AfterEach
    void terminar() {
        entrega.terminar();
    }

    private ConfigurarMiRadarCommand comando() {
        return new ConfigurarMiRadarCommand(persona, persona, true, List.of());
    }

    @Test
    @DisplayName("sin cambios en el servidor se aplica con la hora real como referencia: «hoy» y la hora lógica son las de lo que hizo")
    void seAplicaConLaHoraReal() { // spec 048 FR-019
        PlanDeMiRadar respuesta = servicio.configurar(comando(), SIN_RED);

        assertThat(respuesta).isSameAs(plan);
        verify(configurar).configurar(comando(), HECHO);
    }

    @Test
    @DisplayName("D13: Mi Radar cambiado en el servidor DESPUÉS de lo que hiciste no se aplica: CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR")
    void ganaElCambioMasNuevo() { // spec 048 FR-019
        when(miRadarPort.actualizadoEn(persona)).thenReturn(Optional.of(HECHO.plusSeconds(30)));

        assertThatThrownBy(() -> servicio.configurar(comando(), SIN_RED)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        verify(configurar, never()).configurar(any(), any());
        assertThat(entrega.noAplicado(SIN_RED.idCliente())).contains(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
    }

    @Test
    @DisplayName("un cambio del servidor ANTERIOR a lo que hiciste no choca: se aplica lo del teléfono")
    void unCambioAnteriorNoChoca() { // spec 048 FR-019
        when(miRadarPort.actualizadoEn(persona)).thenReturn(Optional.of(HECHO.minusSeconds(3600)));

        servicio.configurar(comando(), SIN_RED);

        verify(configurar).configurar(comando(), HECHO);
    }

    @Test
    @DisplayName("el mismo envío dos veces se aplica una sola vez y la segunda responde la lectura actual")
    void repetirElEnvio() { // spec 048 FR-008
        servicio.configurar(comando(), SIN_RED);

        PlanDeMiRadar otraVez = servicio.configurar(comando(), SIN_RED);

        assertThat(otraVez).isSameAs(plan);
        verify(configurar, times(1)).configurar(any(), any());
    }
}
