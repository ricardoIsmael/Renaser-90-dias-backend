package com.renaser.os.calendar.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.points.api.ColorSemaforo;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Spec 053 FR-004 (decisión del dueño, 2026-11-14): el color de «Por semáforo» es el del día ya cerrado, el de AYER, nunca el de hoy a medio hacer.
 * El adaptador solo pregunta por la ventana vigente ({@link SemaforoFinder#vigenteDe}, los siete días cerrados que terminan ayer en la zona de cada
 * persona) y jamás por el día en vivo ni por la semana en curso: contra una versión que leyera hoy, estas pruebas fallan.
 */
@ExtendWith(MockitoExtension.class)
class ColoresDelSemaforoDeAyerTest {

    private static final LocalDate AYER = LocalDate.of(2026, 11, 13);

    @Mock
    private SemaforoFinder semaforo;

    // spec 053 FR-004, FR-005
    @Test
    @DisplayName("FR-004: lee solo la ventana vigente (cerrada, termina ayer) y traduce verde, amarillo y rojo; sin datos no entra")
    void leeSoloLaVentanaQueTerminaAyer() {
        UserId verde = UserId.of(UUID.randomUUID());
        UserId rojo = UserId.of(UUID.randomUUID());
        UserId sinDatos = UserId.of(UUID.randomUUID());
        when(semaforo.vigenteDe(anyCollection())).thenReturn(Map.of(
                verde, ventanaHastaAyer("90.0", ColorSemaforo.VERDE),
                rojo, ventanaHastaAyer("20.0", ColorSemaforo.ROJO),
                sinDatos, ventanaHastaAyer(null, ColorSemaforo.SIN_DATOS)));

        Map<UserId, ColorDeAudiencia> colores = new ColoresDelSemaforoAdapter(semaforo).colores(List.of(verde, rojo, sinDatos));

        assertThat(colores).containsOnly(Map.entry(verde, ColorDeAudiencia.VERDE), Map.entry(rojo, ColorDeAudiencia.ROJO));
        verify(semaforo).vigenteDe(anyCollection());
        verifyNoMoreInteractions(semaforo);
    }

    private static VentanaDelSemaforo ventanaHastaAyer(String porcentaje, ColorSemaforo color) {
        return new VentanaDelSemaforo(AYER.minusDays(6), AYER, porcentaje == null ? null : new BigDecimal(porcentaje), color, porcentaje == null ? 0 : 7,
                false, List.of());
    }
}
