package com.renaser.os.rag.infrastructure.adapter.out.persistence;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.FuenteMensaje;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistencia del agregado `conversacion` de `rag`/Renasia contra Postgres real
 * (Testcontainers) — cubre las 3 tablas de este agregado, incluido el N:M de
 * `fuentes_mensaje_renasia`, que un test con mocks no detectaria si esta mal escrito
 * contra el motor real (mismo criterio que `ChatPersistenceAdapterTest`, E-31).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RenasiaConversacionPersistenceAdapterTest {

    @Autowired
    private LoadConversacionRenasiaPort loadConversacionRenasiaPort;
    @Autowired
    private SaveConversacionRenasiaPort saveConversacionRenasiaPort;
    @Autowired
    private SaveMensajeRenasiaPort saveMensajeRenasiaPort;
    @Autowired
    private LoadMensajeRenasiaPort loadMensajeRenasiaPort;
    @Autowired
    private EntityManager entityManager;

    private UserId usuarioId;

    @BeforeEach
    void seedFixtures() {
        usuarioId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", usuarioId.value())
                .setParameter("email", usuarioId + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cursos (id, slug, titulo)
                        VALUES ('curso-fixture', 'curso-fixture', 'Curso fixture')
                        """).executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.lecciones (id, curso_id, titulo)
                        VALUES ('leccion-1', 'curso-fixture', 'Leccion fixture')
                        """).executeUpdate();
    }

    @Test
    void unUsuarioTieneComoMaximoUnaConversacion() {
        assertThat(loadConversacionRenasiaPort.porUsuarioId(usuarioId)).isEmpty();

        ConversacionRenasia guardada = saveConversacionRenasiaPort.save(
                ConversacionRenasia.iniciar(usuarioId, Instant.now()));

        Optional<ConversacionRenasia> recuperada = loadConversacionRenasiaPort.porUsuarioId(usuarioId);
        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().usuarioId()).isEqualTo(guardada.usuarioId());
    }

    @Test
    void guardaYRecuperaUnMensajeDeUsuarioSinFuentes() {
        saveConversacionRenasiaPort.save(ConversacionRenasia.iniciar(usuarioId, Instant.now()));

        MensajeRenasia guardado = saveMensajeRenasiaPort.save(
                MensajeRenasia.escribirDeUsuario(usuarioId, "que es Renasia?", Instant.now()));

        List<MensajeRenasia> pagina = loadMensajeRenasiaPort.pagina(usuarioId, null, 10);
        assertThat(pagina).hasSize(1);
        assertThat(pagina.get(0).id()).isEqualTo(guardado.id());
        assertThat(pagina.get(0).contenido()).isEqualTo("que es Renasia?");
        assertThat(pagina.get(0).fuentes()).isEmpty();
    }

    @Test
    void guardaYRecuperaUnMensajeDeAsistenteConSusFuentes() {
        saveConversacionRenasiaPort.save(ConversacionRenasia.iniciar(usuarioId, Instant.now()));

        MensajeRenasia guardado = saveMensajeRenasiaPort.save(
                MensajeRenasia.escribirDeAsistente(usuarioId, "la respuesta con contexto",
                        List.of(FuenteMensaje.of("leccion-1")), Instant.now()));

        List<MensajeRenasia> pagina = loadMensajeRenasiaPort.pagina(usuarioId, null, 10);
        assertThat(pagina).hasSize(1);
        assertThat(pagina.get(0).id()).isEqualTo(guardado.id());
        assertThat(pagina.get(0).fuentes()).extracting(FuenteMensaje::leccionId).containsExactly("leccion-1");
    }

    @Test
    void laPaginacionPorCursorDevuelveLosMasRecientesPrimero() {
        saveConversacionRenasiaPort.save(ConversacionRenasia.iniciar(usuarioId, Instant.now()));
        Instant t1 = Instant.now();
        Instant t2 = t1.plusSeconds(10);
        Instant t3 = t2.plusSeconds(10);
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(usuarioId, "primero", t1));
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(usuarioId, "segundo", t2));
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(usuarioId, "tercero", t3));

        List<MensajeRenasia> primeraPagina = loadMensajeRenasiaPort.pagina(usuarioId, null, 2);
        assertThat(primeraPagina).extracting(MensajeRenasia::contenido).containsExactly("tercero", "segundo");

        List<MensajeRenasia> siguientePagina = loadMensajeRenasiaPort.pagina(usuarioId,
                primeraPagina.get(primeraPagina.size() - 1).creadoEn(), 2);
        assertThat(siguientePagina).extracting(MensajeRenasia::contenido).containsExactly("primero");
    }
}
