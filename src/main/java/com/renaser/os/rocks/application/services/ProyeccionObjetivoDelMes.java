package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDelMesDelEje;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase.MesDelPlan;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase.PlanMensualDelEje;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;

import java.util.Optional;

/**
 * Traduce el mes EN CURSO de un {@link PlanMensualDelEje} a la proyeccion publica de
 * {@code rocks.api} (2026-09-23). Sin reglas propias: que cifra se muestra
 * ({@link ObjetivoDelMes.ConCifra#cifraQueSeMuestra()}) y que lo guardado por la persona manda
 * sobre lo calculado ({@link MesDelPlan#fueEditado()}) ya lo decide el modulo.
 */
final class ProyeccionObjetivoDelMes {

    private ProyeccionObjetivoDelMes() {
    }

    /** Vacio si el plan no marca ningun mes en curso (programa terminado o sin arrancar). */
    static Optional<ObjetivoDelMesDelEje> delMesEnCurso(PlanMensualDelEje plan) {
        return plan.meses().stream().filter(MesDelPlan::enCurso).findFirst().map(mes -> proyectar(plan, mes));
    }

    private static ObjetivoDelMesDelEje proyectar(PlanMensualDelEje plan, MesDelPlan mes) {
        if (mes.fueEditado()) {
            return delEditado(plan, mes, mes.editado());
        }
        String eje = plan.eje().name();
        return switch (mes.calculado()) {
            case ObjetivoDelMes.ConCifra conCifra -> new ObjetivoDelMesDelEje(eje, mes.numeroMes(),
                    mes.diaDeCierre(), null, conCifra.cifraQueSeMuestra(), plan.unidad(), plan.adelante(), false,
                    null);
            case ObjetivoDelMes.YaAlcanzado alcanzado -> new ObjetivoDelMesDelEje(eje, mes.numeroMes(),
                    mes.diaDeCierre(), null, alcanzado.meta(), plan.unidad(), plan.adelante(), true, null);
            case ObjetivoDelMes.SinCifra sinCifra -> new ObjetivoDelMesDelEje(eje, mes.numeroMes(),
                    mes.diaDeCierre(), null, null, null, false, false, sinCifra.motivo().name());
        };
    }

    /** Lo que la persona escribio manda. Su meta es opcional: un tramo puede ser cualitativo. */
    private static ObjetivoDelMesDelEje delEditado(PlanMensualDelEje plan, MesDelPlan mes, RocaMensual editado) {
        boolean conMeta = editado.tieneMeta();
        return new ObjetivoDelMesDelEje(plan.eje().name(), mes.numeroMes(), mes.diaDeCierre(), editado.titulo(),
                conMeta ? editado.meta().objetivo() : null, conMeta ? editado.meta().unidad() : null,
                plan.adelante(), false, null);
    }
}
