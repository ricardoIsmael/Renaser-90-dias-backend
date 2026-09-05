package com.renaser.os.habits.infrastructure.adapter.out.persistence.habito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataHabitoRepository extends JpaRepository<HabitoJpaEntity, UUID> {

    /**
     * ORDENADO por {@code orden} (V28). Sin el {@code ORDER BY} explicito Postgres devuelve las
     * filas en orden indefinido y la columna {@code orden} quedaba ignorada fuera del panel admin:
     * el catalogo le llegaba al movil en cualquier orden. {@code titulo} es solo el desempate de
     * ultima instancia — desde V28 un empate entre habitos de sistema activos es imposible
     * (indice unico parcial).
     */
    List<HabitoJpaEntity> findByAmbitoAndActivoTrueOrderByOrdenAscTituloAsc(AmbitoHabitoJpa ambito);

    List<HabitoJpaEntity> findByAmbitoOrderByOrdenAscTituloAsc(AmbitoHabitoJpa ambito);

    /**
     * Los habitos PERSONAL comparten {@code orden = 0}, asi que en la practica se ordenan por
     * titulo — y van detras del catalogo porque quien los concatena los agrega despues
     * ({@code MisHabitosService.consultar}).
     */
    List<HabitoJpaEntity> findByAmbitoAndParticipanteIdAndActivoTrueOrderByOrdenAscTituloAsc(
            AmbitoHabitoJpa ambito, UUID participanteId);

    List<HabitoJpaEntity> findByIdIn(java.util.Collection<UUID> ids);

    java.util.Optional<HabitoJpaEntity> findByClaveSistema(String claveSistema);
}
