package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
        Publicacion publicacion = Publicacion.publicar(PublicacionId.of(UUID.randomUUID()), autorId,
                "texto de prueba",
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

    // ────────────────────────────────────────────────────────────────────────────────────
    // existeDeAutorEntre — lo consume community.api.PublicacionMuroFinder, y de ahi `habits`
    // para decidir si el POST DIARIO EN COMUNIDAD esta cumplido. Va contra Postgres real y no
    // con mocks por el mismo motivo que el resto de este archivo: es una query derivada por
    // nombre de metodo, y que compile no prueba que Spring Data la sepa traducir.
    // ────────────────────────────────────────────────────────────────────────────────────

    /**
     * La ventana es {@code [desde, hasta)}. Se prueban los DOS bordes con publicaciones
     * exactamente en ellos, que es donde un {@code >} en vez de {@code >=} (o un {@code <=} en
     * vez de {@code <}) le regalaria — o le negaria — el habito a alguien por un microsegundo.
     */
    @Test
    void existeDeAutorEntreIncluyeElBordeDeAbajoYExcluyeElDeArriba() {
        Instant desde = Instant.parse("2026-08-24T05:00:00Z"); // 24/08 00:00 en Lima
        Instant hasta = Instant.parse("2026-08-25T05:00:00Z"); // 25/08 00:00 en Lima

        crearPublicacion(null, desde);
        assertThat(adapter.existeDeAutorEntre(autorId, desde, hasta)).isTrue();

        UserId otroAutor = autorSuelto();
        crearPublicacionDe(otroAutor, hasta);
        // La publicacion del borde superior pertenece al dia SIGUIENTE, no a este.
        assertThat(adapter.existeDeAutorEntre(otroAutor, desde, hasta)).isFalse();
    }

    @Test
    void existeDeAutorEntreNoMiraLasPublicacionesDeOtroAutorNiOtroDia() {
        Instant desde = Instant.parse("2026-08-24T05:00:00Z");
        Instant hasta = Instant.parse("2026-08-25T05:00:00Z");

        // Del propio autor pero del dia anterior: no alcanza para dar por cumplido el de hoy.
        crearPublicacion(null, Instant.parse("2026-08-23T18:00:00Z"));
        assertThat(adapter.existeDeAutorEntre(autorId, desde, hasta)).isFalse();

        // De OTRO autor, dentro de la ventana: tampoco cuenta para este.
        crearPublicacionDe(autorSuelto(), Instant.parse("2026-08-24T15:00:00Z"));
        assertThat(adapter.existeDeAutorEntre(autorId, desde, hasta)).isFalse();
    }

    /**
     * Una publicacion moderada SIGUE contando: la persona publico, y ocultarla es un acto
     * posterior de otra persona. Es la diferencia deliberada contra {@code feed}, que si filtra
     * {@code oculta = false} — el javadoc de {@code PublicacionMuroFinder} lo explica.
     */
    @Test
    void existeDeAutorEntreCuentaTambienLaPublicacionOculta() {
        Instant desde = Instant.parse("2026-08-24T05:00:00Z");
        Instant hasta = Instant.parse("2026-08-25T05:00:00Z");
        Publicacion publicada = crearPublicacion(null, Instant.parse("2026-08-24T15:00:00Z"));
        publicada.ocultar(Instant.parse("2026-08-24T16:00:00Z"));
        adapter.save(publicada);

        assertThat(adapter.feed(null, 10, null)).isEmpty(); // el feed ya no la muestra
        assertThat(adapter.existeDeAutorEntre(autorId, desde, hasta)).isTrue(); // el habito si
    }

    /** Un autor sin ninguna publicacion: el caso que mantiene el habito pendiente. */
    @Test
    void existeDeAutorEntreEsFalseSinNingunaPublicacion() {
        assertThat(adapter.existeDeAutorEntre(autorId, Instant.parse("2026-08-24T05:00:00Z"),
                Instant.parse("2026-08-25T05:00:00Z"))).isFalse();
    }

    /** Otro usuario con fila propia en `usuarios` — la FK de `publicaciones_muro` la exige. */
    private UserId autorSuelto() {
        UserId otro = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture 2', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", otro.value())
                .setParameter("email", otro + "@renaser.test")
                .executeUpdate();
        return otro;
    }

    private void crearPublicacionDe(UserId otroAutor, Instant creadoEn) {
        adapter.save(Publicacion.publicar(PublicacionId.of(UUID.randomUUID()), otroAutor, "texto de prueba",
                List.of(new MediaPublicacion(MediaPublicacion.BUCKET_DEFAULT, "ruta/1.jpg", "image/jpeg", 0)), null,
                creadoEn));
    }
}
