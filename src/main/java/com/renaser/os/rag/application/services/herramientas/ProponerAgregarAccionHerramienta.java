package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
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

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

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
 * <p><b>Un habito no es una accion (E-455).</b> A "manana quiero hacer caminar 40 minutos a las 7 de la
 * noche", con el habito "Caminar 40 minutos" en su plan, el acompanante propuso agregar una accion al
 * plan en vez de cambiarle la hora al habito ese dia. Si la accion nombra un habito del plan, no se
 * propone: se le devuelve al modelo el camino correcto ({@code proponer_cambio_de_horario} con la
 * fecha, D-230). Cuenta cuando el titulo del habito (de al menos {@link #LARGO_MINIMO_DE_UN_HABITO}
 * letras) aparece entero, como palabras, en el de la accion: el modelo propuso el habito como
 * "Caminar" con meta "40 minutos", asi que un umbral mas alto no lo atrapaba. Como "Leer el informe"
 * tambien nombra "Leer", la persona puede querer de verdad una accion aparte: entonces el modelo
 * vuelve a llamar con {@code accion_aparte='si'} y se propone como siempre.
 *
 * <p>Solo existe con {@code renaser.ia.acompanante.confirmacion-con-botones=true}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class ProponerAgregarAccionHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "proponer_agregar_accion";

    private static final Logger log = LoggerFactory.getLogger(ProponerAgregarAccionHerramienta.class);

    /** Mas corto que "Leer" ya no es el nombre de un habito sino una silaba suelta. */
    static final int LARGO_MINIMO_DE_UN_HABITO = 4;
    static final String ACCION_APARTE = "accion_aparte";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone agregar UNA accion al plan de un dia que todavia no llego (por defecto manana), sin tocar "
                    + "las acciones que ese dia ya tiene: va detras de las de su eje. NO la agrega: deja una "
                    + "propuesta y la persona la confirma con un boton en la app. Para hoy no sirve: el dia en "
                    + "curso no se reacomoda. Usala cuando la persona quiera sumar algo a un dia que viene; para "
                    + "armar un dia entero usa proponer_plan_del_dia. No inventes la accion ni la hora. Si nombra un "
                    + "habito que ya tiene (\"caminar 40 minutos\"), no es una accion: cambiale la hora a ese habito "
                    + "con proponer_cambio_de_horario.",
            List.of(new ParametroHerramienta(ArgumentosDeAjusteDeRocas.EJE, TipoParametroHerramienta.TEXTO,
                            "CUERPO, TRABAJO o RELACIONES.", true),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.TITULO, TipoParametroHerramienta.TEXTO,
                            "La accion, con las palabras de la persona.", true),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.FECHA, TipoParametroHerramienta.TEXTO,
                            "AAAA-MM-DD. Opcional: sin ella, manana.", false),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.INICIO, TipoParametroHerramienta.TEXTO,
                            "Hora de inicio HH:MM, solo si la dijo.", false),
                    new ParametroHerramienta(ArgumentosDeAjusteDeRocas.FIN, TipoParametroHerramienta.TEXTO,
                            "Hora de fin HH:MM, solo si la dijo.", false),
                    new ParametroHerramienta(ACCION_APARTE, TipoParametroHerramienta.TEXTO,
                            "'si' solo si la persona pidio claramente una accion aparte que se parece al nombre de "
                                    + "uno de sus habitos. Omitelo siempre que no.", false)));

    private final ConsultarRocasDelAprendizPort rocasPort;
    private final PlanificarRocasPort planificarPort;
    private final ProponerAccionUseCase proponerAccion;
    private final GestionarPlanDeHabitosPort planDeHabitosPort;

    public ProponerAgregarAccionHerramienta(ConsultarRocasDelAprendizPort rocasPort, PlanificarRocasPort planificarPort,
                                            ProponerAccionUseCase proponerAccion,
                                            GestionarPlanDeHabitosPort planDeHabitosPort) {
        this.rocasPort = rocasPort;
        this.planificarPort = planificarPort;
        this.proponerAccion = proponerAccion;
        this.planDeHabitosPort = planDeHabitosPort;
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
        Optional<String> habito = "si".equalsIgnoreCase(String.valueOf(invocacion.argumento(ACCION_APARTE)).strip())
                ? Optional.empty() : habitoQueNombra(actorId, pedida.accion().titulo());
        if (habito.isPresent()) {
            return ResultadoHerramienta.fallo("'" + habito.get() + "' es un habito de su plan, no una accion: no se "
                    + "agrega. Para hacerlo a otra hora ese dia, llama a consultar_horarios con esa fecha y deja "
                    + "proponer_cambio_de_horario con el habito_id, la hora nueva y la fecha " + fecha + ". Solo si "
                    + "pidio claramente una accion aparte, vuelve a llamar con accion_aparte='si'.");
        }
        if (fecha.isBefore(manana)) {
            return ResultadoHerramienta.fallo(fecha.equals(manana.minusDays(1)) ? TextoDeAjustesDeRocas.DIA_EN_CURSO
                    : "Ese dia ya paso. Se puede agregar " + TextoDeAjustesDeRocas.FECHAS_QUE_SE_PUEDEN_AGREGAR + ".");
        }
        return proponer(actorId, fecha, pedida);
    }

    /** Best-effort: sin el plan, se propone como antes (no se bloquea por no poder mirar). */
    private Optional<String> habitoQueNombra(UserId actorId, String tituloDeLaAccion) {
        String accion = normalizado(tituloDeLaAccion);
        try {
            return planDeHabitosPort.planDe(actorId).habitos().stream()
                    .map(GestionarPlanDeHabitosPort.HabitoDelPlan::titulo)
                    .filter(titulo -> normalizado(titulo).length() >= LARGO_MINIMO_DE_UN_HABITO)
                    .filter(titulo -> (" " + accion + " ").contains(" " + normalizado(titulo) + " "))
                    .findFirst();
        } catch (RuntimeException falla) {
            log.info("[rag] {} no pudo leer el plan de habitos: {}", NOMBRE, falla.toString());
            return Optional.empty();
        }
    }

    /** Minusculas, sin tildes y con un solo espacio: "CAMINAR  40 Minutos" es "caminar 40 minutos". */
    private static String normalizado(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
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
