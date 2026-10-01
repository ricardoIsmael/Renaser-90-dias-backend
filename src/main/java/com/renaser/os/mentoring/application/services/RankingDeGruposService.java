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
import java.util.Collection;
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
        List<AcompanamientoFinder.GrupoAcompanado> grupos = gruposDelMes(cohorteId, mes, ahora);
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

    /**
     * Los grupos que estuvieron EN CURSO en ese mes y tuvieron mentor en él (D-240, E-478).
     *
     * <p>> **Corregido 2026-10-01.** Eran los grupos con mentor vigente HOY para cualquier mes: el
     * ranking de septiembre pedido en octubre mostraba los grupos de octubre (sin muestra de
     * septiembre) y no los que de verdad cerraron septiembre. El dueño quiere ver el ranking final del
     * mes anterior.
     *
     * <p>En el mes en curso el corte es HOY, no el fin de mes: un grupo programado para el día 20 no
     * corrió todavía y no entra. Hoy se mira en la zona de la cohorte (la del primer grupo: los grupos
     * de una cohorte comparten política).
     */
    private List<AcompanamientoFinder.GrupoAcompanado> gruposDelMes(UUID cohorteId, YearMonth mes, Instant ahora) {
        List<AcompanamientoFinder.GrupoAcompanado> delMes = conMentorDeLaCohorte(
                acompanamientoFinder.gruposRegularesEnCursoEntre(mes.atDay(1), mes.atEndOfMonth()), cohorteId);
        if (delMes.isEmpty()) {
            return delMes;
        }
        LocalDate hoy = ahora.atZone(ZoneId.of(delMes.getFirst().zonaHoraria())).toLocalDate();
        boolean mesEnCurso = YearMonth.from(hoy).equals(mes);
        if (!mesEnCurso || hoy.equals(mes.atEndOfMonth())) {
            return delMes;
        }
        return conMentorDeLaCohorte(acompanamientoFinder.gruposRegularesEnCursoEntre(mes.atDay(1), hoy), cohorteId);
    }

    private static List<AcompanamientoFinder.GrupoAcompanado> conMentorDeLaCohorte(
            List<AcompanamientoFinder.GrupoAcompanado> grupos, UUID cohorteId) {
        return grupos.stream()
                .filter(g -> g.cohorteId().equals(cohorteId))
                .filter(g -> g.mentorId() != null)
                .toList();
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

        // El dia del corte todavia esta corriendo: la ultima fecha exigible es la anterior.
        List<ObligacionHabito> obligaciones = obligacionesYaVencidas(ventanas.keySet(),
                inicio.atZone(zona).toLocalDate(), fin.atZone(zona).toLocalDate().minusDays(1));
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
     * Las obligaciones de fechas que ya vencieron. El dia 1 del mes en curso el rango queda vacio
     * ({@code 1 → ultimo del mes anterior}): la respuesta correcta es "nada vencio todavia", no
     * pedirle al finder un rango al reves, que su contrato rechaza con razon (E-469).
     */
    private List<ObligacionHabito> obligacionesYaVencidas(Collection<UUID> alumnos, LocalDate desde,
                                                          LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            return List.of();
        }
        return obligacionesFinder.porParticipantesEntre(alumnos.stream().map(UserId::of).toList(), desde, hasta);
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
