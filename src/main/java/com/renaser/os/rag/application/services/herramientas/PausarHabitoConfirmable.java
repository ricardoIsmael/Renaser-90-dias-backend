package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * La escritura de {@code proponer_pausar_habito}, que solo corre cuando la persona confirma la
 * propuesta con el boton (fase 2, D-153). La propuso {@link PropuestaDePausarHabito}.
 *
 * <p>Delega en {@code CambiarEstadoHabitoDelPlanUseCase} (via {@code habits.api}), que vuelve a
 * correr todas sus guardas: si entre proponer y confirmar el habito salio del plan, {@code habits}
 * lo rechaza y vuelve un {@code Fallo} legible, nunca la excepcion.
 *
 * <p>Sin condicion de flag, igual que {@link MarcarHabitoCompletadoConfirmable}: una propuesta ya
 * guardada tiene que poder confirmarse aunque el flag se apague despues.
 */
@Component
public class PausarHabitoConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(PausarHabitoConfirmable.class);

    private final GestionarPlanDeHabitosPort planPort;

    public PausarHabitoConfirmable(GestionarPlanDeHabitosPort planPort) {
        this.planPort = planPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDePausarHabito.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<UUID> habitoId = CompletacionDeHabito.registroIdDe(
                invocacion.argumento(PropuestaDePausarHabito.ARGUMENTO_HABITO_ID));
        String accion = invocacion.argumento(PropuestaDePausarHabito.ARGUMENTO_ACCION);
        if (habitoId.isEmpty() || accion == null) {
            return ResultadoHerramienta.fallo("La propuesta no trae un habito valido: no se cambio nada.");
        }
        if (PropuestaDePausarHabito.ACCION_REACTIVAR.equals(accion)) {
            return reactivar(actorId, habitoId.get());
        }
        String hastaTexto = invocacion.argumento(PropuestaDePausarHabito.ARGUMENTO_HASTA);
        Optional<LocalDate> hasta = FechaDelPlan.leer(hastaTexto);
        if (!PropuestaDePausarHabito.ACCION_PAUSAR.equals(accion)
                || (!FechaDelPlan.ausente(hastaTexto) && hasta.isEmpty())) {
            return ResultadoHerramienta.fallo("La propuesta no trae un cambio valido: no se cambio nada.");
        }
        return pausar(actorId, habitoId.get(), hasta.orElse(null));
    }

    private ResultadoHerramienta pausar(UserId actorId, UUID habitoId, LocalDate hasta) {
        try {
            planPort.pausar(actorId, habitoId, hasta);
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito(hasta == null ? "Habito pausado, sin fecha de fin."
                : "Habito pausado hasta el " + FechaDelPlan.legible(hasta) + " inclusive.");
    }

    private ResultadoHerramienta reactivar(UserId actorId, UUID habitoId) {
        try {
            planPort.reactivar(actorId, habitoId);
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Habito reactivado en su plan.");
    }

    /** El detalle va al log; a la persona, un motivo que se pueda leer. */
    private static ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo cambiar el estado del habito: {}", PropuestaDePausarHabito.NOMBRE,
                rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case IllegalStateException obligatorio -> "Ese habito es obligatorio: no se puede pausar.";
            case NoSuchElementException fueraDelPlan -> "Ese habito ya no esta en su plan: no se cambio nada.";
            case NotAuthorizedException suspendida -> "La cuenta esta suspendida: no se cambio nada.";
            default -> "No se pudo cambiar ese habito en este momento.";
        });
    }
}
