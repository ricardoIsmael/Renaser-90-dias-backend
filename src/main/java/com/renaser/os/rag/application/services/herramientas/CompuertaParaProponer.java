package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
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

    /** «Sus tres objetivos de 90 días» (o «de 3 meses», «de 1 año»): el plazo es el del programa de ESA persona. */
    static String sinObjetivos(String plazoDelObjetivo) {
        return SIN_PROPUESTA + "todavía no tiene escritos sus tres objetivos de " + plazoDelObjetivo + ", y sin ellos no se "
                + "pueden planificar objetivos. Invítala a escribirlos en la pestaña Plan (o a confirmar los que nacieron de su "
                + "diagnóstico). No le propongas otro plan ni otra acción de objetivos hasta que los tenga (sus hábitos sí se pueden ajustar).";
    }

    private final ConsultarCompuertaDeRocasPort compuertaPort;
    private final ConsultarSituacionDelAprendizPort situacion;

    public CompuertaParaProponer(ConsultarCompuertaDeRocasPort compuertaPort, ConsultarSituacionDelAprendizPort situacion) {
        this.compuertaPort = compuertaPort;
        this.situacion = situacion;
    }

    /** Para el plan de la semana y los cambios a sus objetivos: solo hace falta la llave (las Rocas Maestras). */
    public Optional<String> bloqueoDeObjetivos(UserId actorId) {
        try {
            return compuertaPort.rocasMaestrasCompletas(actorId) ? Optional.empty()
                    : Optional.of(sinObjetivos(PlazoDelObjetivoDeLaPersona.de(situacion, actorId)));
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
