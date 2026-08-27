package com.renaser.os.points.application.services;

import com.renaser.os.community.api.CelulaFinder;
import com.renaser.os.community.api.CelulaFinder.CelulaParticipanteResumen;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingAgregadoUseCase.RankingAgregado;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase.EntradaRanking;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingAgregadoServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 26);

    @Mock
    private ConsultarRankingUseCase consultarRankingUseCase;
    @Mock
    private CelulaFinder celulaFinder;

    private RankingAgregadoService service;

    private final UserId actor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new RankingAgregadoService(consultarRankingUseCase, celulaFinder);
    }

    private static EntradaRanking entrada(String nombre) {
        return new EntradaRanking(UserId.of(UUID.randomUUID()), nombre, 1, BigDecimal.TEN);
    }

    @Test
    @DisplayName("agregado compone LEAGUE, CELL y GENERAL para la misma fecha, nunca COHORT")
    void agregadoComponeLosTresTiposExistentes() {
        List<EntradaRanking> liga = List.of(entrada("Liga"));
        List<EntradaRanking> coherencia = List.of(entrada("Coherencia"));
        List<EntradaRanking> general = List.of(entrada("General"));
        when(consultarRankingUseCase.consultar(actor, TipoRanking.LEAGUE, FECHA)).thenReturn(liga);
        when(consultarRankingUseCase.consultar(actor, TipoRanking.CELL, FECHA)).thenReturn(coherencia);
        when(consultarRankingUseCase.consultar(actor, TipoRanking.GENERAL, FECHA)).thenReturn(general);
        when(celulaFinder.celulaDeParticipante(actor)).thenReturn(Optional.empty());

        RankingAgregado resultado = service.agregado(actor, FECHA);

        assertThat(resultado.fecha()).isEqualTo(FECHA);
        assertThat(resultado.liga()).isEqualTo(liga);
        assertThat(resultado.coherenciaIndividual()).isEqualTo(coherencia);
        assertThat(resultado.general()).isEqualTo(general);
        verify(consultarRankingUseCase).consultar(actor, TipoRanking.LEAGUE, FECHA);
        verify(consultarRankingUseCase).consultar(actor, TipoRanking.CELL, FECHA);
        verify(consultarRankingUseCase).consultar(actor, TipoRanking.GENERAL, FECHA);
    }

    @Test
    @DisplayName("sin celula asignada, el resumen de celula es null, no un error")
    void sinCelulaAsignadaElResumenEsNull() {
        when(consultarRankingUseCase.consultar(any(), any(), any())).thenReturn(List.of());
        when(celulaFinder.celulaDeParticipante(actor)).thenReturn(Optional.empty());

        RankingAgregado resultado = service.agregado(actor, FECHA);

        assertThat(resultado.celula()).isNull();
    }

    @Test
    @DisplayName("con celula asignada, proyecta 1:1 lo que expone community.api.CelulaFinder")
    void conCelulaAsignadaProyectaElResumen() {
        UUID celulaId = UUID.randomUUID();
        when(consultarRankingUseCase.consultar(any(), any(), any())).thenReturn(List.of());
        when(celulaFinder.celulaDeParticipante(actor)).thenReturn(Optional.of(
                new CelulaParticipanteResumen(celulaId, "Celula 1", "Cohorte Agosto", "Mentor Uno", 8, 3)));

        RankingAgregado resultado = service.agregado(actor, FECHA);

        assertThat(resultado.celula()).isNotNull();
        assertThat(resultado.celula().celulaId()).isEqualTo(celulaId);
        assertThat(resultado.celula().cellName()).isEqualTo("Celula 1");
        assertThat(resultado.celula().cohortName()).isEqualTo("Cohorte Agosto");
        assertThat(resultado.celula().mentorName()).isEqualTo("Mentor Uno");
        assertThat(resultado.celula().memberCount()).isEqualTo(8);
        assertThat(resultado.celula().totalCellsInCohort()).isEqualTo(3);
    }

    @Test
    @DisplayName("un actor sin cuenta activa no ve el agregado (misma guarda que cada pestaña plana)")
    void actorSuspendidoEsRechazado() {
        when(consultarRankingUseCase.consultar(actor, TipoRanking.LEAGUE, FECHA))
                .thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThatThrownBy(() -> service.agregado(actor, FECHA)).isInstanceOf(NotAuthorizedException.class);

        // Corta apenas falla la primera pestaña: no sigue pidiendo CELL/GENERAL ni la celula.
        verify(consultarRankingUseCase, never()).consultar(actor, TipoRanking.GENERAL, FECHA);
        verify(celulaFinder, never()).celulaDeParticipante(any());
    }
}
