package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DiasConHabitoCumplidoFinder;
import com.renaser.os.points.api.ResumenPuntajeFinder.ResumenPuntaje;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El contrato publico de racha y puntos ({@code points.api.ResumenPuntajeFinder}) tiene que dar lo
 * mismo que Inicio: puntos de {@code ConsultarPuntajeUseCase} y racha DERIVADA de los dias con habito
 * cumplido, contada en el dia LOCAL del participante (regla 02 §1).
 */
@ExtendWith(MockitoExtension.class)
class ResumenPuntajeServiceTest {

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

    private ResumenPuntajeService servicio() {
        return new ResumenPuntajeService(consultarPuntajeUseCase, participacionProgramaFinder,
                diasConHabitoCumplidoFinder);
    }

    private ParticipacionPrograma enDia12() {
        return new ParticipacionPrograma(aprendiz, true, 12, INICIO, LIMA, FasePrograma.PHASE_2_DEVELOPMENT,
                null, null, UserRole.TRAINEE, false, true);
    }

    /** Racha guardada 4/9 a proposito: si el finder la leyera en vez de derivarla, el test lo delata. */
    private void conPuntaje(int puntosLiga) {
        when(consultarPuntajeUseCase.consultar(aprendiz, aprendiz)).thenReturn(PuntajeParticipante.rehydrate(
                aprendiz, new BigDecimal("100.00"), puntosLiga, 4, 9, MADRUGADA_UTC));
    }

    @Test
    @DisplayName("a las 03:30 UTC la racha se cuenta hasta HOY EN LIMA (el 22), no hasta la fecha UTC (el 23)")
    void laRachaSeCuentaEnElDiaLocal() {
        conPuntaje(150);
        when(participacionProgramaFinder.deParticipante(aprendiz)).thenReturn(Optional.of(enDia12()));
        when(diasConHabitoCumplidoFinder.entre(aprendiz, INICIO, HOY_EN_LIMA)).thenReturn(List.of(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 21), HOY_EN_LIMA));

        Optional<ResumenPuntaje> resumen = servicio().de(aprendiz, MADRUGADA_UTC);

        // Con la fecha del servidor (el 23) el 22 seria "ayer" y daria igual 3; lo que fija el dia local
        // es el "hasta" que se le pide al finder: con el 23, Mockito no encontraria el stub.
        assertThat(resumen).contains(new ResumenPuntaje(150, 3, 4));
    }

    @Test
    @DisplayName("los puntos son los de ConsultarPuntajeUseCase y la racha guardada no se usa")
    void sinDiasCumplidosLaRachaEsCero() {
        conPuntaje(80);
        when(participacionProgramaFinder.deParticipante(aprendiz)).thenReturn(Optional.of(enDia12()));
        when(diasConHabitoCumplidoFinder.entre(aprendiz, INICIO, HOY_EN_LIMA)).thenReturn(List.of());

        assertThat(servicio().de(aprendiz, MADRUGADA_UTC)).contains(new ResumenPuntaje(80, 0, 0));
    }

    @Test
    @DisplayName("un programa que todavia no empezo no tiene racha y no se le pide al finder un rango al reves")
    void programaSinEmpezar() {
        conPuntaje(100);
        ParticipacionPrograma recienInscrito = new ParticipacionPrograma(aprendiz, true, 0,
                HOY_EN_LIMA.plusDays(1), LIMA, FasePrograma.PHASE_1_REBIRTH, null, null, UserRole.TRAINEE,
                false, true);
        when(participacionProgramaFinder.deParticipante(aprendiz)).thenReturn(Optional.of(recienInscrito));

        assertThat(servicio().de(aprendiz, MADRUGADA_UTC)).contains(new ResumenPuntaje(100, 0, 0));
        verify(diasConHabitoCumplidoFinder, never()).entre(any(), any(), any());
    }

    @Test
    @DisplayName("una persona que no existe en users no tiene resumen")
    void sinParticipacion() {
        when(participacionProgramaFinder.deParticipante(aprendiz)).thenReturn(Optional.empty());

        assertThat(servicio().de(aprendiz, MADRUGADA_UTC)).isEmpty();
        verifyNoInteractions(consultarPuntajeUseCase, diasConHabitoCumplidoFinder);
    }

    @Test
    @DisplayName("una cuenta suspendida propaga NotAuthorizedException, igual que ConsultarPuntajeUseCase")
    void cuentaSuspendida() {
        when(participacionProgramaFinder.deParticipante(aprendiz)).thenReturn(Optional.of(enDia12()));
        when(consultarPuntajeUseCase.consultar(aprendiz, aprendiz))
                .thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThatThrownBy(() -> servicio().de(aprendiz, MADRUGADA_UTC)).isInstanceOf(NotAuthorizedException.class);
    }
}
