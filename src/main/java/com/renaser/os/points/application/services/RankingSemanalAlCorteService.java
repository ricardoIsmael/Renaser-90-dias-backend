package com.renaser.os.points.application.services;

import com.renaser.os.points.api.MedicionAcumuladaFinder;
import com.renaser.os.points.api.PorcentajeRocasFinder;
import com.renaser.os.points.api.RankingSemanalFinder;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort.CandidatoRanking;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Coherencia, Kilómetros y Gimnasio de UNA semana, sin guardar foto (D-367). Los mismos candidatos que la foto diaria
 * ({@link LoadRankingCandidatosPort}), el mismo filtro de cuentas cerradas que {@link RankingGeneralAlCorteService} y las
 * mismas consultas en lote (D-43) que {@code RankingService} usa para CELL, KILOMETROS y GIMNASIO; no hay consultas nuevas.
 *
 * <ul>
 *   <li><b>Coherencia</b>: {@code PorcentajeRocasFinder} ya es una ventana de 7 días que termina en el corte; con el corte en el
 *   domingo da justo la semana. Sin clave = no planificó su semana = sin dato (cero, no sale en el podio).</li>
 *   <li><b>Kilómetros y Gimnasio</b>: los finders dan el acumulado desde el Día 1; la semana es el acumulado al domingo menos el
 *   acumulado al domingo anterior (siete días antes). Nunca sale negativo.</li>
 * </ul>
 */
@Service
public class RankingSemanalAlCorteService implements RankingSemanalFinder {

    private static final Comparator<PuestoDeLaSemana> DE_MAYOR_A_MENOR =
            Comparator.comparing(PuestoDeLaSemana::valor).reversed()
                    .thenComparing(p -> p.participanteId().value());

    private final LoadRankingCandidatosPort candidatos;
    private final PorcentajeRocasFinder rocas;
    private final MedicionAcumuladaFinder mediciones;
    private final CuentasCerradasFinder cuentasCerradas;

    RankingSemanalAlCorteService(LoadRankingCandidatosPort candidatos, PorcentajeRocasFinder rocas,
                                 MedicionAcumuladaFinder mediciones, CuentasCerradasFinder cuentasCerradas) {
        this.candidatos = candidatos;
        this.rocas = rocas;
        this.mediciones = mediciones;
        this.cuentasCerradas = cuentasCerradas;
    }

    @Override
    public List<PuestoDeLaSemana> coherencia(LocalDate domingo, Formacion formacion) {
        return armar(formacion, ids -> rocas.porcentajePorParticipante(ids, domingo));
    }

    @Override
    public List<PuestoDeLaSemana> kilometros(LocalDate domingo, Formacion formacion) {
        return armar(formacion, ids -> deLaSemana(mediciones.kilometrosAcumulados(ids, domingo),
                mediciones.kilometrosAcumulados(ids, domingo.minusDays(7))));
    }

    @Override
    public List<PuestoDeLaSemana> gimnasio(LocalDate domingo, Formacion formacion) {
        return armar(formacion, ids -> deLaSemana(enDecimales(mediciones.diasDeGimnasio(ids, domingo)),
                enDecimales(mediciones.diasDeGimnasio(ids, domingo.minusDays(7)))));
    }

    private List<PuestoDeLaSemana> armar(Formacion formacion, Function<List<UserId>, Map<UserId, BigDecimal>> valores) {
        List<CandidatoRanking> padron = candidatos.aprendicesActivosConPuntaje().stream()
                .filter(c -> c.formacion() == formacion).toList();
        if (padron.isEmpty()) {
            return List.of();
        }
        List<UserId> ids = padron.stream().map(CandidatoRanking::participanteId).toList();
        Set<UserId> cerradas = cuentasCerradas.cerradasEntre(ids);
        Map<UserId, BigDecimal> medidos = valores.apply(ids);
        return padron.stream()
                .filter(c -> !cerradas.contains(c.participanteId()))
                .map(c -> new PuestoDeLaSemana(c.participanteId(), c.fullName(),
                        medidos.getOrDefault(c.participanteId(), BigDecimal.ZERO)))
                .sorted(DE_MAYOR_A_MENOR)
                .toList();
    }

    /** Lo de la semana: el acumulado de ahora menos el de hace siete días, sin bajar de cero. */
    private static Map<UserId, BigDecimal> deLaSemana(Map<UserId, BigDecimal> alDomingo, Map<UserId, BigDecimal> alDomingoAnterior) {
        Map<UserId, BigDecimal> semana = new java.util.HashMap<>();
        alDomingo.forEach((id, total) -> {
            BigDecimal diferencia = total.subtract(alDomingoAnterior.getOrDefault(id, BigDecimal.ZERO));
            semana.put(id, diferencia.signum() < 0 ? BigDecimal.ZERO : diferencia);
        });
        return semana;
    }

    private static Map<UserId, BigDecimal> enDecimales(Map<UserId, Integer> dias) {
        Map<UserId, BigDecimal> valores = new java.util.HashMap<>();
        dias.forEach((id, n) -> valores.put(id, BigDecimal.valueOf(n)));
        return valores;
    }
}
