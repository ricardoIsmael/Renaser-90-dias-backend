package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;

import java.util.Collection;
import java.util.Set;

/**
 * Cuales de estas claves {@code muro/} siguen mirando filas del PROPIO community que no son las de
 * la publicacion que se esta borrando.
 *
 * <p>Son dos, y las dos sobreviven al {@code DELETE} de la publicacion:
 * <ul>
 *   <li><b>{@code medias_publicacion} de otra publicacion.</b> Hoy nada obliga a que una clave sea
 *       unica: dos publicaciones vivas pueden compartirla. El {@code ON DELETE CASCADE} de
 *       {@code publicacion_id} solo se lleva las filas de la que se borra.</li>
 *   <li><b>{@code testimonios.foto_evento_ruta}.</b> {@code TestimonioService.promover} copia en
 *       esa columna la clave de la portada de la publicacion —la MISMA clave, no otra copia del
 *       archivo— y {@code testimonios.publicacion_muro_id} es {@code ON DELETE SET NULL}
 *       (V1:1130): el testimonio SOBREVIVE al borrado de la publicacion con la ruta congelada, y
 *       {@code TestimonioService.aVista} la vuelve a firmar en cada
 *       {@code GET /api/v1/testimonios}. Borrar ese objeto deja la vitrina publica del producto
 *       con una foto muerta.</li>
 * </ul>
 *
 * <p>Lo de afuera de community lo responde {@code community.api.ReferenciasExternasDeMediaDelMuro}:
 * este puerto solo mira tablas propias, porque
 * {@code .claude/rules/01-arquitectura-hexagonal} prohibe el SQL nativo contra una tabla ajena.
 *
 * <p><b>Lectura pura.</b> No toca una sola fila: la decision de que se borra vive en
 * {@code PublicacionMuroService}, donde se puede probar sin base de datos. Un unico metodo
 * {@code clavesBorrables()} escondería la regla dentro de un SQL que ninguna prueba unitaria puede
 * ejercitar, que es justo lo que no queremos en un camino que borra (mismo criterio que
 * {@code users.RutasDeAlmacenamientoDeCuentaPort}).
 */
public interface ReferenciasDeMediaDelMuroPort {

    /**
     * De las claves dadas, las que referencia alguna fila de community que NO sea de
     * {@code publicacionId}.
     *
     * <p>Tiene que invocarse ANTES del {@code DELETE} de la publicacion, mientras las filas
     * todavia existen; la exclusion por {@code publicacionId} es justamente lo que evita que la
     * publicacion que se borra se cuente a si misma.
     *
     * @param rutas         claves {@code muro/...}, nunca vacia
     * @param publicacionId la publicacion cuyas propias filas NO cuentan como referencia
     * @return subconjunto de {@code rutas}; vacio si ninguna esta compartida
     */
    Set<String> referenciadasFueraDe(Collection<String> rutas, PublicacionId publicacionId);
}
