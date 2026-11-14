package com.renaser.os.calendar.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.calendar.application.ports.out.audiencia.ColoresDelSemaforoPort.Lectura;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Spec 053 FR-004 y FR-005 (decisiones D4 y D5 del dueño, 2026-11-14): el color de «Por semáforo» es el del último día CERRADO con datos: ayer, o si
 * ayer no tuvo nada medido (domingo, feriado), el último día anterior que sí. Nunca el de hoy a medio hacer ni el promedio de la semana. Solo se
 * pregunta por la ventana vigente ({@link SemaforoFinder#vigenteDe}, que termina ayer): contra una versión que leyera hoy o el promedio, fallan.
 */
@ExtendWith(MockitoExtension.class)
class ColoresDelSemaforoDeAyerTest {

    private static final LocalDate AYER = LocalDate.of(2026, 11, 13);

    @Mock
    private SemaforoFinder semaforo;

    // spec 053 FR-004, FR-005
    @Test
    @DisplayName("FR-004: usa el color del día de ayer, no el promedio de la ventana; sin datos no entra")
    void usaElColorDeAyer() {
        UserId verdeAyerPeroRojoLaSemana = UserId.of(UUID.randomUUID());
        UserId sinDatos = UserId.of(UUID.randomUUID());
        when(semaforo.vigenteDe(anyCollection())).thenReturn(Map.of(
                verdeAyerPeroRojoLaSemana, ventana(ColorSemaforo.ROJO, dia(-6, 10, ColorSemaforo.ROJO), dia(0, 95, ColorSemaforo.VERDE)),
                sinDatos, ventana(ColorSemaforo.SIN_DATOS)));

        Lectura lectura = new ColoresDelSemaforoAdapter(semaforo).leer(List.of(verdeAyerPeroRojoLaSemana, sinDatos));

        assertThat(lectura.colores()).containsOnly(Map.entry(verdeAyerPeroRojoLaSemana, ColorDeAudiencia.VERDE));
        assertThat(lectura.fecha()).isEqualTo(AYER);
        verify(semaforo).vigenteDe(anyCollection());
        verifyNoMoreInteractions(semaforo);
    }

    // spec 053 FR-005
    @Test
    @DisplayName("FR-005: si ayer no tuvo datos, vale el último día con datos, y la lectura dice de qué día es")
    void sinDatosAyerValeElUltimoDiaConDatos() {
        UserId persona = UserId.of(UUID.randomUUID());
        when(semaforo.vigenteDe(anyCollection())).thenReturn(Map.of(
                persona, ventana(ColorSemaforo.AMARILLO, dia(-3, 70, ColorSemaforo.AMARILLO), sinDatosEse(-1), sinDatosEse(0))));

        Lectura lectura = new ColoresDelSemaforoAdapter(semaforo).leer(List.of(persona));

        assertThat(lectura.colores()).containsOnly(Map.entry(persona, ColorDeAudiencia.AMARILLO));
        assertThat(lectura.fecha()).isEqualTo(AYER.minusDays(3));
    }

    private static DiaDelSemaforo dia(int desdeAyer, int porcentaje, ColorSemaforo color) {
        return new DiaDelSemaforo(AYER.plusDays(desdeAyer), EstadoDiaSemaforo.MEDIDO, porcentaje, color, 5, 5, 0, 0);
    }

    private static DiaDelSemaforo sinDatosEse(int desdeAyer) {
        return DiaDelSemaforo.sinPorcentaje(AYER.plusDays(desdeAyer), EstadoDiaSemaforo.SIN_DATOS);
    }

    /** Siete días que terminan ayer: los dados donde se indica y el resto sin datos. */
    private static VentanaDelSemaforo ventana(ColorSemaforo color, DiaDelSemaforo... dados) {
        List<DiaDelSemaforo> dias = new ArrayList<>();
        for (int i = -6; i <= 0; i++) {
            LocalDate f = AYER.plusDays(i);
            DiaDelSemaforo dado = null;
            for (DiaDelSemaforo d : dados) {
                if (d.fecha().equals(f)) {
                    dado = d;
                }
            }
            dias.add(dado != null ? dado : DiaDelSemaforo.sinPorcentaje(f, EstadoDiaSemaforo.SIN_DATOS));
        }
        boolean conDatos = color != ColorSemaforo.SIN_DATOS;
        return new VentanaDelSemaforo(AYER.minusDays(6), AYER, conDatos ? new BigDecimal("50.0") : null, color, conDatos ? 1 : 0, false, dias);
    }
}
