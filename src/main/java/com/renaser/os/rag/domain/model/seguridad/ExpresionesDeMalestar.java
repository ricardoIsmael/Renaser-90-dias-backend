package com.renaser.os.rag.domain.model.seguridad;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * La lista —UNICA, y este es el unico archivo donde vive— de expresiones que hacen que un mensaje
 * escrito por un aprendiz CUENTE como una senal de malestar, mas la unica forma de compararlas
 * contra lo que la persona escribio.
 *
 * <p><b>Esto NO es un diagnostico y no pretende serlo.</b> Encontrar una de estas frases no dice
 * nada sobre el estado de nadie: dice que se escribio una frase de esta lista. Que hacer con eso
 * vive en {@link PatronDeMalestarRepetido} —que exige que se repita— y ni siquiera ahi se afirma
 * nada sobre la persona. Los criterios clinicos de verdad los tiene que firmar un profesional con
 * licencia; ver el javadoc de {@link NivelRiesgo}, que sigue esperando esa firma.
 *
 * <h2>Como se agrega una expresion</h2>
 * Se escribe una linea mas en {@code EXPRESIONES}, en la familia que corresponda, respetando tres
 * cosas:
 * <ol>
 *   <li><b>Ya normalizada</b>: minusculas, sin tildes, sin signos, palabras separadas por UN
 *       espacio. {@code ExpresionesDeMalestarTest} falla si una linea no cumple esto, asi que no
 *       hace falta acordarse — el build avisa.</li>
 *   <li><b>Completa</b>: la frase entera que significa lo que se quiere detectar, no un pedazo.
 *       Esta es la regla que evita el falso positivo que mas importa: la lista dice
 *       {@code "no puedo mas"} y NO {@code "no puedo"}, porque <i>"no puedo con este habito"</i> y
 *       <i>"no puedo a esa hora"</i> son mensajes corrientes de alguien acomodando su dia, no una
 *       senal de nada.</li>
 *   <li><b>Que la use la gente</b>: espanol de Peru, como se escribe en un chat, sin registro
 *       clinico. Nadie le escribe a un asistente "presento sintomatologia depresiva".</li>
 * </ol>
 *
 * <h2>Lo que NO entra en esta lista, a proposito</h2>
 * <b>Senales explicitas de peligro</b> (intencion de hacerse dano, de terminar con su vida, de
 * lastimar a otro). No es un olvido: la lista alimenta una regla que exige varias repeticiones
 * antes de hacer nada, y un mecanismo que se queda callado las dos primeras veces que alguien dice
 * algo asi seria peor que no existir. Ese camino es el de {@link NivelRiesgo#CRITICO} —que dispara
 * siempre, a la primera— y hoy no esta construido: depende de {@code EvaluarRiesgoMensajePort}, de
 * un criterio clinico firmado y de D-80 (saber de forma confiable la edad y el pais para elegir el
 * recurso correcto). Meter esas frases aca seria fingir que ese camino ya existe.
 *
 * <h2>Como se compara</h2>
 * Se normalizan las dos puntas —lo escrito y cada expresion— y se busca la expresion como
 * <b>palabras completas</b>: el texto normalizado se rodea de espacios y la expresion tambien, asi
 * que {@code "mal"} no aparece dentro de {@code "malestar"} ni {@code "no mas"} dentro de
 * {@code "nomas"}. Antes de buscar se borran los contraejemplos: frases que CONTIENEN una
 * expresion de la lista pero no quieren decir lo mismo.
 */
public final class ExpresionesDeMalestar {

    /**
     * Las expresiones. Agrupadas por familia solo para que se lean; para el algoritmo son una
     * lista plana y el orden no significa nada.
     */
    private static final List<String> EXPRESIONES = List.of(
            // --- 1. "Me siento mal" y sus formas directas -----------------------------------
            "me siento mal",
            "me siento muy mal",
            "me siento fatal",
            "me siento pesimo",
            "me siento pesima",
            "no me siento bien",
            "estoy mal",
            "estoy muy mal",
            "me siento vacio",
            "me siento vacia",
            "me siento solo",
            "me siento sola",
            "me siento perdido",
            "me siento perdida",
            "me siento inutil",

            // --- 2. "No puedo mas": agotamiento ---------------------------------------------
            // Todas llevan el cierre ("mas", "fuerzas", "limite") a proposito: es lo que separa
            // el agotamiento de un "no puedo" de agenda. Ver la regla 2 de arriba.
            "no puedo mas",
            "ya no puedo mas",
            "no doy mas",
            "ya no doy mas",
            "no aguanto mas",
            "ya no aguanto mas",
            "no soporto mas",
            "ya no soporto mas",
            "no tengo fuerzas",
            "ya no tengo fuerzas",
            "estoy al limite",
            "ya no la hago",
            "estoy colapsado",
            "estoy colapsada",

            // --- 3. Ganas de abandonar -------------------------------------------------------
            "quiero rendirme",
            "me quiero rendir",
            "voy a rendirme",
            "ya me rendi",
            "quiero abandonar todo",
            "quiero dejar todo",
            "quiero tirar la toalla",

            // --- 4. Tristeza sostenida -------------------------------------------------------
            // "estoy triste" a secas NO esta: es la molestia corriente que produce cualquier
            // programa exigente, que es exactamente lo que describe Severidad.BAJA. Contestarle
            // con un recurso de ayuda a alguien que solo tuvo un mal dia hace que deje de
            // escribir — y entonces el asistente ya no esta el dia que si hace falta.
            "estoy deprimido",
            "estoy deprimida",
            "estoy muy triste",
            "no dejo de llorar",
            "no paro de llorar",
            "solo quiero llorar");

    /**
     * Frases que contienen una expresion de la lista y significan otra cosa. Se borran del texto
     * ANTES de buscar, asi que solo anulan el pedazo que ocupan: un mensaje que ademas diga
     * {@code "no puedo mas"} sigue contando.
     *
     * <p>Se agrega una linea aca cada vez que aparezca un falso positivo real. Misma forma que las
     * expresiones: normalizada y completa.
     */
    private static final List<String> CONTRAEJEMPLOS = List.of(
            // "me siento mal preparado para el examen" no habla de como esta, habla del examen.
            "me siento mal preparado",
            "me siento mal preparada");

    private ExpresionesDeMalestar() {
    }

    /** {@code true} si {@code mensaje} contiene alguna de las expresiones de la lista. */
    public static boolean apareceEn(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return false;
        }
        String texto = sinContraejemplos(normalizar(mensaje));
        return EXPRESIONES.stream().anyMatch(expresion -> texto.contains(" " + expresion + " "));
    }

    /** La lista tal cual, para que la prueba pueda verificarla linea por linea. */
    static List<String> expresiones() {
        return EXPRESIONES;
    }

    /** Los contraejemplos tal cual, por el mismo motivo que {@link #expresiones()}. */
    static List<String> contraejemplos() {
        return CONTRAEJEMPLOS;
    }

    /**
     * Minusculas, sin tildes, sin signos, un solo espacio entre palabras, y rodeado de espacios
     * para que la busqueda sea de palabras completas.
     *
     * <p>La descomposicion NFD separa la tilde de su letra y el filtro de marcas se la lleva, asi
     * que {@code "más"} y {@code "mas"} son la misma cosa — y tambien {@code "mañana"} y
     * {@code "manana"}, que es consistente: tilde y virgulilla se van juntas.
     */
    static String normalizar(String texto) {
        String sinMarcas = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String soloLetrasYDigitos = sinMarcas.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
        return " " + soloLetrasYDigitos.trim() + " ";
    }

    /** Reemplaza cada contraejemplo por espacios, conservando los dos delimitadores. */
    private static String sinContraejemplos(String textoNormalizado) {
        String texto = textoNormalizado;
        for (String contraejemplo : CONTRAEJEMPLOS) {
            texto = texto.replace(" " + contraejemplo + " ", "  ");
        }
        return texto;
    }
}
