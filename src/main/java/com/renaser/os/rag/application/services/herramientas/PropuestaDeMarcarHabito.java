package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La mitad de {@code marcar_habito_completado} que ve el modelo cuando el flag
 * {@code renaser.ia.acompanante.confirmacion-con-botones} esta prendido (fase 2, D-153): en vez
 * de marcar, deja una propuesta y la persona confirma con un boton. La otra mitad, la que escribe,
 * es {@link MarcarHabitoCompletadoConfirmable}.
 *
 * <p><b>Valida antes de proponer, con los datos de {@code habits}</b>: el registro tiene que ser
 * uno de SUS habitos de hoy y seguir en juego ({@link HabitoDelDia#sigueEnJuego()}). No se
 * reimplementa ninguna regla: si {@code deHoyDe} no lo trae con puntos en juego, no se ofrece un
 * boton que va a fallar. Al confirmar, {@code completar} vuelve a correr todas las guardas.
 *
 * <p>Vive aparte de {@code HerramientasAgenteService} para no pasar ese servicio de tres
 * dependencias a cinco (regla 01, parametros ≤ 3) y porque el flag decide dos cosas —que hace
 * la herramienta y como se le describe al modelo— que tienen que leerse del mismo lugar.
 */
@Component
public class PropuestaDeMarcarHabito {

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeMarcarHabito.class);

    private final ConsultarAgendaHabitosPort agendaHabitosPort;
    private final ProponerAccionUseCase proponerAccion;
    private final boolean activa;

    public PropuestaDeMarcarHabito(ConsultarAgendaHabitosPort agendaHabitosPort,
                                   ProponerAccionUseCase proponerAccion,
                                   @Value("${renaser.ia.acompanante.confirmacion-con-botones:false}")
                                   boolean activa) {
        this.agendaHabitosPort = agendaHabitosPort;
        this.proponerAccion = proponerAccion;
        this.activa = activa;
    }

    /** {@code false}: la herramienta marca en el acto, como siempre. */
    public boolean activa() {
        return activa;
    }

    public ResultadoHerramienta proponer(UserId actorId, UUID registroId) {
        Optional<HabitoDelDia> habito = entregableDeHoy(actorId, registroId);
        if (habito.isEmpty()) {
            return ResultadoHerramienta.fallo("Ese habito no esta entre los que todavia puede entregar hoy: puede "
                    + "que ya este hecho, que se le haya vencido el plazo o que no sea uno de los suyos.");
        }
        String resumen = resumenDe(habito.get());
        try {
            proponerAccion.proponer(actorId, invocacionPara(registroId), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                    falla);
            return ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento.");
        }
        return ResultadoHerramienta.exito("Propuesta creada: " + resumen + ". TODAVIA NO esta marcado: la persona "
                + "tiene que tocar Confirmar en la app para que se registre. No digas que ya quedo hecho; dile "
                + "que confirme con el boton.");
    }

    private Optional<HabitoDelDia> entregableDeHoy(UserId actorId, UUID registroId) {
        return agendaHabitosPort.deHoyDe(actorId).stream()
                .filter(habito -> registroId.equals(habito.registroId()))
                .filter(HabitoDelDia::sigueEnJuego)
                .findFirst();
    }

    /** Lo que ve la persona junto a los botones. */
    static String resumenDe(HabitoDelDia habito) {
        return "Marcar '" + habito.titulo() + "' como hecho (+" + habito.puntosEnJuego()
                + " puntos si lo confirmas ahora)";
    }

    /**
     * Se guarda la invocacion normalizada —solo el id, ya validado— y no la que mando el modelo:
     * al confirmar se ejecuta exactamente esto, sin argumentos de mas ni espacios alrededor.
     */
    private static InvocacionHerramienta invocacionPara(UUID registroId) {
        return new InvocacionHerramienta(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, registroId.toString()));
    }
}
