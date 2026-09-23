package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.application.services.herramientas.HorarioSemanalPedido.Accion;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * {@code proponer_horario_por_dia_de_semana} (R2, fase 4, 2026-09-23): PROPONE un horario propio
 * para un dia de la semana ("los lunes a las 5"), apagarlo ese dia todas las semanas, o quitar lo
 * propio para que vuelva al horario general. La escritura la hace
 * {@link HorarioPorDiaDeSemanaConfirmable} (D-153); en {@code habits} es
 * {@code EditarHorarioSemanalUseCase}, el de {@code PUT/DELETE …/weekdays/{weekday}}.
 *
 * <p><b>Fijar la hora cobra cupo</b>, medido en la proxima ocurrencia de ese dia (la fecha efectiva
 * del caso de uso); apagar y quitar no. Por eso el cupo se lee de {@code consultar_horarios} con
 * esa fecha. Un obligatorio no se apaga. Solo se ofrece con {@code confirmacion-con-botones=true}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeHorarioPorDiaDeSemana implements HerramientaAgente {

    public static final String NOMBRE = "proponer_horario_por_dia_de_semana";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "PROPONE un horario que se repite un dia de la semana para un habito del aprendiz: 'fijar' una hora "
                    + "propia ese dia (gasta 1 cambio de la semana), 'apagar' el habito ese dia todas las semanas, "
                    + "o 'quitar' lo propio de ese dia para que vuelva al horario general. NO cambia nada: deja una "
                    + "propuesta que la persona confirma con un boton. Llama SIEMPRE antes a consultar_horarios "
                    + "para tener el habito_id, el cupo y si es obligatorio. Nunca digas que ya quedo hecho.",
            List.of(ParametroHerramienta.obligatorio(ArgumentosDeHorario.HABITO_ID,
                            TipoParametroHerramienta.IDENTIFICADOR, "El habito_id que devolvio consultar_horarios."),
                    ParametroHerramienta.obligatorio(ArgumentosDeHorario.DIA_SEMANA, TipoParametroHerramienta.TEXTO,
                            "lunes, martes, miercoles, jueves, viernes, sabado o domingo."),
                    ParametroHerramienta.obligatorio(ArgumentosDeHorario.ACCION, TipoParametroHerramienta.TEXTO,
                            "'fijar', 'apagar' o 'quitar'."),
                    new ParametroHerramienta(ArgumentosDeHorario.HORA_INICIO, TipoParametroHerramienta.TEXTO,
                            "Solo para 'fijar': hora de inicio HH:mm en 24 horas.", false),
                    new ParametroHerramienta(ArgumentosDeHorario.HORA_LIMITE, TipoParametroHerramienta.TEXTO,
                            "Solo para 'fijar', opcional: hora limite HH:mm, posterior a la de inicio.", false)));

    private final ConsultarHorariosPort horariosPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeHorarioPorDiaDeSemana(ConsultarHorariosPort horariosPort, ProponerAccionUseCase proponerAccion) {
        this.horariosPort = horariosPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        try {
            HorarioSemanalPedido pedido = HorarioSemanalPedido.de(invocacion);
            LocalDate hoy = HorariosParaProponer.de(horariosPort, actorId, null).fecha();
            // La proxima vez que cae ese dia, estrictamente despues de hoy: la fecha efectiva con que
            // habits mide el cupo. Al confirmar, habits la vuelve a calcular con su reloj.
            LocalDate proxima = hoy.with(TemporalAdjusters.next(pedido.diaSemana()));
            HorariosDelDia dia = HorariosParaProponer.de(horariosPort, actorId, proxima);
            HorarioDeHabito habito = HorariosParaProponer.habito(dia, pedido.habitoId());
            String resumen = resumenValidado(pedido, habito, dia);
            return PropuestaPendiente.registrar(proponerAccion, actorId, pedido.invocacion(), resumen);
        } catch (PropuestaImposibleException imposible) {
            return ResultadoHerramienta.fallo(imposible.getMessage());
        }
    }

    /** Lo que ve la persona junto a los botones, despues de descartar lo que habits rechazaria. */
    static String resumenValidado(HorarioSemanalPedido pedido, HorarioDeHabito habito, HorariosDelDia proxima) {
        String dias = "los " + ArgumentosDeHorario.nombre(pedido.diaSemana());
        if (pedido.accion() == Accion.FIJAR) {
            HorariosParaProponer.requireCupo(proxima.cuota());
            return "Fijar '" + habito.titulo() + "' " + dias + " a "
                    + HorariosParaProponer.franjaPedida(pedido.horaInicio(), pedido.horaLimite())
                    + " (hasta ahora ese dia: " + HorariosParaProponer.franja(habito.horaDisparo(), habito.horaLimite())
                    + "), desde el "
                    + ArgumentosDeHorario.texto(proxima.fecha()) + ". Los demas dias no cambian. "
                    + HorariosParaProponer.gastoDeCupo(proxima.cuota());
        }
        if (pedido.accion() == Accion.APAGAR) {
            if (habito.obligatorio()) {
                throw new PropuestaImposibleException("'" + habito.titulo() + "' es obligatorio del programa: no "
                        + "se puede apagar ningun dia.");
            }
            return "Apagar '" + habito.titulo() + "' " + dias + ", todas las semanas, hasta que lo vuelva a activar. "
                    + "No gasta cambios de horario.";
        }
        return "Quitar lo propio de " + dias + " en '" + habito.titulo() + "' (hora propia o apagado): esos dias "
                + "vuelve a su horario general. No gasta cambios de horario.";
    }
}
