package com.renaser.os.shared.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El nombre de un hábito del catálogo en tipo oración para MOSTRARLO a la persona (issue #63). El catálogo del servidor trae casi todos los títulos en
 * MAYÚSCULAS («AGUA TIBIA CON LIMÓN», «RITUAL TIERRA - AGUA - FUEGO (mañana)»); la app los muestra «Agua tibia con limón» y «Ritual tierra - agua - fuego
 * (mañana)» con {@code tituloEnTipoOracion} (src/features/habits/utils/tituloEnTipoOracion.ts). Esta clase es la MISMA regla, para los textos que escribe el
 * servidor y que la persona lee en el panel de SER («Tu hábito Última comida del día empieza a las 18:00…»): si una cambia, cambia la otra.
 *
 * <p><b>Solo cambia lo que se MUESTRA.</b> Lo que se guarda, lo que viaja entre módulos y el texto de las alarmas siguen con el título del catálogo tal cual.
 *
 * <ul>
 *   <li><b>Solo si llega TODO en mayúsculas.</b> Un título con alguna minúscula («Clase diaria», «Pastilla Renacer», el que la persona puso al renombrarlo)
 *       se deja igual.</li>
 *   <li><b>Lo que va entre paréntesis no cuenta para decidirlo.</b> El catálogo escribe el momento en minúsculas dentro del paréntesis («… (mañana)»); se mira
 *       lo de afuera. Un paréntesis que también viene en mayúsculas («(ROMPO EL AYUNO)») pasa a minúsculas. Si no hay letras afuera, decide lo de adentro.</li>
 *   <li><b>Siglas y nombres propios:</b> {@code SER}, {@code IA}, {@code TV} y {@code USD} siguen en mayúsculas; {@code RENASER} queda «Renaser» y
 *       {@code RENACER} (la «Pastilla Renacer») «Renacer», también a mitad de frase. Para sumar una sigla, una línea en {@link #SIGLAS}.</li>
 *   <li>La primera letra va en mayúscula. Los espacios y los signos no se tocan.</li>
 * </ul>
 */
public final class TituloEnTipoOracion {

    /** Siglas que se quedan en mayúsculas. Se agregan acá, una por una, cuando aparezca alguna en el catálogo (la lista es la de la app). */
    static final Set<String> SIGLAS = Set.of("SER", "IA", "TV", "USD");

    /** Nombres propios: se escriben con la inicial en mayúscula aunque vayan a mitad de frase. */
    private static final Map<String, String> NOMBRES_PROPIOS = Map.of("RENASER", "Renaser", "RENACER", "Renacer");

    private static final Pattern GRUPO_ENTRE_PARENTESIS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern LETRAS = Pattern.compile("\\p{L}+");
    private static final Pattern UNA_LETRA = Pattern.compile("\\p{L}");
    private static final Pattern UNA_MINUSCULA = Pattern.compile("\\p{Ll}");

    private TituloEnTipoOracion() {
    }

    /** Un título que llega todo en mayúsculas, en tipo oración; cualquier otro (también {@code null} o vacío), tal cual. */
    public static String de(String titulo) {
        if (titulo == null) {
            return null;
        }
        // Lo que decide es el texto de afuera de los paréntesis; si no hay letras afuera («(MAÑANA)»), el de adentro.
        String fuera = GRUPO_ENTRE_PARENTESIS.matcher(titulo).replaceAll(" ");
        String base = UNA_LETRA.matcher(fuera).find() ? fuera : titulo;
        if (!UNA_LETRA.matcher(base).find() || UNA_MINUSCULA.matcher(base).find()) {
            return titulo;
        }
        return conLaInicialEnMayuscula(convertido(titulo));
    }

    /** Parte el título en lo de afuera y los grupos entre paréntesis (que se quedan como vienen si ya traen minúsculas) y pasa el resto a minúsculas. */
    private static String convertido(String titulo) {
        StringBuilder salida = new StringBuilder();
        Matcher grupo = GRUPO_ENTRE_PARENTESIS.matcher(titulo);
        int desde = 0;
        while (grupo.find()) {
            salida.append(aMinusculasConSiglas(titulo.substring(desde, grupo.start())));
            String entreParentesis = grupo.group();
            // Un paréntesis con minúsculas ya viene bien escrito: se deja.
            salida.append(UNA_MINUSCULA.matcher(entreParentesis).find() ? entreParentesis : aMinusculasConSiglas(entreParentesis));
            desde = grupo.end();
        }
        return salida.append(aMinusculasConSiglas(titulo.substring(desde))).toString();
    }

    /** Un fragmento ya en minúsculas, con las siglas y los nombres propios de vuelta. */
    private static String aMinusculasConSiglas(String fragmento) {
        Matcher palabras = LETRAS.matcher(fragmento.toLowerCase(Locale.ROOT));
        return palabras.replaceAll(palabra -> {
            String mayuscula = palabra.group().toUpperCase(Locale.ROOT);
            if (SIGLAS.contains(mayuscula)) {
                return mayuscula;
            }
            return Matcher.quoteReplacement(NOMBRES_PROPIOS.getOrDefault(mayuscula, palabra.group()));
        });
    }

    private static String conLaInicialEnMayuscula(String texto) {
        Matcher primera = UNA_LETRA.matcher(texto);
        if (!primera.find()) {
            return texto;
        }
        return texto.substring(0, primera.start()) + primera.group().toUpperCase(Locale.ROOT) + texto.substring(primera.end());
    }
}
