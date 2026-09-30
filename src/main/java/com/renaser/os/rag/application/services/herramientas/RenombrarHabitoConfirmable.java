package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * La escritura de {@code proponer_renombrar_habito}, que solo corre cuando la persona toca Confirmar
 * (D-236). La propuso {@link PropuestaDeRenombrarHabito}.
 *
 * <p>Delega en {@code RenombrarHabitoUseCase} / {@code QuitarRenombreHabitoUseCase} (via
 * {@code habits.api}), que vuelven a correr todas sus guardas. Un rechazo vuelve como {@code Fallo}
 * legible, nunca como excepcion. Sin condicion de flag, igual que {@link PausarHabitoConfirmable}:
 * una propuesta ya guardada tiene que poder confirmarse aunque el flag se apague despues.
 */
@Component
public class RenombrarHabitoConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(RenombrarHabitoConfirmable.class);

    private final GestionarPlanDeHabitosPort planPort;

    public RenombrarHabitoConfirmable(GestionarPlanDeHabitosPort planPort) {
        this.planPort = planPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeRenombrarHabito.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<UUID> habitoId = CompletacionDeHabito.registroIdDe(
                invocacion.argumento(PropuestaDeRenombrarHabito.ARGUMENTO_HABITO_ID));
        String accion = invocacion.argumento(PropuestaDeRenombrarHabito.ARGUMENTO_ACCION);
        if (habitoId.isEmpty() || accion == null) {
            return ResultadoHerramienta.fallo("La propuesta no trae un habito valido: no se cambio nada.");
        }
        if (PropuestaDeRenombrarHabito.ACCION_ORIGINAL.equals(accion)) {
            return volverAlOriginal(actorId, habitoId.get());
        }
        Optional<String> nombre = PropuestaDeRenombrarHabito.recortado(
                invocacion.argumento(PropuestaDeRenombrarHabito.ARGUMENTO_NOMBRE));
        Optional<String> motivo = PropuestaDeRenombrarHabito.recortado(
                invocacion.argumento(PropuestaDeRenombrarHabito.ARGUMENTO_MOTIVO));
        if (!PropuestaDeRenombrarHabito.ACCION_RENOMBRAR.equals(accion)
                || PropuestaDeRenombrarHabito.problemaDe(nombre, motivo).isPresent()) {
            return ResultadoHerramienta.fallo("La propuesta no trae un cambio valido: no se cambio nada.");
        }
        return renombrar(actorId, habitoId.get(), nombre.get(), motivo.get());
    }

    private ResultadoHerramienta renombrar(UserId actorId, UUID habitoId, String nombre, String motivo) {
        try {
            planPort.renombrar(actorId, habitoId, nombre, motivo);
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Listo: ahora se llama '" + nombre + "'.");
    }

    private ResultadoHerramienta volverAlOriginal(UserId actorId, UUID habitoId) {
        try {
            planPort.quitarRenombre(actorId, habitoId);
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Listo: volvio a su nombre del programa.");
    }

    /** El detalle va al log; a la persona, un motivo que se pueda leer. */
    private static ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo cambiar el nombre del habito: {}", PropuestaDeRenombrarHabito.NOMBRE,
                rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case NoSuchElementException noExiste -> "Ese habito ya no existe: no se cambio nada.";
            case NotAuthorizedException suspendida -> "La cuenta esta suspendida: no se cambio nada.";
            case IllegalArgumentException noSePuede -> "A ese habito no se le puede cambiar el nombre: no se cambio nada.";
            default -> "No se pudo cambiar el nombre en este momento.";
        });
    }
}
