package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataConversacionRepository extends JpaRepository<ConversacionJpaEntity, UUID> {

    Optional<ConversacionJpaEntity> findByClaveDirecta(String claveDirecta);

    Optional<ConversacionJpaEntity> findByCelulaId(UUID celulaId);

    List<ConversacionJpaEntity> findByCelulaIdIn(Collection<UUID> celulaIds);

    /** Una por formación (`conversacion_global_unica_uk`, V138). */
    Optional<ConversacionJpaEntity> findFirstByTipoAndFormacion(TipoConversacionJpa tipo, String formacion);

    /** Las de un tipo en las formaciones dadas (spec 024): la comunidad general de cada una. */
    List<ConversacionJpaEntity> findByTipoAndFormacionIn(TipoConversacionJpa tipo, Collection<String> formaciones);

    /** Todas las de un tipo. Hoy solo la usa SOPORTE, que tiene una por aprendiz del padron. */
    List<ConversacionJpaEntity> findByTipo(TipoConversacionJpa tipo);

    List<ConversacionJpaEntity> findByIdIn(List<UUID> ids);

    /** Cuántas conversaciones hay de cada tipo en una formación (spec 024). */
    @Query("select c.tipo, count(c) from ConversacionJpaEntity c where c.formacion = :formacion group by c.tipo")
    List<Object[]> contarPorTipo(@Param("formacion") String formacion);

    /** Solo la clave, no la fila: quien pregunta quiere saber cuales faltan, no leerlas. */
    @Query("select c.claveDirecta from ConversacionJpaEntity c where c.claveDirecta in :claves")
    List<String> clavesDirectasEntre(@Param("claves") Collection<String> claves);
}
