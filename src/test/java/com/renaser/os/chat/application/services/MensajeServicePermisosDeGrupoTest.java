package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.SolicitarUrlSubidaMediaChatUseCase.SolicitarUrlSubidaMediaChatCommand;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El agujero que este trabajo cierra.
 *
 * <p>Antes, cualquier accion sobre el chat de un grupo se autorizaba mirando
 * {@code participantes_conversacion}. Esa tabla es una proyeccion y nadie la mantenia al rotar,
 * asi que un exmentor conservaba su fila — y con ella podia leer, escribir, marcar leido y pedir
 * URLs de subida en el chat de gente que ya no acompanaba.
 *
 * <p>Cada prueba de acá deja la proyeccion DICIENDO QUE SI y la pertenencia real diciendo que
 * no. Si alguna pasa, el agujero volvio.
 */
class MensajeServicePermisosDeGrupoTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T12:00:00Z");
    private static final UUID CELULA = UUID.randomUUID();
    private static final ConversacionId CONVERSACION_GRUPO = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId CONVERSACION_DIRECTA = ConversacionId.of(UUID.randomUUID());

    private static final UserId EXMENTOR = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR_ACTUAL = UserId.of(UUID.randomUUID());

    /** Proyección del chat: dice que sí para TODOS. Es la que estaba mal. */
    private final Set<UserId> proyeccion = new HashSet<>();
    /** Pertenencia real al grupo, la fuente de verdad. */
    private final Set<UserId> integrantesReales = new HashSet<>();
    private final List<Mensaje> guardados = new ArrayList<>();

    private MensajeService servicio;

    @BeforeEach
    void preparar() {
        proyeccion.clear();
        integrantesReales.clear();
        guardados.clear();
        // La proyeccion quedo vieja: sigue teniendo al exmentor.
        proyeccion.addAll(List.of(EXMENTOR, MENTOR_ACTUAL));
        integrantesReales.add(MENTOR_ACTUAL);

        LoadConversacionPort conversaciones = new LoadConversacionPort() {
            @Override
            public Optional<Conversacion> porId(ConversacionId id) {
                if (id.equals(CONVERSACION_GRUPO)) {
                    return Optional.of(Conversacion.crearCelula(CONVERSACION_GRUPO, CELULA, AHORA));
                }
                if (id.equals(CONVERSACION_DIRECTA)) {
                    return Optional.of(Conversacion.crearDirecta(CONVERSACION_DIRECTA, "a:b", AHORA));
                }
                return Optional.empty();
            }

            @Override
            public Optional<Conversacion> porClaveDirecta(String claveDirecta) {
                return Optional.empty();
            }

            @Override
            public Optional<Conversacion> porCelulaId(UUID celulaId) {
                return Optional.empty();
            }

            @Override
            public Optional<Conversacion> global() {
                return Optional.empty();
            }

            @Override
            public List<Conversacion> misConversaciones(UserId usuarioId) {
                return List.of();
            }
        };

        EsParticipantePort esParticipante = (conversacionId, usuarioId) -> proyeccion.contains(usuarioId);

        PertenenciaVigentePort pertenencia = new PertenenciaVigentePort() {
            @Override
            public boolean perteneceAlGrupo(UUID celulaId, UserId usuarioId) {
                return integrantesReales.contains(usuarioId);
            }

            @Override
            public List<UserId> integrantesDelGrupo(UUID celulaId) {
                return List.copyOf(integrantesReales);
            }
        };

        MarcarLeidoPort marcarLeido = (conversacionId, usuarioId, momento) -> { };
        SaveMensajePort guardar = mensaje -> {
            guardados.add(mensaje);
            return mensaje;
        };
        LoadMensajePort cargarMensajes = new LoadMensajePort() {
            @Override
            public Optional<Mensaje> porId(com.renaser.os.chat.domain.model.mensaje.MensajeId id) {
                return Optional.empty();
            }

            @Override
            public List<Mensaje> pagina(ConversacionId conversacionId, Instant cursor, int limite) {
                return List.of();
            }

            @Override
            public Map<ConversacionId, Mensaje> ultimosPorConversacion(List<ConversacionId> ids) {
                return Map.of();
            }

            @Override
            public Map<com.renaser.os.chat.domain.model.mensaje.MensajeId, Mensaje> porIds(
                    Collection<com.renaser.os.chat.domain.model.mensaje.MensajeId> ids) {
                return Map.of();
            }
        };
        PublicarMensajeFanoutPort fanout = mensaje -> { };
        UserSummaryFinder usuarios = new UserSummaryFinder() {
            @Override
            public Optional<UserSummary> findById(UserId id) {
                return Optional.of(new UserSummary(id, "Alguien", null, UserRole.MENTOR, UserStatus.ACTIVE));
            }

            @Override
            public Map<UserId, UserSummary> findByIds(Collection<UserId> ids) {
                return Map.of();
            }
        @Override
        public java.util.Optional<UserSummary> findByEmail(String email) {
            return java.util.Optional.empty();
        }

        };
        AlmacenamientoPort almacenamiento = new AlmacenamientoPort() {
            @Override
            public URI firmarSubida(String ruta, String tipoContenido, Duration validez) {
                return URI.create("https://ejemplo.test/" + ruta);
            }

            @Override
            public URI firmarLectura(String ruta, Duration validez) {
                return URI.create("https://ejemplo.test/" + ruta);
            }

            @Override
            public URI urlPublica(String ruta) {
                return URI.create("https://ejemplo.test/" + ruta);
            }

            @Override
            public void borrar(String ruta) {
            }
        };

        servicio = new MensajeService(conversaciones, esParticipante, pertenencia, marcarLeido, guardar,
                cargarMensajes, fanout, usuarios, almacenamiento, FixedClock.at(AHORA),
                UUID::randomUUID);
    }

    private EnviarMensajeCommand mensajeDe(UserId autor, ConversacionId conversacion) {
        return new EnviarMensajeCommand(autor, conversacion, TipoMensaje.TEXTO, "hola",
                null, null, null, null, null, null);
    }

    @Test
    @DisplayName("el exmentor NO puede escribir, aunque la proyeccion todavia lo tenga")
    void exmentorNoEscribe() {
        assertThatThrownBy(() -> servicio.enviar(mensajeDe(EXMENTOR, CONVERSACION_GRUPO)))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("asignacion cambio");
        assertThat(guardados).isEmpty();
    }

    @Test
    @DisplayName("el exmentor NO puede leer")
    void exmentorNoLee() {
        assertThatThrownBy(() -> servicio.listar(EXMENTOR, CONVERSACION_GRUPO, null, 30))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("el exmentor NO obtiene URL de subida: sin permiso no se firma nada")
    void exmentorNoObtieneUrl() {
        assertThatThrownBy(() -> servicio.solicitarUrl(
                new SolicitarUrlSubidaMediaChatCommand(EXMENTOR, CONVERSACION_GRUPO, "image/jpeg")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("el mentor actual si puede escribir y leer")
    void mentorActualSiPuede() {
        assertThatCode(() -> servicio.enviar(mensajeDe(MENTOR_ACTUAL, CONVERSACION_GRUPO)))
                .doesNotThrowAnyException();
        assertThatCode(() -> servicio.listar(MENTOR_ACTUAL, CONVERSACION_GRUPO, null, 30))
                .doesNotThrowAnyException();
        assertThat(guardados).hasSize(1);
    }

    @Test
    @DisplayName("el mentor entrante puede leer el historial aunque la proyeccion no lo tenga aun")
    void mentorEntranteLeeAntesDeQueLaProyeccionSeActualice() {
        UserId reciénLlegado = UserId.of(UUID.randomUUID());
        integrantesReales.add(reciénLlegado);
        // A proposito NO se lo agrega a la proyeccion: la reconciliacion es asincrona.
        assertThat(proyeccion).doesNotContain(reciénLlegado);

        assertThatCode(() -> servicio.listar(reciénLlegado, CONVERSACION_GRUPO, null, 30))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("los DIRECTOS conservan su politica: nadie pierde un DM porque alguien roto")
    void directosNoCambian() {
        // El exmentor sigue en la proyeccion del directo, y eso alcanza: un DM no depende de
        // ninguna celula.
        assertThatCode(() -> servicio.enviar(mensajeDe(EXMENTOR, CONVERSACION_DIRECTA)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un ajeno que nunca estuvo tampoco entra al directo")
    void ajenoNoEntraAlDirecto() {
        UserId ajeno = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> servicio.enviar(mensajeDe(ajeno, CONVERSACION_DIRECTA)))
                .isInstanceOf(NotAuthorizedException.class);
    }
}
