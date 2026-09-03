package com.renaser.os.rag.infrastructure.adapter.out.vectorstore;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FiltroLecciones;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FragmentoRelevante;
import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimientoId;
import com.renaser.os.shared.domain.FixedClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * El test más valioso del agregado {@code conocimiento} (docs/MODULO_RAG.md D-45): es la
 * única forma de verificar contra Postgres real que el SQL con {@code <=>} (distancia
 * coseno) y el {@code CAST(? AS vector)} funcionan de verdad — el dominio no puede
 * probarlo, y un mock del puerto tampoco. Tambien es donde se prueba, contra Postgres real,
 * que {@link FiltroLecciones#soloVisibles} filtra de verdad (el {@code ANY(CAST(? AS text[]))}
 * del WHERE) — un mock del puerto no podria detectar un WHERE mal armado.
 *
 * <p>{@link EmbeddingPort} se reemplaza por un mock (en vez del {@code NoOpEmbeddingAdapter}
 * real, que siempre devuelve ceros) para poder controlar el vector de la "consulta" y
 * verificar que el orden de distancias es el esperado.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PgVectorNativoAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T12:00:00Z"));
    private static final String CONSULTA = "que es el pacto de sangre";

    @Autowired
    private PgVectorNativoAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private EmbeddingPort embeddingPort;

    /** Un id cualquiera, distinto por chunk: la identidad ya no la sortea el agregado. */
    private static ChunkConocimientoId nuevoId() {
        return ChunkConocimientoId.of(UUID.randomUUID());
    }

    /**
     * {@code base_conocimiento.leccion_id} tiene FK real a {@code lecciones.id} (a su vez
     * FK a {@code cursos.id}) — los tests de {@link FiltroLecciones#soloVisibles} necesitan
     * una lección que exista de verdad, no solo un id de texto suelto.
     */
    private String crearLeccion() {
        String cursoId = "c-" + UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cursos (id, slug, titulo, publicado, acceso, origen)
                        VALUES (:id, :id, :id, true, CAST('ABIERTO' AS renaser.acceso_curso), 'skool')
                        """)
                .setParameter("id", cursoId)
                .executeUpdate();
        String leccionId = "l-" + UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.lecciones (id, curso_id, titulo, orden)
                        VALUES (:id, :cursoId, :id, 0)
                        """)
                .setParameter("id", leccionId)
                .setParameter("cursoId", cursoId)
                .executeUpdate();
        return leccionId;
    }

    private static List<Float> vectorConComponentes(int indiceA, float valorA, int indiceB, float valorB) {
        List<Float> vector = new ArrayList<>(Collections.nCopies(ChunkConocimiento.DIMENSION_EMBEDDING, 0.0f));
        vector.set(indiceA, valorA);
        if (indiceB >= 0) {
            vector.set(indiceB, valorB);
        }
        return vector;
    }

    @Test
    void buscarSimilaresDevuelveLosChunksOrdenadosPorDistanciaCosenoAscendente() {
        ChunkConocimiento identico = ChunkConocimiento.indexar(nuevoId(), "LECCION", "texto", "doc-1", null,
                "contenido identico a la consulta", vectorConComponentes(0, 1.0f, -1, 0f), Map.of(), CLOCK);
        ChunkConocimiento parecido = ChunkConocimiento.indexar(nuevoId(), "LECCION", "texto", "doc-2", null,
                "contenido parecido a la consulta", vectorConComponentes(0, 0.9f, 1, 0.1f), Map.of(), CLOCK);
        ChunkConocimiento distinto = ChunkConocimiento.indexar(nuevoId(), "LECCION", "texto", "doc-3", null,
                "contenido sin relacion con la consulta", vectorConComponentes(1, 1.0f, -1, 0f), Map.of(), CLOCK);
        // Insertados en orden distinto al esperado en la respuesta: si el resultado
        // sale ordenado es porque el ORDER BY embedding <=> ... realmente ordena.
        adapter.save(distinto);
        adapter.save(identico);
        adapter.save(parecido);

        when(embeddingPort.generar(eq(CONSULTA))).thenReturn(vectorConComponentes(0, 1.0f, -1, 0f));

        List<FragmentoRelevante> resultado = adapter.buscarSimilares(CONSULTA, 3, FiltroLecciones.sinFiltro());

        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).contenido()).isEqualTo("contenido identico a la consulta");
        assertThat(resultado.get(0).distancia()).isCloseTo(0.0, offset(0.0001));
        assertThat(resultado.get(1).contenido()).isEqualTo("contenido parecido a la consulta");
        assertThat(resultado.get(2).contenido()).isEqualTo("contenido sin relacion con la consulta");
        assertThat(resultado.get(2).distancia()).isCloseTo(1.0, offset(0.0001));
    }

    @Test
    void buscarSimilaresRespetaElLimiteTopK() {
        for (int i = 0; i < 5; i++) {
            adapter.save(ChunkConocimiento.indexar(nuevoId(), "LECCION", "texto", "doc-" + i, null, "contenido " + i,
                    vectorConComponentes(i, 1.0f, -1, 0f), Map.of(), CLOCK));
        }
        when(embeddingPort.generar(eq(CONSULTA))).thenReturn(vectorConComponentes(0, 1.0f, -1, 0f));

        List<FragmentoRelevante> resultado = adapter.buscarSimilares(CONSULTA, 2, FiltroLecciones.sinFiltro());

        assertThat(resultado).hasSize(2);
    }

    @Test
    void saveGuardaLeccionIdNuloCuandoLaFuenteNoVieneDeUnaLeccionPuntual() {
        ChunkConocimiento chunk = ChunkConocimiento.indexar(nuevoId(), "DOCUMENTO_LIBRE", null, "doc-99", null,
                "contenido sin leccion asociada", vectorConComponentes(5, 1.0f, -1, 0f), Map.of("clave", "valor"),
                CLOCK);

        adapter.save(chunk);

        when(embeddingPort.generar(eq(CONSULTA))).thenReturn(vectorConComponentes(5, 1.0f, -1, 0f));
        List<FragmentoRelevante> resultado = adapter.buscarSimilares(CONSULTA, 1, FiltroLecciones.sinFiltro());

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).leccionId()).isNull();
        assertThat(resultado.get(0).contenido()).isEqualTo("contenido sin leccion asociada");
    }

    @Test
    void soloVisiblesDescartaChunksDeLeccionesQueNoEstanEnElConjuntoVisible() {
        String leccionVisible = crearLeccion();
        String leccionBloqueada = crearLeccion();
        adapter.save(ChunkConocimiento.indexar(nuevoId(), "LECCION", "texto", "doc-1", leccionVisible,
                "contenido de la leccion visible", vectorConComponentes(0, 1.0f, -1, 0f), Map.of(), CLOCK));
        adapter.save(ChunkConocimiento.indexar(nuevoId(), "LECCION", "texto", "doc-2", leccionBloqueada,
                "contenido de la leccion bloqueada por dia", vectorConComponentes(0, 0.99f, 1, 0.01f), Map.of(),
                CLOCK));
        when(embeddingPort.generar(eq(CONSULTA))).thenReturn(vectorConComponentes(0, 1.0f, -1, 0f));

        List<FragmentoRelevante> resultado = adapter.buscarSimilares(CONSULTA, 5,
                FiltroLecciones.soloVisibles(Set.of(leccionVisible)));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).leccionId()).isEqualTo(leccionVisible);
    }

    @Test
    void soloVisiblesNuncaDescartaMaterialGeneralSinLeccionAsociada() {
        String leccionBloqueada = crearLeccion();
        adapter.save(ChunkConocimiento.indexar(nuevoId(), "DOCUMENTO_LIBRE", null, "doc-1", null,
                "material general sin leccion", vectorConComponentes(0, 1.0f, -1, 0f), Map.of(), CLOCK));
        adapter.save(ChunkConocimiento.indexar(nuevoId(), "LECCION", "texto", "doc-2", leccionBloqueada,
                "contenido de la leccion bloqueada", vectorConComponentes(0, 0.99f, 1, 0.01f), Map.of(), CLOCK));
        when(embeddingPort.generar(eq(CONSULTA))).thenReturn(vectorConComponentes(0, 1.0f, -1, 0f));

        // Conjunto de visibles VACIO: ningun curso accesible para el actor — igual debe
        // devolver el material general, que no esta ligado a ninguna leccion.
        List<FragmentoRelevante> resultado = adapter.buscarSimilares(CONSULTA, 5,
                FiltroLecciones.soloVisibles(Set.of()));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).leccionId()).isNull();
        assertThat(resultado.get(0).contenido()).isEqualTo("material general sin leccion");
    }
}
