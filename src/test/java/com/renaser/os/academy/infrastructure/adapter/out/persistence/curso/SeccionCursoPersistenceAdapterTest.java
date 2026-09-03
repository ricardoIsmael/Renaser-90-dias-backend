package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Postgres real: {@code listarTodas} es el insumo de
 * {@code LeccionesVisiblesAcademyService} para calcular visibilidad de catálogo en lote SIN
 * pedir secciones curso por curso (anti N+1) — esta prueba verifica que trae secciones de
 * VARIOS cursos en una sola consulta, con su {@code diaDesbloqueo} intacto.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SeccionCursoPersistenceAdapterTest {

    @Autowired
    private SeccionCursoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private String crearCurso() {
        String cursoId = "c-" + UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cursos (id, slug, titulo, publicado, acceso, origen)
                        VALUES (:id, :id, :id, true, CAST('ABIERTO' AS renaser.acceso_curso), 'skool')
                        """)
                .setParameter("id", cursoId)
                .executeUpdate();
        return cursoId;
    }

    /** Sin `dia_desbloqueo` a proposito: se omite la columna en vez de bindear `null`
     * (mismo criterio que {@code CursoPersistenceAdapterTest}). */
    private String crearSeccionSinDia(String cursoId) {
        String seccionId = "s-" + UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.secciones_curso (id, curso_id, titulo, orden)
                        VALUES (:id, :cursoId, :id, 0)
                        """)
                .setParameter("id", seccionId)
                .setParameter("cursoId", cursoId)
                .executeUpdate();
        return seccionId;
    }

    private String crearSeccionConDia(String cursoId, int diaDesbloqueo) {
        String seccionId = "s-" + UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.secciones_curso (id, curso_id, titulo, orden, dia_desbloqueo)
                        VALUES (:id, :cursoId, :id, 0, :dia)
                        """)
                .setParameter("id", seccionId)
                .setParameter("cursoId", cursoId)
                .setParameter("dia", (short) diaDesbloqueo)
                .executeUpdate();
        return seccionId;
    }

    @Test
    @DisplayName("listarTodas: trae secciones de varios cursos distintos, con su dia_desbloqueo")
    void listarTodasTraeSeccionesDeVariosCursos() {
        String cursoA = crearCurso();
        String cursoB = crearCurso();
        String seccionSinDia = crearSeccionSinDia(cursoA);
        String seccionConDia = crearSeccionConDia(cursoB, 17);

        List<SeccionCurso> todas = adapter.listarTodas();
        List<SeccionCurso> deEsteTest = todas.stream()
                .filter(s -> s.id().equals(SeccionCursoId.of(seccionSinDia)) || s.id().equals(SeccionCursoId.of(seccionConDia)))
                .toList();

        assertThat(deEsteTest).hasSize(2);
        SeccionCurso resultadoSinDia = deEsteTest.stream()
                .filter(s -> s.id().equals(SeccionCursoId.of(seccionSinDia))).findFirst().orElseThrow();
        assertThat(resultadoSinDia.cursoId()).isEqualTo(CursoId.of(cursoA));
        assertThat(resultadoSinDia.diaDesbloqueo()).isNull();
        SeccionCurso resultadoConDia = deEsteTest.stream()
                .filter(s -> s.id().equals(SeccionCursoId.of(seccionConDia))).findFirst().orElseThrow();
        assertThat(resultadoConDia.cursoId()).isEqualTo(CursoId.of(cursoB));
        assertThat(resultadoConDia.diaDesbloqueo()).isEqualTo(17);
    }
}
