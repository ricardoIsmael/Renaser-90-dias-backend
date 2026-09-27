package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.services.herramientas.ArgumentosDeAjusteDeRocas.EdicionPedida;
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
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * {@code proponer_editar_objetivo_semanal} (R2, D-177): PROPONE corregir el objetivo semanal de UN eje
 * —titulo, obstaculo, contingencia o como arranca la semana— y la persona lo confirma con un boton. La
 * escritura la hace {@link EditarObjetivoSemanalConfirmable} con {@code EditarDentroDe48hUseCase}, el
 * mismo de {@code PATCH /rocks/weekly/{id}}.
 *
 * <p><b>La ventana se respeta antes de proponer.</b> Para la semana en curso, {@code consultar_rocas}
 * ya dice si el objetivo es editable: fuera de la ventana se contesta con el motivo real y lo que si se
 * puede, sin dejar un boton que va a fallar. La semana siguiente (la que se arma el domingo) no se ve
 * desde aca: la decide {@code rocks} al confirmar. Lo unico que se mira es que exista: la 13 es la
 * ultima del programa y nunca se propone una 14 (D-203).
 *
 * <p><b>La semana queda escrita en la propuesta</b>, como en el cierre: una propuesta del domingo 23:55
 * confirmada el lunes 00:05 edita la semana que la persona vio.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones=true}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class ProponerEditarObjetivoSemanalHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "proponer_editar_objetivo_semanal";

    private static final Logger log = LoggerFactory.getLogger(ProponerEditarObjetivoSemanalHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone corregir el objetivo de la semana de UN eje: su titulo, su obstaculo, su contingencia o como "
                    + "arranca la semana (1 a 10). Manda solo lo que cambia; lo demas queda como esta. NO lo "
                    + "guarda: deja una propuesta y la persona la confirma con un boton en la app. Solo se puede "
                    + "en la ventana de edicion; si ya cerro, te lo dice con el motivo. Usala cuando la persona "
                    + "pida cambiar su objetivo de la semana; para crear uno que no tiene, usa "
                    + "proponer_plan_de_la_semana. No inventes el texto nuevo.",
            List.of(new ParametroHerramienta(ArgumentosDeAjusteDeRocas.EJE, TipoParametroHerramienta.TEXTO,
                            "CUERPO, TRABAJO o RELACIONES.", true),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.TITULO, TipoParametroHerramienta.TEXTO,
                            "El objetivo nuevo, si cambia.", false),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.OBSTACULO, TipoParametroHerramienta.TEXTO,
                            "El obstaculo nuevo, si cambia.", false),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.CONTINGENCIA, TipoParametroHerramienta.TEXTO,
                            "El plan de contingencia nuevo, si cambia.", false),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.AUTOEVALUACION_INICIO,
                            TipoParametroHerramienta.ENTERO, "Como arranca la semana en ese eje, del 1 al 10, si "
                            + "la persona lo dijo.", false),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.SEMANA, TipoParametroHerramienta.TEXTO,
                            "actual (por defecto) o siguiente: la que empieza el lunes, si la armo este domingo.",
                            false)));

    private final ConsultarRocasDelAprendizPort rocasPort;
    private final PlanificarRocasPort planificarPort;
    private final EditarObjetivoSemanalPort editarPort;
    private final ProponerAccionUseCase proponerAccion;

    public ProponerEditarObjetivoSemanalHerramienta(ConsultarRocasDelAprendizPort rocasPort,
                                                    PlanificarRocasPort planificarPort,
                                                    EditarObjetivoSemanalPort editarPort,
                                                    ProponerAccionUseCase proponerAccion) {
        this.rocasPort = rocasPort;
        this.planificarPort = planificarPort;
        this.editarPort = editarPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        EdicionPedida pedida;
        try {
            pedida = ArgumentosDeAjusteDeRocas.leerEdicion(invocacion, planificarPort.ejesValidos());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo(malFormado.getMessage());
        }
        RocasDeLaSemana semana;
        try {
            semana = rocasPort.deLaSemana(actorId);
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo cambiar su objetivo: la cuenta esta suspendida o todavia no "
                    + "tiene el programa de rocas activo.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer la semana", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude consultar su semana en este momento.");
        }
        if (pedida.siguiente()) {
            return semana.numeroSemana() >= EditarObjetivoSemanalPort.ULTIMA_SEMANA
                    ? ResultadoHerramienta.fallo(TextoDeAjustesDeRocas.SIN_SEMANA_SIGUIENTE)
                    : proponer(actorId, semana.numeroSemana() + 1, pedida);
        }
        return impedimentoEnLaSemanaEnCurso(semana, pedida.eje())
                .map(ResultadoHerramienta::fallo)
                .orElseGet(() -> proponer(actorId, semana.numeroSemana(), pedida));
    }

    private Optional<String> impedimentoEnLaSemanaEnCurso(RocasDeLaSemana semana, String eje) {
        Optional<RocaDeLaSemana> objetivo = semana.rocas().stream().filter(r -> r.eje().equals(eje)).findFirst();
        if (objetivo.isEmpty()) {
            return Optional.of("No tiene objetivo de " + TextoDePlanDeRocas.nombreDelEje(eje) + " esta semana: se "
                    + "crea con el plan de la semana, no se edita.");
        }
        if (!objetivo.get().editable()) {
            return Optional.of(TextoDeAjustesDeRocas.ventanaCerrada(editarPort.ventanaDeEdicion()));
        }
        return Optional.empty();
    }

    private ResultadoHerramienta proponer(UserId actorId, int numeroSemana, EdicionPedida pedida) {
        String resumen = TextoDeAjustesDeRocas.resumenDeEdicion(numeroSemana, pedida.eje(), pedida.cambio());
        InvocacionHerramienta normalizada = new InvocacionHerramienta(NOMBRE,
                ArgumentosDeAjusteDeRocas.edicionNormalizada(numeroSemana, pedida.eje(), pedida.cambio()));
        PropuestaCreada creada;
        try {
            creada = proponerAccion.proponer(actorId, normalizada, resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        if (AvisoDePropuesta.yaEstaba(creada)) {
            return AvisoDePropuesta.yaEstabaPendiente(creada);
        }
        return ResultadoHerramienta.exito("Propuesta creada: " + resumen + TextoDePlanDeRocas.NO_ESTA_HECHO);
    }
}
