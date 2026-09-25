package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.AccionDelDia;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.ObjetivoDeLaSemana;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.ResultadoPlanificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementa {@link PlanificarRocasPort} delegando en {@code rocks.api} (D-41): {@code rag} nunca
 * escribe en las tablas de rocas.
 *
 * <p>Arma cada accion como la arma la app ({@code useRocasDiarias.posicionarPorEje}, en el repo del
 * frontend), para que un plan hecho desde el chat sea indistinguible de uno hecho en la pantalla:
 * <ul>
 *   <li><b>posicion</b> 1, 2, 3 dentro de cada eje, en el orden en que vienen. Es una exigencia del
 *       contrato de {@code rocks} (sin huecos, desde 1), no una decision de este adaptador;</li>
 *   <li><b>puntajeImpacto</b> {@value #PUNTAJE_IMPACTO_COMO_LA_APP}: el punto medio de la escala
 *       1-10, el mismo valor que manda la app para no pedirle a la persona que puntue cada accion;</li>
 *   <li><b>esDelegable</b> {@code false}, tambien como la app.</li>
 * </ul>
 */
@Component
class PlanificarRocasAdapter implements PlanificarRocasPort {

    static final int PUNTAJE_IMPACTO_COMO_LA_APP = 5;

    private final PlanificacionDeRocasPort planificacion;

    PlanificarRocasAdapter(PlanificacionDeRocasPort planificacion) {
        this.planificacion = planificacion;
    }

    @Override
    public List<String> ejesValidos() {
        return PlanificacionDeRocasPort.EJES;
    }

    @Override
    public ResultadoPlan crearPlanDelDia(UserId aprendizId, LocalDate fecha, List<AccionDelPlan> acciones) {
        return aResultado(planificacion.crearPlanDelDia(aprendizId, fecha, posicionadasPorEje(acciones)));
    }

    @Override
    public ResultadoPlan crearPlanDeLaSemana(UserId aprendizId, List<ObjetivoSemanal> objetivos) {
        List<ObjetivoDeLaSemana> deRocks = objetivos.stream()
                .map(o -> new ObjetivoDeLaSemana(o.eje(), o.titulo(), o.obstaculo(), o.contingencia()))
                .toList();
        return aResultado(planificacion.crearPlanDeLaSemana(aprendizId, deRocks));
    }

    private static List<AccionDelDia> posicionadasPorEje(List<AccionDelPlan> acciones) {
        Map<String, Integer> contadoPorEje = new HashMap<>();
        List<AccionDelDia> posicionadas = new ArrayList<>();
        for (AccionDelPlan accion : acciones) {
            int posicion = contadoPorEje.merge(accion.eje(), 1, Integer::sum);
            posicionadas.add(new AccionDelDia(accion.eje(), posicion, accion.titulo(), PUNTAJE_IMPACTO_COMO_LA_APP,
                    false, accion.inicio(), accion.fin()));
        }
        return List.copyOf(posicionadas);
    }

    private static ResultadoPlan aResultado(ResultadoPlanificacion resultado) {
        return switch (resultado) {
            case ResultadoPlanificacion.Creado creado -> new ResultadoPlan.Creado(creado.cantidad());
            case ResultadoPlanificacion.Rechazado rechazado ->
                    new ResultadoPlan.Rechazado(Motivo.valueOf(rechazado.motivo().name()));
        };
    }
}
