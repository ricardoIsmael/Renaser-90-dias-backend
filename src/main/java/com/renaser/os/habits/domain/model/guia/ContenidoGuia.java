package com.renaser.os.habits.domain.model.guia;

/**
 * Contenido editable de una {@link GuiaHabito} desde el panel admin (hueco #11):
 * los 6 textos, el mantra (3 campos) y la referencia de fuente. Deliberadamente NO
 * incluye {@code habitoId} ni {@code diaInicio} — esos identifican QUE guia se edita
 * (llave de {@code UpsertGuiaHabitoCommand}), no forman parte de "editar su contenido".
 */
public record ContenidoGuia(String queHacer, String comoHacerlo, String ciencia, String renaser, String alquimia,
                             String resultados, String mantraTitulo, String mantraIntro, String mantraCuerpo,
                             String referenciaFuente) {
}
