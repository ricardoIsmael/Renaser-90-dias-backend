package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
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
 * Cubre {@link ReaccionMuroPersistenceAdapter#listarDe} contra un Postgres real (CLAUDE.MD
 * sec. 0.2: todo adaptador de persistencia nuevo/tocado necesita su prueba de integracion,
 * no solo la unitaria con mocks de {@code PublicacionMuroServiceTest}). El resto de metodos
 * del adaptador ({@code deUsuario}/{@code contarPorTipo}/{@code upsert}/{@code eliminar}/...)
 * ya existia sin cobertura de integracion antes de este cambio; ampliarla entera queda fuera
 * de alcance de esta tarea — ver el riesgo documentado en
 * {@code docs/informes/muro-reacciones.md}.
 *
 * <p>Las reacciones se insertan con SQL nativo (no con {@code adapter.upsert}) porque
 * {@code upsert} fija {@code creado_en = now()} sin dejar elegir el instante — hace falta
 * poder separar dos filas en el tiempo para probar el orden "mas reciente primero".
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ReaccionMuroPersistenceAdapterTest {

    @Autowired
    private ReaccionMuroPersistenceAdapter adapter;

    @Autowired
    private PublicacionPersistenceAdapter publicacionAdapter;

    @Autowired
    private EntityManager entityManager;

    private UserId autorId;

    @BeforeEach
    void seedAutor() {
        autorId = crearUsuario();
    }

    private UserId crearUsuario() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .executeUpdate();
        return id;
    }

    private PublicacionId crearPublicacion() {
        Publicacion publicacion = Publicacion.publicar(PublicacionId.of(UUID.randomUUID()), autorId, "texto",
                List.of(new MediaPublicacion(MediaPublicacion.BUCKET_DEFAULT, "ruta/1.jpg", "image/jpeg", 0)),
                null, Instant.parse("2026-08-20T10:00:00Z"));
        return publicacionAdapter.save(publicacion).id();
    }

    private void insertarReaccion(PublicacionId publicacionId, UserId usuarioId, TipoReaccion tipo,
                                   Instant creadoEn) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.reacciones_muro (publicacion_id, usuario_id, tipo, creado_en)
                        VALUES (:pub, :usr, CAST(:tipo AS renaser.tipo_reaccion), :creado)
                        """)
                .setParameter("pub", publicacionId.value())
                .setParameter("usr", usuarioId.value())
                .setParameter("tipo", tipo.name())
                .setParameter("creado", creadoEn)
                .executeUpdate();
    }

    @Test
    void listarDeDevuelveTodasLasReaccionesMasRecientePrimero() {
        PublicacionId publicacionId = crearPublicacion();
        UserId reactor1 = crearUsuario();
        UserId reactor2 = crearUsuario();
        insertarReaccion(publicacionId, reactor1, TipoReaccion.ME_GUSTA, Instant.parse("2026-08-20T10:00:00Z"));
        insertarReaccion(publicacionId, reactor2, TipoReaccion.NO_ME_GUSTA, Instant.parse("2026-08-20T11:00:00Z"));

        List<ReaccionMuro> reacciones = adapter.listarDe(publicacionId);

        assertThat(reacciones).hasSize(2);
        // Mas reciente primero: reactor2 (11:00) antes que reactor1 (10:00).
        assertThat(reacciones.get(0).usuarioId()).isEqualTo(reactor2);
        assertThat(reacciones.get(0).tipo()).isEqualTo(TipoReaccion.NO_ME_GUSTA);
        assertThat(reacciones.get(1).usuarioId()).isEqualTo(reactor1);
        assertThat(reacciones.get(1).tipo()).isEqualTo(TipoReaccion.ME_GUSTA);
        assertThat(reacciones).allMatch(r -> r.publicacionId().equals(publicacionId));
    }

    @Test
    void listarDeUnaPublicacionSinReaccionesEsVacia() {
        PublicacionId publicacionId = crearPublicacion();

        assertThat(adapter.listarDe(publicacionId)).isEmpty();
    }

    @Test
    void listarDeNoTraeReaccionesDeOtraPublicacion() {
        PublicacionId publicacionA = crearPublicacion();
        PublicacionId publicacionB = crearPublicacion();
        UserId reactor = crearUsuario();
        insertarReaccion(publicacionA, reactor, TipoReaccion.ME_GUSTA, Instant.parse("2026-08-20T10:00:00Z"));

        assertThat(adapter.listarDe(publicacionB)).isEmpty();
        assertThat(adapter.listarDe(publicacionA)).hasSize(1);
    }
}
