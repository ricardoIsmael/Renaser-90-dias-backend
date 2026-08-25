package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.out.coherencia.CargarConteoDiarioRocasPort;
import com.renaser.os.rocks.domain.model.coherencia.DiaRocas;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-43: el punto central de este servicio es que resuelva N participantes
 * con UNA sola llamada al puerto de salida — no un bucle. Cada test que
 * verifica {@code times(1)} sobre {@link CargarConteoDiarioRocasPort} es,
 * literalmente, la prueba de que no volvió el N+1 documentado en
 * {@code prisma/migrations/general_ranking_scores_function.sql}.
 */
@ExtendWith(MockitoExtension.class)
class PorcentajeRocasServiceTest {

    @Mock
    private CargarConteoDiarioRocasPort cargarConteoDiarioRocasPort;

    private PorcentajeRocasService service;

    private static UserId nuevoParticipante() {
        return UserId.of(UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        service = new PorcentajeRocasService(cargarConteoDiarioRocasPort);
    }

    @Test
    void listaDeParticipantesVacia_noLlamaAlPuerto() {
        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of(), LocalDate.of(2026, 8, 24));

        assertThat(resultado).isEmpty();
        verify(cargarConteoDiarioRocasPort, never()).conteoDiarioPorParticipante(any(), any(), any());
    }

    @Test
    void resuelveVariosParticipantesConUnaSolaLlamadaAlPuerto() {
        UserId a = nuevoParticipante();
        UserId b = nuevoParticipante();
        UserId c = nuevoParticipante();
        LocalDate hasta = LocalDate.of(2026, 8, 24);
        LocalDate desdeEsperado = hasta.minusDays(6);

        when(cargarConteoDiarioRocasPort.conteoDiarioPorParticipante(List.of(a, b, c), desdeEsperado, hasta))
                .thenReturn(Map.of(
                        a, List.of(new DiaRocas(hasta, 3, 3)),
                        b, List.of(new DiaRocas(hasta, 3, 0))));
        // c: el puerto no devuelve entrada -> el servicio debe asumir ventana vacía (100), no explotar.

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of(a, b, c), hasta);

        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(a)).isEqualByComparingTo("100.0");
        assertThat(resultado.get(b)).isEqualByComparingTo("0.0");
        assertThat(resultado.get(c)).isEqualByComparingTo("100.0");
        verify(cargarConteoDiarioRocasPort, times(1))
                .conteoDiarioPorParticipante(eq(List.of(a, b, c)), eq(desdeEsperado), eq(hasta));
    }

    @Test
    void laVentanaEsDeSieteDiasUtcCerradosTerminandoEnHasta() {
        UserId a = nuevoParticipante();
        LocalDate hasta = LocalDate.of(2026, 3, 10);

        when(cargarConteoDiarioRocasPort.conteoDiarioPorParticipante(any(), any(), any())).thenReturn(Map.of());

        service.porcentajePorParticipante(List.of(a), hasta);

        // 10, 9, 8, 7, 6, 5, 4 de marzo => 7 días, "desde" = hasta - 6.
        verify(cargarConteoDiarioRocasPort)
                .conteoDiarioPorParticipante(eq(List.of(a)), eq(LocalDate.of(2026, 3, 4)), eq(hasta));
    }

    @Test
    void todoParticipanteDelPedidoApareceEnElResultado_inclusoSinDatos() {
        UserId sinDatos = nuevoParticipante();
        when(cargarConteoDiarioRocasPort.conteoDiarioPorParticipante(any(), any(), any())).thenReturn(Map.of());

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of(sinDatos), LocalDate.of(2026, 8, 24));

        assertThat(resultado).containsEntry(sinDatos, new BigDecimal("100.0"));
    }
}
