package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.ReglasDelCierre;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.RevisionDelEje;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
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

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * {@code proponer_cerrar_semana} (R2, Domingo Ritual, 2026-09-23): PROPONE el cierre de la semana
 * —por eje, autoevaluacion, bloqueo principal y correccion— y la persona lo confirma con un boton
 * (D-153). La escritura real la hace {@link CerrarSemanaConfirmable} con {@code CerrarSemanaUseCase},
 * el mismo de {@code PATCH /rocks/weekly/{id}/review}.
 *
 * <p>El acompanante guia la reflexion en la conversacion; esta herramienta solo recibe las respuestas
 * de la persona. <b>Antes de proponer</b> lee la semana en curso con {@code consultar_rocas}
 * ({@link ConsultarRocasDelAprendizPort}): tiene que haber objetivos, cada eje pedido tiene que tener
 * el suyo, y ninguno puede estar ya cerrado (desde el chat no se reescribe una revision).
 *
 * <p><b>La semana queda escrita en la propuesta.</b> Es la que devolvio {@code rocks} al proponer: un
 * domingo 23:55 confirmado el lunes 00:05 cierra la semana que la persona vio, no la nueva.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones=true}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class ProponerCerrarSemanaHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "proponer_cerrar_semana";
    public static final String ARGUMENTO_CIERRE = "cierre";

    private static final Logger log = LoggerFactory.getLogger(ProponerCerrarSemanaHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone cerrar la semana en curso (el Domingo Ritual): por cada eje con objetivo semanal, la "
                    + "autoevaluacion de como le fue, el bloqueo principal y la correccion para la semana que "
                    + "viene. NO lo guarda: deja una propuesta y la persona la confirma con un boton en la app. "
                    + "Antes llama a consultar_rocas con alcance semana para ver sus objetivos y cuales ya estan "
                    + "cerrados. Guia la reflexion conversando, eje por eje, y usa las respuestas de la persona tal "
                    + "como las dijo; no las inventes ni completes. Nunca digas que quedo cerrada: dile que "
                    + "confirme con el boton.",
            List.of(new ParametroHerramienta(ARGUMENTO_CIERRE, TipoParametroHerramienta.TEXTO,
                    "Un JSON escrito como texto: {\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":7,"
                            + "\"bloqueoPrincipal\":\"Las reuniones me comieron la tarde\","
                            + "\"correccion\":\"Bloquear 2 horas cada manana\"}]}. eje es CUERPO, TRABAJO o "
                            + "RELACIONES, uno por revision. autoevaluacion es un numero entero del 1 al 10. "
                            + "bloqueoPrincipal y correccion son obligatorios. No agregues otros campos.", true)));

    private final ConsultarRocasDelAprendizPort rocasPort;
    private final CerrarSemanaDeRocasPort cierrePort;
    private final ProponerAccionUseCase proponerAccion;

    public ProponerCerrarSemanaHerramienta(ConsultarRocasDelAprendizPort rocasPort, CerrarSemanaDeRocasPort cierrePort,
                                           ProponerAccionUseCase proponerAccion) {
        this.rocasPort = rocasPort;
        this.cierrePort = cierrePort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        ReglasDelCierre reglas = cierrePort.reglas();
        List<RevisionDelEje> revisiones;
        try {
            revisiones = CierreDeSemanaJson.leerDelModelo(invocacion.argumento(ARGUMENTO_CIERRE), reglas);
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo(malFormado.getMessage());
        }
        RocasDeLaSemana semana;
        try {
            semana = rocasPort.deLaSemana(actorId);
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo cerrar su semana: la cuenta esta suspendida o todavia no "
                    + "tiene el programa de rocas activo.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer la semana", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude consultar su semana en este momento.");
        }
        Optional<String> impedimento = TextoDeCierreDeSemana.motivoParaNoProponer(semana, revisiones);
        if (impedimento.isPresent()) {
            return ResultadoHerramienta.fallo(impedimento.get());
        }
        return proponer(actorId, semana, revisiones, reglas);
    }

    private ResultadoHerramienta proponer(UserId actorId, RocasDeLaSemana semana, List<RevisionDelEje> revisiones,
                                          ReglasDelCierre reglas) {
        String resumen = TextoDeCierreDeSemana.resumen(semana, revisiones, reglas);
        InvocacionHerramienta normalizada = new InvocacionHerramienta(NOMBRE,
                Map.of(ARGUMENTO_CIERRE, CierreDeSemanaJson.normalizado(semana.numeroSemana(), revisiones)));
        try {
            proponerAccion.proponer(actorId, normalizada, resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento.");
        }
        return ResultadoHerramienta.exito("Propuesta creada: " + resumen + TextoDeCierreDeSemana.SIN_PUNTOS
                + TextoDePlanDeRocas.NO_ESTA_HECHO);
    }
}
