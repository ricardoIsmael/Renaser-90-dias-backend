package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.Motivo;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanDelDia;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * {@code proponer_plan_del_dia} (R2, 2026-09-23): PROPONE el plan de un dia —las acciones por eje,
 * con hora opcional— y la persona lo confirma con un boton (D-153). La escritura real la hace
 * {@link CrearPlanDelDiaConfirmable} con {@code CrearPlanDiarioUseCase}, el mismo de
 * {@code POST /rocks/plan}.
 *
 * <p><b>Que se verifica antes de proponer, y que no.</b> La forma del JSON ({@link PlanDeRocasJson}),
 * y lo que {@code consultar_rocas} ya expone: que fecha es manana en la zona de la persona, que el
 * dia en curso ya armado no se reacomoda, y si manana ya tiene acciones (confirmar las reemplaza, y
 * el resumen lo dice). Cuantas acciones por eje, que fechas son planificables y la ventana de las
 * 18:00 NO se revisan aca: las corre el caso de uso al confirmar.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones=true}: sin los botones
 * en la app, una propuesta no se puede confirmar.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class ProponerPlanDelDiaHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "proponer_plan_del_dia";
    public static final String ARGUMENTO_PLAN = "plan";

    private static final Logger log = LoggerFactory.getLogger(ProponerPlanDelDiaHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone crear el plan de un dia (por defecto manana): las acciones del dia por eje, con hora "
                    + "opcional. NO lo crea: deja una propuesta y la persona la confirma con un boton en la app. "
                    + "Antes llama a consultar_rocas con alcance manana para ver si ya tiene plan y cuando abre la "
                    + "ventana de planificacion. Usala solo cuando la persona pida armar o corregir su plan y ya "
                    + "te haya dicho que acciones quiere; no inventes acciones ni horas. Si ese dia todavia no "
                    + "llego y ya tenia plan, confirmar lo reemplaza. Nunca digas que el plan quedo creado: "
                    + "dile que lo confirme con el boton.",
            List.of(new ParametroHerramienta(ARGUMENTO_PLAN, TipoParametroHerramienta.TEXTO,
                    "Un JSON escrito como texto: {\"fecha\":\"AAAA-MM-DD\",\"acciones\":[{\"eje\":\"CUERPO\","
                            + "\"titulo\":\"Caminar 30 minutos\",\"inicio\":\"06:00\",\"fin\":\"06:30\"}]}. "
                            + "fecha es opcional (sin ella, manana). eje es CUERPO, TRABAJO o RELACIONES. inicio y "
                            + "fin son opcionales, en HH:MM. Dentro de cada eje, en orden de prioridad: la primera "
                            + "es la VERDE. No agregues otros campos.", true)));

    private final ConsultarRocasDelAprendizPort rocasPort;
    private final PlanificarRocasPort planificarPort;
    private final ProponerAccionUseCase proponerAccion;

    public ProponerPlanDelDiaHerramienta(ConsultarRocasDelAprendizPort rocasPort, PlanificarRocasPort planificarPort,
                                         ProponerAccionUseCase proponerAccion) {
        this.rocasPort = rocasPort;
        this.planificarPort = planificarPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        PlanDelDia pedido;
        try {
            pedido = PlanDeRocasJson.leerPlanDelDia(invocacion.argumento(ARGUMENTO_PLAN), planificarPort.ejesValidos());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo(malFormado.getMessage());
        }
        try {
            return proponerSobreLoQueYaTiene(actorId, pedido);
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo planificar sus rocas: la cuenta esta suspendida o todavia "
                    + "no tiene el programa de rocas activo.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer las rocas", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude revisar su plan en este momento.");
        }
    }

    private ResultadoHerramienta proponerSobreLoQueYaTiene(UserId actorId, PlanDelDia pedido) {
        RocasDelDia manana = rocasPort.deManana(actorId);
        LocalDate fecha = pedido.fecha() == null ? manana.fecha() : pedido.fecha();
        if (esHoyYaArmado(actorId, fecha, manana.fecha())) {
            return ResultadoHerramienta.fallo(TextoDePlanDeRocas.rechazoDelDia(Motivo.YA_PLANIFICADO));
        }
        String resumen = TextoDePlanDeRocas.resumenDelDia(fecha, pedido.acciones()) + avisoDeReemplazo(fecha, manana);
        return proponer(actorId, new PlanDelDia(fecha, pedido.acciones()), resumen);
    }

    /** El dia en curso ya armado no se reacomoda ({@code ALREADY_PLANNED}): no se ofrece un boton que va a fallar. */
    private boolean esHoyYaArmado(UserId actorId, LocalDate fecha, LocalDate manana) {
        return fecha.equals(manana.minusDays(1)) && !rocasPort.deHoy(actorId).rocas().isEmpty();
    }

    private static String avisoDeReemplazo(LocalDate fecha, RocasDelDia manana) {
        if (fecha.equals(manana.fecha()) && manana.planDeManana().creado()) {
            return " Reemplaza las " + manana.planDeManana().rocasPlanificadas() + " accion(es) que ya tiene para "
                    + "ese dia.";
        }
        return fecha.isAfter(manana.fecha()) ? " Si ese dia ya tiene acciones, se reemplazan." : "";
    }

    private ResultadoHerramienta proponer(UserId actorId, PlanDelDia plan, String resumen) {
        InvocacionHerramienta normalizada = new InvocacionHerramienta(NOMBRE,
                Map.of(ARGUMENTO_PLAN, PlanDeRocasNormalizado.delDia(plan)));
        try {
            proponerAccion.proponer(actorId, normalizada, resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento.");
        }
        return ResultadoHerramienta.exito("Propuesta creada: " + resumen + TextoDePlanDeRocas.NO_ESTA_HECHO);
    }
}
