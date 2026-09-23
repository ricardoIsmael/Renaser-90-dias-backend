package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.calendario.ConsultarEventosDelAprendizPort;
import com.renaser.os.rag.application.ports.out.calendario.ConsultarEventosDelAprendizPort.AgendaDeEventos;
import com.renaser.os.rag.application.ports.out.calendario.ConsultarEventosDelAprendizPort.EventoDeLaAgenda;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * {@code consultar_eventos} (R0, solo lectura, 2026-09-23): los eventos del calendario que ve el
 * aprendiz hoy o en los proximos siete dias, en su hora local y con su confirmacion de asistencia.
 *
 * <p>No decide nada: que eventos le corresponden (audiencia, elegibilidad, celula, curso) y como se
 * expanden las recurrencias lo resuelve {@code calendar} con el mismo caso de uso que
 * {@code GET /calendar/events}; el rango en dias locales lo arma {@code calendar.api}. Aca se
 * valida el argumento y se arma el texto.
 */
@Component
public class ConsultarEventosHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_eventos";
    public static final String ARGUMENTO_ALCANCE = "alcance";

    /** "semana" = hoy y los seis dias siguientes: una ventana movil, no la semana de programa. */
    static final int DIAS_DE_LA_SEMANA = 7;

    private static final DateTimeFormatter FECHA_Y_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Logger log = LoggerFactory.getLogger(ConsultarEventosHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Devuelve los eventos del calendario del programa que le corresponden al aprendiz, con fecha, hora "
                    + "local de inicio y fin y si confirmo asistencia. Usala antes de responder que eventos tiene o de planificar su dia o su semana.",
            List.of(new ParametroHerramienta(ARGUMENTO_ALCANCE, TipoParametroHerramienta.TEXTO,
                    "hoy (solo hoy) o semana (hoy y los 6 dias siguientes). Omitelo para semana.", false)));

    private final ConsultarEventosDelAprendizPort eventosPort;

    public ConsultarEventosHerramienta(ConsultarEventosDelAprendizPort eventosPort) {
        this.eventosPort = eventosPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        return diasDelAlcance(invocacion.argumento(ARGUMENTO_ALCANCE))
                .map(dias -> consultar(actorId, dias))
                .orElseGet(() -> ResultadoHerramienta.fallo("El alcance tiene que ser hoy o semana."));
    }

    /** Vacio si el valor no es ninguno de los dos; omitido o en blanco es la semana. */
    static Optional<Integer> diasDelAlcance(String alcance) {
        if (alcance == null || alcance.isBlank()) {
            return Optional.of(DIAS_DE_LA_SEMANA);
        }
        return switch (alcance.trim().toLowerCase(Locale.ROOT)) {
            case "hoy" -> Optional.of(1);
            case "semana" -> Optional.of(DIAS_DE_LA_SEMANA);
            default -> Optional.empty();
        };
    }

    private ResultadoHerramienta consultar(UserId actorId, int dias) {
        try {
            return ResultadoHerramienta.exito(textoDe(eventosPort.proximosDias(actorId, dias)));
        } catch (NoSuchElementException sinCuenta) {
            return ResultadoHerramienta.fallo("No encontre la cuenta para consultar su calendario.");
        } catch (NotAuthorizedException suspendida) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida: no puedo consultar su calendario.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer el calendario", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude consultar su calendario en este momento.");
        }
    }

    private static String textoDe(AgendaDeEventos agenda) {
        String rango = agenda.desde().equals(agenda.hasta()) ? "del " + agenda.desde()
                : "del " + agenda.desde() + " al " + agenda.hasta();
        if (agenda.eventos().isEmpty()) {
            return "No tiene eventos del calendario " + rango + ".";
        }
        StringBuilder texto = new StringBuilder("Eventos ").append(rango).append(" (hora local):\n");
        agenda.eventos().forEach(evento -> texto.append(lineaDe(evento)).append('\n'));
        return texto.toString().trim();
    }

    private static String lineaDe(EventoDeLaAgenda evento) {
        return "- " + franjaDe(evento.iniciaLocal(), evento.terminaLocal()) + " | " + evento.titulo()
                + " | asistencia=" + asistenciaDe(evento.asistencia());
    }

    private static String franjaDe(LocalDateTime inicia, LocalDateTime termina) {
        if (termina == null) {
            return FECHA_Y_HORA.format(inicia);
        }
        DateTimeFormatter formatoFin = termina.toLocalDate().equals(inicia.toLocalDate()) ? HORA : FECHA_Y_HORA;
        return FECHA_Y_HORA.format(inicia) + " a " + formatoFin.format(termina);
    }

    private static String asistenciaDe(String asistencia) {
        if (asistencia == null) {
            return "sin responder";
        }
        return switch (asistencia) {
            case "ASISTE" -> "confirmo que va";
            case "NO_ASISTE" -> "dijo que no va";
            case "QUIZAS" -> "quizas";
            default -> asistencia;
        };
    }
}
