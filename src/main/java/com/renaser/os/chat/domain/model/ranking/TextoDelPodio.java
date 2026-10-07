package com.renaser.os.chat.domain.model.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Puesto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * El texto que va debajo de la imagen del podio (D-262): tres plantillas aprobadas por el dueño que rotan una por
 * semana (A, B, C, en ese orden), así el grupo no lee lo mismo todos los lunes.
 *
 * <p>Las plantillas nombran al 1.º, 2.º y 3.º. Con empates y con menos de tres se adaptan sin dejar huecos: dos
 * primeros «comparten el primer puesto», una línea sin nadie a quien nombrar no se escribe, y el plural pasa a
 * singular cuando se nombra a una sola persona. El 4.º y el 5.º no van en el texto: están en la imagen.
 */
public final class TextoDelPodio {

    public enum Plantilla { A, B, C }

    private TextoDelPodio() {
    }

    /** @throws IllegalArgumentException si el podio está vacío: sin nadie a quien felicitar no hay texto */
    public static String para(PodioDeLaSemana podio, SemanaDelRanking semana) {
        if (podio.estaVacio()) {
            throw new IllegalArgumentException("Un podio vacío no se publica");
        }
        List<String> lineas = switch (plantillaDe(semana)) {
            case A -> plantillaA(podio.podio());
            case B -> plantillaB(podio.podio());
            case C -> plantillaC(podio.podio());
        };
        return String.join("\n", lineas);
    }

    public static Plantilla plantillaDe(SemanaDelRanking semana) {
        return Plantilla.values()[(int) Math.floorMod(semana.numero(), Plantilla.values().length)];
    }

    private static List<String> plantillaA(List<Puesto> podio) {
        List<String> primeros = nombres(primeros(podio), Puesto::nombre);
        List<String> lineas = new ArrayList<>();
        lineas.add("🏆 ¡Cerramos la semana y este es nuestro podio!");
        lineas.add(primeros.size() == 1
                ? "🥇 Felicidades, " + primeros.get(0) + ", ¡primer puesto! Tu constancia inspira a toda la comunidad."
                : "🥇 Felicidades, " + lista(primeros) + ", ¡comparten el primer puesto! Su constancia inspira a toda la comunidad.");
        List<String> resto = nombres(resto(podio), p -> medalla(p) + " " + p.nombre());
        if (!resto.isEmpty()) {
            lineas.add(lista(resto) + ", ¡qué gran semana! " + (resto.size() == 1
                    ? "Se nota cómo avanzas en tu transformación 👏"
                    : "Se nota cómo avanzan en su transformación 👏"));
        }
        lineas.add("🔥 Y tú, aprendiz, ¿qué esperas para superar a tus compañeros? Esta semana empieza de cero: cada hábito cuenta 💪");
        return lineas;
    }

    private static List<String> plantillaB(List<Puesto> podio) {
        List<String> primeros = nombres(primeros(podio), Puesto::nombre);
        List<String> lineas = new ArrayList<>();
        lineas.add("✨ Ranking general de la semana ✨");
        lineas.add(primeros.size() == 1
                ? "👑 " + primeros.get(0) + " se queda con el 1.er puesto, ¡felicidades!"
                : "👑 " + lista(primeros) + " comparten el 1.er puesto, ¡felicidades!");
        List<String> resto = nombres(resto(podio), p -> p.nombre() + " (" + p.lugar() + ".º)");
        if (!resto.isEmpty()) {
            lineas.add("🌟 " + lista(resto) + ": " + (resto.size() == 1 ? "tu" : "su") + " compromiso se nota día a día.");
        }
        lineas.add("🚀 Aprendiz, la próxima foto puede llevar tu nombre. ¿Qué esperas para superar a tus compañeros? 🔥");
        return lineas;
    }

    private static List<String> plantillaC(List<Puesto> podio) {
        List<String> todos = nombres(podio, p -> medalla(p) + " " + p.nombre());
        return List.of(
                "🌅 ¡Feliz lunes, comunidad Renaser!",
                lista(todos) + (todos.size() == 1
                        ? " lideró la semana. ¡Felicidades por tu constancia! 🙌"
                        : " lideraron la semana. ¡Felicidades por su constancia! 🙌"),
                "💛 A cada aprendiz: no importa dónde estés hoy, lo que cuenta es el paso que das ahora.",
                "💪 ¿Qué esperas para superar a tus compañeros? ¡Nos vemos en el podio! 🏆");
    }

    private static List<Puesto> primeros(List<Puesto> podio) {
        return podio.stream().filter(p -> p.lugar() == 1).toList();
    }

    private static List<Puesto> resto(List<Puesto> podio) {
        return podio.stream().filter(p -> p.lugar() > 1).toList();
    }

    private static List<String> nombres(List<Puesto> puestos, Function<Puesto, String> comoSeNombra) {
        return puestos.stream().map(comoSeNombra).toList();
    }

    private static String medalla(Puesto puesto) {
        return switch (puesto.lugar()) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            default -> "🥉";
        };
    }

    /** «Ana», «Ana y Luis», «Ana, Luis y Carla». */
    private static String lista(List<String> nombres) {
        if (nombres.size() == 1) {
            return nombres.get(0);
        }
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.get(nombres.size() - 1);
    }
}
