package com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataMiembroGrupoRepository extends JpaRepository<MiembroGrupoJpaEntity, MiembroGrupoId> {

    List<MiembroGrupoJpaEntity> findByGrupoIdIn(List<Long> grupoIds);
}
