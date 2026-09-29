package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.plan.CrearHabitoPersonalPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.habitopersonal.HabitoPersonalPedido;
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
import java.util.Optional;

/**
 * {@code proponer_crear_habito_personal} (D-229, pedido del dueño del 2026-09-29): la persona le
 * pide a SER un habito nuevo y SER deja una tarjeta para crearlo. No escribe: la escritura es
 * {@link CrearHabitoPersonalConfirmable}, que corre el mismo caso de uso que el "Crear habito" de
 * Training ({@code POST /api/v1/habits}).
 *
 * <p><b>La categoria no se adivina.</b> Es obligatoria para el negocio pero se declara opcional
 * para el modelo a proposito: un parametro obligatorio empuja al modelo a rellenarlo aunque la
 * persona no lo haya dicho. Si falta, la herramienta no propone y le dice al modelo que la pregunte
 * ({@link ArgumentosDeHabitoPersonal#SIN_CATEGORIA}).
 *
 * <p><b>Valida antes de proponer</b> para no ofrecer un boton que va a fallar: tiene que haber
 * programa y cuenta activa (la lectura del plan lo rechaza, igual que al staff sin programa), la
 * hora tiene que caber en el dia, y no puede existir ya un habito con ese nombre en su plan (del
 * programa o propio, pausado o no). Una propuesta identica pendiente no se repite (D-176).
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones} prendido: sin botones
 * en la app, una propuesta no tiene quien la confirme.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeCrearHabitoPersonal implements HerramientaAgente {

    public static final String NOMBRE = "proponer_crear_habito_personal";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeCrearHabitoPersonal.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone crear un habito PROPIO nuevo en el plan de la persona. Usala solo si pide agregar o crear "
                    + "un habito que no tiene. NO lo crea: deja una propuesta y la persona tiene que tocar "
                    + "Confirmar en la app; nunca digas que ya quedo creado. La categoria es la dimension de "
                    + "Training (Cuerpo, Mente, Emociones o Espiritu): si la persona no la dijo, NO llames todavia, "
                    + "preguntasela primero en una frase. Hora, dias y meta solo si los dijo: nunca los inventes.",
            List.of(ParametroHerramienta.obligatorio(ArgumentosDeHabitoPersonal.NOMBRE, TipoParametroHerramienta.TEXTO,
                            "Como se llama el habito, corto y con las palabras de la persona (por ejemplo 'Leer 20 "
                                    + "minutos')."),
                    new ParametroHerramienta(ArgumentosDeHabitoPersonal.CATEGORIA, TipoParametroHerramienta.TEXTO,
                            "'Cuerpo', 'Mente', 'Emociones' o 'Espiritu', la que eligio la persona. Si no la dijo, "
                                    + "no llames la herramienta: preguntasela.", false),
                    new ParametroHerramienta(ArgumentosDeHabitoPersonal.HORA, TipoParametroHerramienta.TEXTO,
                            "Hora del dia en HH:mm de 24 horas (por ejemplo 07:30). Omitela si no la dijo.", false),
                    new ParametroHerramienta(ArgumentosDeHabitoPersonal.DIAS, TipoParametroHerramienta.TEXTO,
                            "Dias de la semana separados por coma (por ejemplo 'lunes, miercoles, viernes'). "
                                    + "Omitelo si no los dijo: queda todos los dias.", false),
                    new ParametroHerramienta(ArgumentosDeHabitoPersonal.META, TipoParametroHerramienta.TEXTO,
                            "La meta, si la dijo (por ejemplo '20 paginas'). Omitela si no.", false)));

    private final GestionarPlanDeHabitosPort planPort;
    private final CrearHabitoPersonalPort crearPort;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeCrearHabitoPersonal(GestionarPlanDeHabitosPort planPort, CrearHabitoPersonalPort crearPort,
                                          ProponerAccionUseCase proponerAccion) {
        this.planPort = planPort;
        this.crearPort = crearPort;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        HabitoPersonalPedido pedido;
        try {
            pedido = ArgumentosDeHabitoPersonal.leer(invocacion, crearPort.ultimaHoraDeInicio());
        } catch (PropuestaImposibleException motivo) {
            return ResultadoHerramienta.fallo(motivo.getMessage());
        }
        return LecturaDelPlan.conPlan(planPort, actorId, plan -> proponerSiNoLoTiene(actorId, plan, pedido));
    }

    private ResultadoHerramienta proponerSiNoLoTiene(UserId actorId, PlanDelAprendiz plan, HabitoPersonalPedido pedido) {
        Optional<HabitoDelPlan> existente = mismoNombre(plan, pedido);
        if (existente.isPresent()) {
            return ResultadoHerramienta.fallo(yaLoTiene(existente.get()));
        }
        String resumen = pedido.resumen();
        PropuestaCreada creada;
        try {
            creada = proponerAccion.proponer(actorId, ArgumentosDeHabitoPersonal.normalizada(NOMBRE, pedido), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        if (AvisoDePropuesta.yaEstaba(creada)) {
            return AvisoDePropuesta.yaEstabaPendiente(creada);
        }
        return AvisoDePropuesta.creada(resumen, pedido.horaElegida() ? null : horaPorDefecto(pedido));
    }

    /** Compartido con la confirmacion: entre proponer y confirmar la persona pudo crearlo a mano. */
    static Optional<HabitoDelPlan> mismoNombre(PlanDelAprendiz plan, HabitoPersonalPedido pedido) {
        return plan.habitos().stream().filter(habito -> pedido.seLlamaIgualQue(habito.titulo())).findFirst();
    }

    private static String yaLoTiene(HabitoDelPlan habito) {
        return "Ya tiene un habito llamado '" + habito.titulo() + "'" + (habito.pausadoHoy() ? " (esta pausado)" : "")
                + ": no se propuso otro igual. Diselo en una frase; si quiere cambiarle la hora o los dias, eso se "
                + "hace sobre el que ya tiene, y si quiere uno distinto, que le ponga otro nombre.";
    }

    private static String horaPorDefecto(HabitoPersonalPedido pedido) {
        return "No dio hora: quedo propuesto a las " + ArgumentosDeHorario.texto(pedido.hora())
                + ", como en Training; si quiere otra, puede decirtela antes de confirmar.";
    }
}
