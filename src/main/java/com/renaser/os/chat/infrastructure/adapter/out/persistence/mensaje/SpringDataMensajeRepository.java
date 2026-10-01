package com.renaser.os.chat.infrastructure.adapter.out.persistence.mensaje;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataMensajeRepository extends JpaRepository<MensajeJpaEntity, UUID> {

    /**
     * Dos metodos (con/sin cursor) en vez de {@code (:cursor IS NULL OR ...)} — mismo
     * defecto E-31 que ya documento `community` (Postgres no puede inferir el tipo de un
     * parametro que solo aparece en {@code ? IS NULL}), ver
     * {@code SpringDataPublicacionRepository} como plantilla.
     */
    @Query("""
            SELECT m FROM MensajeJpaEntity m
            WHERE m.conversacionId = :conversacionId
            ORDER BY m.creadoEn DESC
            """)
    List<MensajeJpaEntity> paginaSinCursor(@Param("conversacionId") UUID conversacionId, Pageable pageable);

    @Query("""
            SELECT m FROM MensajeJpaEntity m
            WHERE m.conversacionId = :conversacionId
              AND m.creadoEn < :cursor
            ORDER BY m.creadoEn DESC
            """)
    List<MensajeJpaEntity> paginaConCursor(@Param("conversacionId") UUID conversacionId,
                                            @Param("cursor") Instant cursor, Pageable pageable);

    /**
     * De las claves dadas, las que algun mensaje todavia usa como media.
     *
     * <p>Compartir una publicacion del Muro en el chat no copia el archivo: guarda la MISMA clave
     * {@code muro/} ({@code CompartirPublicacionService}). Esta consulta es la que le contesta al
     * Muro —via {@code community.api.ReferenciasExternasDeMediaDelMuro}— si todavia hace falta el
     * objeto antes de sacarlo del bucket.
     *
     * <p><b>Sin filtro por {@code eliminadoEn} ni por {@code oculto}, a proposito.</b> En el chat
     * no hay borrado fisico: borrar un mensaje es un tombstone y la fila se queda con su
     * {@code media_ruta}, que {@code MensajeService.urlDeLectura} vuelve a firmar en cada lectura.
     * Filtrar por esas columnas haria que el Muro borrara del bucket un objeto que la conversacion
     * todavia sirve.
     */
    @Query("""
            SELECT DISTINCT m.mediaRuta FROM MensajeJpaEntity m
            WHERE m.mediaRuta IN :rutas
            """)
    List<String> mediaRutasEnUso(@Param("rutas") Collection<String> rutas);

    /**
     * Ultimo mensaje por conversacion EN UNA SOLA consulta (nunca N+1 — CLAUDE.MD del encargo).
     *
     * <p><b>E-471.</b> Antes era {@code SELECT DISTINCT ON (conversacion_id) * ... WHERE conversacion_id
     * IN (...) ORDER BY conversacion_id, creado_en DESC}: con varias conversaciones Postgres leia y
     * ordenaba TODOS sus mensajes para quedarse con uno por grupo (prueba de carga D-238: 6,98 ms con
     * 3.379 mensajes, y crece con la historia del chat). Ahora, por cada conversacion pedida, una
     * busqueda {@code LATERAL ... LIMIT 1} sobre el indice {@code mensajes_conversacion_idx
     * (conversacion_id, creado_en)} que ya existia: lee una fila por conversacion (0,03 ms medido).
     *
     * <p>Mismo resultado que la anterior: sin filtrar borrados ni ocultos, y una conversacion sin
     * mensajes no devuelve fila. Las conversaciones se toman de {@code conversaciones} (tabla del
     * mismo modulo) porque la lista {@code IN (:ids)} es la forma en que se expande un parametro
     * coleccion; un id que no existe tampoco devolvia nada antes. Lo verifica
     * {@code UltimoMensajePorConversacionIT} contra la consulta vieja.
     */
    @Query(value = ULTIMOS_POR_CONVERSACION, nativeQuery = true)
    List<MensajeJpaEntity> ultimosPorConversacion(@Param("conversacionIds") List<UUID> conversacionIds);

    /** La consulta de {@link #ultimosPorConversacion}, aparte para que la prueba mire su plan (E-471). */
    String ULTIMOS_POR_CONVERSACION = """
            SELECT m.*
            FROM renaser.conversaciones c
            CROSS JOIN LATERAL (
                SELECT *
                FROM renaser.mensajes
                WHERE conversacion_id = c.id
                ORDER BY creado_en DESC
                LIMIT 1
            ) m
            WHERE c.id IN (:conversacionIds)
            """;
}
