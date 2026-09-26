package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.EstadoDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.HabitoDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.HabitoPausado;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * El texto de los habitos de hoy dentro de "Donde esta la persona ahora mismo" (D-176). Va en cada
 * turno, asi que es lo mas corto que sigue siendo inequivoco: una linea por habito de hoy, en
 * palabras ("hecho", "vencido"), y una linea con los pausados. Sin ids (E-270).
 *
 * <p><b>Los titulos se aplanan y se acotan.</b> Un habito personal o un renombre (D-133) es texto
 * que escribio la persona, y aca termina en el prompt de SISTEMA. Mismo criterio que
 * {@code GoogleGenAiRenasiaChatAdapter.enUnaSolaLinea} con el ambito: sin saltos de linea no se
 * puede dibujar una seccion falsa del prompt.
 */
final class HabitosDeHoyEnElPrompt {

    /** El titulo mas largo del catalogo ronda los 40; un renombre de 60 ya es un parrafo. */
    private static final int LARGO_MAXIMO_DEL_TITULO = 60;
    private static final DateTimeFormatter DIA_Y_FECHA =
            DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.forLanguageTag("es"));

    static final String SIN_DATOS = "(No se pudo leer como van sus habitos de hoy: consultalo con las "
            + "herramientas antes de decir como esta uno.)";

    private HabitosDeHoyEnElPrompt() {
    }

    static String texto(HabitosDeHoy habitos) {
        if (habitos == null) {
            return SIN_DATOS;
        }
        return deHoy(habitos.deHoy()) + "\n" + pausados(habitos.pausados());
    }

    private static String deHoy(List<HabitoDeHoy> deHoy) {
        if (deHoy.isEmpty()) {
            return "Sus habitos de hoy, al empezar este turno: hoy no tiene ninguno generado.";
        }
        return "Sus habitos de hoy, al empezar este turno:\n" + deHoy.stream()
                .map(habito -> "- " + titulo(habito.titulo()) + ": " + estado(habito))
                .collect(Collectors.joining("\n"));
    }

    /** "pide foto" solo en lo que falta entregar: en uno hecho no aporta nada. */
    private static String estado(HabitoDeHoy habito) {
        String palabra = palabra(habito.estado());
        boolean faltaEntregar = habito.estado() == EstadoDeHoy.PENDIENTE || habito.estado() == EstadoDeHoy.EN_CURSO
                || habito.estado() == EstadoDeHoy.VENCIDO;
        return habito.pideFoto() && faltaEntregar ? palabra + ", pide foto" : palabra;
    }

    private static String palabra(EstadoDeHoy estado) {
        return switch (estado) {
            case PENDIENTE -> "pendiente";
            case EN_CURSO -> "en curso";
            case HECHO -> "hecho";
            case VENCIDO -> "vencido (se le paso la hora)";
            case FALLIDO -> "no cumplido";
        };
    }

    private static String pausados(List<HabitoPausado> pausados) {
        if (pausados.isEmpty()) {
            return "Pausados: ninguno.";
        }
        return "Pausados (existen, pero hoy no se le piden): " + pausados.stream()
                .map(habito -> titulo(habito.titulo()) + (habito.hasta() == null ? " (sin fecha de fin)"
                        : " (hasta el " + habito.hasta().format(DIA_Y_FECHA) + ")"))
                .collect(Collectors.joining(", ")) + ".";
    }

    private static String titulo(String titulo) {
        String plano = titulo.replaceAll("\\s+", " ").trim();
        return plano.length() <= LARGO_MAXIMO_DEL_TITULO ? plano : plano.substring(0, LARGO_MAXIMO_DEL_TITULO);
    }
}
