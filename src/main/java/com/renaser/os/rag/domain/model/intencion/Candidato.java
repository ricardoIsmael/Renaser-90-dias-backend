package com.renaser.os.rag.domain.model.intencion;

import java.util.Objects;

/**
 * La mejor opcion que encontro un clasificador, con las dos medidas crudas que la respaldan.
 *
 * <p><b>Por que dos numeros y no una "confianza" de 0 a 1.</b> Convertir similitud en probabilidad
 * exige una calibracion (una temperatura, una curva) que hoy no esta medida contra frases reales de
 * los aprendices. Inventar esa curva seria presentar como probabilidad un numero que no lo es. Se
 * exponen los datos que si son ciertos, y los umbrales se fijan despues de correr el dataset
 * ({@code docs/arquitectura/PROPUESTA_JEV_ROUTER_TOOL_CALLING.md} §6):
 *
 * <ul>
 *   <li>{@code similitud}: coseno entre el mensaje y la referencia mas parecida de esta opcion
 *       (−1 a 1). Dice cuanto se parece.</li>
 *   <li>{@code margen}: cuanto le saca a la segunda opcion. Dice si hubo duda: 0.81 contra 0.80
 *       es un empate aunque 0.81 sea alto.</li>
 * </ul>
 *
 * @param etiqueta  el nombre de la intencion, o el {@code registroId} del habito
 * @param similitud coseno con la referencia mas parecida de esta etiqueta
 * @param margen    {@code similitud} menos la de la segunda etiqueta; igual a {@code similitud} si
 *                  no habia otra con que comparar
 */
public record Candidato(String etiqueta, double similitud, double margen) {

    public Candidato {
        Objects.requireNonNull(etiqueta, "etiqueta es obligatoria");
        if (etiqueta.isBlank()) {
            throw new IllegalArgumentException("etiqueta no puede estar vacia");
        }
    }
}
