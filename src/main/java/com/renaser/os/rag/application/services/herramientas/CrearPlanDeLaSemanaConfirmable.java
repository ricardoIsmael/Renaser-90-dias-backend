package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ObjetivoSemanal;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ResultadoPlan;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * La escritura de {@code proponer_plan_de_la_semana}, que solo corre cuando la persona confirma la
 * propuesta con el boton (D-153). La propuso {@link ProponerPlanDeLaSemanaHerramienta}.
 *
 * <p>Crea los objetivos con {@code CrearPlanSemanalUseCase} (via {@code rocks.api}), que vuelve a
 * correr todas sus guardas. Ese caso de uso deja como estan los ejes que ya tenian objetivo esa
 * semana: si se guardaron menos de los pedidos, el resultado lo dice.
 */
@Component
public class CrearPlanDeLaSemanaConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(CrearPlanDeLaSemanaConfirmable.class);

    private final PlanificarRocasPort planificarPort;

    public CrearPlanDeLaSemanaConfirmable(PlanificarRocasPort planificarPort) {
        this.planificarPort = planificarPort;
    }

    @Override
    public String herramienta() {
        return ProponerPlanDeLaSemanaHerramienta.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        List<ObjetivoSemanal> objetivos;
        try {
            objetivos = PlanDeRocasJson.leerPlanDeLaSemana(
                    invocacion.argumento(ProponerPlanDeLaSemanaHerramienta.ARGUMENTO_PLAN), planificarPort.ejesValidos());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo("La propuesta guardada no se puede leer. Pidele que lo vuelva a pedir.");
        }
        try {
            return aResultado(objetivos.size(), planificarPort.crearPlanDeLaSemana(actorId, objetivos));
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo crear el plan de la semana confirmado", falla);
            return ResultadoHerramienta.fallo("No pude guardar los objetivos de la semana en este momento.");
        }
    }

    private static ResultadoHerramienta aResultado(int pedidos, ResultadoPlan resultado) {
        return switch (resultado) {
            case ResultadoPlan.Creado creado -> ResultadoHerramienta.exito(textoDeCreados(pedidos, creado.cantidad()));
            case ResultadoPlan.Rechazado rechazado ->
                    ResultadoHerramienta.fallo(TextoDePlanDeRocas.rechazoDeLaSemana(rechazado.motivo()));
        };
    }

    private static String textoDeCreados(int pedidos, int creados) {
        String texto = "Objetivos de la semana guardados: " + creados + ".";
        if (creados < pedidos) {
            return texto + " Los otros ejes ya tenian objetivo esa semana y se dejaron como estaban.";
        }
        return texto;
    }
}
