package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code consultar_habitos_obligatorios} (D-165, pedido del dueño el 2026-09-25): cuales habitos son
 * obligatorios del programa y que si se puede cambiar de cada tipo, para que el acompanante diga
 * "no puedo, es obligatorio del programa" con el motivo y ofrezca lo que si se puede.
 *
 * <p>Antes el acompanante solo sabia de un obligatorio si ese dia le tocaba ({@code consultar_horarios}
 * lo marca por dia): uno semanal, como la audioterapia, no aparecia. Y la herramienta de pausa
 * buscaba en los desbloqueos, donde los obligatorios no estan (E-245).
 *
 * <p>Solo lee: no necesita el flag de botones. Los obligatorios los decide {@code habits}
 * ({@code habitos.desactivable = false}, V18), no esta clase. Todo lo demas se pausa como en Plan.
 */
@Component
public class ConsultarHabitosObligatoriosHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_habitos_obligatorios";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Dice cuales habitos son obligatorios del programa (no se apagan ningun dia ni se pausan), que si se "
                    + "puede con cada habito y cuales estan pausados hoy. Usala antes de responder si la persona "
                    + "pide apagar, pausar, quitar o saltarse un habito, o pregunta cuales son obligatorios o que "
                    + "puede cambiar de sus habitos.",
            List.of());

    private final GestionarPlanDeHabitosPort planPort;
    private final ConsultarHorariosPort horariosPort;

    public ConsultarHabitosObligatoriosHerramienta(GestionarPlanDeHabitosPort planPort,
                                                  ConsultarHorariosPort horariosPort) {
        this.planPort = planPort;
        this.horariosPort = horariosPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return LecturaDelPlan.conPlan(planPort, actorId,
                plan -> ResultadoHerramienta.exito(textoDe(plan) + cupoDeCambios(actorId)));
    }

    /**
     * El cupo real de cambios de horario de esta semana: sin el, el modelo ofrecia "cambiarle la hora"
     * o afirmaba que no quedaban cambios sin haberlo leido (bateria del 2026-09-25). Si no se puede
     * leer, se omite: lo demas de la respuesta sigue siendo cierto.
     */
    private String cupoDeCambios(UserId actorId) {
        try {
            HorariosDelDia hoy = HorariosParaProponer.de(horariosPort, actorId, null);
            return "\n" + HorariosParaProponer.lineaDeCupo(hoy.cuota());
        } catch (PropuestaImposibleException sinHorarios) {
            return "";
        }
    }

    static String textoDe(PlanDelAprendiz plan) {
        return obligatorios(plan) + "\n" + LoQueSiSePuede.CON_LOS_DEMAS + "\n" + pausados(plan);
    }

    private static String obligatorios(PlanDelAprendiz plan) {
        List<HabitoDelPlan> obligatorios = plan.obligatorios();
        if (obligatorios.isEmpty()) {
            return "No tiene habitos obligatorios del programa.";
        }
        return "Obligatorios del programa (no se apagan ningun dia ni se pausan): "
                + obligatorios.stream().map(HabitoDelPlan::titulo).collect(Collectors.joining(", "))
                + ". " + LoQueSiSePuede.CON_UN_OBLIGATORIO;
    }

    /** Los pausados hoy: para no ofrecer pausar lo que ya lo esta, ni olvidar que se pueden reactivar. */
    private static String pausados(PlanDelAprendiz plan) {
        List<String> titulos = plan.habitos().stream().filter(HabitoDelPlan::pausadoHoy)
                .map(habito -> habito.titulo() + (habito.pausadoHasta() == null ? " (sin fecha de fin)"
                        : " (hasta el " + FechaDelPlan.legible(habito.pausadoHasta()) + ")"))
                .toList();
        return titulos.isEmpty() ? "Hoy no tiene habitos pausados."
                : "Pausados hoy: " + String.join(", ", titulos) + ".";
    }
}
