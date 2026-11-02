package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.HabitoCumplido;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Marcar un habito como hecho, dicho en el idioma de las herramientas: leer el {@code registro_id}
 * que mando el modelo y traducir el rechazo del negocio a un {@code Fallo} legible.
 *
 * <p><b>Existe para que haya UNA sola traduccion</b> (fase 2, D-153). Hoy completan dos caminos:
 * {@code HerramientasAgenteService} con el flag {@code confirmacion-con-botones} apagado, y
 * {@link MarcarHabitoCompletadoConfirmable} cuando la persona toca "Confirmar". Los textos son los
 * mismos que tenia el servicio antes de partirse, sin tocar una coma: con el flag apagado la
 * herramienta tiene que seguir diciendo exactamente lo que decia.
 */
public final class CompletacionDeHabito {

    private static final Logger log = LoggerFactory.getLogger(CompletacionDeHabito.class);

    private CompletacionDeHabito() {
    }

    /** Vacio si lo que mando el modelo no es un UUID (se lo invento, o mando el titulo). */
    public static Optional<UUID> registroIdDe(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(texto.trim()));
        } catch (IllegalArgumentException noEsUnIdentificador) {
            return Optional.empty();
        }
    }

    private static Optional<HabitoDelDia> delDia(ConsultarAgendaHabitosPort agendaHabitosPort, UserId actorId, UUID registroId) {
        try {
            List<HabitoDelDia> deHoy = agendaHabitosPort.deHoyDe(actorId);
            return deHoy == null ? Optional.empty()
                    : deHoy.stream().filter(habito -> registroId.equals(habito.registroId())).findFirst();
        } catch (RuntimeException sinLeer) {
            log.info("[rag] el habito completado sale sin su id ni su dia: {}", sinLeer.toString());
            return Optional.empty();
        }
    }

    public static ResultadoHerramienta identificadorInvalido() {
        return ResultadoHerramienta.fallo("Ese identificador de habito no es valido. Consulta primero los "
                + "habitos del dia y usa el id que devuelven.");
    }

    /**
     * Completa con las guardas de siempre ({@code habits}); un rechazo vuelve como {@code Fallo}, nunca sube.
     *
     * <p>Spec 038: el exito dice ademas QUE habito se cumplio y de que dia ({@link HabitoCumplido}), para que la app haga lo mismo que cuando se
     * cumple desde Training (apagar las alarmas de ese habito, celebrar). Se lee ANTES de completar, porque despues el registro ya no es de los
     * que se pueden entregar; si no se puede leer, el exito sale igual sin el dato.
     */
    public static ResultadoHerramienta completar(ConsultarAgendaHabitosPort agendaHabitosPort, UserId actorId,
                                                 UUID registroId) {
        Optional<HabitoDelDia> antes = delDia(agendaHabitosPort, actorId, registroId);
        try {
            int puntos = agendaHabitosPort.completar(actorId, registroId);
            String texto = "Habito marcado como completado. Puntos otorgados: " + puntos + ".";
            return antes.filter(habito -> habito.habitoId() != null && habito.fecha() != null)
                    .map(habito -> ResultadoHerramienta.exito(texto,
                            new HabitoCumplido(registroId, habito.habitoId(), habito.fecha(), puntos)))
                    .orElseGet(() -> ResultadoHerramienta.exito(texto));
        } catch (RuntimeException fallaDelNegocio) {
            // El detalle va al log; al modelo solo un motivo apto para repetirle a la persona.
            log.info("[rag] la herramienta {} no pudo completar el habito: {}",
                    CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO, fallaDelNegocio.toString());
            return ResultadoHerramienta.fallo("No se pudo marcar ese habito como completado: puede que ya este "
                    + "hecho, que su dia ya haya cerrado o que no sea uno de los suyos.");
        }
    }
}
