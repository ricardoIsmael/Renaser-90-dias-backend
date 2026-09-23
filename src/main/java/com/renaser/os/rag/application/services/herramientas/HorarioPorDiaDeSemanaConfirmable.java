package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.HorarioSemanal;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * La escritura de {@code proponer_horario_por_dia_de_semana}, que solo corre cuando la persona
 * confirma con el boton (fase 4, D-153). La propuso {@link PropuestaDeHorarioPorDiaDeSemana}.
 *
 * <p>Delega en {@code habits} ({@code EditarHorarioSemanalUseCase}), que vuelve a cobrar el cupo al
 * fijar y a rechazar el apagado de un obligatorio. Un {@code IllegalStateException} quiere decir
 * cosas distintas segun la accion, por eso hay una traduccion por accion.
 */
@Component
public class HorarioPorDiaDeSemanaConfirmable implements AccionConfirmable {

    private static final RechazoDeHorario RECHAZO_AL_FIJAR = new RechazoDeHorario(
            "No se pudo: ya no le quedan cambios de horario esa semana para ese habito.",
            "No se pudo: la hora de inicio es demasiado tarde para completar el habito antes de la medianoche.");
    private static final RechazoDeHorario RECHAZO_AL_APAGAR = new RechazoDeHorario(
            "No se pudo: ese habito es obligatorio del programa y no se puede apagar.",
            "No se pudo aplicar el cambio de ese dia de la semana.");

    private final AjustarHorariosPort ajustarHorarios;

    public HorarioPorDiaDeSemanaConfirmable(AjustarHorariosPort ajustarHorarios) {
        this.ajustarHorarios = ajustarHorarios;
    }

    @Override
    public String herramienta() {
        return PropuestaDeHorarioPorDiaDeSemana.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        HorarioSemanalPedido pedido;
        try {
            pedido = HorarioSemanalPedido.de(invocacion);
        } catch (PropuestaImposibleException guardadaInvalida) {
            return ResultadoHerramienta.fallo("La propuesta guardada no es valida; pide el cambio de nuevo.");
        }
        try {
            return ResultadoHerramienta.exito(ejecutar(actorId, pedido));
        } catch (RuntimeException rechazo) {
            RechazoDeHorario traduccion = pedido.accion() == HorarioSemanalPedido.Accion.FIJAR ? RECHAZO_AL_FIJAR
                    : RECHAZO_AL_APAGAR;
            return traduccion.traducir(herramienta(), rechazo);
        }
    }

    private String ejecutar(UserId actorId, HorarioSemanalPedido pedido) {
        String dias = "los " + ArgumentosDeHorario.nombre(pedido.diaSemana());
        switch (pedido.accion()) {
            case FIJAR -> {
                ajustarHorarios.fijarDiaDeLaSemana(actorId, new HorarioSemanal(pedido.habitoId(), pedido.diaSemana(),
                        pedido.horaInicio(), pedido.horaLimite()));
                return "Horario fijado para " + dias + ": "
                        + HorariosParaProponer.franjaPedida(pedido.horaInicio(), pedido.horaLimite()) + ".";
            }
            case APAGAR -> {
                ajustarHorarios.apagarDiaDeLaSemana(actorId, pedido.habitoId(), pedido.diaSemana());
                return "Habito apagado " + dias + ".";
            }
            default -> {
                ajustarHorarios.quitarDiaDeLaSemana(actorId, pedido.habitoId(), pedido.diaSemana());
                return "Listo: " + dias + " vuelven al horario general.";
            }
        }
    }
}
