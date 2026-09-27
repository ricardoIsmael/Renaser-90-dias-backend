package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.services.herramientas.ArgumentosDeAjusteDeRocas.AccionPedida;
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
import java.util.NoSuchElementException;

/**
 * {@code proponer_agregar_accion} (R2, D-177): PROPONE sumar UNA accion a un dia que todavia no llego,
 * sin tocar las que ese dia ya tiene. La escritura la hace {@link AgregarAccionConfirmable} con
 * {@code AgregarRocaDiariaUseCase} (via {@code rocks.api.AgregarAccionAlDiaPort}).
 *
 * <p><b>Por que no {@code proponer_plan_del_dia}.</b> Aquella reemplaza el dia entero; para sumar una
 * accion habria que reenviar las demas, y lo que el chat no ve (descripcion, acciones internas,
 * puntaje) se perderia.
 *
 * <p><b>Hoy no.</b> El dia en curso se rechaza antes de proponer ("el dia en curso no se reacomoda"):
 * si se puede sumar algo a hoy es una decision del dueno todavia pendiente. Que "hoy" es lo dice
 * {@code rocks} (manana menos un dia, en la zona de la persona), nunca el reloj del servidor.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones=true}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class ProponerAgregarAccionHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "proponer_agregar_accion";

    private static final Logger log = LoggerFactory.getLogger(ProponerAgregarAccionHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone agregar UNA accion al plan de un dia que todavia no llego (por defecto manana), sin tocar "
                    + "las acciones que ese dia ya tiene: va detras de las de su eje. NO la agrega: deja una "
                    + "propuesta y la persona la confirma con un boton en la app. Para hoy no sirve: el dia en "
                    + "curso no se reacomoda. Usala cuando la persona quiera sumar algo a un dia que viene; para "
                    + "armar un dia entero usa proponer_plan_del_dia. No inventes la accion ni la hora.",
            List.of(new ParametroHerramienta(ArgumentosDeAjusteDeRocas.EJE, TipoParametroHerramienta.TEXTO,
                            "CUERPO, TRABAJO o RELACIONES.", true),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.TITULO, TipoParametroHerramienta.TEXTO,
                            "La accion, con las palabras de la persona.", true),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.FECHA, TipoParametroHerramienta.TEXTO,
                            "AAAA-MM-DD. Opcional: sin ella, manana.", false),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.INICIO, TipoParametroHerramienta.TEXTO,
                            "Hora de inicio HH:MM, solo si la dijo.", false),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.FIN, TipoParametroHerramienta.TEXTO,
                            "Hora de fin HH:MM, solo si la dijo.", false)));

    private final ConsultarRocasDelAprendizPort rocasPort;
    private final PlanificarRocasPort planificarPort;
    private final ProponerAccionUseCase proponerAccion;

    public ProponerAgregarAccionHerramienta(ConsultarRocasDelAprendizPort rocasPort, PlanificarRocasPort planificarPort,
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
        AccionPedida pedida;
        try {
            pedida = ArgumentosDeAjusteDeRocas.leerAccion(invocacion, planificarPort.ejesValidos());
        } catch (PlanMalFormadoException malFormado) {
            return ResultadoHerramienta.fallo(malFormado.getMessage());
        }
        LocalDate manana;
        try {
            manana = rocasPort.deManana(actorId).fecha();
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo planificar sus rocas: la cuenta esta suspendida o todavia "
                    + "no tiene el programa de rocas activo.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer las rocas", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude revisar su plan en este momento.");
        }
        LocalDate fecha = pedida.fecha() == null ? manana : pedida.fecha();
        if (fecha.isBefore(manana)) {
            return ResultadoHerramienta.fallo(fecha.equals(manana.minusDays(1)) ? TextoDeAjustesDeRocas.DIA_EN_CURSO
                    : "Ese dia ya paso. Se puede agregar desde manana hasta el ultimo dia de esta semana de programa.");
        }
        return proponer(actorId, fecha, pedida);
    }

    private ResultadoHerramienta proponer(UserId actorId, LocalDate fecha, AccionPedida pedida) {
        String resumen = TextoDeAjustesDeRocas.resumenDeAccion(fecha, pedida.accion());
        InvocacionHerramienta normalizada = new InvocacionHerramienta(NOMBRE,
                ArgumentosDeAjusteDeRocas.accionNormalizada(fecha, pedida.accion()));
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
