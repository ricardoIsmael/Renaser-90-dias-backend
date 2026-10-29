package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.application.ports.out.FotosDeHuecosPort;
import com.renaser.os.mentoring.domain.model.huecos.FotoDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Qué semana se entrega y de dónde sale: calculada si es la en curso, de la foto si ya terminó (spec 015). Los relojes
 * caen a propósito en la madrugada UTC, que en Lima todavía es el día anterior (regla 02).
 */
class HuecosDeLaSemanaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Miércoles 28 de octubre de 2026, 10:00 en Lima. */
    private static final FixedClock MEDIA_MANANA = FixedClock.at(Instant.parse("2026-10-28T15:00:00Z"));
    /** Lunes 26 en UTC y todavía domingo 25 a las 22:00 en Lima: la semana en curso es la del lunes 19. */
    private static final FixedClock MADRUGADA_UTC = FixedClock.at(Instant.parse("2026-10-26T03:00:00Z"));
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 10, 14);
    private static final LocalDate PRIMERA_SEMANA = LocalDate.of(2026, 10, 12);

    private final UserId persona = UserId.of(UUID.randomUUID());
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final CalculoDeLosHuecos calculo = mock(CalculoDeLosHuecos.class);
    private final FotosDeHuecosPort fotos = mock(FotosDeHuecosPort.class);

    @BeforeEach
    void preparar() {
        when(participaciones.deParticipante(persona)).thenReturn(Optional.of(participacion(true, false)));
        when(calculo.calcular(any(), any(), any())).thenAnswer(invocacion ->
                SemanasDeHuecosDePrueba.delContrato(invocacion.getArgument(1)));
    }

    private ParticipacionPrograma participacion(boolean activado, boolean suspendido) {
        return new ParticipacionPrograma(persona, true, activado ? 15 : 0, DIA_UNO, LIMA,
                FasePrograma.PHASE_2_DEVELOPMENT, null, null, UserRole.TRAINEE, suspendido, activado);
    }

    private HuecosDeLaSemanaService servicio(FixedClock reloj) {
        return new HuecosDeLaSemanaService(participaciones, calculo, fotos, reloj);
    }

    @Test
    @DisplayName("sin semana: la en curso de la persona, calculada al pedirla, con la primera semana de su programa")
    void semanaEnCurso() {
        HuecosDeLaSemana huecos = servicio(MEDIA_MANANA).semanaDe(persona, null);

        assertThat(huecos.semana()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(huecos.primeraSemana()).isEqualTo(PRIMERA_SEMANA);
        assertThat(huecos.cerrada()).isFalse();
        verify(calculo).calcular(any(), eq(LocalDate.of(2026, 10, 26)),
                eq(LocalDate.of(2026, 10, 28)));
        verifyNoInteractions(fotos);
    }

    @Test
    @DisplayName("a las 03:00 UTC del lunes todavia es domingo en Lima: la semana en curso es la anterior y la del lunes es futura")
    void madrugadaUtc() {
        HuecosDeLaSemanaService servicio = servicio(MADRUGADA_UTC);

        assertThat(servicio.semanaDe(persona, null).semana()).isEqualTo(LocalDate.of(2026, 10, 19));
        verify(calculo).calcular(any(), eq(LocalDate.of(2026, 10, 19)),
                eq(LocalDate.of(2026, 10, 25)));
        assertThatThrownBy(() -> servicio.semanaDe(persona, LocalDate.of(2026, 10, 26)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una semana ya terminada sale de la foto, con cerrada = true, sin recalcular nada")
    void semanaCerradaConFoto() {
        LocalDate lunes = LocalDate.of(2026, 10, 19);
        SemanaDeHuecos guardada = SemanasDeHuecosDePrueba.delContrato(lunes);
        when(fotos.deLaSemana(persona, lunes)).thenReturn(Optional.of(
                new FotoDeHuecos(persona, LIMA, guardada, Instant.parse("2026-10-26T06:00:00Z"))));

        HuecosDeLaSemana huecos = servicio(MEDIA_MANANA).semanaDe(persona, lunes);

        assertThat(huecos.cerrada()).isTrue();
        assertThat(huecos.semana()).isEqualTo(lunes);
        assertThat(huecos.resumen().cumplidosATiempo()).isEqualTo(32);
        verify(calculo, never()).calcular(any(), any(), any());
    }

    @Test
    @DisplayName("una semana terminada sin foto (anterior a la funcion) se calcula igual y no se dice cerrada")
    void semanaCerradaSinFoto() {
        LocalDate lunes = LocalDate.of(2026, 10, 19);
        when(fotos.deLaSemana(persona, lunes)).thenReturn(Optional.empty());

        HuecosDeLaSemana huecos = servicio(MEDIA_MANANA).semanaDe(persona, lunes);

        assertThat(huecos.cerrada()).isFalse();
        verify(calculo).calcular(any(), eq(lunes),
                eq(LocalDate.of(2026, 10, 28)));
    }

    @Test
    @DisplayName("una semana futura, una anterior a la primera y lo que no es lunes dan 400 (IllegalArgumentException)")
    void semanasInvalidas() {
        HuecosDeLaSemanaService servicio = servicio(MEDIA_MANANA);

        assertThatThrownBy(() -> servicio.semanaDe(persona, LocalDate.of(2026, 11, 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.semanaDe(persona, PRIMERA_SEMANA.minusWeeks(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.semanaDe(persona, LocalDate.of(2026, 10, 27)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(calculo, fotos);
    }

    @Test
    @DisplayName("sin Dia 1 elegido no hay primera semana y la unica semana posible es la en curso")
    void sinDiaUno() {
        when(participaciones.deParticipante(persona)).thenReturn(Optional.of(participacion(false, false)));
        HuecosDeLaSemanaService servicio = servicio(MEDIA_MANANA);

        assertThat(servicio.semanaDe(persona, null).primeraSemana()).isNull();
        assertThatThrownBy(() -> servicio.semanaDe(persona, LocalDate.of(2026, 10, 19)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una cuenta suspendida no se calcula")
    void suspendida() {
        when(participaciones.deParticipante(persona)).thenReturn(Optional.of(participacion(true, true)));

        assertThatThrownBy(() -> servicio(MEDIA_MANANA).semanaDe(persona, null))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(calculo, fotos);
    }

    @Test
    @DisplayName("una persona que no existe es 404")
    void inexistente() {
        UserId otra = UserId.of(UUID.randomUUID());
        when(participaciones.deParticipante(otra)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio(MEDIA_MANANA).semanaDe(otra, null))
                .isInstanceOf(NoSuchElementException.class);
    }
}
