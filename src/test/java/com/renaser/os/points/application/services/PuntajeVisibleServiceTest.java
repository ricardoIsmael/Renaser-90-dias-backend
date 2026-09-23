package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DiasConHabitoCumplidoFinder;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeVisibleUseCase.PuntajeVisible;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-216: {@code GET /api/v1/points/{id}} mostraba la racha GUARDADA, que vale 0 para todo el mundo
 * porque nadie la escribe. Ahora muestra la derivada, la misma que Hoy.
 */
@ExtendWith(MockitoExtension.class)
class PuntajeVisibleServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 2026-09-23 03:30 UTC = 2026-09-22 22:30 en Lima: el dia local todavia es el anterior. */
    private static final Instant MADRUGADA_UTC = Instant.parse("2026-09-23T03:30:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 22);
    /** Coherente con el dia 12: del 11 al 22 de septiembre son 12 dias de programa. */
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 11);

    @Mock
    private ConsultarPuntajeUseCase consultarPuntajeUseCase;
    @Mock
    private ParticipacionProgramaFinder participacionProgramaFinder;
    @Mock
    private DiasConHabitoCumplidoFinder diasConHabitoCumplidoFinder;

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final FixedClock reloj = FixedClock.at(MADRUGADA_UTC);

    private PuntajeVisibleService servicio() {
        return new PuntajeVisibleService(consultarPuntajeUseCase, participacionProgramaFinder,
                diasConHabitoCumplidoFinder, reloj);
    }

    /** Racha guardada 0/0, como en produccion: la que se muestre tiene que salir de los dias cumplidos. */
    private void conPuntajeGuardado() {
        when(consultarPuntajeUseCase.consultar(aprendiz, aprendiz)).thenReturn(PuntajeParticipante.rehydrate(
                aprendiz, new BigDecimal("100.00"), 150, 0, 0, MADRUGADA_UTC));
    }

    private ParticipacionPrograma enDia12() {
        return new ParticipacionPrograma(aprendiz, true, 12, INICIO, LIMA, FasePrograma.PHASE_2_DEVELOPMENT,
                null, null, UserRole.TRAINEE, false, true);
    }

    @Test
    @DisplayName("muestra la racha derivada (3, record 4), no la guardada (0), contada hasta HOY EN LIMA")
    void rachaDerivada() {
        conPuntajeGuardado();
        when(participacionProgramaFinder.deParticipante(aprendiz)).thenReturn(Optional.of(enDia12()));
        when(diasConHabitoCumplidoFinder.entre(aprendiz, INICIO, HOY_EN_LIMA)).thenReturn(List.of(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 21), HOY_EN_LIMA));

        PuntajeVisible visible = servicio().consultar(aprendiz, aprendiz);

        assertThat(visible.puntosLiga()).isEqualTo(150);
        assertThat(visible.rachaActual()).isEqualTo(3);
        assertThat(visible.rachaMaxima()).isEqualTo(4);
    }

    @Test
    @DisplayName("quien no esta inscrito ve su puntaje sin racha, sin consultar dias cumplidos")
    void sinInscripcion() {
        conPuntajeGuardado();
        when(participacionProgramaFinder.deParticipante(aprendiz)).thenReturn(Optional.empty());

        PuntajeVisible visible = servicio().consultar(aprendiz, aprendiz);

        assertThat(visible.rachaActual()).isZero();
        verify(diasConHabitoCumplidoFinder, never()).entre(any(), any(), any());
    }

    @Test
    @DisplayName("las reglas de acceso siguen siendo las de ConsultarPuntajeUseCase")
    void accesoIntacto() {
        UserId otro = UserId.of(UUID.randomUUID());
        when(participacionProgramaFinder.deParticipante(otro)).thenReturn(Optional.empty());
        when(consultarPuntajeUseCase.consultar(aprendiz, otro))
                .thenThrow(new NotAuthorizedException("Solo el propio participante o un administrativo"));

        assertThatThrownBy(() -> servicio().consultar(aprendiz, otro)).isInstanceOf(NotAuthorizedException.class);
    }
}
