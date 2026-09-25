package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
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
 * La escritura de {@code proponer_iniciar_santuario}, que solo corre cuando la persona confirma
 * (D-153). La propuso {@link PropuestaDeIniciarSantuario}.
 *
 * <p>Delega en {@code IniciarSesionBloqueoUseCase} (via {@code habits.api.EnfoqueDiarioPort}), que
 * vuelve a correr sus guardas: dueno, cuenta activa, que sea un Santuario, que no tenga sesion y que
 * ya sea su hora. Un rechazo vuelve como {@code Fallo} legible.
 *
 * <p>Sin condicion de flag, igual que {@link PausarHabitoConfirmable}.
 */
@Component
public class IniciarSantuarioConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(IniciarSantuarioConfirmable.class);

    private final EnfoqueDiarioDelAprendizPort enfoquePort;

    public IniciarSantuarioConfirmable(EnfoqueDiarioDelAprendizPort enfoquePort) {
        this.enfoquePort = enfoquePort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeIniciarSantuario.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<UUID> registroId = CompletacionDeHabito.registroIdDe(
                invocacion.argumento(PropuestaDeIniciarSantuario.ARGUMENTO_REGISTRO_ID));
        if (registroId.isEmpty()) {
            return ResultadoHerramienta.fallo("La propuesta no trae un Santuario valido: no se inicio nada.");
        }
        try {
            enfoquePort.iniciarSantuario(actorId, registroId.get());
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Santuario iniciado. Completarlo o salir antes se hace desde la app.");
    }

    /** El detalle va al log; a la persona, un motivo que se pueda leer. */
    private static ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo iniciar el Santuario: {}", PropuestaDeIniciarSantuario.NOMBRE, rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case IllegalStateException noSePuede -> "No se pudo iniciar: todavia no es su hora, o ya se habia "
                    + "iniciado. No se cambio nada.";
            case IllegalArgumentException noEsSantuario -> "Ese habito no es un Santuario: no se inicio nada.";
            case NoSuchElementException noExiste -> "Ese Santuario ya no esta en su dia: no se inicio nada.";
            case NotAuthorizedException suspendida -> "La cuenta esta suspendida: no se inicio nada.";
            default -> "No se pudo iniciar el Santuario en este momento.";
        });
    }
}
