package com.renaser.os.rag.domain.model.herramienta;

/** El tipo declarado de un {@link ParametroHerramienta}. Ver el javadoc de esa clase sobre por
 * que la lista es corta y no un JSON Schema. */
public enum TipoParametroHerramienta {

    TEXTO,
    /** Un UUID en su forma canonica. Se distingue de {@link #TEXTO} porque es el unico que hoy
     * se valida de verdad antes de ejecutar: un modelo inventa identificadores sin ningun pudor. */
    IDENTIFICADOR,
    ENTERO
}
