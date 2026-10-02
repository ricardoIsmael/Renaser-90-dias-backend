package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    /**
     * Una tanda de cuentas cerradas cuya gracia vencio (D-243), en orden de id y despues de
     * {@code despuesDe}: el barrido pagina por clave y no por numero de pagina, porque cada cuenta
     * que borra desaparece de la consulta y correria el desplazamiento.
     */
    @Query("""
            select u.id from UserJpaEntity u
             where u.bajaSolicitadaEn is not null and u.bajaSolicitadaEn <= :corte and u.id > :despuesDe
             order by u.id""")
    List<UUID> cerradasVencidas(@Param("corte") Instant corte, @Param("despuesDe") UUID despuesDe, Limit limite);

    /** De estos ids, los cerrados para eliminar (D-243). */
    @Query("select u.id from UserJpaEntity u where u.id in :ids and u.bajaSolicitadaEn is not null")
    List<UUID> cerradasEntre(@Param("ids") Collection<UUID> ids);

    List<UserJpaEntity> findByRolInAndEstado(Collection<RolUsuarioJpa> roles, EstadoUsuarioJpa estado,
                                              Pageable pageable);

    List<UserJpaEntity> findByRolIn(Collection<RolUsuarioJpa> roles, Pageable pageable);

    long countByRolInAndEstado(Collection<RolUsuarioJpa> roles, EstadoUsuarioJpa estado);

    long countByRolIn(Collection<RolUsuarioJpa> roles);
}
