package com.renaser.os.chat.domain.model.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Puesto;
import com.renaser.os.chat.domain.model.ranking.TextoDelPodio.Plantilla;

import java.util.ArrayList;
import java.util.List;

import static com.renaser.os.chat.domain.model.ranking.TextoDelPodio.lista;
import static com.renaser.os.chat.domain.model.ranking.TextoDelPodio.medalla;
import static com.renaser.os.chat.domain.model.ranking.TextoDelPodio.nombres;
import static com.renaser.os.chat.domain.model.ranking.TextoDelPodio.primeros;
import static com.renaser.os.chat.domain.model.ranking.TextoDelPodio.resto;

/**
 * Los textos de Coherencia, Kilómetros y Gimnasio (D-367). Cada ranking habla de lo SUYO, con su propio arranque, su propia
 * felicitación y su propio cierre (no es la plantilla del General con otro título): la Coherencia, de cumplir lo planificado;
 * los Kilómetros, del movimiento; el Gimnasio, de los días entrenados. El del General sigue en {@link TextoDelPodio}.
 *
 * <p>Cada uno tiene sus tres plantillas A, B y C (la misma estructura del General: la A felicita con medallas, la B es una
 * tabla corta y la C es el saludo del lunes). Con empates y con menos de tres se adaptan igual que las del General: los
 * empatados «comparten» el primer puesto, una línea sin nadie a quien nombrar no se escribe y el singular/plural sigue a
 * cuántas personas se nombran.
 *
 * <p>Los formatos reciben el nombre (o la lista de nombres) como {@code %1$s} y, en la felicitación del primer puesto, el
 * valor con su unidad como {@code %2$s} («42,5 km», «5 días», «96,4 %»).
 */
final class TextosDeLosRankings {

    private enum Estilo { MEDALLA, ORDINAL }

    /**
     * @param juntos si es true, la línea del primer puesto nombra a los tres (con medalla) y no hay línea aparte para el resto
     *               (es la forma de la plantilla C); en ese caso los formatos no llevan valor, porque no es el mismo para todos
     */
    private record Molde(String apertura, String lider1, String liderN, String resto1, String restoN, Estilo estilo,
                         boolean juntos, List<String> cierre) {
    }

    private TextosDeLosRankings() {
    }

    static List<String> lineas(TipoDeRankingSemanal tipo, Plantilla plantilla, List<Puesto> podio) {
        Molde molde = moldeDe(tipo, plantilla);
        List<Puesto> primeros = primeros(podio);
        String valor = tipo.conUnidad(primeros.get(0).puntaje());
        List<String> lineas = new ArrayList<>();
        lineas.add(molde.apertura());
        if (molde.juntos()) {
            List<String> todos = nombres(podio, p -> medalla(p) + " " + p.nombre());
            lineas.add((todos.size() == 1 ? molde.lider1() : molde.liderN()).formatted(lista(todos), valor));
        } else {
            List<String> lideres = nombres(primeros, Puesto::nombre);
            lineas.add((lideres.size() == 1 ? molde.lider1() : molde.liderN()).formatted(lista(lideres), valor));
            List<String> otros = nombres(resto(podio), p -> molde.estilo() == Estilo.MEDALLA
                    ? medalla(p) + " " + p.nombre() : p.nombre() + " (" + p.lugar() + ".º)");
            if (!otros.isEmpty()) {
                lineas.add((otros.size() == 1 ? molde.resto1() : molde.restoN()).formatted(lista(otros), valor));
            }
        }
        lineas.addAll(molde.cierre());
        return lineas;
    }

    private static Molde moldeDe(TipoDeRankingSemanal tipo, Plantilla plantilla) {
        return switch (tipo) {
            case COHERENCIA -> coherencia(plantilla);
            case KILOMETROS -> kilometros(plantilla);
            case GIMNASIO -> gimnasio(plantilla);
            case GENERAL -> throw new IllegalArgumentException("El texto del General vive en TextoDelPodio");
        };
    }

    /** Cumplir lo que planificaste: los objetivos del día. */
    private static Molde coherencia(Plantilla plantilla) {
        return switch (plantilla) {
            case A -> new Molde("🎯 ¡Cerramos la semana y estos son los más coherentes!",
                    "🥇 Felicidades, %1$s, ¡primer puesto en coherencia! Cumpliste el %2$s de lo que planificaste.",
                    "🥇 Felicidades, %1$s, ¡comparten el primer puesto en coherencia! Cumplieron el %2$s de lo que planificaron.",
                    "%1$s, ¡qué semana! Lo que planificaste, lo hiciste 👏",
                    "%1$s, ¡qué semana! Lo que planificaron, lo hicieron 👏",
                    Estilo.MEDALLA, false,
                    List.of("📝 Y tú, aprendiz, ¿ya planificaste los objetivos de hoy? Planifica y cumple: así se construye la coherencia 💪"));
            case B -> new Molde("✨ Coherencia de la semana ✨",
                    "👑 %1$s lidera la coherencia: cumplió el %2$s de lo planificado, ¡felicidades!",
                    "👑 %1$s comparten el 1.er puesto en coherencia: cumplieron el %2$s de lo planificado, ¡felicidades!",
                    "🌟 %1$s: cumplir tus objetivos del día también es avanzar.",
                    "🌟 %1$s: cumplir sus objetivos del día también es avanzar.",
                    Estilo.ORDINAL, false,
                    List.of("🚀 Aprendiz, abre tus objetivos de hoy y cumple el primero: la próxima foto puede llevar tu nombre 🎯"));
            case C -> new Molde("🌅 ¡Feliz lunes, comunidad Renaser! Así quedó la coherencia de la semana:",
                    "%1$s cumplió lo que planificó mejor que nadie. ¡Felicidades por tu coherencia! 🙌",
                    "%1$s cumplieron lo que planificaron mejor que nadie. ¡Felicidades por su coherencia! 🙌",
                    null, null, Estilo.MEDALLA, true,
                    List.of("💛 A cada aprendiz: planificar es el primer paso; cumplir es el que te transforma.",
                            "🎯 Esta semana, ¿qué objetivo vas a cumplir primero? ¡Nos vemos en el podio! 🏆"));
        };
    }

