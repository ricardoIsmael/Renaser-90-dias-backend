package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.CambioDeHorario;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.HorarioCambiado;
import com.renaser.os.rag.application.services.herramientas.PropuestaDeCambioDeHorario.CambioPedido;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * La escritura de {@code proponer_cambio_de_horario}, que solo corre cuando la persona confirma con
 * el boton (fase 4, D-153). La propuso {@link PropuestaDeCambioDeHorario}.
 *
 * <p>Delega en {@code habits} ({@code EditarPreferenciaHorarioUseCase}, el mismo del PATCH de la
 * app), que vuelve a cobrar la cuota y a exigir fecha futura con SU reloj: si entre proponer y
 * confirmar la persona gasto el cupo desde la app o paso la medianoche, el rechazo vuelve como
 * {@code Fallo} legible y el cupo queda intacto.
 */
@Component
public class CambioDeHorarioConfirmable implements AccionConfirmable {

    private static final RechazoDeHorario RECHAZO = new RechazoDeHorario(
            "No se pudo: ya no le quedan cambios de horario esa semana para ese habito. Los que ya reacomodo esa "
                    + "semana los puede seguir ajustando desde la app.",
            "No se pudo: ese dia ya no se puede reacomodar (solo dias futuros), o la hora de inicio es demasiado "
                    + "tarde para completar el habito antes de la medianoche.");

    private final AjustarHorariosPort ajustarHorarios;

    public CambioDeHorarioConfirmable(AjustarHorariosPort ajustarHorarios) {
        this.ajustarHorarios = ajustarHorarios;
    }

    @Override
    public String herramienta() {
        return PropuestaDeCambioDeHorario.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        CambioPedido cambio;
        try {
            cambio = CambioPedido.de(invocacion);
        } catch (PropuestaImposibleException guardadaInvalida) {
            return ResultadoHerramienta.fallo("La propuesta guardada no es valida; pide el cambio de nuevo.");
        }
        try {
            HorarioCambiado cambiado = ajustarHorarios.cambiarHorario(actorId, new CambioDeHorario(cambio.habitoId(),
                    cambio.horaInicio(), cambio.horaLimite(), cambio.fecha()));
            return ResultadoHerramienta.exito(textoDe(cambiado, cambio.fecha() != null));
        } catch (RuntimeException rechazo) {
            return RECHAZO.traducir(herramienta(), rechazo);
        }
    }

    /** Con lo que devolvio {@code habits} (fecha efectiva y cupo ya recalculados con su reloj). */
    static String textoDe(HorarioCambiado cambiado, boolean soloEseDia) {
        String cupo = cambiado.semanaDeAcomodoLibre() ? "No gasto cambios: es su semana de acomodo libre."
                : "Le quedan " + cambiado.cambiosRestantes() + " de " + cambiado.cambiosLimite()
                        + " cambios de horario esa semana.";
        return "Horario cambiado: " + HorariosParaProponer.franja(cambiado.horaInicio(), cambiado.horaLimite())
                + (soloEseDia ? " solo el " : " desde el ") + ArgumentosDeHorario.texto(cambiado.rigeDesde()) + ". "
                + cupo;
    }
}
