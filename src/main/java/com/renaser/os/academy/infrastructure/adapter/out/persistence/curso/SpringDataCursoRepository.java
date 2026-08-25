package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataCursoRepository extends JpaRepository<CursoJpaEntity, String> {

    List<CursoJpaEntity> findAllByOrderByOrdenAsc();
}
