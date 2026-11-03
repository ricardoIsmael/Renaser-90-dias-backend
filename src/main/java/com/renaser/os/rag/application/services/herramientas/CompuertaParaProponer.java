package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
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

    /** El aviso con el plazo de siempre (90 días); cada persona recibe el suyo con {@link #sinObjetivos(String)}. */
    static final String SIN_OBJETIVOS = sinObjetivos("90 días");

    /**
     * Issue #63: antes de que empiece su programa (dia 0), el motivo por el que no se puede armar el plan es PRIMERO que su programa todavia no empieza. Sin esto
     * el modelo repetia solo «tus objetivos de 90 dias estan pendientes» (la Escuela) y no decia cuando empieza. Vale igual para las tres formaciones.
     */
    static final String PROGRAMA_POR_EMPEZAR = " Su programa todavía no empieza (es el día 0): antes de lo de los objetivos, dile cuándo empieza (la fecha "
            + "está en «Donde esta la persona ahora mismo»); no contestes solo que sus objetivos están pendientes.";

    /** «Sus tres objetivos de 90 días» (o «de 3 meses», «de 1 año»): el plazo es el del programa de ESA persona. */
    static String sinObjetivos(String plazoDelObjetivo) {
        return SIN_PROPUESTA + "todavía no tiene escritos sus tres objetivos de " + plazoDelObjetivo + ", y sin ellos no se "
                + "pueden planificar objetivos. Invítala a escribirlos en la pestaña Plan (o a confirmar los que nacieron de su "
                + "diagnóstico). No le propongas otro plan ni otra acción de objetivos hasta que los tenga (sus hábitos sí se pueden ajustar).";
    }

    /**
     * Spec 036 (D-309): quien todavia no activo su programa no tiene Dia 1, y sin el no hay dias del programa que planificar: {@code rocks} rechaza la
     * fecha con {@code INVALID_DATE}. Quien ya lo activo (empieza manana) si puede planificar: el servidor lo deja, asi que SER tambien.
     */
    static final String PROGRAMA_SIN_ACTIVAR = SIN_PROPUESTA + "todavía no activó su programa (no tiene Día 1), y sin él no hay días del programa "
            + "que planificar: el servidor rechazaría cualquier día. Dile que lo active con el botón «Empezar mañana» (en Hoy, Plan o Training) y que, "
            + "cuando empiece, planificas su día. No le propongas ningún plan del día ni acción de objetivos hasta entonces.";

    private final ConsultarCompuertaDeRocasPort compuertaPort;
    private final ConsultarSituacionDelAprendizPort situacion;

    public CompuertaParaProponer(ConsultarCompuertaDeRocasPort compuertaPort, ConsultarSituacionDelAprendizPort situacion) {
        this.compuertaPort = compuertaPort;
        this.situacion = situacion;
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

    /** Su programa todavia no tiene dia (dia 0). Best-effort: sin lectura de la situacion, {@code false} y el texto sale como antes. */
    private boolean programaPorEmpezar(UserId actorId) {
        try {
            return situacion.de(actorId).map(SituacionDelAprendiz::programaPorEmpezar).orElse(false);
        } catch (RuntimeException falla) {
            log.info("[rag] la propuesta sale sin saber si su programa ya empezó: {}", falla.toString());
            return false;
        }
    }

    /** Para el plan de la semana y los cambios a sus objetivos: solo hace falta la llave (las Rocas Maestras). */
    public Optional<String> bloqueoDeObjetivos(UserId actorId) {
        try {
            return compuertaPort.rocasMaestrasCompletas(actorId) ? Optional.empty()
                    : Optional.of(sinObjetivos(PlazoDelObjetivoDeLaPersona.de(situacion, actorId)) + (programaPorEmpezar(actorId) ? PROGRAMA_POR_EMPEZAR : ""));
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
            return faltan.isEmpty() ? Optional.empty() : Optional.of(sinObjetivoSemanal(faltan, fecha));
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
