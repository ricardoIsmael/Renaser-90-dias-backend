package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.registro.ContarRegistrosDiariosHabitsPort;
import com.renaser.os.habits.domain.model.registro.ConteoDiarioHabitos;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PorcentajeHabitosServiceTest {

    private static final LocalDate HASTA = LocalDate.of(2026, 8, 24);
    // Ventana de Ley VI: 7 dias UTC cerrados incluyendo "hasta" -> [hasta-6, hasta].
    private static final LocalDate DESDE_ESPERADO = LocalDate.of(2026, 8, 18);

    @Mock
    private ContarRegistrosDiariosHabitsPort contarPort;

    @Test
    void calculaVariosParticipantesEnUnaSolaLlamadaAlPuertoEnLote() {
        UserId participanteConDatos = UserId.of(UUID.randomUUID());
        UserId participanteSinDatos = UserId.of(UUID.randomUUID());
        Set<UserId> participantes = Set.of(participanteConDatos, participanteSinDatos);

        // participanteConDatos: un solo dia, 100% completado -> 100.0
        // participanteSinDatos: ausente del mapa devuelto por el puerto -> "sin datos" -> 100.0
        // (mismo valor final por motivos distintos: es a proposito que el test no dependa de la
        // coincidencia — ver calculaPorcentajeDistintoDeCienCuandoHayDatosParciales)
        when(contarPort.contarPorParticipanteYDia(eq(participantes), eq(DESDE_ESPERADO), eq(HASTA)))
                .thenReturn(Map.of(participanteConDatos, List.of(new ConteoDiarioHabitos(HASTA, 2, 2, 0))));

        PorcentajeHabitosService service = new PorcentajeHabitosService(contarPort);

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(participantes, HASTA);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(participanteConDatos)).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(resultado.get(participanteSinDatos)).isEqualByComparingTo(new BigDecimal("100.0"));
        verify(contarPort).contarPorParticipanteYDia(any(), any(), any());
    }

    @Test
    void calculaPorcentajeDistintoDeCienCuandoHayDatosParciales() {
        UserId participante = UserId.of(UUID.randomUUID());

        when(contarPort.contarPorParticipanteYDia(any(), any(), any()))
                .thenReturn(Map.of(participante, List.of(new ConteoDiarioHabitos(HASTA, 4, 2, 0))));

        PorcentajeHabitosService service = new PorcentajeHabitosService(contarPort);

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(Set.of(participante), HASTA);

        assertThat(resultado.get(participante)).isEqualByComparingTo(new BigDecimal("50.0"));
    }

    @Test
    void participantesVaciosNoConsultaElPuerto() {
        PorcentajeHabitosService service = new PorcentajeHabitosService(contarPort);

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(Set.of(), HASTA);

        assertThat(resultado).isEmpty();
        verify(contarPort, never()).contarPorParticipanteYDia(any(), any(), any());
    }
}
