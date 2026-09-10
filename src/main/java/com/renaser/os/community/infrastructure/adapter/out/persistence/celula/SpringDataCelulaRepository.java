package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataCelulaRepository extends JpaRepository<CelulaJpaEntity, UUID> {

    List<CelulaJpaEntity> findByCohorteIdOrderByNombreAsc(UUID cohorteId);

    List<CelulaJpaEntity> findAllByOrderByNombreAsc();

    Optional<CelulaJpaEntity> findByMentorId(UUID mentorId);

    /**
     * Grupos cuyo periodo cierra dentro de la ventana, ambos extremos inclusive.
     *
     * <p>Proyeccion y no la entidad: quien llama necesita nombre y fechas para componer un aviso,
     * no la celula entera con su mentor, su cohorte y su cupo. Este barrido recorre todo el padron
     * una vez al dia.
     *
     * <p>El {@code periodoFin IS NOT NULL} lo cubre ya el BETWEEN —un nulo no esta entre nada—,
     * pero se deja explicito: es la condicion que hace que las celulas sin periodo queden fuera, y
     * esconderla en la semantica de SQL de los nulos la vuelve invisible para quien lea esto.
     */
    @Query("""
            SELECT c.id AS celulaId, c.nombre AS nombre,
                   c.periodoInicio AS inicioDelPeriodo, c.periodoFin AS finDelPeriodo
            FROM CelulaJpaEntity c
            WHERE c.periodoFin IS NOT NULL AND c.periodoFin BETWEEN :desde AND :hasta
            ORDER BY c.periodoFin ASC
            """)
    List<GrupoQueVenceProjection> conCierreEntre(@Param("desde") LocalDate desde,
                                                  @Param("hasta") LocalDate hasta);

    interface GrupoQueVenceProjection {
        UUID getCelulaId();

        String getNombre();

        LocalDate getInicioDelPeriodo();

        LocalDate getFinDelPeriodo();
    }

    /**
     * El grupo de RECEPCION cuyo periodo contiene ese dia.
     *
     * <p>Orden por {@code periodoInicio DESC}: si hay varios abiertos a la vez gana el que empezo
     * mas tarde, que es el que le deja mas dias de bienvenida a quien entra hoy. Con el criterio
     * contrario, alguien que se registra el ultimo dia de un grupo viejo se queda sin recepcion.
     */
    @Query("""
            SELECT c FROM CelulaJpaEntity c
            WHERE c.tipo = com.renaser.os.community.domain.model.acompanamiento.TipoCelula.RECEPCION
              AND c.periodoInicio IS NOT NULL
              AND :dia BETWEEN c.periodoInicio AND c.periodoFin
            ORDER BY c.periodoInicio DESC
            """)
    List<CelulaJpaEntity> recepcionesVigentesEn(@Param("dia") LocalDate dia);
}
