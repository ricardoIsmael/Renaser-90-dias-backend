package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.habitopersonal.CategoriaDelHabito;
import com.renaser.os.rag.domain.model.habitopersonal.HabitoPersonalPedido;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Los argumentos de {@code proponer_crear_habito_personal} (D-229), leidos igual al proponer y al
 * confirmar. Un argumento que no se entiende sale como {@link PropuestaImposibleException} con el
 * motivo ya escrito para el modelo, como en {@link ArgumentosDeHorario}.
 */
final class ArgumentosDeHabitoPersonal {

    static final String NOMBRE = "nombre";
    static final String CATEGORIA = "categoria";
    static final String HORA = "hora";
    static final String DIAS = "dias";
    static final String META = "meta";

    static final String SIN_CATEGORIAS_EN_SU_FORMACION = "Su formacion todavia no tiene categorias de habitos, asi que por ahora no "
            + "se puede crear uno propio. NO lo propongas: dile con calidez que las categorias las arma el equipo del programa y que, cuando "
            + "existan, podra pedirtelo.";

    /** Spec 038: las categorias son las de SU formacion, no las cuatro fijas de la Escuela. */
    static String sinCategoria(List<CategoriaDelHabito> disponibles) {
        return "Falta la categoria del habito. NO lo propongas todavia: preguntale a la persona, en una frase, en cual de las categorias "
                + "de su formacion lo quiere (" + nombresDe(disponibles) + ") y vuelve a llamar con la que elija. No la elijas tu.";
    }

    private ArgumentosDeHabitoPersonal() {
    }

    /**
     * @param ultimaHora  la hora mas tarde a la que puede arrancar un habito (regla de {@code habits})
     * @param disponibles las categorias activas de la formacion de la persona (spec 038): una que no sea de ellas se rechaza
     */
    static HabitoPersonalPedido leer(InvocacionHerramienta invocacion, LocalTime ultimaHora, List<CategoriaDelHabito> disponibles) {
        if (disponibles.isEmpty()) {
            throw new PropuestaImposibleException(SIN_CATEGORIAS_EN_SU_FORMACION);
        }
        CategoriaDelHabito categoria = CategoriaDelHabito.leer(invocacion.argumento(CATEGORIA), disponibles)
                .orElseThrow(() -> new PropuestaImposibleException(categoriaInvalida(invocacion.argumento(CATEGORIA), disponibles)));
        LocalTime hora = horaDe(invocacion.argumento(HORA), ultimaHora);
        try {
            return HabitoPersonalPedido.de(invocacion.argumento(NOMBRE), categoria, hora,
                    diasDe(invocacion.argumento(DIAS)), invocacion.argumento(META));
        } catch (IllegalArgumentException invalido) {
            throw new PropuestaImposibleException(invalido.getMessage());
        }
    }

    /** Lo que se guarda en la propuesta: nombre limpio, clave de la dimension, hora HH:mm siempre. */
    static InvocacionHerramienta normalizada(String herramienta, HabitoPersonalPedido pedido) {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put(NOMBRE, pedido.nombre());
        argumentos.put(CATEGORIA, pedido.categoria().clave());
        argumentos.put(HORA, ArgumentosDeHorario.texto(pedido.hora()));
        if (!pedido.todosLosDias()) {
            argumentos.put(DIAS, pedido.diasParaGuardar());
        }
        if (pedido.meta() != null) {
            argumentos.put(META, pedido.meta());
        }
        return new InvocacionHerramienta(herramienta, argumentos);
    }

    private static String categoriaInvalida(String texto, List<CategoriaDelHabito> disponibles) {
        return ArgumentosDeHorario.presente(texto)
                ? "'" + texto.trim() + "' no es una de las categorias de habitos de su formacion. Tienen que ser "
                        + nombresDe(disponibles) + ": si no esta claro cual, preguntaselo a la persona. No ofrezcas otras."
                : sinCategoria(disponibles);
    }

    /** «Cuerpo, Mente, Emociones o Espíritu»: solo las de su formacion, con el nombre que ve en Training. */
    static String nombresDe(List<CategoriaDelHabito> categorias) {
        List<String> nombres = categorias.stream().map(CategoriaDelHabito::nombre).toList();
        if (nombres.size() <= 1) {
            return String.join("", nombres);
        }
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " o " + nombres.getLast();
    }

    private static LocalTime horaDe(String texto, LocalTime ultimaHora) {
        if (!ArgumentosDeHorario.presente(texto)) {
            return null;
        }
        LocalTime hora = ArgumentosDeHorario.hora(texto, HORA);
        ArgumentosDeHorario.requireInicioDentroDelDia(hora, ultimaHora);
        return hora;
    }

    /** "lunes, miercoles y viernes", "MONDAY,FRIDAY" o "todos los dias"; {@code null} = los siete. */
    private static Set<DayOfWeek> diasDe(String texto) {
        if (!ArgumentosDeHorario.presente(texto)) {
            return null;
        }
        String normalizado = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (normalizado.contains("todos")) {
            return null;
        }
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(normalizado.split("[,;/\\s]+|\\by\\b"))
                .map(String::trim)
                .filter(parte -> !parte.isEmpty() && !"y".equals(parte))
                .forEach(parte -> dias.add(ArgumentosDeHorario.diaSemana(parte)));
        return dias;
    }
}
