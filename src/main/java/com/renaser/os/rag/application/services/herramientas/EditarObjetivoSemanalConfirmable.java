package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.services.herramientas.ArgumentosDeAjusteDeRocas.EdicionGuardada;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * La escritura de {@code proponer_editar_objetivo_semanal} (D-177), que solo corre cuando la persona
 * confirma (D-153). {@code rocks} vuelve a mirar la ventana: si cerro entre proponer y confirmar, vuelve
 * el rechazo con el motivo real y lo que si se puede.
 */
@Component
public class EditarObjetivoSemanalConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(EditarObjetivoSemanalConfirmable.class);

    private final EditarObjetivoSemanalPort editarPort;
    private final PlanificarRocasPort planificarPort;

    public EditarObjetivoSemanalConfirmable(EditarObjetivoSemanalPort editarPort, PlanificarRocasPort planificarPort) {
        this.editarPort = editarPort;
        this.planificarPort = planificarPort;
    }

    @Override
    public String herramienta() {
        return ProponerEditarObjetivoSemanalHerramienta.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        EdicionGuardada edicion;
        try {
            edicion = ArgumentosDeAjusteDeRocas.leerEdicionGuardada(invocacion, planificarPort.ejesValidos());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo("La propuesta guardada no se puede leer. Pidele que lo vuelva a pedir.");
        }
        try {
            return switch (editarPort.editar(actorId, edicion.semana(), edicion.eje(), edicion.cambio())) {
                case EditarObjetivoSemanalPort.Resultado.Editado editado -> ResultadoHerramienta.exito(
                        "Objetivo de " + TextoDePlanDeRocas.nombreDelEje(editado.eje()) + " de la semana "
                                + edicion.semana() + " actualizado.");
                case EditarObjetivoSemanalPort.Resultado.Rechazado rechazado -> ResultadoHerramienta.fallo(
                        TextoDeAjustesDeRocas.rechazoDeEdicion(rechazado.motivo(), editarPort.ventanaDeEdicion()));
            };
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo editar el objetivo semanal confirmado", falla);
            return ResultadoHerramienta.fallo("No pude guardar el cambio en este momento.");
        }
    }
}
