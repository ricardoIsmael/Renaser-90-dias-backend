package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diagnostico.ConsultarDiagnosticoDeLaPersonaPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
import com.renaser.os.rag.application.services.InicioDelPrograma;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * La pregunta que el acompanante hace ANTES de dejar una tarjeta que escribe rocas (D-247, E-496): si
 * {@code rocks} la va a rechazar al confirmar, no se crea la tarjeta y el modelo recibe por que, para que
 * se lo diga a la persona y le ofrezca el camino real.
 *
 * <p>Caso real (2026-10-02): SER le propuso a una persona sin objetivos de 90 dias un plan para sus
 * objetivos; al tocar "Confirmar" fallo con {@code ROCKS_LOCKED}. La regla no se copia aca: la responde
 * {@code rocks.api.CompuertaDeRocasFinder}.
 *
 * <p><b>Best-effort.</b> Si la consulta falla, no bloquea: la propuesta sale como antes y {@code rocks}
 * sigue rechazando al confirmar, con un texto legible ({@link TextoDePlanDeRocas#ROCAS_BLOQUEADAS}).
 */
@Component
public class CompuertaParaProponer {

    private static final Logger log = LoggerFactory.getLogger(CompuertaParaProponer.class);

    static final String SIN_PROPUESTA = "No se creó ninguna propuesta: ";

    /** El aviso con el plazo de siempre (90 días) y para quien no tiene diagnóstico; cada persona recibe el suyo con {@link #sinObjetivos(String, boolean)}. */
    static final String SIN_OBJETIVOS = sinObjetivos("90 días");

    /** «Sus tres objetivos de 90 días» (o «de 3 meses», «de 1 año»): el plazo es el del programa de ESA persona. Sin diagnóstico: solo «escríbelos en Plan». */
    static String sinObjetivos(String plazoDelObjetivo) {
        return sinObjetivos(plazoDelObjetivo, false);
    }

    /**
     * Issue #65: el camino a los objetivos depende de lo que la persona tiene. Con un diagnóstico terminado, los objetivos nacieron de él y los confirma en
     * Plan (o los escribe ella). Sin diagnóstico, solo los escribe ella en Plan y el texto no nombra ninguno: SER decía «confirma los de tu diagnóstico» a
     * una cuenta de la Escuela que nunca lo tuvo.
     */
    static String sinObjetivos(String plazoDelObjetivo, boolean nacenDeSuDiagnostico) {
        String camino = nacenDeSuDiagnostico
                ? "Invítala a escribirlos en la pestaña Plan (o a confirmar los que nacieron de su diagnóstico). "
                : "Invítala a escribirlos en la pestaña Plan («Escríbelos en Plan»). ";
        return SIN_PROPUESTA + "todavía no tiene escritos sus tres objetivos de " + plazoDelObjetivo + ", y sin ellos no se "
                + "pueden planificar objetivos. " + camino + "No le propongas otro plan ni otra acción de objetivos hasta que los tenga (sus hábitos sí se pueden ajustar).";
    }

    /**
     * Spec 036 (D-309): quien todavia no activo su programa no tiene Dia 1, y sin el no hay dias del programa que planificar: {@code rocks} rechaza la
     * fecha con {@code INVALID_DATE}. Quien ya lo activo (empieza manana) si puede planificar: el servidor lo deja, asi que SER tambien.
     */
    static final String PROGRAMA_SIN_ACTIVAR = SIN_PROPUESTA + "todavía no activó su programa (no tiene Día 1), y sin él no hay días del programa "
            + "que planificar: el servidor rechazaría cualquier día. Dile que lo active con el botón «Empezar mañana» (en Hoy, Plan o Entrenar) y que, "
            + "cuando empiece, planificas su día. No le propongas ningún plan del día ni acción de objetivos hasta entonces.";

    private final ConsultarCompuertaDeRocasPort compuertaPort;
    private final ConsultarSituacionDelAprendizPort situacion;
    private final ConsultarDiagnosticoDeLaPersonaPort diagnostico;

    public CompuertaParaProponer(ConsultarCompuertaDeRocasPort compuertaPort, ConsultarSituacionDelAprendizPort situacion,
                                 ConsultarDiagnosticoDeLaPersonaPort diagnostico) {
        this.compuertaPort = compuertaPort;
        this.situacion = situacion;
        this.diagnostico = diagnostico;
    }

    /**
     * Para planificar un dia (el plan del dia y agregar una accion): vacio si ya activo su programa o no se puede saber; si no, el motivo para el modelo.
     * Best-effort como el resto: sin lectura de la situacion no se bloquea y sigue decidiendo el servidor.
     */
    public Optional<String> bloqueoPorProgramaSinActivar(UserId actorId) {
        try {
            return situacion.de(actorId).filter(SituacionDelAprendiz::programaSinActivar).map(sinActivar -> PROGRAMA_SIN_ACTIVAR);
        } catch (RuntimeException falla) {
            log.info("[rag] la propuesta sale sin consultar si activó su programa: {}", falla.toString());
            return Optional.empty();
        }
    }

    /** Una sola lectura de la situacion por pregunta. Best-effort: si falla, vacia y el texto sale como antes. */
    private Optional<SituacionDelAprendiz> situacionDe(UserId actorId) {
        try {
            return situacion.de(actorId);
        } catch (RuntimeException falla) {
            log.info("[rag] la propuesta sale sin leer la situacion de la persona: {}", falla.toString());
            return Optional.empty();
        }
    }

    private static String inicioDe(Optional<SituacionDelAprendiz> situacionDeLaPersona) {
        return situacionDeLaPersona.flatMap(InicioDelPrograma::de).map(inicio -> inicio.paraLasHerramientas() + "\n").orElse("");
    }

    /**
     * Issue #65: la linea que abre el resultado de una herramienta de plan u objetivos mientras el programa de la persona no empezo («Su programa empieza
     * mañana (martes 3 de noviembre). … Díselo antes de cualquier otra cosa.»), seguida de un salto de linea; vacia si ya empezo, si todavia no lo activo (sin
     * Dia 1 no hay fecha) o si no se puede saber. Lo dice el texto de la herramienta y no solo el prompt: el modelo lee SIEMPRE lo que la herramienta devuelve,
     * pero no siempre se acuerda de lo que el prompt le pide. La calcula {@link InicioDelPrograma}, la misma cuenta que el prompt.
     */
    public String inicioDelPrograma(UserId actorId) {
        return inicioDe(situacionDe(actorId));
    }

    /** Para el plan de la semana y los cambios a sus objetivos: solo hace falta la llave (las Rocas Maestras). */
    public Optional<String> bloqueoDeObjetivos(UserId actorId) {
        try {
            if (compuertaPort.rocasMaestrasCompletas(actorId)) {
                return Optional.empty();
            }
            Optional<SituacionDelAprendiz> deLaPersona = situacionDe(actorId);
            return Optional.of(inicioDe(deLaPersona) + sinObjetivos(PlazoDelObjetivoDeLaPersona.de(deLaPersona),
                    ObjetivosDelDiagnosticoDeLaPersona.nacieronDeSuDiagnostico(diagnostico, actorId)));
        } catch (RuntimeException falla) {
            log.info("[rag] la propuesta sale sin consultar la compuerta de rocas: {}", falla.toString());
            return Optional.empty();
        }
    }

    /** Para acciones de un dia: la llave, y ademas el objetivo semanal de cada eje en la semana de esa fecha. */
    public Optional<String> bloqueoDelDia(UserId actorId, LocalDate fecha, Collection<String> ejes) {
        Optional<String> sinLlave = bloqueoDeObjetivos(actorId);
        if (sinLlave.isPresent()) {
            return sinLlave;
        }
        try {
            List<String> faltan = compuertaPort.ejesSinObjetivoSemanal(actorId, fecha).stream()
                    .filter(ejes::contains).toList();
            return faltan.isEmpty() ? Optional.empty() : Optional.of(inicioDelPrograma(actorId) + sinObjetivoSemanal(faltan, fecha));
        } catch (RuntimeException falla) {
            log.info("[rag] la propuesta sale sin consultar los objetivos semanales: {}", falla.toString());
            return Optional.empty();
        }
    }

    static String sinObjetivoSemanal(List<String> ejes, LocalDate fecha) {
        String nombres = ejes.stream().map(TextoDePlanDeRocas::nombreDelEje).collect(Collectors.joining(" y "));
        return SIN_PROPUESTA + (ejes.size() == 1 ? nombres + " no tiene" : nombres + " no tienen")
                + " objetivo semanal en la semana del " + TextoDePlanDeRocas.diaYFecha(fecha) + ", y las acciones "
                + "del día cuelgan de ese objetivo. Ofrécele primero definir el objetivo de la semana de ese eje "
                + "(proponer_plan_de_la_semana) y espera a que diga que sí; no propongas por tu cuenta otro plan.";
    }
}
