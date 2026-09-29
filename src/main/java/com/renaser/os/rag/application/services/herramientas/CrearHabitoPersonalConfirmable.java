package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.CrearHabitoPersonalPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.habitopersonal.HabitoPersonalPedido;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * La escritura de {@code proponer_crear_habito_personal}, que solo corre cuando la persona toca
 * "Confirmar" en la tarjeta (D-229). La propuso {@link PropuestaDeCrearHabitoPersonal}.
 *
 * <p>Vuelve a leer los argumentos guardados con las mismas reglas, vuelve a mirar que no exista ya
 * un habito con ese nombre (pudo crearlo a mano entre proponer y confirmar, o confirmar otra
 * tarjeta igual) y delega en {@code habits}, que corre sus propias guardas: sin programa o con la
 * cuenta suspendida, no crea nada. Un rechazo vuelve como {@code Fallo} legible, nunca como
 * excepcion.
 *
 * <p>Sin condicion de flag, igual que {@link PausarHabitoConfirmable}: una propuesta ya guardada
 * tiene que poder confirmarse aunque el flag se apague despues.
 */
@Component
public class CrearHabitoPersonalConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(CrearHabitoPersonalConfirmable.class);

    private final GestionarPlanDeHabitosPort planPort;
    private final CrearHabitoPersonalPort crearPort;

    public CrearHabitoPersonalConfirmable(GestionarPlanDeHabitosPort planPort, CrearHabitoPersonalPort crearPort) {
        this.planPort = planPort;
        this.crearPort = crearPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeCrearHabitoPersonal.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        HabitoPersonalPedido pedido;
        try {
            pedido = ArgumentosDeHabitoPersonal.leer(invocacion, crearPort.ultimaHoraDeInicio());
        } catch (PropuestaImposibleException invalida) {
            return ResultadoHerramienta.fallo("La propuesta no trae un hábito válido: no se creó nada.");
        }
        return LecturaDelPlan.conPlan(planPort, actorId, plan -> crearSiNoLoTiene(actorId, plan, pedido));
    }

    private ResultadoHerramienta crearSiNoLoTiene(UserId actorId, PlanDelAprendiz plan, HabitoPersonalPedido pedido) {
        if (PropuestaDeCrearHabitoPersonal.mismoNombre(plan, pedido).isPresent()) {
            return ResultadoHerramienta.fallo("Ya tienes un hábito llamado '" + pedido.nombre()
                    + "': no se creó otro.");
        }
        try {
            crearPort.crear(actorId, pedido);
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Hábito '" + pedido.nombre() + "' creado en " + pedido.dimension().etiqueta()
                + ", a las " + ArgumentosDeHorario.texto(pedido.hora()) + ", " + pedido.diasLegibles()
                + ". Ya lo ves en Training.");
    }

    /** El detalle va al log; a la persona, un motivo que se pueda leer. */
    private static ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo crear el habito: {}", PropuestaDeCrearHabitoPersonal.NOMBRE, rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case NotAuthorizedException suspendida -> "La cuenta está suspendida: no se creó nada.";
            case NoSuchElementException sinPrograma -> "No encontré un programa activo para esta cuenta: no se creó nada.";
            case IllegalArgumentException invalido -> "Los datos del hábito no son válidos: no se creó nada.";
            default -> "No se pudo crear el hábito en este momento.";
        });
    }
}
