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
     * El grupo de RECEPCION vigente para ese dia.
     *
     * <p><b>Sin periodo = recepcion PERMANENTE.</b> Una bienvenida sin fechas esta siempre vigente:
     * es el modelo por defecto, porque el corte de quien esta en la bienvenida lo pone el DIA DE
     * PROGRAMA de cada persona ({@code dia_traslado} de la politica), no el calendario del grupo.
     * Ponerle fechas a la recepcion solo tiene sentido para una bienvenida temporal de una fecha
     * concreta; sin ellas, recibe a todo el que se va registrando, indefinidamente.
     *
     * <p>Orden {@code periodo_inicio DESC NULLS LAST}: si ademas de la permanente hay una fechada y
     * hoy cae en su rango, esa gana (una bienvenida especial de un evento manda sobre la de siempre);
     * si no, cae en la permanente. Entre dos fechadas gana la que empezo mas tarde, que le deja mas
     * dias de bienvenida a quien entra hoy.
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
              AND (c.periodo_inicio IS NULL
                   OR CAST(:dia AS date) BETWEEN c.periodo_inicio AND c.periodo_fin)
            ORDER BY c.periodo_inicio DESC NULLS LAST
            """)
    List<CelulaJpaEntity> recepcionesVigentesEn(@Param("dia") LocalDate dia);
}
