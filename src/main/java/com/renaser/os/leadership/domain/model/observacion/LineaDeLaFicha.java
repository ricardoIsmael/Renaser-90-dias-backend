package com.renaser.os.leadership.domain.model.observacion;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * La línea con el enlace a la ficha que cierra el mensaje de «Avisar a su mentor» (spec 056, FR-019):
 *
 * <pre>
 * &lt;lo que escribió el líder&gt;
 *
 * Ficha de &lt;nombre&gt;: /mentor/groups/&lt;grupo&gt;/learners/&lt;persona&gt;
 * </pre>
 *
 * <p><b>Es un contrato con la app.</b> El mensaje viaja como texto por el chat de dos; la app reconoce esta última línea con {@link #PATRON} y la vuelve
 * tocable (abre {@code /mentor/groups/<g>/learners/<p>}, la ficha que el mentor ve de SU aprendiz). Por eso la línea va siempre sola, al final, después de una
 * línea en blanco, y el nombre nunca la parte en dos. Si este formato cambia, la app deja de reconocerla: cámbiese en los dos lados y en
 * {@code LineaDeLaFichaTest}.
 *
 * <p>No es la observación guardada: lo que queda en «Lo que le dijiste» es solo el texto del líder, sin esta línea.
 */
public final class LineaDeLaFicha {

    /** El formato de la línea: nombre, id del grupo e id de la persona. */
    public static final String FORMATO = "Ficha de %s: /mentor/groups/%s/learners/%s";

    private static final String ID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    /**
     * Reconoce la línea entera ({@code matches}). Grupos: 1 el nombre, 2 la ruta de la app ({@code /mentor/groups/<g>/learners/<p>}), 3 el id del grupo y
     * 4 el de la persona.
     */
    public static final Pattern PATRON = Pattern.compile("Ficha de (.+): (/mentor/groups/(" + ID + ")/learners/(" + ID + "))");

    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private LineaDeLaFicha() {
    }

    /**
     * La línea de la ficha de esa persona en ese grupo. Los saltos de línea y los espacios repetidos del nombre se vuelven un solo espacio; sin nombre
     * dice «la persona», para que la línea siga cumpliendo {@link #PATRON}.
     */
    public static String de(String nombre, UUID grupoId, UUID personaId) {
        String limpio = nombre == null ? "" : ESPACIOS.matcher(nombre.trim()).replaceAll(" ");
        return FORMATO.formatted(limpio.isEmpty() ? "la persona" : limpio, grupoId, personaId);
    }

    /** El texto del líder y, separada por una línea en blanco, la línea de la ficha como última línea del mensaje. */
    public static String alFinalDe(String texto, String nombre, UUID grupoId, UUID personaId) {
        return texto + "\n\n" + de(nombre, grupoId, personaId);
    }
}
