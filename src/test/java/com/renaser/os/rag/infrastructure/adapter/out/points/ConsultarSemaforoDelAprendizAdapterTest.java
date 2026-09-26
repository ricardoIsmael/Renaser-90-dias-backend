package com.renaser.os.rag.infrastructure.adapter.out.points;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.SemanaCerrada;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort.SemaforoDelAprendiz;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort.Tramo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** D-177: el semaforo se lee como lo guardo {@code points}, con la palabra de su color (RL-30). */
class ConsultarSemaforoDelAprendizAdapterTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final SemaforoFinder finder = mock(SemaforoFinder.class);
    private final ConsultarSemaforoDelAprendizAdapter adapter = new ConsultarSemaforoDelAprendizAdapter(finder);

    @Test
    @DisplayName("la ventana vigente y la ultima semana cerrada, con su etiqueta")
    void vigenteYUltimaSemana() {
        VentanaDelSemaforo vigente = new VentanaDelSemaforo(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 23),
                new BigDecimal("72.5"), ColorSemaforo.AMARILLO, 7, false, List.of());
        SemanaCerrada cerrada = new SemanaCerrada(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18),
                new BigDecimal("81.0"), ColorSemaforo.VERDE, 7, Instant.parse("2026-09-19T05:00:00Z"));
        when(finder.detalleDe(APRENDIZ, 1)).thenReturn(new DetalleDelSemaforo(true, true, LIMA, null, vigente,
                List.of(cerrada), Instant.parse("2026-09-24T05:00:00Z")));

        Optional<SemaforoDelAprendiz> semaforo = adapter.de(APRENDIZ);

        assertThat(semaforo).contains(new SemaforoDelAprendiz(
                new Tramo(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 23), new BigDecimal("72.5"),
                        ColorSemaforo.AMARILLO.etiqueta(), 7),
                new Tramo(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18), new BigDecimal("81.0"),
                        ColorSemaforo.VERDE.etiqueta(), 7)));
    }

    @Test
    @DisplayName("quien no se mide no tiene semaforo")
    void noAplica() {
        when(finder.detalleDe(APRENDIZ, 1)).thenReturn(DetalleDelSemaforo.noAplica(LIMA, true));

        assertThat(adapter.de(APRENDIZ)).isEmpty();
    }
}
