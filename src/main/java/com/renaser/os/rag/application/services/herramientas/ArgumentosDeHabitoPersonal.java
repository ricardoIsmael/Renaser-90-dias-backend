package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.habitopersonal.DimensionDelHabito;
import com.renaser.os.rag.domain.model.habitopersonal.HabitoPersonalPedido;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
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

    static final String SIN_CATEGORIA = "Falta la categoria del habito. NO lo propongas todavia: preguntale a la "
            + "persona, en una frase, en cual de las cuatro dimensiones lo quiere (Cuerpo, Mente, Emociones o "
            + "Espiritu) y vuelve a llamar con la que elija. No la elijas tu.";

    private ArgumentosDeHabitoPersonal() {
    }

    /** @param ultimaHora la hora mas tarde a la que puede arrancar un habito (regla de {@code habits}) */
    static HabitoPersonalPedido leer(InvocacionHerramienta invocacion, LocalTime ultimaHora) {
        DimensionDelHabito dimension = DimensionDelHabito.leer(invocacion.argumento(CATEGORIA))
                .orElseThrow(() -> new PropuestaImposibleException(categoriaInvalida(invocacion.argumento(CATEGORIA))));
        LocalTime hora = horaDe(invocacion.argumento(HORA), ultimaHora);
        try {
            return HabitoPersonalPedido.de(invocacion.argumento(NOMBRE), dimension, hora,
                    diasDe(invocacion.argumento(DIAS)), invocacion.argumento(META));
        } catch (IllegalArgumentException invalido) {
            throw new PropuestaImposibleException(invalido.getMessage());
        }
    }

    /** Lo que se guarda en la propuesta: nombre limpio, clave de la dimension, hora HH:mm siempre. */
    static InvocacionHerramienta normalizada(String herramienta, HabitoPersonalPedido pedido) {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put(NOMBRE, pedido.nombre());
        argumentos.put(CATEGORIA, pedido.dimension().name());
        argumentos.put(HORA, ArgumentosDeHorario.texto(pedido.hora()));
        if (!pedido.todosLosDias()) {
            argumentos.put(DIAS, pedido.diasParaGuardar());
        }
        if (pedido.meta() != null) {
            argumentos.put(META, pedido.meta());
        }
        return new InvocacionHerramienta(herramienta, argumentos);
    }

    private static String categoriaInvalida(String texto) {
        return ArgumentosDeHorario.presente(texto)
                ? "'" + texto.trim() + "' no es una de las dimensiones donde se crean habitos. Tienen que ser "
                        + "Cuerpo, Mente, Emociones o Espiritu: si no esta claro cual, preguntaselo a la persona."
                : SIN_CATEGORIA;
    }

    private static LocalTime horaDe(String texto, LocalTime ultimaHora) {
        if (!ArgumentosDeHorario.presente(texto)) {
            return null;
        }
        LocalTime hora = ArgumentosDeHorario.hora(texto, HORA);
        if (hora.isAfter(ultimaHora)) {
            throw new PropuestaImposibleException("Un habito puede arrancar como mas tarde a las "
                    + ArgumentosDeHorario.texto(ultimaHora) + ". Dile eso y pidele otra hora.");
        }
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
