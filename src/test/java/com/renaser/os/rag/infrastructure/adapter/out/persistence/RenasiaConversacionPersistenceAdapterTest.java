package com.renaser.os.rag.infrastructure.adapter.out.persistence;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.FuenteMensaje;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.rag.domain.model.conversacion.AgenteConversacional.COMPANION;
import static com.renaser.os.rag.domain.model.conversacion.AgenteConversacional.COURSE_TUTOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistencia del agregado `conversacion` de `rag`/Renasia contra Postgres real
 * (Testcontainers) — cubre las 3 tablas de este agregado, incluido el N:M de
 * `fuentes_mensaje_renasia`, que un test con mocks no detectaria si esta mal escrito
 * contra el motor real (mismo criterio que `ChatPersistenceAdapterTest`, E-31). Desde D-102
 * tambien la columna `agente` de V27 y el WHERE por agente de la paginacion.
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

    /** Un id cualquiera, distinto por mensaje: la identidad ya no la sortea el agregado. */
    private static MensajeRenasiaId nuevoId() {
        return MensajeRenasiaId.of(UUID.randomUUID());
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
                MensajeRenasia.escribirDeUsuario(nuevoId(), usuarioId, COMPANION, "que es Renasia?", Instant.now()));

        List<MensajeRenasia> pagina = loadMensajeRenasiaPort.pagina(usuarioId, COMPANION, null, 10);
        assertThat(pagina).hasSize(1);
        assertThat(pagina.get(0).id()).isEqualTo(guardado.id());
        assertThat(pagina.get(0).contenido()).isEqualTo("que es Renasia?");
        assertThat(pagina.get(0).agente()).isEqualTo(COMPANION);
        assertThat(pagina.get(0).fuentes()).isEmpty();
    }

    @Test
    void guardaYRecuperaUnMensajeDeAsistenteConSusFuentes() {
        saveConversacionRenasiaPort.save(ConversacionRenasia.iniciar(usuarioId, Instant.now()));

        MensajeRenasia guardado = saveMensajeRenasiaPort.save(
                MensajeRenasia.escribirDeAsistente(nuevoId(), usuarioId, COMPANION, "la respuesta con contexto",
                        List.of(FuenteMensaje.of("leccion-1")), Instant.now()));

        List<MensajeRenasia> pagina = loadMensajeRenasiaPort.pagina(usuarioId, COMPANION, null, 10);
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
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(nuevoId(), usuarioId, COMPANION, "primero", t1));
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(nuevoId(), usuarioId, COMPANION, "segundo", t2));
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(nuevoId(), usuarioId, COMPANION, "tercero", t3));

        List<MensajeRenasia> primeraPagina = loadMensajeRenasiaPort.pagina(usuarioId, COMPANION, null, 2);
        assertThat(primeraPagina).extracting(MensajeRenasia::contenido).containsExactly("tercero", "segundo");

        List<MensajeRenasia> siguientePagina = loadMensajeRenasiaPort.pagina(usuarioId, COMPANION,
                primeraPagina.get(primeraPagina.size() - 1).creadoEn(), 2);
        assertThat(siguientePagina).extracting(MensajeRenasia::contenido).containsExactly("primero");
    }

    /**
     * D-102: dos historiales sobre la misma conversacion 1:1. Lo que se hablo con Sparkie no
     * aparece al paginar el chat del acompanante ni al reves — en ninguna de las dos consultas
     * (con y sin cursor). Este test falla contra el codigo anterior a V27, que no tenia columna
     * por donde separar.
     */
    @Test
    @DisplayName("D-102: el historial de un agente nunca incluye mensajes del otro")
    void elHistorialDeUnAgenteNoIncluyeElDelOtro() {
        saveConversacionRenasiaPort.save(ConversacionRenasia.iniciar(usuarioId, Instant.now()));
        Instant t1 = Instant.now();
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(nuevoId(), usuarioId, COMPANION,
                "al acompanante", t1));
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(nuevoId(), usuarioId, COURSE_TUTOR,
                "al tutor", t1.plusSeconds(5)));
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeAsistente(nuevoId(), usuarioId, COURSE_TUTOR,
                "respuesta del tutor", List.of(FuenteMensaje.of("leccion-1")), t1.plusSeconds(10)));

        List<MensajeRenasia> delAcompanante = loadMensajeRenasiaPort.pagina(usuarioId, COMPANION, null, 10);
        List<MensajeRenasia> delTutor = loadMensajeRenasiaPort.pagina(usuarioId, COURSE_TUTOR, null, 10);
        List<MensajeRenasia> delTutorConCursor = loadMensajeRenasiaPort.pagina(usuarioId, COURSE_TUTOR,
                t1.plusSeconds(10), 10);

        assertThat(delAcompanante).extracting(MensajeRenasia::contenido).containsExactly("al acompanante");
        assertThat(delTutor).extracting(MensajeRenasia::contenido)
                .containsExactly("respuesta del tutor", "al tutor");
        assertThat(delTutor).extracting(MensajeRenasia::agente).containsOnly(COURSE_TUTOR);
        assertThat(delTutorConCursor).extracting(MensajeRenasia::contenido).containsExactly("al tutor");
    }
}
