package com.renaser.os.points.application.services;

import com.renaser.os.points.api.PorcentajeCursosFinder;
import com.renaser.os.points.api.PorcentajeHabitosFinder;
import com.renaser.os.points.api.RankingGeneralFinder.PuestoEnElRankingGeneral;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort.CandidatoRanking;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El ranking general a un corte (D-262): las mismas personas, fórmula y filtro que la pestaña General. */
class RankingGeneralAlCorteServiceTest {

    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 4);

    private final LoadRankingCandidatosPort candidatos = mock(LoadRankingCandidatosPort.class);
    private final PorcentajeHabitosFinder habitos = mock(PorcentajeHabitosFinder.class);
    private final PorcentajeCursosFinder cursos = mock(PorcentajeCursosFinder.class);
    private final CuentasCerradasFinder cerradas = mock(CuentasCerradasFinder.class);
    private final RankingGeneralAlCorteService servicio = new RankingGeneralAlCorteService(candidatos, habitos, cursos,
            cerradas);

    @Test
    @DisplayName("75 % hábitos (7 días hasta el domingo) + 25 % cursos, de mayor a menor; cerradas fuera; sin dato = 0")
    void rankingAlCorte() {
        UserId ana = id(), beto = id(), carla = id(), cerrada = id();
        when(candidatos.aprendicesActivosConPuntaje()).thenReturn(List.of(candidato(ana, "Ana"), candidato(beto, "Beto"),
                candidato(carla, "Carla"), candidato(cerrada, "Cerrada")));
        when(cerradas.cerradasEntre(any())).thenReturn(Set.of(cerrada));
        when(habitos.porcentajePorParticipante(any(), eq(DOMINGO))).thenReturn(Map.of(ana, new BigDecimal("80.0"),
                beto, new BigDecimal("100.0"), cerrada, new BigDecimal("100.0")));
        when(cursos.porcentajePorParticipante(any())).thenReturn(Map.of(ana, new BigDecimal("100.0")));

        List<PuestoEnElRankingGeneral> ranking = servicio.alCorte(DOMINGO);

        assertThat(ranking).extracting(PuestoEnElRankingGeneral::nombreCompleto).containsExactly("Beto", "Ana", "Carla");
        assertThat(ranking).extracting(PuestoEnElRankingGeneral::puntaje)
                .containsExactly(new BigDecimal("100.0"), new BigDecimal("85.0"), BigDecimal.ZERO);
        verify(habitos).porcentajePorParticipante(any(), eq(DOMINGO));
    }

    @Test
    @DisplayName("sin candidatos no consulta nada")
    void sinCandidatos() {
        when(candidatos.aprendicesActivosConPuntaje()).thenReturn(List.of());

        assertThat(servicio.alCorte(DOMINGO)).isEmpty();
    }

    private static UserId id() {
        return UserId.of(UUID.randomUUID());
    }

    private static CandidatoRanking candidato(UserId id, String nombre) {
        return new CandidatoRanking(id, nombre, 0, BigDecimal.ZERO);
    }
}
