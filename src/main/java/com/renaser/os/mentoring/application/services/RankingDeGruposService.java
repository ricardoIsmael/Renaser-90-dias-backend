package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoBasico;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAprendiz;
import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase;
import com.renaser.os.points.api.CalculoCumplimientoPort;
import com.renaser.os.points.api.EvaluacionCumplimiento;
import com.renaser.os.points.api.ObligacionEvidencia;
import com.renaser.os.points.api.VentanaEvaluacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranking mensual entre grupos, con el MISMO motor que la evaluación del mentor.
 *
 * <p>La diferencia con la nota del mentor es una sola: acá no se filtra por quién acompañaba.
 * Se mide al grupo durante todo el mes, así que un grupo puede puntuar distinto de un mentor que
 * estuvo solo la mitad — y eso es correcto, no una inconsistencia (plan.md §8).
 *
 * <p>Se calcula al leer y no se cachea. Con las decenas de grupos de una cohorte alcanza; cuando
 * no alcance, el lugar del snapshot ya está preparado en {@code ranking_celulas} (V46), que es de
 * {@code points}: este módulo calcularía y aquel guardaría, sin que la tabla cambie de dueño.
 */
@Service
public class RankingDeGruposService implements ConsultarRankingDeGruposUseCase {

    private final AcompanamientoFinder acompanamientoFinder;
    private final ObligacionesHistoricasFinder obligacionesFinder;
    private final EntregasPorRegistroFinder entregasFinder;
    private final CalculoCumplimientoPort calculoCumplimiento;
    private final Clock clock;

    public RankingDeGruposService(AcompanamientoFinder acompanamientoFinder,
                                   ObligacionesHistoricasFinder obligacionesFinder,
                                   EntregasPorRegistroFinder entregasFinder,
                                   CalculoCumplimientoPort calculoCumplimiento, Clock clock) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.obligacionesFinder = obligacionesFinder;
        this.entregasFinder = entregasFinder;
        this.calculoCumplimiento = calculoCumplimiento;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public RankingDeGrupos ranking(UserId actorId, UUID cohorteId, YearMonth mes) {
        Instant ahora = clock.now();

        // Solo grupos regulares con mentor: la recepción queda fuera del ranking (P-08), porque
        // es transitoria y comparar su cumplimiento con el de un grupo estable no dice nada.
        List<AcompanamientoFinder.GrupoAcompanado> grupos =
                acompanamientoFinder.gruposConMentorVigente(ahora).stream()
                        .filter(g -> g.cohorteId().equals(cohorteId))
                        .toList();
        if (grupos.isEmpty()) {
            return new RankingDeGrupos(cohorteId, mes.toString(), null, null, List.of());
        }

        ZoneId zona = ZoneId.of(grupos.getFirst().zonaHoraria());
        Instant inicio = mes.atDay(1).atStartOfDay(zona).toInstant();
        // Mismo corte que la evaluacion del mentor: lo que todavia no vencio no se cuenta como
        // incumplido. Sin esto, el ranking de un mes en curso castigaria a todos los grupos por
        // los dias que faltan, y el orden cambiaria solo por cuantos dias quedan del mes.
        Instant finDelMes = mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant();
        Instant fin = ahora.isBefore(finDelMes) ? ahora : finDelMes;
        if (!fin.isAfter(inicio)) {
            return new RankingDeGrupos(cohorteId, mes.toString(), zona.getId(), null, List.of());
        }

        List<Calculado> calculados = new ArrayList<>();
        String version = null;
        for (AcompanamientoFinder.GrupoAcompanado grupo : grupos) {
            EvaluacionCumplimiento evaluacion = evaluarGrupo(grupo.grupoId(), inicio, fin, zona);
            version = evaluacion.versionFormula();
            calculados.add(new Calculado(grupo.grupoId(), grupo.nombre(), evaluacion));
        }

        return new RankingDeGrupos(cohorteId, mes.toString(), zona.getId(), version, ordenar(calculados));
    }

    private EvaluacionCumplimiento evaluarGrupo(UUID grupoId, Instant inicio, Instant fin, ZoneId zona) {
        Map<UUID, List<VentanaEvaluacion>> ventanas = new LinkedHashMap<>();
        for (TramoDeAprendiz tramo : acompanamientoFinder.tramosDeAprendices(grupoId, inicio, fin)) {
            ventanas.computeIfAbsent(tramo.aprendizId().value(), a -> new ArrayList<>())
                    .add(new VentanaEvaluacion(tramo.desde(), tramo.hasta() == null ? fin : tramo.hasta()));
        }
        if (ventanas.isEmpty()) {
            return calculoCumplimiento.evaluar(List.of(), Map.of());
        }

        LocalDate desde = inicio.atZone(zona).toLocalDate();
        // El dia del corte todavia esta corriendo: la ultima fecha exigible es la anterior.
        LocalDate hasta = fin.atZone(zona).toLocalDate().minusDays(1);
        List<ObligacionHabito> obligaciones = obligacionesFinder.porParticipantesEntre(
                ventanas.keySet().stream().map(UserId::of).toList(), desde, hasta);
        Map<UUID, EntregaDeEvidencia> entregas =
                entregasFinder.porRegistros(obligaciones.stream().map(ObligacionHabito::registroId).toList());

        return calculoCumplimiento.evaluar(obligaciones.stream()
                .filter(ObligacionHabito::exigible)
                .map(o -> new ObligacionEvidencia(o.registroId(), o.participanteId().value(),
                        o.fecha().plusDays(1).atStartOfDay(zona).toInstant().minusMillis(1),
                        entregas.get(o.registroId()) == null ? null : entregas.get(o.registroId()).primeraEntregaEn(),
                        entregas.get(o.registroId()) != null && entregas.get(o.registroId()).verificada()))
                .toList(), ventanas);
    }

    /**
     * Ordena por el valor SIN redondear y comparte posición en los empates: 1, 2, 2, 4. Redondear
     * antes de ordenar juntaría en un empate a dos grupos que no empataron, y saltar la posición
     * es lo que mantiene la cuenta coherente con la cantidad de grupos.
     *
     * <p>Los grupos sin muestra van al final, sin calificación. No son los peores: son los que no
     * se pueden medir.
     */
    private static List<FilaDeGrupo> ordenar(List<Calculado> calculados) {
        List<Calculado> ordenados = calculados.stream()
                .sorted(Comparator
                        .comparing((Calculado c) -> c.evaluacion().porcentaje() == null)
                        .thenComparing(c -> c.evaluacion().porcentaje(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Calculado::nombre))
                .toList();

        List<FilaDeGrupo> filas = new ArrayList<>();
        BigDecimal anterior = null;
        int posicion = 0;
        for (int i = 0; i < ordenados.size(); i++) {
            Calculado c = ordenados.get(i);
            BigDecimal valor = c.evaluacion().porcentaje();
            boolean empata = anterior != null && valor != null && valor.compareTo(anterior) == 0;
            posicion = empata ? posicion : i + 1;
            anterior = valor;
            filas.add(new FilaDeGrupo(posicion, c.grupoId(), c.nombre(), valor,
                    c.evaluacion().aprendicesEvaluados(), c.evaluacion().entregadas(),
                    c.evaluacion().esperadas(), c.evaluacion().estado().name()));
        }
        return filas;
    }

    private record Calculado(UUID grupoId, String nombre, EvaluacionCumplimiento evaluacion) {
    }
}