    /** Movimiento: la distancia recorrida en la semana. */
    private static Molde kilometros(Plantilla plantilla) {
        return switch (plantilla) {
            case A -> new Molde("🏃 ¡Cerramos la semana y estos son los que más kilómetros recorrieron!",
                    "🥇 Felicidades, %1$s, ¡primer puesto! Recorriste %2$s esta semana: cada paso te acerca a tu mejor versión.",
                    "🥇 Felicidades, %1$s, ¡comparten el primer puesto! Recorrieron %2$s cada uno esta semana: cada paso los acerca a su mejor versión.",
                    "%1$s, ¡qué ritmo! Se nota cómo avanzas, kilómetro a kilómetro 👏",
                    "%1$s, ¡qué ritmo! Se nota cómo avanzan, kilómetro a kilómetro 👏",
                    Estilo.MEDALLA, false,
                    List.of("👟 Y tú, aprendiz, ¿cuántos kilómetros vas a sumar esta semana? Sal a moverte: cada kilómetro cuenta 💪"));
            case B -> new Molde("✨ Kilómetros de la semana ✨",
                    "👑 %1$s se queda con el 1.er puesto con %2$s recorridos, ¡felicidades!",
                    "👑 %1$s comparten el 1.er puesto con %2$s recorridos cada uno, ¡felicidades!",
                    "🌟 %1$s: tus pasos de esta semana se notan.",
                    "🌟 %1$s: sus pasos de esta semana se notan.",
                    Estilo.ORDINAL, false,
                    List.of("🚀 Aprendiz, ponte las zapatillas y camina o corre hoy: la próxima foto puede llevar tu nombre 🏅"));
            case C -> new Molde("🌄 ¡Feliz lunes, comunidad Renaser! Así se recorrió la semana:",
                    "%1$s recorrió más que nadie esta semana. ¡Felicidades por tu energía! 🙌",
                    "%1$s recorrieron más que nadie esta semana. ¡Felicidades por su energía! 🙌",
                    null, null, Estilo.MEDALLA, true,
                    List.of("💛 A cada aprendiz: no importa cuánto camines hoy, lo que importa es que te muevas.",
                            "🏃 ¿Cuántos kilómetros vas a sumar esta semana? ¡Nos vemos en el podio! 🏆"));
        };
    }

    /** Los días entrenados en la semana. */
    private static Molde gimnasio(Plantilla plantilla) {
        return switch (plantilla) {
            case A -> new Molde("🏋️ ¡Cerramos la semana y estos son los que más entrenaron en el gimnasio!",
                    "🥇 Felicidades, %1$s, ¡primer puesto! Entrenaste %2$s esta semana: la disciplina se nota.",
                    "🥇 Felicidades, %1$s, ¡comparten el primer puesto! Entrenaron %2$s cada uno esta semana: la disciplina se nota.",
                    "%1$s, ¡qué semana de entrenamiento! Tu disciplina se nota 👏",
                    "%1$s, ¡qué semana de entrenamiento! Su disciplina se nota 👏",
                    Estilo.MEDALLA, false,
                    List.of("🔥 Y tú, aprendiz, ¿cuántos días vas a entrenar esta semana? Tu cuerpo también es parte de tu transformación 💪"));
            case B -> new Molde("✨ Gimnasio de la semana ✨",
                    "👑 %1$s se queda con el 1.er puesto con %2$s de entrenamiento, ¡felicidades!",
                    "👑 %1$s comparten el 1.er puesto con %2$s de entrenamiento cada uno, ¡felicidades!",
                    "🌟 %1$s: cada entrenamiento suma, y el tuyo se nota.",
                    "🌟 %1$s: cada entrenamiento suma, y los suyos se notan.",
                    Estilo.ORDINAL, false,
                    List.of("🚀 Aprendiz, agenda tu próximo entrenamiento: la próxima foto puede llevar tu nombre 🏆"));
            case C -> new Molde("🌅 ¡Feliz lunes, comunidad Renaser! Así se entrenó en el gimnasio esta semana:",
                    "%1$s fue quien más entrenó. ¡Felicidades por tu disciplina! 🙌",
                    "%1$s fueron quienes más entrenaron. ¡Felicidades por su disciplina! 🙌",
                    null, null, Estilo.MEDALLA, true,
                    List.of("💛 A cada aprendiz: el mejor entrenamiento es el que haces hoy, no el que prometes para mañana.",
                            "🏋️ ¿Cuántos días vas a entrenar esta semana? ¡Nos vemos en el podio! 🏆"));
        };
    }
}
