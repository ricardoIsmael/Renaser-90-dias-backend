package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
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
 * <p>Caso real (2026-10-02): SER le propuso a una persona sin su Mapa de Renacimiento un plan para sus
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

    static final String SIN_MAPA = SIN_PROPUESTA + "todavía no completó su Mapa de Renacimiento (el del día 7), "
            + "y sin él no se pueden planificar objetivos: de ahí salen sus tres objetivos de 90 días. Invítala a "
            + "completarlo en la pestaña Plan, botón \"Ir al Mapa de Renacimiento\". No le propongas otro plan ni "
            + "otra acción de objetivos hasta que lo complete (sus hábitos sí se pueden ajustar).";

    static final String MAPA_SIN_ACTIVAR = SIN_PROPUESTA + "su Mapa de Renacimiento está respondido, pero sus "
            + "tres objetivos de 90 días (Rocas Maestras) no quedaron creados, y sin ellos no se pueden planificar "
            + "objetivos. Díselo con claridad y sugiérele escribir a soporte para que lo revisen. No le propongas "
            + "otro plan ni otra acción de objetivos (sus hábitos sí se pueden ajustar).";

    private final ConsultarCompuertaDeRocasPort compuertaPort;
    private final ConsultarMapaDeRenacimientoPort mapaPort;

    public CompuertaParaProponer(ConsultarCompuertaDeRocasPort compuertaPort, ConsultarMapaDeRenacimientoPort mapaPort) {
        this.compuertaPort = compuertaPort;
        this.mapaPort = mapaPort;
    }

    /** Para el plan de la semana y los cambios a sus objetivos: solo hace falta la llave (las Rocas Maestras). */
    public Optional<String> bloqueoDeObjetivos(UserId actorId) {
        try {
            return compuertaPort.rocasMaestrasCompletas(actorId) ? Optional.empty() : Optional.of(sinMaestras(actorId));
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

    /** Sin Mapa, o si no se puede leer, el camino es completarlo; con Mapa, la activacion fallo. */
    private String sinMaestras(UserId actorId) {
        try {
            return mapaPort.de(actorId).tieneMapa() ? MAPA_SIN_ACTIVAR : SIN_MAPA;
        } catch (RuntimeException falla) {
            log.info("[rag] sin Rocas Maestras y sin poder leer el Mapa: {}", falla.toString());
            return SIN_MAPA;
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
