package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort.Panorama;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort.ProximoEvento;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * "Donde estoy": dia del programa, fase, fecha y hora en la zona de la persona, coherencia de la
 * semana y proximo evento (R0, solo lectura; {@code PROPUESTA_ACOMPANANTE_90_DIAS.md} §4).
 *
 * <p>El dia y la fase salen de {@link ConsultarSituacionDelAprendizPort}, el mismo que ya alimenta
 * el prompt: una sola fuente, para que la herramienta y el prompt no puedan decir dias distintos.
 *
 * <p><b>Toda cuenta de fechas se hace aca</b>, con un unico instante del {@link Clock} y en la zona
 * del participante (regla 02): el modelo recibe "manana a las 19:00", no un instante UTC que
 * tendria que convertir.
 *
 * <p><b>Racha y puntos de liga no estan</b>: ningun contrato publico los expone todavia (viven en
 * {@code points.application} y {@code points.domain}). La respuesta lo dice, para que el modelo no
 * los invente.
 */
@Component
public class ConsultarResumenDelProgramaHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_resumen_del_programa";

    static final int DIAS_DEL_PROGRAMA = 90;
    static final int FASES_DEL_PROGRAMA = 4;

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve donde esta parado el aprendiz en su programa de 90 dias: el dia (N de 90) y la fase, "
                    + "la fecha y la hora de ahora en su zona horaria, su coherencia de los ultimos 7 dias y su "
                    + "proximo evento del calendario. Usala cuando pregunte que dia u hora es para el, como "
                    + "viene su semana o que evento tiene a continuacion. No trae racha ni puntos de liga.");

    private static final DateTimeFormatter FECHA_Y_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Locale CASTELLANO = Locale.forLanguageTag("es");

    private final ConsultarSituacionDelAprendizPort situacionPort;
    private final ConsultarPanoramaDelProgramaPort panoramaPort;
    private final Clock clock;

    public ConsultarResumenDelProgramaHerramienta(ConsultarSituacionDelAprendizPort situacionPort,
                                                  ConsultarPanoramaDelProgramaPort panoramaPort, Clock clock) {
        this.situacionPort = situacionPort;
        this.panoramaPort = panoramaPort;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<SituacionDelAprendiz> situacion = situacionPort.de(actorId);
        if (situacion.isEmpty()) {
            return ResultadoHerramienta.exito("No esta inscrito en el programa de 90 dias: no tiene dia ni fase.");
        }
        Instant ahora = clock.now();
        return panoramaPort.de(actorId, ahora)
                .map(panorama -> ResultadoHerramienta.exito(resumen(situacion.get(), panorama, ahora)))
                .orElseGet(() -> ResultadoHerramienta.fallo("No pude consultar su programa en este momento."));
    }

    private static String resumen(SituacionDelAprendiz situacion, Panorama panorama, Instant ahora) {
        ZonedDateTime ahoraEnSuZona = ahora.atZone(panorama.zona());
        return String.join("\n",
                lineaDelDia(situacion),
                "Ahora, en su zona (" + panorama.zona().getId() + "): " + diaDeSemana(ahoraEnSuZona.toLocalDate())
                        + " " + FECHA_Y_HORA.format(ahoraEnSuZona) + ".",
                lineaDeCoherencia(panorama),
                lineaDelEvento(panorama, ahoraEnSuZona),
                "Racha y puntos de liga: no disponibles en esta consulta; los ve en la pantalla de Inicio.");
    }

    /** Dia 0 es una inscripcion cuyo programa todavia no arranco: no se le dice "dia 0 de 90". */
    private static String lineaDelDia(SituacionDelAprendiz situacion) {
        if (situacion.diaPrograma() < 1) {
            return "Su programa de 90 dias todavia no empezo.";
        }
        return "Dia del programa: " + situacion.diaPrograma() + " de " + DIAS_DEL_PROGRAMA
                + " (fase " + situacion.fase() + " de " + FASES_DEL_PROGRAMA + ").";
    }

    private static String lineaDeCoherencia(Panorama panorama) {
        return panorama.coherencia()
                .map(porcentaje -> "Coherencia de los ultimos 7 dias: " + porcentaje.toPlainString()
                        + "% de las acciones diarias planificadas, cumplidas.")
                .orElse("Coherencia: no planifico acciones diarias en los ultimos 7 dias, asi que no hay dato.");
    }

    /** "hoy", "manana" o "en N dias" se cuenta entre fechas LOCALES, nunca entre fechas UTC. */
    private static String lineaDelEvento(Panorama panorama, ZonedDateTime ahoraEnSuZona) {
        if (panorama.proximoEvento().isEmpty()) {
            return "Proximo evento: no tiene eventos proximos en su calendario.";
        }
        ProximoEvento evento = panorama.proximoEvento().get();
        ZonedDateTime iniciaEnSuZona = evento.iniciaEn().atZone(panorama.zona());
        long diasHastaElEvento = ChronoUnit.DAYS.between(ahoraEnSuZona.toLocalDate(), iniciaEnSuZona.toLocalDate());
        return "Proximo evento: " + evento.titulo() + ", " + cuandoEs(diasHastaElEvento) + " ("
                + diaDeSemana(iniciaEnSuZona.toLocalDate()) + " " + FECHA_Y_HORA.format(iniciaEnSuZona)
                + ", hora local).";
    }

    private static String cuandoEs(long diasHastaElEvento) {
        if (diasHastaElEvento <= 0) {
            return "hoy";
        }
        return diasHastaElEvento == 1 ? "manana" : "en " + diasHastaElEvento + " dias";
    }

    private static String diaDeSemana(LocalDate fecha) {
        return fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, CASTELLANO);
    }
}
