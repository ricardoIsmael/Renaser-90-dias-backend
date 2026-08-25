package com.renaser.os.community.infrastructure.adapter.out.persistence.categoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataCategoriaMuroRepository extends JpaRepository<CategoriaMuroJpaEntity, String> {

    List<CategoriaMuroJpaEntity> findByActivaTrueOrderByOrdenAscClaveAsc();

    List<CategoriaMuroJpaEntity> findAllByOrderByOrdenAscClaveAsc();
}
