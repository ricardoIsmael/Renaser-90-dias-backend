package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.users.api.UserRole;
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
 * Contra Postgres real: el JOIN `cursos` + `roles_permitidos_curso` (via
 * `renaser.roles`, AC-05) es la consulta que reemplaza a la RPC
 * `catalogo_cursos_bloqueados` (0018, GET /api/v1/cursos/bloqueados) y, junto
 * con `ProgresoLeccionPersistenceAdapterTest`, a `progreso_cursos`
 * (GET /api/v1/cursos, ver `docs/MODULO_ACADEMY.md` §5, decisiones AC-14/AC-15).
 * El gating por dia en si (`Curso.bloqueadoPorDiaPara`/`visibleEnCatalogoPara`)
 * ya esta cubierto en `CursoTest` (unitario, sin Spring); esto valida que la
 * fila que llega de Postgres (roles incluidos, enum nativo `acceso_curso`) es
 * la que el dominio espera.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CursoPersistenceAdapterTest {

    @Autowired
    private CursoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    /**
     * Sin `dia_desbloqueo` a proposito: se omite la columna en vez de bindear
     * `null` como parametro — Postgres no puede inferir el tipo de un
     * parametro nativo que solo recibe `null` ("could not determine data type
     * of parameter"). La columna es nullable, asi que omitirla ya da `NULL`.
     */
    private void crearCurso(String id, int orden, boolean publicado, String acceso) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cursos (id, slug, titulo, orden, publicado, acceso, origen)
                        VALUES (:id, :id, :titulo, :orden, :publicado, CAST(:acceso AS renaser.acceso_curso), 'skool')
                        """)
                .setParameter("id", id)
                .setParameter("titulo", "Titulo " + id)
                .setParameter("orden", (short) orden)
                .setParameter("publicado", publicado)
                .setParameter("acceso", acceso)
                .executeUpdate();
    }

    private void crearCursoConDia(String id, int orden, boolean publicado, String acceso, int diaDesbloqueo) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cursos (id, slug, titulo, orden, publicado, acceso, origen, dia_desbloqueo)
                        VALUES (:id, :id, :titulo, :orden, :publicado, CAST(:acceso AS renaser.acceso_curso), 'skool', :dia)
                        """)
                .setParameter("id", id)
                .setParameter("titulo", "Titulo " + id)
                .setParameter("orden", (short) orden)
                .setParameter("publicado", publicado)
                .setParameter("acceso", acceso)
                .setParameter("dia", (short) diaDesbloqueo)
                .executeUpdate();
    }

    private void restringirARol(String cursoId, String claveRol) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.roles_permitidos_curso (curso_id, rol_id)
                        SELECT :cursoId, id FROM renaser.roles WHERE clave = :clave
                        """)
                .setParameter("cursoId", cursoId)
                .setParameter("clave", claveRol)
                .executeUpdate();
    }

    @Test
    void listarTodosDevuelveOrdenadoPorOrden() {
        String sufijo = UUID.randomUUID().toString();
        crearCurso("c-" + sufijo + "-b", 2, true, "ABIERTO");
        crearCurso("c-" + sufijo + "-a", 1, true, "ABIERTO");

        List<Curso> todos = adapter.listarTodos().stream()
                .filter(c -> c.id().value().contains(sufijo))
                .toList();

        assertThat(todos).hasSize(2);
        assertThat(todos.get(0).id()).isEqualTo(CursoId.of("c-" + sufijo + "-a"));
        assertThat(todos.get(1).id()).isEqualTo(CursoId.of("c-" + sufijo + "-b"));
    }

    @Test
    void listarTodosTraduceRolesPermitidosDesdeRenaserRoles() {
        String id = "c-" + UUID.randomUUID();
        crearCurso(id, 0, true, "ABIERTO");
        restringirARol(id, "APRENDIZ");
        restringirARol(id, "MENTOR");

        Curso curso = adapter.byId(CursoId.of(id)).orElseThrow();

        assertThat(curso.rolesPermitidos()).containsExactlyInAnyOrder(UserRole.TRAINEE, UserRole.MENTOR);
    }

    @Test
    void byIdSinRolesPermitidosDevuelveConjuntoVacio() {
        String id = "c-" + UUID.randomUUID();
        crearCurso(id, 0, true, "ABIERTO");

        Curso curso = adapter.byId(CursoId.of(id)).orElseThrow();

        assertThat(curso.rolesPermitidos()).isEmpty();
    }

    @Test
    void byIdDevuelveVacioSiElCursoNoExiste() {
        assertThat(adapter.byId(CursoId.of("no-existe-" + UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("listarTodos() + gating de dominio reproduce el resultado de catalogo_cursos_bloqueados")
    void listarTodosMasElGatingDeDominioReproduceCatalogoCursosBloqueados() {
        String id = "c-" + UUID.randomUUID();
        crearCursoConDia(id, 0, true, "ABIERTO", 30);

        Curso curso = adapter.listarTodos().stream()
                .filter(c -> c.id().equals(CursoId.of(id)))
                .findFirst()
                .orElseThrow();

        // TRAINEE en el dia 10: todavia no llega al dia 30 -> aparece bloqueado por dia.
        assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, 10)).isTrue();
        assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, 10)).isFalse();
        // En el dia 30: ya lo ve, deja de estar en "bloqueados".
        assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, 30)).isFalse();
        assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, 30)).isTrue();
    }
}
