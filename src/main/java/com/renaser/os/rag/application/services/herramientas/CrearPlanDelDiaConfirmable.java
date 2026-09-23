package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ResultadoPlan;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanDelDia;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * La escritura de {@code proponer_plan_del_dia}, que solo corre cuando la persona confirma la
 * propuesta con el boton (D-153). La propuso {@link ProponerPlanDelDiaHerramienta}.
 *
 * <p>Crea el plan con {@code CrearPlanDiarioUseCase} (via {@code rocks.api}), que vuelve a correr
 * todas sus guardas: si entre proponer y confirmar se cerro la fecha o el dia empezo, {@code rocks}
 * lo rechaza y vuelve un {@code Fallo} legible. Sin flag, a diferencia de la herramienta: una
 * propuesta ya guardada tiene que poder confirmarse aunque despues se apague el flag.
 */
@Component
public class CrearPlanDelDiaConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(CrearPlanDelDiaConfirmable.class);

    private final PlanificarRocasPort planificarPort;

    public CrearPlanDelDiaConfirmable(PlanificarRocasPort planificarPort) {
        this.planificarPort = planificarPort;
    }

    @Override
    public String herramienta() {
        return ProponerPlanDelDiaHerramienta.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        PlanDelDia plan;
        try {
            plan = PlanDeRocasJson.leerPlanDelDia(invocacion.argumento(ProponerPlanDelDiaHerramienta.ARGUMENTO_PLAN),
                    planificarPort.ejesValidos());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo("La propuesta guardada no se puede leer. Pidele que lo vuelva a pedir.");
        }
        if (plan.fecha() == null) {
            return ResultadoHerramienta.fallo("La propuesta guardada no tiene fecha. Pidele que lo vuelva a pedir.");
        }
        try {
            return aResultado(plan, planificarPort.crearPlanDelDia(actorId, plan.fecha(), plan.acciones()));
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo crear el plan del dia confirmado", falla);
            return ResultadoHerramienta.fallo("No pude guardar el plan en este momento.");
        }
    }

    private static ResultadoHerramienta aResultado(PlanDelDia plan, ResultadoPlan resultado) {
        return switch (resultado) {
            case ResultadoPlan.Creado creado -> ResultadoHerramienta.exito("Plan del "
                    + TextoDePlanDeRocas.diaYFecha(plan.fecha()) + " guardado con " + creado.cantidad()
                    + " accion(es).");
            case ResultadoPlan.Rechazado rechazado ->
                    ResultadoHerramienta.fallo(TextoDePlanDeRocas.rechazoDelDia(rechazado.motivo()));
        };
    }
}
