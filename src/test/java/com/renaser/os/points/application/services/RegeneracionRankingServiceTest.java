package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase.RegenerarSnapshotsCommand;
import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase.ResultadoRegeneracion;
import com.renaser.os.points.application.ports.out.puntaje.VerificarActorAdministrativoPort;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Rehacer el corte del ranking a pedido de un operador (D-129). */
@ExtendWith(MockitoExtension.class)
class RegeneracionRankingServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-15T10:00:00Z"));

    @Mock
    private GenerarSnapshotRankingUseCase generarSnapshotRankingUseCase;
    @Mock
    private VerificarActorAdministrativoPort verificarActorAdministrativoPort;

    private final UserId actor = UserId.of(UUID.randomUUID());

    private RegeneracionRankingService service() {
        return new RegeneracionRankingService(generarSnapshotRankingUseCase, verificarActorAdministrativoPort, CLOCK);
    }

    @Test
    void unAdminRegeneraLosTresTiposDelDiaDeHoy() {
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actor)).thenReturn(true);

        ResultadoRegeneracion resultado = service().regenerar(new RegenerarSnapshotsCommand(actor, null));

        assertThat(resultado.fecha()).isEqualTo(CLOCK.today());
        assertThat(resultado.tipos()).containsExactly("LEAGUE", "CELL", "GENERAL");
        assertThat(resultado.fallados()).isEmpty();
        verify(generarSnapshotRankingUseCase).generar(TipoRanking.LEAGUE, CLOCK.today());
        verify(generarSnapshotRankingUseCase).generar(TipoRanking.CELL, CLOCK.today());
        verify(generarSnapshotRankingUseCase).generar(TipoRanking.GENERAL, CLOCK.today());
    }

    @Test
    void conFechaExplicitaRehaceEseDiaYNoElDeHoy() {
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actor)).thenReturn(true);
        LocalDate ayer = CLOCK.today().minusDays(1);

        ResultadoRegeneracion resultado = service().regenerar(new RegenerarSnapshotsCommand(actor, ayer));

        assertThat(resultado.fecha()).isEqualTo(ayer);
        verify(generarSnapshotRankingUseCase).generar(TipoRanking.LEAGUE, ayer);
    }

    /** La prueba que importa: sin ser ADMIN/ALCHEMIST activo no se toca NADA. */
    @Test
    void quienNoEsAdministrativoNoRegeneraNiUnTipo() {
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actor)).thenReturn(false);

        assertThatThrownBy(() -> service().regenerar(new RegenerarSnapshotsCommand(actor, null)))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(generarSnapshotRankingUseCase);
    }

    /**
     * Best-effort, igual que el cron: una tabla incompleta es mejor que una vieja. Si CELL falla,
     * LEAGUE y GENERAL igual quedan rehechos, y el que fallo se informa en vez de tragarse.
     */
    @Test
    void unTipoQueFallaNoTumbaALosOtros() {
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actor)).thenReturn(true);
        // `lenient`: sin esto, Mockito estricto lanza "argument mismatch" en la llamada con LEAGUE
        // --que no esta stubeada-- y el catch best-effort del servicio la cuenta como fallo real.
        lenient().doThrow(new IllegalStateException("sin candidatos"))
                .when(generarSnapshotRankingUseCase).generar(TipoRanking.CELL, CLOCK.today());

        ResultadoRegeneracion resultado = service().regenerar(new RegenerarSnapshotsCommand(actor, null));

        assertThat(resultado.tipos()).containsExactly("LEAGUE", "GENERAL");
        assertThat(resultado.fallados()).hasSize(1);
        assertThat(resultado.fallados().get(0)).contains("CELL").contains("sin candidatos");
        verify(generarSnapshotRankingUseCase).generar(TipoRanking.GENERAL, CLOCK.today());
    }

    @Test
    void elComandoExigeActor() {
        assertThatThrownBy(() -> new RegenerarSnapshotsCommand(null, null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(generarSnapshotRankingUseCase, verificarActorAdministrativoPort);
    }
}
