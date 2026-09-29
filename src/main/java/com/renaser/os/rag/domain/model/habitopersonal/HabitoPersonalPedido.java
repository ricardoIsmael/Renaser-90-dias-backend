package com.renaser.os.rag.domain.model.habitopersonal;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Un habito propio que el acompanante propone crear, ya validado (D-229). Lo arma la herramienta
 * {@code proponer_crear_habito_personal} al proponer y lo vuelve a armar la confirmacion al
 * ejecutar, desde los mismos argumentos guardados.
 *
 * <p>Los topes de largo son los del alta de la app ({@code CrearHabitoPersonalCommand}: titulo
 * 120, meta 200), repetidos aca para no ofrecer una tarjeta que {@code habits} va a rechazar. La
 * regla que manda sigue siendo la de {@code habits}: al confirmar se vuelve a validar alla.
 *
 * <p>La categoria es obligatoria: sin ella no hay pedido. Es lo que obliga al acompanante a
 * preguntarla en vez de adivinarla.
 *
 * @param horaElegida {@code false} si la persona no dio hora y se uso {@link #HORA_POR_DEFECTO}
 * @param dias        nunca vacio; los siete si no eligio
 * @param meta        {@code null} si no dio meta
 */
public record HabitoPersonalPedido(String nombre, DimensionDelHabito dimension, LocalTime hora,
                                   boolean horaElegida, Set<DayOfWeek> dias, String meta) {

    /**
     * La misma hora con que arranca el "Crear habito" de Training ({@code INICIO_DE_JORNADA_MIN}
     * de {@code PlanificarDimensionModal}): la persona la ve en la tarjeta antes de confirmar.
     */
    public static final LocalTime HORA_POR_DEFECTO = LocalTime.of(6, 0);
    public static final int LARGO_MAXIMO_NOMBRE = 120;
    public static final int LARGO_MAXIMO_META = 200;

    private static final Locale CASTELLANO = Locale.forLanguageTag("es");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public HabitoPersonalPedido {
        Objects.requireNonNull(dimension, "la dimension es obligatoria");
        Objects.requireNonNull(hora, "la hora es obligatoria");
        dias = dias == null || dias.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : EnumSet.copyOf(dias);
    }

    /**
     * @param hora {@code null} = no dio hora: se usa {@link #HORA_POR_DEFECTO}
     * @param dias {@code null} o vacio = los siete dias
     * @param meta {@code null} o en blanco = sin meta
     * @throws IllegalArgumentException con el motivo, si el nombre falta o algo se pasa de largo
     */
    public static HabitoPersonalPedido de(String nombre, DimensionDelHabito dimension, LocalTime hora,
                                          Set<DayOfWeek> dias, String meta) {
        String limpio = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("Falta el nombre del habito.");
        }
        if (limpio.length() > LARGO_MAXIMO_NOMBRE) {
            throw new IllegalArgumentException("El nombre del habito puede tener hasta " + LARGO_MAXIMO_NOMBRE
                    + " caracteres.");
        }
        String metaLimpia = meta == null || meta.isBlank() ? null : meta.trim();
        if (metaLimpia != null && metaLimpia.length() > LARGO_MAXIMO_META) {
            throw new IllegalArgumentException("La meta puede tener hasta " + LARGO_MAXIMO_META + " caracteres.");
        }
        return new HabitoPersonalPedido(limpio, dimension, hora == null ? HORA_POR_DEFECTO : hora, hora != null,
                dias, metaLimpia);
    }

    /** Sin distinguir mayusculas, tildes ni espacios de mas: "Leer " y "leer" son el mismo habito. */
    public boolean seLlamaIgualQue(String titulo) {
        return titulo != null && comparable(titulo).equals(comparable(nombre));
    }

    public boolean todosLosDias() {
        return dias.size() == DayOfWeek.values().length;
    }

    /** Lo que ve la persona en la tarjeta: «Nuevo hábito: Leer · Mente · 06:00 · todos los días». */
    public String resumen() {
        return "Nuevo hábito: " + nombre + " · " + dimension.etiqueta() + " · " + hora.format(HORA) + " · "
                + diasLegibles() + (meta == null ? "" : " · meta: " + meta);
    }

    /** "todos los días" o "lunes, miércoles y viernes", en el orden de la semana. */
    public String diasLegibles() {
        if (todosLosDias()) {
            return "todos los días";
        }
        var nombres = dias.stream().map(dia -> dia.getDisplayName(TextStyle.FULL, CASTELLANO)).toList();
        if (nombres.size() == 1) {
            return nombres.getFirst();
        }
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.getLast();
    }

    /** Los dias como se guardan en la propuesta: "MONDAY,WEDNESDAY"; vacio si son los siete. */
    public String diasParaGuardar() {
        return todosLosDias() ? "" : dias.stream().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    private static String comparable(String texto) {
        return DimensionDelHabito.sinTildes(texto.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }
}
