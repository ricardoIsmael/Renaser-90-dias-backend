package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-31 (docs/BITACORA_ERRORES.md): {@code feed}/{@code feedOculto} usaban el patron JPQL
 * {@code (:cursor IS NULL OR col < :cursor)}, que Postgres no puede preparar porque no hay
 * forma de inferir el tipo de un parametro que aparece solo en {@code ? IS NULL} — rompia
 * en produccion con "could not determine data type of parameter $1" apenas se pedia la
 * primera pagina del Muro (sin cursor), el caso mas comun. Los tests unitarios con mocks
 * (`PublicacionMuroServiceTest`) no lo detectaban porque no hablan con Postgres real — de
 * ahi que este test corra contra un Postgres real via Testcontainers, cubriendo las 4
 * combinaciones de {@code feed} (con/sin cursor x con/sin categoria) y las 2 de
 * {@code feedOculto}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class PublicacionPersistenceAdapterTest {

    @Autowired
    private PublicacionPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId autorId;

    @BeforeEach
    void seedAutorYCategorias() {
        autorId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", autorId.value())
                .setParameter("email", autorId + "@renaser.test")
                .executeUpdate();
        for (String clave : List.of("general", "logros")) {
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.categorias_muro (clave, etiqueta, emoji)
                            VALUES (:clave, :clave, '🏷')
                            """)
                    .setParameter("clave", clave)
                    .executeUpdate();
        }
    }

    private Publicacion crearPublicacion(String categoriaClave, Instant creadoEn) {
        Publicacion publicacion = Publicacion.publicar(autorId, "texto de prueba",
                List.of(new MediaPublicacion(MediaPublicacion.BUCKET_DEFAULT, "ruta/1.jpg", "image/jpeg", 0)),
                categoriaClave, creadoEn);
        return adapter.save(publicacion);
    }

    @Test
    void feedSinCursorNiCategoriaTraeTodoLoVisibleOrdenadoDesc() {
        crearPublicacion(null, Instant.parse("2026-08-20T10:00:00Z"));
        crearPublicacion("general", Instant.parse("2026-08-21T10:00:00Z"));
        crearPublicacion("logros", Instant.parse("2026-08-22T10:00:00Z"));

        List<Publicacion> pagina = adapter.feed(null, 10, null);

        assertThat(pagina).hasSize(3);
        assertThat(pagina).extracting(p -> p.creadoEn())
                .containsExactly(Instant.parse("2026-08-22T10:00:00Z"), Instant.parse("2026-08-21T10:00:00Z"),
                        Instant.parse("2026-08-20T10:00:00Z"));
    }

    @Test
    void feedConCursorSoloTraeLoAnteriorAlCursor() {
        crearPublicacion(null, Instant.parse("2026-08-20T10:00:00Z"));
        crearPublicacion(null, Instant.parse("2026-08-21T10:00:00Z"));
        crearPublicacion(null, Instant.parse("2026-08-22T10:00:00Z"));

        List<Publicacion> pagina = adapter.feed(Instant.parse("2026-08-22T10:00:00Z"), 10, null);

        assertThat(pagina).hasSize(2);
        assertThat(pagina).extracting(p -> p.creadoEn())
                .containsExactly(Instant.parse("2026-08-21T10:00:00Z"), Instant.parse("2026-08-20T10:00:00Z"));
    }

    @Test
    void feedSinCursorConCategoriaFiltraSoloEsaCategoria() {
        crearPublicacion("general", Instant.parse("2026-08-20T10:00:00Z"));
        crearPublicacion("logros", Instant.parse("2026-08-21T10:00:00Z"));
        crearPublicacion("general", Instant.parse("2026-08-22T10:00:00Z"));

        List<Publicacion> pagina = adapter.feed(null, 10, "general");

        assertThat(pagina).hasSize(2);
        assertThat(pagina).allMatch(p -> "general".equals(p.categoriaClave()));
    }

    @Test
    void feedConCursorYCategoriaCombinaAmbosFiltros() {
        crearPublicacion("general", Instant.parse("2026-08-20T10:00:00Z"));
        crearPublicacion("general", Instant.parse("2026-08-21T10:00:00Z"));
        crearPublicacion("logros", Instant.parse("2026-08-22T10:00:00Z"));
        crearPublicacion("general", Instant.parse("2026-08-23T10:00:00Z"));

        List<Publicacion> pagina = adapter.feed(Instant.parse("2026-08-23T10:00:00Z"), 10, "general");

        assertThat(pagina).hasSize(2);
        assertThat(pagina).allMatch(p -> "general".equals(p.categoriaClave()));
        assertThat(pagina).extracting(p -> p.creadoEn())
                .containsExactly(Instant.parse("2026-08-21T10:00:00Z"), Instant.parse("2026-08-20T10:00:00Z"));
    }

    @Test
    void feedOcultoSinCursorTraeSoloLoOculto() {
        Publicacion visible = crearPublicacion(null, Instant.parse("2026-08-20T10:00:00Z"));
        Publicacion oculta = crearPublicacion(null, Instant.parse("2026-08-21T10:00:00Z"));
        oculta.ocultar(Instant.parse("2026-08-21T11:00:00Z"));
        adapter.save(oculta);

        List<Publicacion> pagina = adapter.feedOculto(null, 10);

        assertThat(pagina).hasSize(1);
        assertThat(pagina.get(0).id()).isEqualTo(oculta.id());
        assertThat(visible.oculta()).isFalse();
    }

    @Test
    void feedOcultoConCursorSoloTraeLoAnteriorAlCursor() {
        Publicacion oculta1 = crearPublicacion(null, Instant.parse("2026-08-20T10:00:00Z"));
        oculta1.ocultar(Instant.parse("2026-08-20T11:00:00Z"));
        adapter.save(oculta1);
        Publicacion oculta2 = crearPublicacion(null, Instant.parse("2026-08-21T10:00:00Z"));
        oculta2.ocultar(Instant.parse("2026-08-21T11:00:00Z"));
        adapter.save(oculta2);

        List<Publicacion> pagina = adapter.feedOculto(Instant.parse("2026-08-21T10:00:00Z"), 10);

        assertThat(pagina).hasSize(1);
        assertThat(pagina.get(0).id()).isEqualTo(oculta1.id());
    }
}
