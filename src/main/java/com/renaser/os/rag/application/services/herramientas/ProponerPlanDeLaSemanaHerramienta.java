package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ObjetivoSemanal;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code proponer_plan_de_la_semana} (R2, 2026-09-23): PROPONE los objetivos semanales —uno por eje,
 * con obstaculo y contingencia opcionales— y la persona los confirma con un boton (D-153). La
 * escritura real la hace {@link CrearPlanDeLaSemanaConfirmable} con {@code CrearPlanSemanalUseCase},
 * el mismo de {@code POST /rocks/weekly}.
 *
 * <p><b>Antes de proponer solo se valida la forma del JSON.</b> No se mira si algun eje ya tiene
 * objetivo: la semana que se planifica la decide {@code rocks} (el domingo es la que empieza el
 * lunes) y {@code consultar_rocas} expone la semana EN CURSO, asi que compararlas daria un falso
 * "ya lo tiene" justo el dia del ritual. Lo que ya existe {@code rocks} lo deja como esta, y el
 * resumen lo avisa.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones=true}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class ProponerPlanDeLaSemanaHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "proponer_plan_de_la_semana";
    public static final String ARGUMENTO_PLAN = "plan";

    private static final Logger log = LoggerFactory.getLogger(ProponerPlanDeLaSemanaHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone crear los objetivos de la semana: uno por eje, con su obstaculo y su contingencia si la "
                    + "persona los dijo. NO los crea: deja una propuesta y la persona la confirma con un boton en "
                    + "la app. Antes llama a consultar_rocas con alcance semana para ver que objetivos ya tiene; "
                    + "un eje que ya tiene objetivo esa semana no se cambia desde aca. Usala solo cuando la persona "
                    + "pida armar su semana y ya te haya dicho sus objetivos; no los inventes. Nunca digas que "
                    + "quedaron creados: dile que los confirme con el boton.",
            List.of(new ParametroHerramienta(ARGUMENTO_PLAN, TipoParametroHerramienta.TEXTO,
                    "Un JSON escrito como texto: {\"objetivos\":[{\"eje\":\"TRABAJO\",\"titulo\":\"Cerrar 2 ventas\","
                            + "\"obstaculo\":\"Poco tiempo\",\"contingencia\":\"Llamar en la hora de almuerzo\"}]}. "
                            + "eje es CUERPO, TRABAJO o RELACIONES, uno por objetivo. obstaculo y contingencia son "
                            + "opcionales. No agregues otros campos.", true)));

    private final PlanificarRocasPort planificarPort;
    private final ProponerAccionUseCase proponerAccion;

    public ProponerPlanDeLaSemanaHerramienta(PlanificarRocasPort planificarPort, ProponerAccionUseCase proponerAccion) {
        this.planificarPort = planificarPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        List<ObjetivoSemanal> objetivos;
        try {
            objetivos = PlanDeRocasJson.leerPlanDeLaSemana(invocacion.argumento(ARGUMENTO_PLAN),
                    planificarPort.ejesValidos());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo(malFormado.getMessage());
        }
        String resumen = TextoDePlanDeRocas.resumenDeLaSemana(objetivos);
        InvocacionHerramienta normalizada = new InvocacionHerramienta(NOMBRE,
                Map.of(ARGUMENTO_PLAN, PlanDeRocasNormalizado.deLaSemana(objetivos)));
        try {
            proponerAccion.proponer(actorId, normalizada, resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento.");
        }
        return ResultadoHerramienta.exito("Propuesta creada: " + resumen + TextoDePlanDeRocas.NO_ESTA_HECHO);
    }
}
