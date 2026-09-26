package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rocks.api.RocasDelAprendizFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Implementa {@link ConsultarRocasDelAprendizPort} delegando en {@code rocks.api} (D-41): {@code rag}
 * nunca consulta las tablas de rocas. Es una traduccion y nada mas; la zona, la ventana nocturna y
 * el bloqueo Pareto los resuelve {@code rocks}.
 */
@Component
class ConsultarRocasDelAprendizAdapter implements ConsultarRocasDelAprendizPort {

    private final RocasDelAprendizFinder finder;

    ConsultarRocasDelAprendizAdapter(RocasDelAprendizFinder finder) {
        this.finder = finder;
    }

    @Override
    public RocasDelDia deHoy(UserId aprendizId) {
        return aRocasDelDia(finder.deHoy(aprendizId));
    }

    @Override
    public RocasDelDia deManana(UserId aprendizId) {
        return aRocasDelDia(finder.deManana(aprendizId));
    }

    @Override
    public RocasDeLaSemana deLaSemana(UserId aprendizId) {
        RocasDelAprendizFinder.RocasDeLaSemana semana = finder.deLaSemana(aprendizId);
        List<RocaDeLaSemana> rocas = semana.rocas().stream()
                .map(r -> new RocaDeLaSemana(r.eje(), r.titulo(), r.obstaculo(), r.contingencia(), r.editable(),
                        r.revisada(), r.autoevaluacionInicio()))
                .toList();
        return new RocasDeLaSemana(semana.numeroSemana(), semana.inicio(), semana.fin(), rocas);
    }

    @Override
    public List<ObjetivoDelMes> delMes(UserId aprendizId) {
        return finder.delMes(aprendizId).stream()
                .map(o -> new ObjetivoDelMes(o.eje(), o.numeroMes(), o.diaDeCierre(), o.tituloPropio(), o.cifra(),
                        o.unidad(), o.unidadAdelante(), o.metaAlcanzada(), o.motivoSinCifra()))
                .toList();
    }

    @Override
    public ProgresoDeLaSemana progresoDeLaSemana(UserId aprendizId) {
        RocasDelAprendizFinder.ProgresoDeLaSemana p = finder.progresoDeLaSemana(aprendizId);
        List<DiaDeLaSemana> dias = p.dias().stream()
                .map(d -> new DiaDeLaSemana(d.fecha(), d.completadas(), d.total(), d.esHoy())).toList();
        List<BalanceDelEje> porEje = p.porEje().stream()
                .map(b -> new BalanceDelEje(b.eje(), b.planificadas(), b.completadas())).toList();
        return new ProgresoDeLaSemana(p.numeroSemana(), p.inicio(), p.fin(), p.hoy(), p.progresoSemanalPct(), dias,
                p.ritmo(), p.diasCompletadosUltimos7(), porEje, aPlanDeManana(p.planificacion()),
                p.planificacionBloqueada());
    }

    @Override
    public List<ObjetivoDeNoventaDias> objetivosDeNoventaDias(UserId aprendizId) {
        return finder.objetivosDeNoventaDias(aprendizId).stream()
                .map(o -> new ObjetivoDeNoventaDias(o.eje(), o.objetivo(), o.meta(), o.avance(), o.unidad(),
                        o.unidadAdelante(), o.lineaBase(), o.porcentaje()))
                .toList();
    }

    @Override
    public Optional<CierreDeLaSemanaAnterior> cierreDeLaSemanaAnterior(UserId aprendizId) {
        return finder.cierreDeLaSemanaAnterior(aprendizId).map(c -> new CierreDeLaSemanaAnterior(c.numeroSemana(),
                c.ejes().stream().map(e -> new CierreDelEje(e.eje(), e.titulo(), e.autoevaluacionInicio(),
                        e.autoevaluacionFin(), e.bloqueoPrincipal(), e.correccion())).toList()));
    }

    private static PlanDeManana aPlanDeManana(RocasDelAprendizFinder.PlanificacionDeManana plan) {
        return new PlanDeManana(plan.planCreado(), plan.rocasPlanificadas(), plan.ventanaAbierta(), plan.ventanaAbreA(),
                plan.puedeCrearPlan());
    }

    private static RocasDelDia aRocasDelDia(RocasDelAprendizFinder.RocasDelDia dia) {
        List<RocaDelDia> rocas = dia.rocas().stream()
                .map(r -> new RocaDelDia(r.id(), r.eje(), r.posicion(), r.color(), r.titulo(), r.horaInicio(), r.horaFin(),
                        r.completada(), r.bloqueadaPorPareto()))
                .toList();
        return new RocasDelDia(dia.fecha(), rocas, aPlanDeManana(dia.planificacion()));
    }
}
