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
 * La escritura de {@code proponer_iniciar_dia_sin_celular}, que solo corre cuando la persona
 * confirma (D-153). La propuso {@link PropuestaDeIniciarDiaSinCelular}.
 *
 * <p>Delega en {@code IniciarRachaUseCase} (via {@code habits.api.EnfoqueDiarioPort}), que vuelve a
 * correr sus guardas: dueno, cuenta activa, que el habito sea "Dia sin celular", que no haya otra
 * racha en curso y que la meta sea valida. Un rechazo vuelve como {@code Fallo} legible.
 *
 * <p>Sin condicion de flag, igual que {@link PausarHabitoConfirmable}.
 */
@Component
public class IniciarDiaSinCelularConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(IniciarDiaSinCelularConfirmable.class);

    private final EnfoqueDiarioDelAprendizPort enfoquePort;

    public IniciarDiaSinCelularConfirmable(EnfoqueDiarioDelAprendizPort enfoquePort) {
        this.enfoquePort = enfoquePort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeIniciarDiaSinCelular.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<UUID> registroId = CompletacionDeHabito.registroIdDe(
                invocacion.argumento(PropuestaDeIniciarDiaSinCelular.ARGUMENTO_REGISTRO_ID));
        Optional<Integer> horas = PropuestaDeIniciarDiaSinCelular.horasDe(
                invocacion.argumento(PropuestaDeIniciarDiaSinCelular.ARGUMENTO_HORAS_OBJETIVO));
        if (registroId.isEmpty() || horas.isEmpty()) {
            return ResultadoHerramienta.fallo("La propuesta no trae una racha valida: no se inicio nada.");
        }
        try {
            enfoquePort.iniciarDiaSinCelular(actorId, registroId.get(), horas.get());
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Racha sin celular iniciada con meta de " + horas.get() + " horas. "
                + "Cerrarla con evidencia se hace desde la app.");
    }

    /** El detalle va al log; a la persona, un motivo que se pueda leer. */
    private static ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo iniciar la racha: {}", PropuestaDeIniciarDiaSinCelular.NOMBRE, rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case IllegalStateException yaHayUna -> "Ya hay una racha en curso o el habito de hoy ya no esta "
                    + "pendiente: no se inicio nada.";
            case IllegalArgumentException invalida -> "Esa meta o ese habito no sirven para una racha sin celular: "
                    + "no se inicio nada.";
            case NoSuchElementException noExiste -> "Ese habito ya no esta en su dia: no se inicio nada.";
            case NotAuthorizedException suspendida -> "La cuenta esta suspendida: no se inicio nada.";
            default -> "No se pudo iniciar la racha en este momento.";
        });
    }
}
