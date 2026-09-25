package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rocks.api.RocasDelAprendizFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

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
                        r.revisada()))
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

    private static RocasDelDia aRocasDelDia(RocasDelAprendizFinder.RocasDelDia dia) {
        List<RocaDelDia> rocas = dia.rocas().stream()
                .map(r -> new RocaDelDia(r.eje(), r.posicion(), r.color(), r.titulo(), r.horaInicio(), r.horaFin(),
                        r.completada(), r.bloqueadaPorPareto()))
                .toList();
        RocasDelAprendizFinder.PlanificacionDeManana plan = dia.planificacion();
        return new RocasDelDia(dia.fecha(), rocas, new PlanDeManana(plan.planCreado(), plan.rocasPlanificadas(),
                plan.ventanaAbierta(), plan.ventanaAbreA(), plan.puedeCrearPlan()));
    }
}
