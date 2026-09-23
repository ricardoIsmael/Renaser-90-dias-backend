package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.services.herramientas.PropuestaDeApagarDia.DiaPedido;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * La escritura de {@code proponer_apagar_dia}, que solo corre cuando la persona confirma con el
 * boton (fase 4, D-153). La propuso {@link PropuestaDeApagarDia}.
 *
 * <p>Delega en {@code habits} ({@code CambiarEstadoHabitoEnFechaUseCase}), que vuelve a exigir que
 * el dia no haya pasado y que el habito no sea obligatorio.
 *
 * <p><b>Encender solo quita el apagado de ESA fecha.</b> Si el habito ademas esta apagado por dia
 * de semana ("los jueves no"), ese dia sigue apagado. No se decide aca que hacer con eso: se relee
 * el horario y, si sigue apagado, se le dice al modelo para que no afirme lo contrario.
 */
@Component
public class ApagarDiaConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(ApagarDiaConfirmable.class);

    private static final RechazoDeHorario RECHAZO = new RechazoDeHorario(
            "No se pudo: ese habito es obligatorio del programa y no se puede apagar.",
            "No se pudo: ese dia ya paso.");

    private final AjustarHorariosPort ajustarHorarios;
    private final ConsultarHorariosPort horariosPort;

    public ApagarDiaConfirmable(AjustarHorariosPort ajustarHorarios, ConsultarHorariosPort horariosPort) {
        this.ajustarHorarios = ajustarHorarios;
        this.horariosPort = horariosPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeApagarDia.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        DiaPedido pedido;
        try {
            pedido = DiaPedido.de(invocacion);
        } catch (PropuestaImposibleException guardadaInvalida) {
            return ResultadoHerramienta.fallo("La propuesta guardada no es valida; pide el cambio de nuevo.");
        }
        try {
            ajustarHorarios.cambiarEstadoDelDia(actorId, pedido.habitoId(), pedido.fecha(), !pedido.apagar());
        } catch (RuntimeException rechazo) {
            return RECHAZO.traducir(herramienta(), rechazo);
        }
        String dia = ArgumentosDeHorario.texto(pedido.fecha());
        if (pedido.apagar()) {
            return ResultadoHerramienta.exito("Habito apagado el " + dia + ".");
        }
        return ResultadoHerramienta.exito(sigueApagado(actorId, pedido)
                .map(apagado -> apagado
                        ? "Se quito el apagado del " + dia + ", pero el habito sigue apagado ese dia porque esta "
                                + "apagado todos los " + ArgumentosDeHorario.nombre(pedido.fecha().getDayOfWeek())
                                + ". Eso se cambia con proponer_horario_por_dia_de_semana."
                        : "Habito activado de nuevo el " + dia + ".")
                .orElse("Se quito el apagado del " + dia + "."));
    }

    /** Si no se puede releer, no se inventa: se informa solo lo que se hizo. */
    private Optional<Boolean> sigueApagado(UserId actorId, DiaPedido pedido) {
        try {
            return Optional.of(horariosPort.deFecha(actorId, pedido.fecha()).habitos().stream()
                    .anyMatch(habito -> pedido.habitoId().equals(habito.habitoId()) && habito.apagado()));
        } catch (RuntimeException falla) {
            log.info("[rag] no se pudo releer el horario despues de encender un dia: {}", falla.toString());
            return Optional.empty();
        }
    }
}
