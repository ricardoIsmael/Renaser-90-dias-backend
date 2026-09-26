package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.AgregarAccionAlPlanPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.services.herramientas.ArgumentosDeAjusteDeRocas.AccionPedida;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * La escritura de {@code proponer_agregar_accion} (D-177), que solo corre cuando la persona confirma
 * (D-153). {@code rocks} vuelve a correr todas las guardas: si entre proponer y confirmar ese dia paso a
 * ser hoy, o el eje se lleno desde la app, vuelve el rechazo con su motivo. Sin flag, como las demas
 * confirmables: una propuesta ya guardada se puede confirmar aunque despues se apague.
 */
@Component
public class AgregarAccionConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(AgregarAccionConfirmable.class);

    private final AgregarAccionAlPlanPort agregarPort;
    private final PlanificarRocasPort planificarPort;

    public AgregarAccionConfirmable(AgregarAccionAlPlanPort agregarPort, PlanificarRocasPort planificarPort) {
        this.agregarPort = agregarPort;
        this.planificarPort = planificarPort;
    }

    @Override
    public String herramienta() {
        return ProponerAgregarAccionHerramienta.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        AccionPedida pedida;
        try {
            pedida = ArgumentosDeAjusteDeRocas.leerAccion(invocacion, planificarPort.ejesValidos());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo("La propuesta guardada no se puede leer. Pidele que lo vuelva a pedir.");
        }
        if (pedida.fecha() == null) {
            return ResultadoHerramienta.fallo("La propuesta guardada no tiene fecha. Pidele que lo vuelva a pedir.");
        }
        try {
            return switch (agregarPort.agregar(actorId, pedida.fecha(), pedida.accion())) {
                case AgregarAccionAlPlanPort.Resultado.Agregada agregada ->
                        ResultadoHerramienta.exito(TextoDeAjustesDeRocas.accionAgregada(pedida.fecha(), agregada));
                case AgregarAccionAlPlanPort.Resultado.Rechazado rechazado ->
                        ResultadoHerramienta.fallo(TextoDeAjustesDeRocas.rechazoDeAccion(rechazado.motivo()));
            };
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo agregar la accion confirmada", falla);
            return ResultadoHerramienta.fallo("No pude guardar la accion en este momento.");
        }
    }
}
