package com.renaser.os.rocks.infrastructure.adapter.out.persistence.coherencia;

import com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria.RocaDiariaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio propio para la única consulta agregada que necesita D-43 — no
 * reutiliza {@code SpringDataRocaDiariaRepository} porque es package-private
 * del paquete {@code rocadiaria}. Spring Data admite más de un repositorio
 * sobre la misma {@code @Entity}; no hay conflicto en tener dos.
 *
 * <p>JPQL, no SQL nativo (a diferencia del extinto {@code RegistrarEvidenciaRocaPersistenceAdapter}
 * que RK-2 reemplazó por {@code evidence.api.RegistrarEvidenciaPort}, ver
 * {@code docs/MODULO_EVIDENCE.md}): esta consulta es puro {@code GROUP BY} sobre
 * columnas propias de {@code rocas_diarias}, sin cast a tipos enum de Postgres —
 * no hace falta bajar a nativo.
 */
interface SpringDataConteoDiarioRocasRepository extends JpaRepository<RocaDiariaJpaEntity, UUID> {

    /**
     * Una fila por (participante, día) con al menos una Roca Diaria en el
     * rango — {@code total} = COUNT(*), {@code completadas} = cuántas de esas
     * tienen {@code completada = true}. EN LOTE: {@code participantes} es la
     * lista completa pedida, una sola consulta.
     */
    @Query("""
            SELECT r.participanteId, r.fecha, COUNT(r), SUM(CASE WHEN r.completada = true THEN 1L ELSE 0L END)
            FROM RocaDiariaJpaEntity r
            WHERE r.participanteId IN :participantes AND r.fecha BETWEEN :desde AND :hasta
            GROUP BY r.participanteId, r.fecha
            """)
    List<Object[]> conteoDiarioPorParticipante(@Param("participantes") Collection<UUID> participantes,
                                                @Param("desde") LocalDate desde,
                                                @Param("hasta") LocalDate hasta);
}
