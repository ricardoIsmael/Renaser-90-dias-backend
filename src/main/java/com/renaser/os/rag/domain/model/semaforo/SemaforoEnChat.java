package com.renaser.os.rag.domain.model.semaforo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Qué le dice el acompañante a la persona cuando se cierra su semana del semáforo (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.2): el color, su palabra y el porcentaje, con una
 * frase corta según el color. Mismo molde que {@code LogrosEnChat} y {@code AvisosEnChat} (D-155):
 * plantilla, no IA — costo cero y ningún dato inventado.
 *
 * <p><b>Todo es configuración, nada es constante.</b> Hay un texto por color (los cuatro casos de la
 * semana) y se usan tal cual: el dueño pidió el 2026-09-25 que los avisos sean automáticos según el
 * caso, y el mensaje quedó encendido por defecto. Se sobreescriben por entorno; una plantilla vacía
 * apaga ese color.
 * <blockquote><b>Corregido 2026-09-25.</b> Decía que los textos eran provisorios hasta que el dueño
 * los aprobara, con el mensaje apagado por defecto.</blockquote>
 *
 * <p>Marcadores: {@code {etiqueta}} (la palabra del color, que viaja en el evento: un estado nunca
 * se comunica solo con el color, RL-30) y {@code {porcentaje}} (el de la semana, con punto decimal
 * como en la tarjeta de la app: «86» o «78.3»).
 *
 * <p><b>Resiliente</b> (decisión del dueño, 2026-09-25): si después de reemplazar queda un marcador
 * —mal escrito, o un {@code {porcentaje}} en la plantilla de sin datos—, a la persona le llega
 * {@link #TEXTO_DE_RESPALDO} y nunca un «{algo}» a la vista.
 * <blockquote><b>Corregido 2026-09-25.</b> Decía que un marcador desconocido quedaba literal, «igual
 * que en {@code AvisosEnChat}: se ve el error de redacción en vez de esconderlo». Con el mensaje
 * encendido por defecto, ese error lo vería la persona y no quien escribió la plantilla.</blockquote>
 *
 * <p><b>Sin porcentaje no hay número.</b> Una semana sin porcentaje se cuenta con la plantilla de
 * SIN DATOS, sea cual sea el color que traiga: por contrato, porcentaje nulo es "ningún día tuvo algo
 * programado" ({@code SemanaDelSemaforoCerradaEvent}). Esa plantilla nunca recibe el número.
 *
 * @param activo     el interruptor general ({@code renaser.ia.acompanante.semaforo-en-chat})
 * @param plantillas texto por color
 */
public record SemaforoEnChat(boolean activo, Map<ColorDeLaSemana, String> plantillas) {

    /** Lo que se escribe si una plantilla deja un marcador sin reemplazar: sin cifras ni color. */
    public static final String TEXTO_DE_RESPALDO =
            "Tu semana ya cerró. Mira tu semáforo en la app para ver cómo te fue.";

    private static final Pattern MARCADOR = Pattern.compile("\\{[^{}]*}");

    public SemaforoEnChat {
        plantillas = plantillas == null ? Map.of() : Map.copyOf(plantillas);
    }

    public static SemaforoEnChat apagado() {
        return new SemaforoEnChat(false, Map.of());
    }

    /** Un cierre pasa al chat solo con el interruptor prendido y un texto escrito para su color. */
    public boolean aplicaA(CierreDeSemana cierre) {
        return activo && cierre != null && !plantillaDe(cierre).isBlank();
    }

    /** Vacío si ese cierre no pasa al chat (ver {@link #aplicaA}). */
    public Optional<String> redactar(CierreDeSemana cierre) {
        if (!aplicaA(cierre)) {
            return Optional.empty();
        }
        String texto = plantillaDe(cierre).replace("{etiqueta}", cierre.etiqueta());
        if (!cierre.sinPorcentaje()) {
            texto = texto.replace("{porcentaje}", cierre.porcentajeLegible());
        }
        return Optional.of(MARCADOR.matcher(texto).find() ? TEXTO_DE_RESPALDO : texto.strip());
    }

    private String plantillaDe(CierreDeSemana cierre) {
        String plantilla = plantillas.get(cierre.colorDelTexto());
        return plantilla == null ? "" : plantilla;
    }

    /**
     * Lo que trae el evento del cierre, ya en términos del chat.
     *
     * @param etiqueta   la palabra del color, tal como la define {@code points}
     * @param porcentaje el de la semana, con un decimal; null si ningún día tuvo algo programado
     */
    public record CierreDeSemana(ColorDeLaSemana color, String etiqueta, BigDecimal porcentaje) {

        public CierreDeSemana {
            Objects.requireNonNull(color, "color es obligatorio");
            Objects.requireNonNull(etiqueta, "etiqueta es obligatoria");
        }

        boolean sinPorcentaje() {
            return porcentaje == null || color == ColorDeLaSemana.SIN_DATOS;
        }

        ColorDeLaSemana colorDelTexto() {
            return sinPorcentaje() ? ColorDeLaSemana.SIN_DATOS : color;
        }

        /**
         * 86.0 → "86"; 78.3 → "78.3": un decimal como mucho y sin ceros de más. Punto decimal, como la
         * tarjeta de la app (`lecturaDelSemaforo.ts`): la misma cifra no puede verse de dos formas.
         */
        String porcentajeLegible() {
            return porcentaje.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
    }
}
