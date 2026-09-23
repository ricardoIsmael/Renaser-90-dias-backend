package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Leer el plan del aprendiz antes de proponer, con la traduccion de errores de siempre: una
 * herramienta no lanza, devuelve un {@code Fallo} legible (contrato de {@link HerramientaAgente}).
 * Compartido por las dos herramientas del plan de habitos para que digan lo mismo.
 */
final class LecturaDelPlan {

    private static final Logger log = LoggerFactory.getLogger(LecturaDelPlan.class);

    private LecturaDelPlan() {
    }

    static ResultadoHerramienta conPlan(GestionarPlanDeHabitosPort planPort, UserId actorId,
                                        Function<PlanDelAprendiz, ResultadoHerramienta> siguiente) {
        PlanDelAprendiz plan;
        try {
            plan = planPort.planDe(actorId);
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException suspendida) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida: no puedo cambiar su plan de habitos.");
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo leer el plan de habitos para proponer un cambio", falla);
            return ResultadoHerramienta.fallo("No pude consultar su plan de habitos en este momento.");
        }
        return siguiente.apply(plan);
    }
}
