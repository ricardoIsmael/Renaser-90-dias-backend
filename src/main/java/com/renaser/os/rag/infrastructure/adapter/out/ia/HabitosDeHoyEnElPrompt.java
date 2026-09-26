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
 *
 * <p><b>Los hechos van primero y en su propia linea (E-289).</b> Con una linea por habito
 * ("- ULTIMA COMIDA DEL DIA: hecho") el modelo igual contesto a "me salto la ultima comida" que
 * saltarsela lo alejaba de su objetivo: la regla de D-175 le gano al estado, que estaba ahi. Ahora
 * lo hecho abre la lista con la instruccion pegada ("no le propongas hacerlos, saltarlos ni
 * registrarlos otra vez"), y el resto va despues.
 *
 * <p><b>Un habito renombrado lleva los dos nombres (E-290).</b> "Batido de papaya (JUGO VERDE del
 * programa)": sin el del programa, a "se me paso la hora del jugo verde" el modelo no lo podia unir
 * con lo que la persona llama "Batido de papaya" y le contesto que todavia podia registrarlo. Del
 * renombre solo viaja el titulo, nunca el motivo (puede tener datos de salud).
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
        List<HabitoDeHoy> hechos = deHoy.stream().filter(habito -> habito.estado() == EstadoDeHoy.HECHO).toList();
        List<HabitoDeHoy> resto = deHoy.stream().filter(habito -> habito.estado() != EstadoDeHoy.HECHO).toList();
        return "Sus habitos de hoy, al empezar este turno:\n" + hechos(hechos) + "\n" + resto(resto);
    }

    private static String hechos(List<HabitoDeHoy> hechos) {
        if (hechos.isEmpty()) {
            return "Ya hechos hoy: ninguno todavia.";
        }
        return "Ya hechos hoy (no le propongas hacerlos, saltarlos ni registrarlos otra vez): " + hechos.stream()
                .map(HabitosDeHoyEnElPrompt::nombre).collect(Collectors.joining(", ")) + ".";
    }

    private static String resto(List<HabitoDeHoy> resto) {
        if (resto.isEmpty()) {
            return "Los demas de hoy: ninguno, ya hizo todos.";
        }
        return "Los demas de hoy:\n" + resto.stream()
                .map(habito -> "- " + nombre(habito) + ": " + estado(habito))
                .collect(Collectors.joining("\n"));
    }

    /** "Batido de papaya (JUGO VERDE del programa)" si lo renombro (D-133); si no, el titulo a secas. */
    private static String nombre(HabitoDeHoy habito) {
        String propio = titulo(habito.titulo());
        if (habito.tituloDelPrograma() == null) {
            return propio;
        }
        return propio + " (" + titulo(habito.tituloDelPrograma()) + " del programa)";
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
