package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort.LeccionCatalogo;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
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
 * Contra Postgres real: {@code listarIdentificadores} es el insumo de
 * {@code LeccionesVisiblesAcademyService} (bug de fuga de contenido en `rag`, ver
 * `docs/MODULO_RAG.md`) — esta prueba verifica que la proyección JPQL trae {@code seccionId}
 * {@code null} cuando la lección es suelta y el valor correcto cuando pertenece a una
 * sección, sin traer {@code cuerpoHtml}/{@code cuerpoMd} (la query ni los selecciona).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class LeccionPersistenceAdapterTest {

    @Autowired
    private LeccionPersistenceAdapter adapter;

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

    private String crearSeccion(String cursoId) {
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

    /** Sin `seccion_id` a proposito: se omite la columna en vez de bindear `null` como
     * parametro, mismo criterio que {@code CursoPersistenceAdapterTest#crearCurso} con
     * `dia_desbloqueo` — Postgres no puede inferir el tipo de un parametro nativo que solo
     * recibe `null`. La columna es nullable, asi que omitirla ya da `NULL`. */
    private void crearLeccionSuelta(String id, String cursoId) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.lecciones (id, curso_id, titulo, orden, cuerpo_html)
                        VALUES (:id, :cursoId, :id, 0, :cuerpo)
                        """)
                .setParameter("id", id)
                .setParameter("cursoId", cursoId)
                .setParameter("cuerpo", "cuerpo largo que listarIdentificadores no deberia traer")
                .executeUpdate();
    }

    private void crearLeccionDeSeccion(String id, String cursoId, String seccionId) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.lecciones (id, curso_id, seccion_id, titulo, orden, cuerpo_html)
                        VALUES (:id, :cursoId, :seccionId, :id, 0, :cuerpo)
                        """)
                .setParameter("id", id)
                .setParameter("cursoId", cursoId)
                .setParameter("seccionId", seccionId)
                .setParameter("cuerpo", "cuerpo largo que listarIdentificadores no deberia traer")
                .executeUpdate();
    }

    @Test
    @DisplayName("listarIdentificadores: incluye la seccion cuando la leccion pertenece a una, null cuando es suelta")
    void listarIdentificadoresTraeCursoYSeccionCorrectos() {
        String cursoId = crearCurso();
        String seccionId = crearSeccion(cursoId);
        crearLeccionSuelta("l-suelta-" + UUID.randomUUID(), cursoId);
        String idDeSeccion = "l-de-seccion-" + UUID.randomUUID();
        crearLeccionDeSeccion(idDeSeccion, cursoId, seccionId);

        List<LeccionCatalogo> resultado = adapter.listarIdentificadores().stream()
                .filter(l -> l.cursoId().equals(CursoId.of(cursoId)))
                .toList();

        assertThat(resultado).hasSize(2);
        LeccionCatalogo deSeccion = resultado.stream()
                .filter(l -> l.id().equals(LeccionId.of(idDeSeccion)))
                .findFirst().orElseThrow();
        assertThat(deSeccion.seccionId()).isEqualTo(SeccionCursoId.of(seccionId));
        LeccionCatalogo suelta = resultado.stream()
                .filter(l -> !l.id().equals(LeccionId.of(idDeSeccion)))
                .findFirst().orElseThrow();
        assertThat(suelta.seccionId()).isNull();
    }
}
