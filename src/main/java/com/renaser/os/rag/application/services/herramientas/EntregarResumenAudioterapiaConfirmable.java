package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort;
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
 * La escritura de {@code proponer_resumen_audioterapia} (D-171), que solo corre cuando la persona
 * confirma. La propuso {@link PropuestaDeResumenAudioterapia}.
 *
 * <p>Delega en {@code habits.api.AudioterapiaDelAprendizPort}: evidencia de TEXTO y completar, con
 * las guardas de siempre. Si entre proponer y confirmar la persona la entrego desde la app, o paso
 * su medianoche, {@code habits} lo rechaza y vuelve un {@code Fallo} legible.
 *
 * <p>Sin condicion de flag, igual que {@link EntregarResumenEspirituConfirmable}: una propuesta ya
 * guardada tiene que poder confirmarse aunque el flag se apague despues.
 */
@Component
public class EntregarResumenAudioterapiaConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(EntregarResumenAudioterapiaConfirmable.class);

    private final AudioterapiaSemanalPort audioterapiaPort;

    public EntregarResumenAudioterapiaConfirmable(AudioterapiaSemanalPort audioterapiaPort) {
        this.audioterapiaPort = audioterapiaPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeResumenAudioterapia.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        String texto = invocacion.argumento(PropuestaDeResumenAudioterapia.ARGUMENTO_TEXTO);
        Optional<UUID> registroId = CompletacionDeHabito.registroIdDe(
                invocacion.argumento(PropuestaDeResumenAudioterapia.ARGUMENTO_REGISTRO_ID));
        if (registroId.isEmpty() || texto == null || texto.isBlank()) {
            return ResultadoHerramienta.fallo("La propuesta no trae respuestas validas: no se entrego nada.");
        }
        int puntos;
        try {
            puntos = audioterapiaPort.entregarRespuestas(actorId, registroId.get(), texto);
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Audioterapia de hoy entregada. Puntos otorgados: " + puntos + ".");
    }

    /** El detalle va al log; a la persona, un motivo que se pueda leer. */
    private static ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo entregar: {}", PropuestaDeResumenAudioterapia.NOMBRE, rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case IllegalStateException yaHecha -> "La Audioterapia de hoy ya estaba entregada o cerrada: no se "
                    + "entrego nada.";
            case NoSuchElementException otroDia -> "Esa Audioterapia ya no es la de hoy: no se entrego nada.";
            case NotAuthorizedException sinAcceso -> "La cuenta esta suspendida: no se entrego nada.";
            default -> "No se pudo entregar la Audioterapia en este momento.";
        });
    }
}
