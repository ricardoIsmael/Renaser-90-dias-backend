package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.ResultadoCierre;
import com.renaser.os.rag.application.services.herramientas.CierreDeSemanaJson.CierreGuardado;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * La escritura de {@code proponer_cerrar_semana}, que solo corre cuando la persona confirma la
 * propuesta con el boton (D-153). La propuso {@link ProponerCerrarSemanaHerramienta}.
 *
 * <p>Cierra la semana GUARDADA en la propuesta con {@code CerrarSemanaUseCase} (via
 * {@code rocks.api.CierreDeSemanaPort}), que vuelve a correr todas sus guardas. Si entre proponer y
 * confirmar algun eje se cerro en la app, {@code rocks} no lo pisa y no escribe ninguno.
 */
@Component
public class CerrarSemanaConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(CerrarSemanaConfirmable.class);

    private final CerrarSemanaDeRocasPort cierrePort;

    public CerrarSemanaConfirmable(CerrarSemanaDeRocasPort cierrePort) {
        this.cierrePort = cierrePort;
    }

    @Override
    public String herramienta() {
        return ProponerCerrarSemanaHerramienta.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        CierreGuardado cierre;
        try {
            cierre = CierreDeSemanaJson.leerGuardado(
                    invocacion.argumento(ProponerCerrarSemanaHerramienta.ARGUMENTO_CIERRE), cierrePort.reglas());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo("La propuesta guardada no se puede leer. Pidele que lo vuelva a pedir.");
        }
        try {
            return switch (cierrePort.cerrarSemana(actorId, cierre.semana(), cierre.revisiones())) {
                case ResultadoCierre.Cerrada cerrada ->
                        ResultadoHerramienta.exito(TextoDeCierreDeSemana.cerrada(cierre.semana(), cerrada.ejes()));
                case ResultadoCierre.Rechazado rechazado ->
                        ResultadoHerramienta.fallo(TextoDeCierreDeSemana.rechazo(rechazado.motivo()));
            };
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo cerrar la semana confirmada", falla);
            return ResultadoHerramienta.fallo("No pude guardar el cierre de la semana en este momento.");
        }
    }
}
