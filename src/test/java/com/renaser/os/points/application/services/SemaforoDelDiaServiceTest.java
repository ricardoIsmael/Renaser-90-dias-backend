package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.ConteoDiarioHabitosFinder;
import com.renaser.os.points.api.ConteoDiarioObjetivosFinder;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.points.domain.model.semaforo.PausaId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El día en curso del semáforo, en vivo (D-223): la MISMA regla que el barrido (hábitos + objetivos, redondeo
 * a entero, cortes 80/60) y los mismos estados sin porcentaje. Es lo que elige la tarjeta de las 23:50.
 */
class SemaforoDelDiaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 9, 20);
    private static final LocalDate DIA_NOVENTA = DIA_UNO.plusDays(89);
    private static final LocalDate HOY = LocalDate.of(2026, 9, 28);
    private static final UserId ANA = UserId.of(UUID.randomUUID());

    private final ProgramasActivadosFinder programas = mock(ProgramasActivadosFinder.class);
    private final ConteoDiarioHabitosFinder habitos = mock(ConteoDiarioHabitosFinder.class);
    private final ConteoDiarioObjetivosFinder objetivos = mock(ConteoDiarioObjetivosFinder.class);
    private final SemaforoEnMemoria tablas = new SemaforoEnMemoria();
    private final Map<UserId, List<ConteoDelDia>> habitosDeHoy = new HashMap<>();
    private final Map<UserId, List<ConteoDelDia>> objetivosDeHoy = new HashMap<>();

    private final SemaforoDelDiaService servicio = new SemaforoDelDiaService(programas, tablas, habitos, objetivos);

    @BeforeEach
    void anaEnSuPrograma() {
        when(programas.deVarios(anyCollection())).thenReturn(Map.of(ANA, new ProgramaActivado(ANA, LIMA, DIA_UNO, DIA_NOVENTA)));
        when(habitos.porParticipanteEntre(anyCollection(), eq(HOY), eq(HOY))).thenReturn(habitosDeHoy);
        when(objetivos.porParticipanteEntre(anyCollection(), eq(HOY), eq(HOY))).thenReturn(objetivosDeHoy);
    }

    private DiaDelSemaforo diaDeAna(int habitosCumplidos, int habitosProgramados) {
        habitosDeHoy.put(ANA, List.of(new ConteoDelDia(HOY, habitosProgramados, habitosCumplidos)));
        return servicio.delDia(List.of(ANA), HOY).get(ANA);
    }

    @ParameterizedTest(name = "{0}/{1} = {2} % → {3}")
    @CsvSource({
            "8, 10, 80, VERDE",
            "6, 10, 60, AMARILLO",
            "5, 10, 50, ROJO",
            // El % del día se redondea a entero ANTES de pintar (ReglaDelSemaforo): el texto y la tarjeta
            // muestran siempre el mismo número y el mismo color.
            "799, 1000, 80, VERDE",
            "794, 1000, 79, AMARILLO",
            "599, 1000, 60, AMARILLO",
            "594, 1000, 59, ROJO",
    })
    @DisplayName("D-223: porcentaje y color del día con la regla del semáforo")
    void cortesDelDia(int cumplidos, int programados, int porcentaje, ColorSemaforo color) {
        DiaDelSemaforo dia = diaDeAna(cumplidos, programados);

        assertThat(dia.estado()).isEqualTo(EstadoDiaSemaforo.MEDIDO);
        assertThat(dia.porcentaje()).isEqualTo(porcentaje);
        assertThat(dia.color()).isEqualTo(color);
    }

    @Test
    @DisplayName("D-223: suma los objetivos del día, como el semáforo (4 de 5 hábitos + 0 de 1 objetivo = 67 %)")
    void sumaLosObjetivos() {
        objetivosDeHoy.put(ANA, List.of(new ConteoDelDia(HOY, 1, 0)));

        DiaDelSemaforo dia = diaDeAna(4, 5);

        assertThat(dia.porcentaje()).isEqualTo(67);
        assertThat(dia.color()).isEqualTo(ColorSemaforo.AMARILLO);
        assertThat(dia.habitosProgramados()).isEqualTo(5);
        assertThat(dia.objetivosProgramados()).isEqualTo(1);
    }

    @Test
    @DisplayName("D-223: sin nada programado ese día es SIN_DATOS, nunca 0 ni 100")
    void sinNadaProgramado() {
        assertThat(servicio.delDia(List.of(ANA), HOY).get(ANA).estado()).isEqualTo(EstadoDiaSemaforo.SIN_DATOS);
    }

    @Test
    @DisplayName("D-223: antes del Día 1 y después del 90 no se mide")
    void fueraDelPrograma() {
        when(programas.deVarios(anyCollection()))
                .thenReturn(Map.of(ANA, new ProgramaActivado(ANA, LIMA, HOY.plusDays(1), HOY.plusDays(90))));
        assertThat(diaDeAna(5, 5).estado()).isEqualTo(EstadoDiaSemaforo.FUERA_DEL_PROGRAMA);

        when(programas.deVarios(anyCollection()))
                .thenReturn(Map.of(ANA, new ProgramaActivado(ANA, LIMA, HOY.minusDays(90), HOY.minusDays(1))));
        assertThat(diaDeAna(5, 5).estado()).isEqualTo(EstadoDiaSemaforo.FUERA_DEL_PROGRAMA);
    }

    @Test
    @DisplayName("D-223: un día con la cuenta suspendida (D-209) o en pausa no se mide")
    void suspendidaOEnPausa() {
        Instant ahora = Instant.parse("2026-09-28T15:00:00Z");
        tablas.guardar(PausaDeMedicion.porSuspension(PausaId.of(UUID.randomUUID()), ANA, HOY, ahora));
        assertThat(diaDeAna(5, 5).estado()).isEqualTo(EstadoDiaSemaforo.CUENTA_SUSPENDIDA);

        tablas.pausas.clear();
        tablas.guardar(PausaDeMedicion.iniciar(PausaId.of(UUID.randomUUID()), ANA, HOY, HOY.plusDays(2), ahora));
        assertThat(diaDeAna(5, 5).estado()).isEqualTo(EstadoDiaSemaforo.PAUSADO);
    }

    @Test
    @DisplayName("D-223: sin programa activado no hay clave, y una colección vacía no consulta nada")
    void sinPrograma() {
        when(programas.deVarios(anyCollection())).thenReturn(Map.of());
        assertThat(servicio.delDia(List.of(ANA), HOY)).isEmpty();

        assertThat(servicio.delDia(List.of(), HOY)).isEmpty();
        verify(habitos, never()).porParticipanteEntre(anyCollection(), any(), any());
    }
}
