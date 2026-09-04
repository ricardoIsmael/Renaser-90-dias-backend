package com.renaser.os.habits.infrastructure.adapter.out.persistence.desbloqueo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataDesbloqueoHabitoRepository extends JpaRepository<DesbloqueoHabitoJpaEntity, DesbloqueoHabitoPk> {

    List<DesbloqueoHabitoJpaEntity> findByParticipanteId(UUID participanteId);

    Optional<DesbloqueoHabitoJpaEntity> findByParticipanteIdAndHabitoId(UUID participanteId, UUID habitoId);

    /**
     * C-12/E-75 (docs/BITACORA_ERRORES.md): INSERT ... ON CONFLICT DO NOTHING sobre la PK
     * compuesta (participante_id, habito_id) — idempotente, nunca lanza por PK duplicada.
     * Elegir el mismo habito dos veces (doble tap, reintento de red) no duplica la fila; dos
     * elecciones concurrentes del mismo habito se serializan contra la restriccion UNIQUE de
     * Postgres en vez de que ambas violen la PK.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO renaser.desbloqueos_habito
                (participante_id, habito_id, dia_desbloqueo, elegido_en, creado_en, actualizado_en)
            VALUES (:participanteId, :habitoId, CAST(:diaDesbloqueo AS smallint), :elegidoEn, :ahora, :ahora)
            ON CONFLICT (participante_id, habito_id) DO NOTHING
            """, nativeQuery = true)
    void elegirSiFalta(@Param("participanteId") UUID participanteId, @Param("habitoId") UUID habitoId,
                        @Param("diaDesbloqueo") int diaDesbloqueo, @Param("elegidoEn") Instant elegidoEn,
                        @Param("ahora") Instant ahora);

    /** Idempotente por naturaleza: borrar lo que no existe afecta 0 filas y no falla (D-87). */
    @Modifying(clearAutomatically = true)
    void deleteByParticipanteIdAndHabitoId(UUID participanteId, UUID habitoId);
}
