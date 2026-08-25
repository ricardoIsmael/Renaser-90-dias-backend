package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase.EntradaRanking;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort.CandidatoRanking;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingPort.EntradaRankingConNombre;
import com.renaser.os.points.application.ports.out.ranking.SaveRankingSnapshotPort;
import com.renaser.os.points.domain.model.ranking.PosicionRanking;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.points.api.PorcentajeCursosFinder;
import com.renaser.os.points.api.PorcentajeHabitosFinder;
import com.renaser.os.points.api.PorcentajeRocasFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 24);

    @Mock
    private LoadRankingCandidatosPort loadRankingCandidatosPort;
    @Mock
    private SaveRankingSnapshotPort saveRankingSnapshotPort;
    @Mock
    private LoadRankingPort loadRankingPort;
    @Mock
    private PorcentajeHabitosFinder porcentajeHabitosFinder;
    @Mock
    private PorcentajeRocasFinder porcentajeRocasFinder;
    @Mock
    private PorcentajeCursosFinder porcentajeCursosFinder;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private RankingService service;

    @BeforeEach
    void setUp() {
        service = new RankingService(loadRankingCandidatosPort, saveRankingSnapshotPort, loadRankingPort,
                porcentajeHabitosFinder, porcentajeRocasFinder, porcentajeCursosFinder, userSummaryFinder);
        // Actor activo por defecto: los tests de consultar() no son sobre autorizacion.
        lenient().when(userSummaryFinder.findById(any())).thenAnswer(inv ->
                Optional.of(new UserSummary(inv.getArgument(0), "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    private static UserId id() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("LEAGUE ordena por puntosLiga descendente y numera posiciones desde 1")
    void leagueOrdenaPorPuntosLiga() {
        UserId a = id();
        UserId b = id();
        UserId c = id();
        when(loadRankingCandidatosPort.aprendicesActivosConPuntaje()).thenReturn(List.of(
                new CandidatoRanking(a, "A", 50, BigDecimal.valueOf(10)),
                new CandidatoRanking(b, "B", 200, BigDecimal.valueOf(10)),
                new CandidatoRanking(c, "C", 100, BigDecimal.valueOf(10))));

        service.generar(TipoRanking.LEAGUE, FECHA);

        ArgumentCaptor<List<PosicionRanking>> captor = ArgumentCaptor.forClass(List.class);
        verify(saveRankingSnapshotPort).reemplazar(eq(TipoRanking.LEAGUE), eq(FECHA), captor.capture());
        List<PosicionRanking> posiciones = captor.getValue();

        assertThat(posiciones).hasSize(3);
        assertThat(posiciones.get(0).participanteId()).isEqualTo(b); // 200
        assertThat(posiciones.get(0).posicion()).isEqualTo(1);
        assertThat(posiciones.get(1).participanteId()).isEqualTo(c); // 100
        assertThat(posiciones.get(2).participanteId()).isEqualTo(a); // 50
    }

    @Test
    @DisplayName("CELL ordena por coherencia descendente, no por puntosLiga")
    void cellOrdenaPorCoherencia() {
        UserId a = id();
        UserId b = id();
        when(loadRankingCandidatosPort.aprendicesActivosConPuntaje()).thenReturn(List.of(
                new CandidatoRanking(a, "A", 1000, BigDecimal.valueOf(10)),
                new CandidatoRanking(b, "B", 1, BigDecimal.valueOf(90))));

        service.generar(TipoRanking.CELL, FECHA);

        ArgumentCaptor<List<PosicionRanking>> captor = ArgumentCaptor.forClass(List.class);
        verify(saveRankingSnapshotPort).reemplazar(eq(TipoRanking.CELL), eq(FECHA), captor.capture());
        assertThat(captor.getValue().get(0).participanteId()).isEqualTo(b); // coherencia 90 > 10
    }

    @Test
    @DisplayName("GENERAL pondera 50/35/15 y consulta cada modulo UNA sola vez, no una por aprendiz")
    void generalPonderaLosTresModulosEnLote() {
        UserId a = id();
        UserId b = id();
        when(loadRankingCandidatosPort.aprendicesActivosConPuntaje()).thenReturn(List.of(
                new CandidatoRanking(a, "A", 0, BigDecimal.ZERO),
                new CandidatoRanking(b, "B", 0, BigDecimal.ZERO)));
        when(porcentajeHabitosFinder.porcentajePorParticipante(anyCollection(), eq(FECHA)))
                .thenReturn(Map.of(a, new BigDecimal("80.0"), b, new BigDecimal("40.0")));
        when(porcentajeRocasFinder.porcentajePorParticipante(anyCollection(), eq(FECHA)))
                .thenReturn(Map.of(a, new BigDecimal("60.0"), b, new BigDecimal("20.0")));
        when(porcentajeCursosFinder.porcentajePorParticipante(anyCollection()))
                .thenReturn(Map.of(a, new BigDecimal("40.0"), b, new BigDecimal("100.0")));

        service.generar(TipoRanking.GENERAL, FECHA);

        ArgumentCaptor<List<PosicionRanking>> captor = ArgumentCaptor.forClass(List.class);
        verify(saveRankingSnapshotPort).reemplazar(eq(TipoRanking.GENERAL), eq(FECHA), captor.capture());

        // a = 0.5*80 + 0.35*60 + 0.15*40 = 67.0 ; b = 0.5*40 + 0.35*20 + 0.15*100 = 42.0
        assertThat(captor.getValue().get(0).participanteId()).isEqualTo(a);
        assertThat(captor.getValue().get(0).puntaje()).isEqualByComparingTo("67.0");
        assertThat(captor.getValue().get(1).puntaje()).isEqualByComparingTo("42.0");

        // El punto de D-43: una consulta por modulo para TODOS, nunca una por participante.
        verify(porcentajeHabitosFinder).porcentajePorParticipante(anyCollection(), eq(FECHA));
        verify(porcentajeRocasFinder).porcentajePorParticipante(anyCollection(), eq(FECHA));
        verify(porcentajeCursosFinder).porcentajePorParticipante(anyCollection());
    }

    @Test
    @DisplayName("GENERAL: al aprendiz sin datos no se lo castiga con 0, vale 100")
    void generalSinDatosVale100() {
        UserId a = id();
        when(loadRankingCandidatosPort.aprendicesActivosConPuntaje())
                .thenReturn(List.of(new CandidatoRanking(a, "A", 0, BigDecimal.ZERO)));
        when(porcentajeHabitosFinder.porcentajePorParticipante(anyCollection(), eq(FECHA))).thenReturn(Map.of());
        when(porcentajeRocasFinder.porcentajePorParticipante(anyCollection(), eq(FECHA))).thenReturn(Map.of());
        when(porcentajeCursosFinder.porcentajePorParticipante(anyCollection())).thenReturn(Map.of());

        service.generar(TipoRanking.GENERAL, FECHA);

        ArgumentCaptor<List<PosicionRanking>> captor = ArgumentCaptor.forClass(List.class);
        verify(saveRankingSnapshotPort).reemplazar(eq(TipoRanking.GENERAL), eq(FECHA), captor.capture());
        assertThat(captor.getValue().get(0).puntaje()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("COHORT sigue sin ser generable: falta el dato de cohorte")
    void cohortNoEsGenerable() {
        assertThatThrownBy(() -> service.generar(TipoRanking.COHORT, FECHA))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(saveRankingSnapshotPort, never()).reemplazar(any(), any(), any());
    }

    @Test
    @DisplayName("consultar() proyecta las filas del puerto out 1:1, sin recalcular nada")
    void consultarProyectaSnapshotExistente() {
        UserId a = id();
        when(loadRankingPort.porTipoYFecha(TipoRanking.LEAGUE, FECHA)).thenReturn(List.of(
                new EntradaRankingConNombre(a, "A", 1, BigDecimal.valueOf(150))));

        List<EntradaRanking> resultado = service.consultar(id(), TipoRanking.LEAGUE, FECHA);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).participanteId()).isEqualTo(a);
        assertThat(resultado.get(0).posicion()).isEqualTo(1);
        assertThat(resultado.get(0).puntaje()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("el ranking exige actor: un id inexistente no puede leer el padron completo")
    void consultarRechazaActorInexistente() {
        UserId fantasma = id();
        when(userSummaryFinder.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(fantasma, TipoRanking.LEAGUE, FECHA))
                .isInstanceOf(NotAuthorizedException.class);

        verify(loadRankingPort, never()).porTipoYFecha(any(), any());
    }

    @Test
    @DisplayName("el ranking exige cuenta ACTIVA: un actor suspendido no ve el padron")
    void consultarRechazaActorSuspendido() {
        UserId suspendido = id();
        when(userSummaryFinder.findById(suspendido)).thenReturn(Optional.of(
                new UserSummary(suspendido, "X", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.consultar(suspendido, TipoRanking.LEAGUE, FECHA))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("consultar() sin snapshot generado devuelve lista vacia, no un error")
    void consultarSinSnapshotDevuelveVacio() {
        when(loadRankingPort.porTipoYFecha(TipoRanking.GENERAL, FECHA)).thenReturn(List.of());

        assertThat(service.consultar(id(), TipoRanking.GENERAL, FECHA)).isEmpty();
    }
}
