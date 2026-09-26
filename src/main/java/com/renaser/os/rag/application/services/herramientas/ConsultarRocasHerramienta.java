package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * {@code consultar_rocas} (R0, solo lectura, 2026-09-23): las rocas del aprendiz para hoy, manana,
 * la semana de programa o el mes en curso, y si el plan de manana ya esta creado.
 *
 * <p><b>D-177 (2026-09-26)</b> sumo dos alcances y un agregado: {@code progreso} (el avance de la
 * semana, dia por dia, el ritmo y si el plan de manana esta pendiente o ya no es opcional),
 * {@code noventa} (el objetivo de los 90 dias por eje, con meta, punto de partida y avance) y, en
 * {@code semana}, como arrancaba cada eje y lo que escribio al cerrar la semana anterior. Si ese
 * agregado falla, la semana sale igual y lo dice.
 *
 * <p>No decide nada: la fecha de hoy en la zona del participante, la ventana nocturna de
 * planificacion (18:00 local, {@code VentanaPlanificacionDiaria}), el bloqueo Pareto y la compuerta
 * de "puede crear el plan" los resuelve {@code rocks} con los mismos casos de uso que la app. Aca
 * se valida el argumento, se traducen los fallos y se arma el texto ({@link TextoDeRocas}).
 */
@Component
public class ConsultarRocasHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_rocas";
    public static final String ARGUMENTO_ALCANCE = "alcance";

    private static final Logger log = LoggerFactory.getLogger(ConsultarRocasHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Devuelve las rocas (objetivos) del aprendiz. Con alcance hoy o manana: cada roca diaria con su eje, "
                    + "orden Pareto, hora de inicio y fin, estado y si le falta la evidencia, mas si el plan de "
                    + "manana ya esta creado y cuando abre la ventana de planificacion. Con semana: los objetivos "
                    + "semanales con su obstaculo, su contingencia y como arrancaba, mas lo que escribio al cerrar "
                    + "la semana anterior (bloqueo y correccion). Con progreso: el avance de la semana en %, cada "
                    + "dia con lo completado, el ritmo de los ultimos 7 dias y si el plan de manana esta pendiente. "
                    + "Con mes: el objetivo del mes en curso por eje. Con noventa: el objetivo de los 90 dias por "
                    + "eje, con su meta y cuanto lleva. Usala antes de responder sobre sus rocas, su plan o sus "
                    + "objetivos; no los supongas.",
            List.of(new ParametroHerramienta(ARGUMENTO_ALCANCE, TipoParametroHerramienta.TEXTO,
                    "Uno de: hoy, manana, semana, progreso, mes, noventa. Omitelo para hoy.", false)));

    private final ConsultarRocasDelAprendizPort rocasPort;

    public ConsultarRocasHerramienta(ConsultarRocasDelAprendizPort rocasPort) {
        this.rocasPort = rocasPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return Alcance.desde(invocacion.argumento(ARGUMENTO_ALCANCE))
                .map(alcance -> consultarTraduciendoFallos(actorId, alcance))
                .orElseGet(() -> ResultadoHerramienta.fallo(
                        "El alcance tiene que ser uno de: hoy, manana, semana, progreso, mes o noventa."));
    }

    private ResultadoHerramienta consultarTraduciendoFallos(UserId actorId, Alcance alcance) {
        try {
            return ResultadoHerramienta.exito(consultar(actorId, alcance));
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo consultar sus rocas: la cuenta esta suspendida o todavia "
                    + "no tiene el programa de rocas activo.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer las rocas", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude consultar sus rocas en este momento.");
        }
    }

    private String consultar(UserId actorId, Alcance alcance) {
        return switch (alcance) {
            case HOY -> TextoDeRocas.delDia("hoy", rocasPort.deHoy(actorId), true);
            case MANANA -> TextoDeRocas.delDia("manana", rocasPort.deManana(actorId), false);
            case SEMANA -> TextoDeRocas.deLaSemana(rocasPort.deLaSemana(actorId)) + cierreAnterior(actorId);
            case PROGRESO -> TextoDeObjetivos.progreso(rocasPort.progresoDeLaSemana(actorId));
            case MES -> TextoDeRocas.delMes(rocasPort.delMes(actorId));
            case NOVENTA -> TextoDeObjetivos.noventa(rocasPort.objetivosDeNoventaDias(actorId));
        };
    }

    /** Un agregado: si falla, la semana que ya se leyo sale igual, avisando que falta esto. */
    private String cierreAnterior(UserId actorId) {
        try {
            return TextoDeObjetivos.cierreAnterior(rocasPort.cierreDeLaSemanaAnterior(actorId));
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer el cierre de la semana anterior", NOMBRE, falla);
            return "\n(No pude leer el cierre de la semana anterior.)";
        }
    }

    /** Lo que el modelo puede pedir. Se acepta "mañana" con enie: el modelo la escribe igual. */
    enum Alcance {
        HOY, MANANA, SEMANA, PROGRESO, MES, NOVENTA;

        /** Vacio si el valor no es ninguno de estos; omitido o en blanco es {@link #HOY}. */
        static Optional<Alcance> desde(String valor) {
            if (valor == null || valor.isBlank()) {
                return Optional.of(HOY);
            }
            String normalizado = valor.trim().toLowerCase(Locale.ROOT).replace('ñ', 'n');
            return Arrays.stream(values())
                    .filter(alcance -> alcance.name().toLowerCase(Locale.ROOT).equals(normalizado))
                    .findFirst();
        }
    }
}
