package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase;
import com.renaser.os.points.application.ports.out.puntaje.VerificarActorAdministrativoPort;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rehace el corte del ranking a pedido (D-129). Clase aparte y no un metodo mas de
 * {@code RankingService}, que ya carga siete dependencias: esto es una operacion de operador, con
 * su propio guard, y no tiene nada que ver con consultar ni con generar.
 *
 * <p>Los tipos que se generan son <b>los mismos tres del cron</b> y en el mismo orden. Si mañana
 * entra COHORT, entra en los dos lugares — por eso la lista vive en una constante con nombre y no
 * repartida.
 */
@Service
public class RegeneracionRankingService implements RegenerarSnapshotsRankingUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegeneracionRankingService.class);

    /** Los mismos de {@code SnapshotRankingScheduler}. COHORT sigue afuera: le falta el dato de cohorte. */
    static final List<TipoRanking> TIPOS = List.of(TipoRanking.LEAGUE, TipoRanking.CELL, TipoRanking.GENERAL);

    private final GenerarSnapshotRankingUseCase generarSnapshotRankingUseCase;
    private final VerificarActorAdministrativoPort verificarActorAdministrativoPort;
    private final Clock clock;

    RegeneracionRankingService(GenerarSnapshotRankingUseCase generarSnapshotRankingUseCase,
                                VerificarActorAdministrativoPort verificarActorAdministrativoPort, Clock clock) {
        this.generarSnapshotRankingUseCase = generarSnapshotRankingUseCase;
        this.verificarActorAdministrativoPort = verificarActorAdministrativoPort;
        this.clock = clock;
    }

    /**
     * <p>Sin {@code @Transactional} a proposito: cada tipo se genera en su propia transaccion, asi
     * un fallo en el tercero no tira abajo los dos que ya quedaron bien — el mismo criterio
     * best-effort del cron, y por el mismo motivo (una tabla vieja es peor que una incompleta).
     */
    @Override
    public ResultadoRegeneracion regenerar(RegenerarSnapshotsCommand command) {
        if (!verificarActorAdministrativoPort.esAdministrativoActivo(command.actorId())) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST activos regeneran el corte del ranking");
        }
        LocalDate fecha = command.fecha() != null ? command.fecha() : clock.today();

        List<String> generados = new ArrayList<>(TIPOS.size());
        List<String> fallados = new ArrayList<>();
        for (TipoRanking tipo : TIPOS) {
            try {
                generarSnapshotRankingUseCase.generar(tipo, fecha);
                generados.add(tipo.name());
            } catch (RuntimeException e) {
                fallados.add(tipo.name() + ": " + e.getMessage());
                log.error("[points.RegeneracionRanking] fallo regenerando {} para {}: {}", tipo, fecha,
                        e.getMessage(), e);
            }
        }
        log.info("[points.RegeneracionRanking] corte del {} regenerado por {}: {} ok, {} con error", fecha,
                command.actorId(), generados.size(), fallados.size());
        return new ResultadoRegeneracion(fecha, generados, fallados);
    }
}
