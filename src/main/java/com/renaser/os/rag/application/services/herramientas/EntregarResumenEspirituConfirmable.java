package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * La escritura de {@code proponer_resumen_espiritu}, que solo corre cuando la persona confirma la
 * propuesta con el boton (D-153). La propuso {@link PropuestaDeResumenEspiritu}.
 *
 * <p>Delega en {@code EntregarResumenEspirituUseCase} (via {@code habits.api.EnfoqueDiarioPort}),
 * que vuelve a correr todas sus guardas y completa "Pastilla Renacer" de hoy con sus puntos. Si
 * entre proponer y confirmar el audio ya se entrego, {@code habits} lo rechaza y vuelve un
 * {@code Fallo} legible.
 *
 * <p>Sin condicion de flag, igual que {@link PausarHabitoConfirmable}: una propuesta ya guardada
 * tiene que poder confirmarse aunque el flag se apague despues.
 */
@Component
public class EntregarResumenEspirituConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(EntregarResumenEspirituConfirmable.class);

    private final EnfoqueDiarioDelAprendizPort enfoquePort;
    private final ConsultarAgendaHabitosPort agendaHabitos;

    public EntregarResumenEspirituConfirmable(EnfoqueDiarioDelAprendizPort enfoquePort,
                                              ConsultarAgendaHabitosPort agendaHabitos) {
        this.enfoquePort = enfoquePort;
        this.agendaHabitos = agendaHabitos;
    }

    @Override
    public String herramienta() {
        return PropuestaDeResumenEspiritu.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        String resumen = invocacion.argumento(PropuestaDeResumenEspiritu.ARGUMENTO_RESUMEN);
        Integer dia = diaDe(invocacion.argumento(PropuestaDeResumenEspiritu.ARGUMENTO_DIA));
        if (dia == null || resumen == null || resumen.isBlank()) {
            return ResultadoHerramienta.fallo("La propuesta no trae un resumen valido: no se envio nada.");
        }
        // Issue #99: la entrega marca la Pastilla Renacer de hoy; que la app apague sus alarmas y celebre. Esta entrega no devuelve los
        // puntos: se toman los que estaban en juego justo antes, los mismos que muestra la tarjeta de Training en ese momento.
        Optional<HabitoDelDia> pastilla = CompletacionDeHabito.porHacerDeHoy(agendaHabitos, actorId,
                CompletacionDeHabito.deClave(HabitoDelDia.CLAVE_PASTILLA_RENACER));
        boolean aTiempo;
        try {
            aTiempo = enfoquePort.entregarResumenEspiritu(actorId, dia, resumen);
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        int puntos = pastilla.map(HabitoDelDia::puntosEnJuego).orElse(0); // map con null deja el Optional vacio
        return CompletacionDeHabito.exitoConElHabito(aTiempo
                ? "Resumen de Espiritu enviado a tiempo; tambien se marca 'Pastilla Renacer' de hoy."
                : "Resumen de Espiritu guardado, pero fuera de plazo; tambien se marca 'Pastilla Renacer' de hoy.",
                pastilla, puntos);
    }

    private static Integer diaDe(String texto) {
        try {
            return texto == null ? null : Integer.valueOf(texto.trim());
        } catch (NumberFormatException noEsUnNumero) {
            return null;
        }
    }

    /** El detalle va al log; a la persona, un motivo que se pueda leer. */
    private static ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo entregar el resumen: {}", PropuestaDeResumenEspiritu.NOMBRE, rechazo.toString());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case IllegalStateException yaRegistrado -> "Ese audio ya se habia entregado o vencio: no se envio nada.";
            case NoSuchElementException noDesbloqueado -> "Ese audio ya no esta disponible: no se envio nada.";
            case NotAuthorizedException sinAcceso -> "La cuenta esta suspendida: no se envio nada.";
            default -> "No se pudo enviar el resumen en este momento.";
        });
    }
}
