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
     * <p>Orden por {@code periodo_inicio DESC}: si hay varios abiertos a la vez gana el que empezo
     * mas tarde, que es el que le deja mas dias de bienvenida a quien entra hoy. Con el criterio
     * contrario, alguien que se registra el ultimo dia de un grupo viejo se queda sin recepcion.
     *
     * <blockquote><b>NATIVA, y el CAST va escrito a mano. E-180.</b> Esta consulta era JPQL con el
     * literal {@code c.tipo = ...TipoCelula.RECEPCION}. La columna esta mapeada
     * {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)}, y para ese literal Hibernate genera
     * {@code cast(? as tipocelula)} —el nombre simple del enum Java en minusculas— cuando el tipo
     * de Postgres se llama {@code renaser.tipo_celula}. Resultado: {@code PSQLException: type
     * "tipocelula" does not exist} en CADA llamada.
     *
     * <p>Y no rompia ninguna prueba, porque el unico consumidor es el ingreso automatico a la
     * bienvenida, que trata "no hay recepcion" como un caso valido. O sea: la automatica no metia a
     * nadie en ningun grupo y el sistema lo reportaba como si simplemente no hubiera bienvenida
     * abierta. Es exactamente el mismo defecto que E-171 con {@code estadoregistrojpa}.</blockquote>
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM renaser.celulas c
            WHERE c.tipo = CAST('RECEPCION' AS renaser.tipo_celula)
              AND c.periodo_inicio IS NOT NULL
              AND CAST(:dia AS date) BETWEEN c.periodo_inicio AND c.periodo_fin
            ORDER BY c.periodo_inicio DESC
            """)
    List<CelulaJpaEntity> recepcionesVigentesEn(@Param("dia") LocalDate dia);
}
