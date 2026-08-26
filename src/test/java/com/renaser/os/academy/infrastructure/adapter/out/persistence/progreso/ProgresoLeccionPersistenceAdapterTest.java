package com.renaser.os.academy.infrastructure.adapter.out.persistence.progreso;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Postgres real: {@code completadasPorCurso} es el JOIN nativo
 * (`progreso_lecciones` + `lecciones`, agrupado por `curso_id`) que reemplaza
 * la parte de agregacion de la RPC `progreso_cursos` — la otra mitad
 * (`total_lecciones`, `LeccionPersistenceAdapter.contarPorCurso`) es un
 * `GROUP BY` simple ya cubierto por el mapeo JPQL. Ambas alimentan
 * `GET /api/v1/cursos` (`CatalogoAcademyService.misCursos`), que es donde vive
 * hoy la logica de `progreso_cursos` — ver `docs/MODULO_ACADEMY.md` §5,
 * decision AC-14 (no se creo un endpoint standalone: el REST ya la cubre
 * completa desde el primer intento, sin fallback a Supabase).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProgresoLeccionPersistenceAdapterTest {

    @Autowired
    private ProgresoLeccionPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId crearUsuario() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .executeUpdate();
        return id;
    }

    private CursoId crearCursoConLecciones(String prefijo, int cantidadLecciones) {
        String cursoId = prefijo + "-" + UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cursos (id, slug, titulo, publicado, acceso, origen)
                        VALUES (:id, :id, :id, true, CAST('ABIERTO' AS renaser.acceso_curso), 'skool')
                        """)
                .setParameter("id", cursoId)
                .executeUpdate();
        for (int i = 0; i < cantidadLecciones; i++) {
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.lecciones (id, curso_id, titulo, orden)
                            VALUES (:id, :cursoId, :titulo, :orden)
                            """)
                    .setParameter("id", cursoId + "-l" + i)
                    .setParameter("cursoId", cursoId)
                    .setParameter("titulo", "Leccion " + i)
                    .setParameter("orden", (short) i)
                    .executeUpdate();
        }
        return CursoId.of(cursoId);
    }

    @Test
    @DisplayName("completadasPorCurso: agrupa por curso solo las lecciones del usuario, ignorando otros cursos/usuarios")
    void completadasPorCursoAgrupaCorrectamente() {
        UserId usuario = crearUsuario();
        UserId otroUsuario = crearUsuario();
        CursoId cursoA = crearCursoConLecciones("ca", 3);
        CursoId cursoB = crearCursoConLecciones("cb", 2);

        // El usuario completa 2 lecciones de A y 1 de B.
        adapter.marcarCompletada(new ProgresoLeccion(usuario, LeccionId.of(cursoA.value() + "-l0"), Instant.now()));
        adapter.marcarCompletada(new ProgresoLeccion(usuario, LeccionId.of(cursoA.value() + "-l1"), Instant.now()));
        adapter.marcarCompletada(new ProgresoLeccion(usuario, LeccionId.of(cursoB.value() + "-l0"), Instant.now()));
        // El otro usuario no debe contaminar el conteo del primero.
        adapter.marcarCompletada(new ProgresoLeccion(otroUsuario, LeccionId.of(cursoA.value() + "-l2"), Instant.now()));
        // completadasPorCurso es SQL nativo: Hibernate no infiere sola las tablas que toca,
        // asi que no auto-flushea antes de una query nativa (a diferencia de JPQL/Criteria) — flush explicito.
        entityManager.flush();

        Map<CursoId, Integer> resultado = adapter.completadasPorCurso(usuario);

        assertThat(resultado).containsEntry(cursoA, 2).containsEntry(cursoB, 1);
    }

    @Test
    @DisplayName("completadasPorCurso: usuario sin progreso -> mapa vacio")
    void completadasPorCursoSinProgresoMapaVacio() {
        UserId usuario = crearUsuario();

        assertThat(adapter.completadasPorCurso(usuario)).isEmpty();
    }

    @Test
    @DisplayName("marcarCompletada: idempotente, no duplica fila ni cambia completadaEn")
    void marcarCompletadaEsIdempotente() {
        UserId usuario = crearUsuario();
        CursoId curso = crearCursoConLecciones("cc", 1);
        LeccionId leccion = LeccionId.of(curso.value() + "-l0");
        Instant primeraVez = Instant.now();

        ProgresoLeccion primero = adapter.marcarCompletada(new ProgresoLeccion(usuario, leccion, primeraVez));
        ProgresoLeccion segundo = adapter
                .marcarCompletada(new ProgresoLeccion(usuario, leccion, primeraVez.plusSeconds(60)));

        assertThat(segundo.completadaEn()).isEqualTo(primero.completadaEn());
        entityManager.flush();
        assertThat(adapter.completadasPorCurso(usuario)).containsEntry(curso, 1);
    }

    @Test
    @DisplayName("estaCompletada y leccionesCompletadas reflejan el estado guardado")
    void estaCompletadaYLeccionesCompletadas() {
        UserId usuario = crearUsuario();
        CursoId curso = crearCursoConLecciones("cd", 2);
        LeccionId completada = LeccionId.of(curso.value() + "-l0");
        LeccionId sinCompletar = LeccionId.of(curso.value() + "-l1");
        adapter.marcarCompletada(new ProgresoLeccion(usuario, completada, Instant.now()));

        assertThat(adapter.estaCompletada(usuario, completada)).isTrue();
        assertThat(adapter.estaCompletada(usuario, sinCompletar)).isFalse();
        assertThat(adapter.leccionesCompletadas(usuario)).isEqualTo(Set.of(completada));
    }

    @Test
    @DisplayName("desmarcarCompletada: borra el progreso guardado (inverso de marcarCompletada, AC-16)")
    void desmarcarCompletadaBorraElProgreso() {
        UserId usuario = crearUsuario();
        CursoId curso = crearCursoConLecciones("ce", 1);
        LeccionId leccion = LeccionId.of(curso.value() + "-l0");
        adapter.marcarCompletada(new ProgresoLeccion(usuario, leccion, Instant.now()));

        adapter.desmarcarCompletada(usuario, leccion);
        entityManager.flush();

        assertThat(adapter.estaCompletada(usuario, leccion)).isFalse();
        assertThat(adapter.completadasPorCurso(usuario)).doesNotContainKey(curso);
    }

    @Test
    @DisplayName("desmarcarCompletada: idempotente, no falla si la leccion no estaba completada")
    void desmarcarCompletadaEsIdempotente() {
        UserId usuario = crearUsuario();
        CursoId curso = crearCursoConLecciones("cf", 1);
        LeccionId leccion = LeccionId.of(curso.value() + "-l0");

        adapter.desmarcarCompletada(usuario, leccion);

        assertThat(adapter.estaCompletada(usuario, leccion)).isFalse();
    }

    /**
     * Criterio de aceptacion de D-43 (`PorcentajeCursosFinder`): con VARIOS
     * participantes, {@code completadasPorCursoEnLote} tiene que resolver en
     * UNA sola consulta — no una por cabeza. Se prueba con estadisticas de
     * Hibernate en vez de solo revisar el resultado, porque el bug real que
     * motivo el encargo ("Too many database connections opened") es sobre
     * CUANTAS consultas se disparan, no sobre si el resultado es correcto.
     */
    @Test
    @DisplayName("completadasPorCursoEnLote: agrupa por usuario y curso, en UNA sola consulta para varios usuarios")
    void completadasPorCursoEnLoteAgrupaEnUnaSolaConsulta() {
        UserId usuarioA = crearUsuario();
        UserId usuarioB = crearUsuario();
        UserId usuarioC = crearUsuario();
        CursoId cursoA = crearCursoConLecciones("ga", 3);
        CursoId cursoB = crearCursoConLecciones("gb", 2);

        adapter.marcarCompletada(new ProgresoLeccion(usuarioA, LeccionId.of(cursoA.value() + "-l0"), Instant.now()));
        adapter.marcarCompletada(new ProgresoLeccion(usuarioA, LeccionId.of(cursoA.value() + "-l1"), Instant.now()));
        adapter.marcarCompletada(new ProgresoLeccion(usuarioB, LeccionId.of(cursoB.value() + "-l0"), Instant.now()));
        // usuarioC no completo nada: no debe aparecer en el resultado.
        entityManager.flush();

        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics estadisticas = sessionFactory.getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();

        Map<UserId, Map<CursoId, Integer>> resultado =
                adapter.completadasPorCursoEnLote(List.of(usuarioA, usuarioB, usuarioC));

        assertThat(estadisticas.getQueryExecutionCount()).isEqualTo(1);
        assertThat(resultado.get(usuarioA)).containsEntry(cursoA, 2);
        assertThat(resultado.get(usuarioB)).containsEntry(cursoB, 1);
        assertThat(resultado).doesNotContainKey(usuarioC);
    }

    @Test
    @DisplayName("completadasPorCursoEnLote: coleccion vacia -> mapa vacio, sin consultar")
    void completadasPorCursoEnLoteColeccionVaciaMapaVacio() {
        assertThat(adapter.completadasPorCursoEnLote(List.of())).isEmpty();
    }
}
