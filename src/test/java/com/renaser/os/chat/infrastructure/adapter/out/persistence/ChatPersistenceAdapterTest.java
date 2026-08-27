package com.renaser.os.chat.infrastructure.adapter.out.persistence;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia de `chat` contra Postgres real (Testcontainers) — los tests con mocks
 * (`ConversacionServiceTest`/`MensajeServiceTest`) no detectarian un CHECK/UNIQUE
 * violado, ni si una consulta en lote (conteo de no-leidos, ultimo mensaje por
 * conversacion) esta bien escrita contra el motor real. Mismo criterio que
 * `PublicacionPersistenceAdapterTest` de `community` (E-31).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ChatPersistenceAdapterTest {

    @Autowired
    private LoadConversacionPort loadConversacionPort;
    @Autowired
    private SaveConversacionPort saveConversacionPort;
    @Autowired
    private AgregarParticipantePort agregarParticipantePort;
    @Autowired
    private EsParticipantePort esParticipantePort;
    @Autowired
    private MarcarLeidoPort marcarLeidoPort;
    @Autowired
    private ContarNoLeidosPort contarNoLeidosPort;
    @Autowired
    private ListarUsuariosDeConversacionPort listarUsuariosDeConversacionPort;
    @Autowired
    private SaveMensajePort saveMensajePort;
    @Autowired
    private LoadMensajePort loadMensajePort;
    @Autowired
    private EntityManager entityManager;

    private UserId usuarioA;
    private UserId usuarioB;

    @BeforeEach
    void seedUsuarios() {
        usuarioA = UserId.of(UUID.randomUUID());
        usuarioB = UserId.of(UUID.randomUUID());
        for (UserId usuario : List.of(usuarioA, usuarioB)) {
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                            VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                            """)
                    .setParameter("id", usuario.value())
                    .setParameter("email", usuario + "@renaser.test")
                    .executeUpdate();
        }
    }

    @Test
    void guardaYRecuperaUnaConversacionDirectaConSusParticipantes() {
        String clave = Conversacion.claveDirectaDe(usuarioA, usuarioB);
        Conversacion guardada = saveConversacionPort.save(Conversacion.crearDirecta(clave, Instant.now()));
        agregarParticipantePort.agregar(Participante.unirse(guardada.id(), usuarioA, Instant.now()));
        agregarParticipantePort.agregar(Participante.unirse(guardada.id(), usuarioB, Instant.now()));

        Optional<Conversacion> recuperada = loadConversacionPort.porClaveDirecta(clave);

        assertThat(recuperada).isPresent();
        assertThat(esParticipantePort.esParticipante(guardada.id(), usuarioA)).isTrue();
        assertThat(esParticipantePort.esParticipante(guardada.id(), usuarioB)).isTrue();
        assertThat(loadConversacionPort.misConversaciones(usuarioA)).extracting(Conversacion::id)
                .containsExactly(guardada.id());
    }

    @Test
    void soloPuedeExistirUnaConversacionGlobal() {
        saveConversacionPort.save(Conversacion.crearGlobal(Instant.now()));

        assertThatThrownBy(() -> saveConversacionPort.save(Conversacion.crearGlobal(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void agregarEsIdempotenteYNoPisaElUltimoLeidoYaRegistrado() {
        Conversacion global = saveConversacionPort.save(Conversacion.crearGlobal(Instant.now()));
        Instant primeraVez = Instant.parse("2026-08-20T10:00:00Z");
        Instant delMensaje = Instant.parse("2026-08-20T12:00:00Z");
        agregarParticipantePort.agregar(Participante.unirse(global.id(), usuarioA, primeraVez));
        saveMensajePort.save(Mensaje.escribir(global.id(), usuarioB, TipoMensaje.TEXTO, "hola", null, null, null,
                null, null, null, delMensaje));

        // Un segundo "unirse" (ej. reintento del listener), MUCHO despues del mensaje, no
        // debe pisar el ultimo_leido_en original — si lo hiciera, el mensaje de arriba
        // pasaria a contar como leido y el assert de abajo fallaria.
        agregarParticipantePort.agregar(Participante.unirse(global.id(), usuarioA, delMensaje.plusSeconds(3600)));

        Map<ConversacionId, Long> conteo = contarNoLeidosPort.contarNoLeidos(usuarioA, List.of(global.id()));
        assertThat(conteo).containsEntry(global.id(), 1L);
    }

    @Test
    void contarNoLeidosEnLoteContraPostgresReal() {
        Conversacion c1 = saveConversacionPort.save(
                Conversacion.crearDirecta(Conversacion.claveDirectaDe(usuarioA, usuarioB), Instant.now()));
        agregarParticipantePort.agregar(Participante.unirse(c1.id(), usuarioA, Instant.parse("2026-08-20T10:00:00Z")));
        agregarParticipantePort.agregar(Participante.unirse(c1.id(), usuarioB, Instant.parse("2026-08-20T10:00:00Z")));

        saveMensajePort.save(Mensaje.escribir(c1.id(), usuarioB, TipoMensaje.TEXTO, "hola", null, null, null, null,
                null, null, Instant.parse("2026-08-21T10:00:00Z")));
        saveMensajePort.save(Mensaje.escribir(c1.id(), usuarioB, TipoMensaje.TEXTO, "como va", null, null, null,
                null, null, null, Instant.parse("2026-08-21T11:00:00Z")));

        Map<ConversacionId, Long> conteo = contarNoLeidosPort.contarNoLeidos(usuarioA, List.of(c1.id()));

        assertThat(conteo).containsEntry(c1.id(), 2L);
    }

    @Test
    void marcarLeidoBajaElConteoDeNoLeidos() {
        Conversacion c1 = saveConversacionPort.save(
                Conversacion.crearDirecta(Conversacion.claveDirectaDe(usuarioA, usuarioB), Instant.now()));
        agregarParticipantePort.agregar(Participante.unirse(c1.id(), usuarioA, Instant.parse("2026-08-20T10:00:00Z")));
        agregarParticipantePort.agregar(Participante.unirse(c1.id(), usuarioB, Instant.parse("2026-08-20T10:00:00Z")));
        Instant delMensaje = Instant.parse("2026-08-21T10:00:00Z");
        saveMensajePort.save(Mensaje.escribir(c1.id(), usuarioB, TipoMensaje.TEXTO, "hola", null, null, null, null,
                null, null, delMensaje));

        marcarLeidoPort.marcarLeido(c1.id(), usuarioA, delMensaje.plusSeconds(1));

        Map<ConversacionId, Long> conteo = contarNoLeidosPort.contarNoLeidos(usuarioA, List.of(c1.id()));
        assertThat(conteo.getOrDefault(c1.id(), 0L)).isZero();
    }

    @Test
    void ultimosPorConversacionEnLoteContraPostgresReal() {
        Conversacion c1 = saveConversacionPort.save(
                Conversacion.crearDirecta(Conversacion.claveDirectaDe(usuarioA, usuarioB), Instant.now()));
        Conversacion c2 = saveConversacionPort.save(Conversacion.crearGlobal(Instant.now()));

        saveMensajePort.save(Mensaje.escribir(c1.id(), usuarioA, TipoMensaje.TEXTO, "primero", null, null, null,
                null, null, null, Instant.parse("2026-08-21T10:00:00Z")));
        Mensaje ultimoC1 = saveMensajePort.save(Mensaje.escribir(c1.id(), usuarioB, TipoMensaje.TEXTO, "ultimo",
                null, null, null, null, null, null, Instant.parse("2026-08-21T11:00:00Z")));
        Mensaje ultimoC2 = saveMensajePort.save(Mensaje.escribir(c2.id(), usuarioA, TipoMensaje.TEXTO, "en global",
                null, null, null, null, null, null, Instant.parse("2026-08-21T09:00:00Z")));

        Map<ConversacionId, Mensaje> ultimos = loadMensajePort.ultimosPorConversacion(List.of(c1.id(), c2.id()));

        assertThat(ultimos.get(c1.id()).id()).isEqualTo(ultimoC1.id());
        assertThat(ultimos.get(c2.id()).id()).isEqualTo(ultimoC2.id());
    }

    @Test
    void paginacionKeysetDeMensajesNuncaUsaOffset() {
        Conversacion c1 = saveConversacionPort.save(Conversacion.crearGlobal(Instant.now()));
        for (int i = 0; i < 5; i++) {
            saveMensajePort.save(Mensaje.escribir(c1.id(), usuarioA, TipoMensaje.TEXTO, "mensaje " + i, null, null,
                    null, null, null, null, Instant.parse("2026-08-21T10:0" + i + ":00Z")));
        }

        List<Mensaje> primeraPagina = loadMensajePort.pagina(c1.id(), null, 2);
        assertThat(primeraPagina).hasSize(2);
        assertThat(primeraPagina.get(0).texto()).isEqualTo("mensaje 4");

        List<Mensaje> segundaPagina = loadMensajePort.pagina(c1.id(), primeraPagina.get(1).creadoEn(), 2);
        assertThat(segundaPagina).hasSize(2);
        assertThat(segundaPagina.get(0).texto()).isEqualTo("mensaje 2");
    }

    @Test
    void usuariosDeDevuelveTodosLosParticipantesDeUnaConversacion() {
        Conversacion global = saveConversacionPort.save(Conversacion.crearGlobal(Instant.now()));
        agregarParticipantePort.agregar(Participante.unirse(global.id(), usuarioA, Instant.now()));
        agregarParticipantePort.agregar(Participante.unirse(global.id(), usuarioB, Instant.now()));

        List<UserId> usuarios = listarUsuariosDeConversacionPort.usuariosDe(global.id());

        assertThat(usuarios).containsExactlyInAnyOrder(usuarioA, usuarioB);
    }

    @Test
    void porIdsResuelveVariosMensajesEnUnaSolaConsulta() {
        Conversacion global = saveConversacionPort.save(Conversacion.crearGlobal(Instant.now()));
        Mensaje m1 = saveMensajePort.save(Mensaje.escribir(global.id(), usuarioA, TipoMensaje.TEXTO, "uno", null,
                null, null, null, null, null, Instant.parse("2026-08-21T10:00:00Z")));
        Mensaje m2 = saveMensajePort.save(Mensaje.escribir(global.id(), usuarioB, TipoMensaje.TEXTO, "dos", null,
                null, null, null, null, null, Instant.parse("2026-08-21T10:01:00Z")));

        Map<MensajeId, Mensaje> resueltos = loadMensajePort.porIds(List.of(m1.id(), m2.id()));

        assertThat(resueltos).containsOnlyKeys(m1.id(), m2.id());
        assertThat(resueltos.get(m1.id()).texto()).isEqualTo("uno");
        assertThat(resueltos.get(m2.id()).texto()).isEqualTo("dos");
    }
}
