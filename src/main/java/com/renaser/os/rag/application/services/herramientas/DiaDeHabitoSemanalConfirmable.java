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
 * La escritura de {@code proponer_dia_de_habito_semanal}, que solo corre cuando la persona
 * confirma la propuesta con el boton (fase 2, D-153). La propuso {@link PropuestaDeDiaDeHabitoSemanal}.
 *
 * <p>Delega en {@code ElegirDiaSemanalUseCase} (via {@code habits.api}), que vuelve a correr su
 * guarda: si se confirma pasada la medianoche del dia elegido, o ya en otra semana, {@code habits}
 * lo rechaza y vuelve un {@code Fallo} legible, nunca la excepcion.
 *
 * <p>Sin condicion de flag, igual que {@link MarcarHabitoCompletadoConfirmable}.
 */
@Component
public class DiaDeHabitoSemanalConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(DiaDeHabitoSemanalConfirmable.class);

    private final GestionarPlanDeHabitosPort planPort;

    public DiaDeHabitoSemanalConfirmable(GestionarPlanDeHabitosPort planPort) {
        this.planPort = planPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeDiaDeHabitoSemanal.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<UUID> habitoId = CompletacionDeHabito.registroIdDe(
                invocacion.argumento(PropuestaDeDiaDeHabitoSemanal.ARGUMENTO_HABITO_ID));
        Optional<LocalDate> fecha = FechaDelPlan.leer(
                invocacion.argumento(PropuestaDeDiaDeHabitoSemanal.ARGUMENTO_FECHA));
        if (habitoId.isEmpty() || fecha.isEmpty()) {
            return ResultadoHerramienta.fallo("La propuesta no trae un habito y un dia validos: no se cambio nada.");
        }
        try {
            planPort.elegirDiaSemanal(actorId, habitoId.get(), fecha.get());
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Dia anotado: " + FechaDelPlan.legible(fecha.get()) + ".");
    }

    /** El detalle va al log; a la persona, un motivo que se pueda leer. */
    private static ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo elegir el dia: {}", PropuestaDeDiaDeHabitoSemanal.NOMBRE, rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case IllegalArgumentException diaNoValido ->
                    "Ese dia ya no se puede elegir: solo dias de esta semana que no hayan pasado.";
            case NoSuchElementException noEncontrado -> "No encontre ese habito: no se cambio nada.";
            case NotAuthorizedException noPermitido ->
                    "Ahora no se puede elegir dia (Dia 0 del programa o cuenta suspendida): no se cambio nada.";
            default -> "No se pudo anotar ese dia en este momento.";
        });
    }
}
