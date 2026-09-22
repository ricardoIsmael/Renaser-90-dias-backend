package com.renaser.os.rag.domain.model.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La mitad de valor de esta clase esta en los casos NEGATIVOS.
 *
 * <p>Un detector de malestar que cuenta de mas es peor que uno que no existe: le contesta con un
 * recurso de ayuda a alguien que solo estaba acomodando su horario, la persona se siente
 * malinterpretada y deja de escribirle al asistente — y entonces el asistente ya no esta el dia que
 * si hace falta. Ademas satura el aviso de los administradores hasta que dejan de mirarlo.
 */
class ExpresionesDeMalestarTest {

    @ParameterizedTest
    @DisplayName("cuenta lo que la gente escribe de verdad cuando esta mal")
    @ValueSource(strings = {
            "me siento mal",
            "Me siento mal.",
            "hoy me siento MAL, no se que hacer",
            "ya no puedo más",           // con tilde: la normalizacion la saca
            "no puedo mas con todo esto",
            "ya no doy más la verdad",
            "no aguanto más este ritmo",
            "no me siento bien hace dias",
            "creo que quiero tirar la toalla",
            "estoy al limite",
            "no tengo fuerzas para nada",
            "solo quiero llorar",
            "¿te cuento? estoy muy triste",
            "estoy colapsada con el trabajo y el programa"})
    void cuentaLasExpresionesDeMalestar(String mensaje) {
        assertThat(ExpresionesDeMalestar.apareceEn(mensaje)).isTrue();
    }

    /**
     * Los dos primeros los nombro el dueno explicitamente: "no puedo con este habito" y "no puedo a
     * esa hora" NO son "no puedo mas". Es la razon por la que la lista guarda frases COMPLETAS y no
     * pedazos — si alguna vez alguien agrega "no puedo" a secas, esta prueba se pone en rojo.
     */
    @ParameterizedTest
    @DisplayName("NO cuenta a alguien que simplemente esta acomodando su dia")
    @ValueSource(strings = {
            "no puedo con este habito",
            "no puedo con este hábito, ¿lo cambio?",
            "no puedo a esa hora",
            "no puedo los martes, tengo clase",
            "no puedo levantarme a las 5",
            "mañana no puedo hacer el ejercicio",
            "no puedo entrar a la app",
            "estoy cansado hoy",
            "estoy triste porque perdio mi equipo",
            "me fue mal en el examen",
            "el dia estuvo malo",
            "me siento mal preparado para la exposicion",
            "me siento mal preparada, ¿me recomiendas algo?",
            "que hago si no doy con la respuesta",
            "no se si esto esta bien"})
    void noCuentaLosFalsosPositivos(String mensaje) {
        assertThat(ExpresionesDeMalestar.apareceEn(mensaje)).isFalse();
    }

    /**
     * El contraejemplo anula SOLO el pedazo que ocupa: si el mismo mensaje trae ademas una
     * expresion de verdad, cuenta igual. Sin esto, escribir la frase equivocada al lado alcanzaria
     * para desactivar la deteccion del resto del mensaje.
     */
    @Test
    void elContraejemploNoAnulaElRestoDelMensaje() {
        assertThat(ExpresionesDeMalestar.apareceEn("me siento mal preparado y ademas ya no puedo mas")).isTrue();
    }

    @Test
    @DisplayName("una palabra que CONTIENE a otra no cuenta: 'mal' dentro de 'malestar'")
    void noCuentaCoincidenciasParciales() {
        assertThat(ExpresionesDeMalestar.apareceEn("estoy malhumorado")).isFalse();
        assertThat(ExpresionesDeMalestar.apareceEn("no puedo masticar bien")).isFalse();
    }

    @Test
    void ignoraMensajesVaciosYNulos() {
        assertThat(ExpresionesDeMalestar.apareceEn(null)).isFalse();
        assertThat(ExpresionesDeMalestar.apareceEn("   ")).isFalse();
    }

    /**
     * Guarda del formato de la lista: si alguien agrega una expresion con mayuscula, tilde o signo,
     * nunca coincidiria con nada (el texto a comparar viene normalizado) y el error seria invisible
     * — la expresion simplemente no detectaria nunca. Esta prueba lo convierte en un build en rojo.
     */
    @Test
    @DisplayName("cada linea de la lista ya esta escrita en forma normalizada")
    void laListaEstaEnFormaCanonica() {
        for (String expresion : ExpresionesDeMalestar.expresiones()) {
            assertThat(ExpresionesDeMalestar.normalizar(expresion))
                    .as("la expresion \"%s\" no esta normalizada (minusculas, sin tildes, sin signos)", expresion)
                    .isEqualTo(" " + expresion + " ");
        }
        for (String contraejemplo : ExpresionesDeMalestar.contraejemplos()) {
            assertThat(ExpresionesDeMalestar.normalizar(contraejemplo))
                    .as("el contraejemplo \"%s\" no esta normalizado", contraejemplo)
                    .isEqualTo(" " + contraejemplo + " ");
        }
    }

    /**
     * Un contraejemplo que no contenga ninguna expresion no anula nada: es una linea muerta que
     * hace creer que se cubrio un caso. Ver el javadoc de {@code ExpresionesDeMalestar}.
     */
    @Test
    void cadaContraejemploAnulaAlgunaExpresionDeLaLista() {
        for (String contraejemplo : ExpresionesDeMalestar.contraejemplos()) {
            String texto = ExpresionesDeMalestar.normalizar(contraejemplo);
            assertThat(ExpresionesDeMalestar.expresiones())
                    .as("el contraejemplo \"%s\" no contiene ninguna expresion: no anula nada", contraejemplo)
                    .anyMatch(expresion -> texto.contains(" " + expresion + " "));
        }
    }

    /**
     * Las senales explicitas de peligro NO estan en esta lista, a proposito: alimentan una regla
     * que exige repeticion, y un mecanismo que se queda callado las dos primeras veces que alguien
     * dice algo asi seria peor que no existir. Ese camino es NivelRiesgo.CRITICO, que dispara a la
     * primera y todavia no esta construido (falta criterio clinico firmado y D-80).
     *
     * <p>Si alguien agrega una de estas frases aca sin haber construido ese camino, esta prueba se
     * pone en rojo y lo obliga a leer el javadoc antes de seguir.
     */
    @Test
    @DisplayName("las senales explicitas de peligro NO se tratan con esta lista (no se ocultan: van por otro camino)")
    void lasSenalesExplicitasDePeligroNoEstanEnEstaLista() {
        assertThat(ExpresionesDeMalestar.apareceEn("me quiero morir")).isFalse();
        assertThat(ExpresionesDeMalestar.apareceEn("me quiero hacer dano")).isFalse();
        assertThat(ExpresionesDeMalestar.apareceEn("no quiero seguir viviendo")).isFalse();
    }
}
