package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;

interface SpringDataRocaDiariaRepository extends JpaRepository<RocaDiariaJpaEntity, UUID> {

    /**
     * Borra el plan de un dia, con un DELETE de verdad y <b>ejecutado en el acto</b>.
     *
     * > <b>Corregido el 2026-09-22 (E-209).</b> Primero fue un derivado,
     * > `deleteByParticipanteIdAndFecha` a secas. Spring Data resuelve eso cargando las filas y
     * > llamando `em.remove()` en cada una, o sea que el borrado queda <b>encolado</b> hasta el
     * > flush — y Hibernate, al hacer flush, ejecuta los INSERT <b>antes</b> que los DELETE. El
     * > efecto: al reemplazar el plan de un dia, los inserts nuevos chocaban contra los viejos en
     * > `rocas_diarias_participante_id_fecha_eje_posicion_key` y volvia un 409 que la app mostraba
     * > como "ese dia ya esta armado". Lo encontro la prueba en el emulador, no un test.
     *
     * <p>{@code flushAutomatically} vacia lo pendiente ANTES del delete y {@code clearAutomatically}
     * limpia el contexto despues, para que los inserts que vienen no arrastren entidades que ya no
     * existen en la base.
     *
     * <p>Arrastra `acciones_diarias` por el `ON DELETE CASCADE` de la V61.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RocaDiariaJpaEntity r where r.participanteId = :participanteId and r.fecha = :fecha")
    void borrarDeParticipanteYFecha(@Param("participanteId") UUID participanteId, @Param("fecha") LocalDate fecha);

    /**
     * Bloqueo pesimista para el camino de ESCRITURA (mismo patron que
     * {@code SpringDataRegistroHabitoRepository.findByIdParaEscritura}, que a su vez espeja
     * {@code SpringDataPuntajeParticipanteRepository.findByIdParaEscritura}). Sin el, un doble
     * toque o un reintento por timeout de red hacian que dos requests concurrentes leyeran la
     * misma roca con {@code completada = false}, ambas pasaran la validacion en memoria y ambas
     * completaran: doble evidencia, doble premio, dos {@code RocaCompletadaEvent} (C-2,
     * docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RocaDiariaJpaEntity r WHERE r.id = :id")
    Optional<RocaDiariaJpaEntity> findByIdParaEscritura(@Param("id") UUID id);

    List<RocaDiariaJpaEntity> findByParticipanteIdAndFecha(UUID participanteId, LocalDate fecha);

    int countByParticipanteIdAndFecha(UUID participanteId, LocalDate fecha);

    int countByParticipanteIdAndCompletadaTrue(UUID participanteId);

    @Query("select min(r.completadaEn) from RocaDiariaJpaEntity r "
            + "where r.participanteId = :participanteId and r.completada = true")
    Optional<Instant> primeraCompletadaEnDeParticipante(@Param("participanteId") UUID participanteId);

    @Query("select r.fecha from RocaDiariaJpaEntity r "
            + "where r.participanteId = :participanteId and r.completada = true")
    List<LocalDate> fechasCompletadasDeParticipante(@Param("participanteId") UUID participanteId);
}
