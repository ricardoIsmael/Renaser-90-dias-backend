package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.util.Locale;

/**
 * Escribe el mensaje de la persona: reemplaza cada variable del texto por su dato (FR-005). El resultado nunca
 * tiene una llave: si falta un dato es que el mensaje no era candidato, y se avisa en vez de dejar un hueco.
 *
 * <p>Dos retoques de redacción, para que lo escrito se lea bien y no solo se rellene:
 * <ul>
 *   <li>Un hábito del catálogo se guarda en mayúsculas («JUGO VERDE»); dentro de una frase se escribe en
 *       minúsculas. Uno que tenga alguna minúscula («Clase diaria», «Pastilla Renacer») se deja como está.</li>
 *   <li>Con un solo pendiente, «faltan 1» pasa a «falta 1».</li>
 * </ul>
 * El nombre es el primer nombre. Las llaves que traiga un dato (un hábito que la persona renombró) se quitan para
 * que no parezcan una variable.
 */
public final class RedaccionDelMensaje {

    private static final String FALTAN_EN_PLURAL = "faltan " + Variable.FALTAN.marcador();
    private static final String FALTA_EN_SINGULAR = "falta " + Variable.FALTAN.marcador();

    private RedaccionDelMensaje() {
    }

    public static String redactar(MensajeDelSemaforo mensaje, DatosDelMensaje datos) {
        String texto = mensaje.texto();
        if (Integer.valueOf(1).equals(datos.faltan())) {
            texto = texto.replace(FALTAN_EN_PLURAL, FALTA_EN_SINGULAR);
        }
        for (Variable variable : mensaje.variables()) {
            texto = texto.replace(variable.marcador(), valor(mensaje, variable, datos));
        }
        return texto;
    }

    private static String valor(MensajeDelSemaforo mensaje, Variable variable, DatosDelMensaje datos) {
        String dato = datos.valorDe(variable);
        if (dato == null) {
            throw new IllegalStateException(
                    "El mensaje " + mensaje.id() + " pide " + variable.marcador() + " y no hay dato");
        }
        String sinLlaves = dato.replace("{", "").replace("}", "");
        boolean esHabito = variable == Variable.HABITO_FLOJO || variable == Variable.HABITO_FUERTE;
        return esHabito ? enLaFrase(sinLlaves) : sinLlaves;
    }

    private static String enLaFrase(String habito) {
        boolean todoEnMayusculas = habito.equals(habito.toUpperCase(Locale.ROOT));
        return todoEnMayusculas ? habito.toLowerCase(Locale.ROOT) : habito;
    }
}
