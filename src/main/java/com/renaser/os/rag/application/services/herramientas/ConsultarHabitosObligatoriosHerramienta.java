package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoObligatorio;
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
 * ({@code habitos.desactivable = false}, V18), no esta clase.
 */
@Component
public class ConsultarHabitosObligatoriosHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_habitos_obligatorios";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Dice cuales habitos son obligatorios del programa (no se apagan ningun dia ni se pausan), cuales se "
                    + "pueden pausar y que si se puede cambiar de cada uno. Usala antes de responder si la persona "
                    + "pide apagar, pausar, quitar o saltarse un habito, o pregunta cuales son obligatorios o que "
                    + "puede cambiar de sus habitos.",
            List.of());

    private final GestionarPlanDeHabitosPort planPort;

    public ConsultarHabitosObligatoriosHerramienta(GestionarPlanDeHabitosPort planPort) {
        this.planPort = planPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return LecturaDelPlan.conPlan(planPort, actorId, plan -> ResultadoHerramienta.exito(textoDe(plan)));
    }

    static String textoDe(PlanDelAprendiz plan) {
        return obligatorios(plan) + "\n" + pausables(plan) + "\n" + LoQueSiSePuede.CON_UNO_DE_LA_BASE;
    }

    private static String obligatorios(PlanDelAprendiz plan) {
        if (plan.obligatorios().isEmpty()) {
            return "No tiene habitos obligatorios del programa.";
        }
        return "Obligatorios del programa (no se apagan ningun dia ni se pausan): "
                + plan.obligatorios().stream().map(HabitoObligatorio::titulo).collect(Collectors.joining(", "))
                + ". " + LoQueSiSePuede.CON_UN_OBLIGATORIO;
    }

    /** La pausa es solo para los que se suman a su plan (los desbloqueos), y nunca para un obligatorio. */
    private static String pausables(PlanDelAprendiz plan) {
        List<String> titulos = plan.habitos().stream().filter(habito -> !habito.obligatorio())
                .map(habito -> habito.titulo() + (habito.pausadoHoy() ? " (hoy esta pausado)" : ""))
                .toList();
        return titulos.isEmpty() ? "No tiene habitos que se puedan pausar: la pausa es solo para los que se suman "
                + "a su plan." : "Se pueden pausar (hasta una fecha o sin fin) solo los que se suman a su plan: "
                + String.join(", ", titulos) + ".";
    }
}
