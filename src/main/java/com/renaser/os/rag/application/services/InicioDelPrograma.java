package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Cuando empieza el programa de quien todavia no lo empezo (spec 036, issue #65): el Dia 1 que activo, dicho como «manana», «hoy» o «el jueves 5 de noviembre».
 *
 * <p>Lo calcula UNA sola vez, aca, y lo usan las dos formas en que SER se entera: la situacion que va en el prompt
 * ({@code GoogleGenAiRenasiaChatAdapter.programaPorEmpezar}) y el texto que devuelven las herramientas de objetivos y de plan
 * ({@link #paraLasHerramientas()}). El prompt ya lo pedia y el modelo no siempre lo cumplia (issue #65): en el resultado de la herramienta, que el modelo SI
 * lee antes de contestar, la fecha no depende de que se acuerde.
 *
 * @param diaUno  el Dia 1 activado
 * @param momento si empieza manana, hoy o mas adelante, respecto de hoy EN SU ZONA
 */
public record InicioDelPrograma(LocalDate diaUno, Momento momento) {

    public enum Momento { MANANA, HOY, MAS_ADELANTE }

    /** «martes 3 de noviembre»: el dia de la semana y la fecha, sin el año, como la dice la app («Tu programa empieza el martes 3 de noviembre»). */
    private static final DateTimeFormatter DIA_DE_INICIO =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"));

    /**
     * Vacio si su programa ya empezo, o si todavia no lo activo (sin Dia 1 no hay fecha que decir: eso lo dicen «Empezar manana» y
     * {@code PROGRAMA_SIN_ACTIVAR}, no una fecha inventada).
     */
    public static Optional<InicioDelPrograma> de(SituacionDelAprendiz situacion) {
        if (situacion == null || !situacion.programaPorEmpezar() || situacion.diaUno() == null) {
            return Optional.empty();
        }
        LocalDate hoy = situacion.hoy();
        long dias = hoy == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(hoy, situacion.diaUno());
        Momento momento = dias == 1 ? Momento.MANANA : dias <= 0 && hoy != null ? Momento.HOY : Momento.MAS_ADELANTE;
        return Optional.of(new InicioDelPrograma(situacion.diaUno(), momento));
    }

    /** «martes 3 de noviembre». */
    public String fecha() {
        return diaUno.format(DIA_DE_INICIO);
    }

    /** «manana (martes 3 de noviembre)», «hoy (...)» o «el jueves 5 de noviembre»; sin la ñ, como el resto del prompt de la situacion. */
    public String cuandoSinEnie() {
        return switch (momento) {
            case MANANA -> "manana (" + fecha() + ")";
            case HOY -> "hoy (" + fecha() + ")";
            case MAS_ADELANTE -> "el " + fecha();
        };
    }

    /**
     * La linea que abre el resultado de una herramienta de objetivos o de plan mientras el programa no empieza (issue #65): cuando empieza y la orden de
     * decirlo primero. Es la misma para las tres formaciones: el plazo («90 días», «3 meses», «1 año») ya lo dicen las frases de los objetivos.
     */
    public String paraLasHerramientas() {
        String cuando = switch (momento) {
            case MANANA -> "mañana (" + fecha() + ")";
            case HOY -> "hoy (" + fecha() + ")";
            case MAS_ADELANTE -> "el " + fecha();
        };
        return "Su programa empieza " + cuando + ". Todavía no tiene día ni fase. Díselo antes de cualquier otra cosa.";
    }
}
