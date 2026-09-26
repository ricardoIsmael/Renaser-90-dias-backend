package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.ConversacionesDeUsuarioPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.ports.out.presencia.PresenciaPort;
import com.renaser.os.chat.application.ports.out.presencia.PublicarPresenciaFanoutPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que este servicio vino a reemplazar era un texto fijo: la app escribia "● En linea" debajo
 * del nombre de cualquier persona. Asi que la prueba que mas importa no es que el indicador se
 * encienda, sino que <b>no se encienda cuando no corresponde</b> — y que la pantalla siga
 * funcionando cuando el dato no se puede averiguar.
 */
class PresenciaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-17T12:00:00Z");
    private static final UUID CELULA = UUID.randomUUID();
    private static final ConversacionId GRUPO = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId DIRECTA = ConversacionId.of(UUID.randomUUID());

    private static final UserId YO = UserId.of(UUID.randomUUID());
    private static final UserId OTRO = UserId.of(UUID.randomUUID());
    private static final UserId EXMENTOR = UserId.of(UUID.randomUUID());

    /** La proyeccion `participantes_conversacion`, que se queda vieja. */
    private final Set<UserId> proyeccion = new HashSet<>();
    /** La pertenencia real al grupo. */
    private final Set<UserId> integrantesReales = new HashSet<>();
    /** Lo que Redis diria que esta conectado. */
    private final Set<UserId> conectados = new LinkedHashSet<>();
    /** Los avisos que salieron por el canal de la conversacion. */
    private final List<String> publicados = new ArrayList<>();

    private boolean redisCaido;
    private PresenciaService servicio;

    @BeforeEach
    void preparar() {
        proyeccion.clear();
        integrantesReales.clear();
        conectados.clear();
        publicados.clear();
        redisCaido = false;

        proyeccion.addAll(List.of(YO, OTRO, EXMENTOR));
        integrantesReales.addAll(List.of(YO, OTRO));

        PresenciaPort presencia = new PresenciaPort() {
            @Override
            public void marcarEnLinea(UserId usuarioId, Duration vigencia) {
                siRedisCaidoFallar();
                conectados.add(usuarioId);
            }

            @Override
            public void marcarFueraDeLinea(UserId usuarioId) {
                siRedisCaidoFallar();
                conectados.remove(usuarioId);
            }

            @Override
            public Set<UserId> enLineaDe(Collection<UserId> candidatos) {
                siRedisCaidoFallar();
                Set<UserId> resultado = new LinkedHashSet<>(candidatos);
                resultado.retainAll(conectados);
                return resultado;
            }
        };

        PublicarPresenciaFanoutPort fanout = (usuarioId, enLinea, destinos) ->
                destinos.forEach(destino -> publicados.add(destino.value() + ":" + usuarioId.value() + ":" + enLinea));

        ConversacionesDeUsuarioPort conversacionesDe = usuarioId -> List.of(GRUPO, DIRECTA);

        ListarUsuariosDeConversacionPort roster = new ListarUsuariosDeConversacionPort() {
            @Override
            public List<UserId> usuariosDe(ConversacionId conversacionId) {
                return List.of(YO, OTRO);
            }

            @Override
            public Map<ConversacionId, UserId> otroParticipanteDeDirectas(List<ConversacionId> ids, UserId actorId) {
                return Map.of();
            }
        };

        LoadConversacionPort conversaciones = new LoadConversacionPort() {
            @Override
            public Optional<Conversacion> porId(ConversacionId id) {
                if (id.equals(GRUPO)) {
                    return Optional.of(Conversacion.crearCelula(GRUPO, CELULA, AHORA));
                }
                if (id.equals(DIRECTA)) {
                    return Optional.of(Conversacion.crearDirecta(DIRECTA, "a:b", AHORA));
                }
                return Optional.empty();
            }

            @Override
            public Optional<Conversacion> porClaveDirecta(String claveDirecta) {
                return Optional.empty();
            }

            @Override
            public java.util.Set<String> clavesDirectasExistentes(java.util.Collection<String> claves) {
                return java.util.Set.of();
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
            public List<Conversacion> deSoporte() {
                return List.of();
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

        /* Nadie de este test es staff administrativo y aca no hay ninguna conversacion de
           SOPORTE: la rama nueva del guard no se activa y el finder solo esta para armar el
           servicio. La revocacion del soporte la fija RevocacionDeSoportePorBajaDeRolTest. */
        UserSummaryFinder usuarios = new UserSummaryFinder() {
            @Override
            public Optional<UserSummary> findById(UserId id) {
                return Optional.of(new UserSummary(id, "Alguien", null, UserRole.TRAINEE, UserStatus.ACTIVE));
            }

            @Override
            public Map<UserId, UserSummary> findByIds(Collection<UserId> ids) {
                return Map.of();
            }

            @Override
            public List<UserSummary> aprendicesActivos() {
                return List.of();
            }

            @Override
            public Optional<UserSummary> findByEmail(String email) {
                return Optional.empty();
            }
        };

        servicio = new PresenciaService(presencia, fanout, conversacionesDe, roster, conversaciones,
                esParticipante, pertenencia, usuarios);
    }

    private void siRedisCaidoFallar() {
        if (redisCaido) {
            throw new IllegalStateException("Redis no responde");
        }
    }

    @Test
    @DisplayName("sin nadie conectado no devuelve a nadie: es lo contrario del 'En linea' fijo de antes")
    void nadieConectado() {
        assertThat(servicio.enLineaEn(DIRECTA, YO)).isEmpty();
    }

    @Test
    @DisplayName("devuelve al otro cuando el otro tiene un socket abierto")
    void otroConectado() {
        servicio.seConecto(OTRO);

        assertThat(servicio.enLineaEn(DIRECTA, YO)).containsExactly(OTRO);
    }

    @Test
    @DisplayName("no se devuelve a uno mismo: el indicador habla de la otra persona")
    void nuncaYoMismo() {
        servicio.seConecto(YO);
        servicio.seConecto(OTRO);

        assertThat(servicio.enLineaEn(DIRECTA, YO)).containsExactly(OTRO);
    }

    @Test
    @DisplayName("al desconectarse deja de figurar")
    void seApaga() {
        servicio.seConecto(OTRO);
        servicio.seDesconecto(OTRO);

        assertThat(servicio.enLineaEn(DIRECTA, YO)).isEmpty();
    }

    @Test
    @DisplayName("un exmentor con la proyeccion vieja NO puede ver quien esta en linea en el grupo")
    void exmentorNoVeElGrupo() {
        servicio.seConecto(OTRO);

        assertThatThrownBy(() -> servicio.enLineaEn(GRUPO, EXMENTOR))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("quien no participa de una directa tampoco la puede mirar")
    void ajenoNoVeLaDirecta() {
        proyeccion.remove(EXMENTOR);

        assertThatThrownBy(() -> servicio.enLineaEn(DIRECTA, EXMENTOR))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("si Redis no responde se contesta 'nadie', nunca se inventa una presencia")
    void redisCaidoNoInventa() {
        servicio.seConecto(OTRO);
        redisCaido = true;

        assertThat(servicio.enLineaEn(DIRECTA, YO)).isEmpty();
    }

    @Test
    @DisplayName("si Redis no responde al conectarse, el chat no se rompe y no se avisa de mas")
    void conectarseConRedisCaidoNoLanza() {
        redisCaido = true;

        servicio.seConecto(OTRO);

        assertThat(publicados).isEmpty();
    }

    @Test
    @DisplayName("el cambio se avisa a TODAS las conversaciones donde esa persona aparece")
    void avisaATodasSusConversaciones() {
        servicio.seConecto(OTRO);

        assertThat(publicados).containsExactly(
                GRUPO.value() + ":" + OTRO.value() + ":true",
                DIRECTA.value() + ":" + OTRO.value() + ":true");
    }

    /**
     * Regresion de la auditoria del 2026-09-18. <b>Falla contra el codigo viejo.</b>
     *
     * <p>Leer quien esta en linea en un grupo ya revalidaba la pertenencia vigente
     * ({@link #exmentorNoVeElGrupo}), pero ANUNCIARLO no: el reparto salia a
     * {@code conversacionesDe(usuarioId)} tal cual, o sea a la proyeccion. El estado de conexion de
     * una persona se publicaba al canal de todo grupo donde conservara fila, incluidos los que ya
     * no integra — y para un grupo cuyo periodo termino eso no se corrige nunca, porque el fin de
     * periodo no publica {@code ComposicionDeCelulaCambiadaEvent}.
     *
     * <p>La conversacion DIRECTA si tiene que seguir avisando: ahi la proyeccion <i>es</i> la
     * fuente de verdad, y recortarla de mas seria romper el producto para arreglar otra cosa.
     */
    @Test
    @DisplayName("el aviso de presencia no sale al grupo que la persona ya no integra")
    void noAvisaAlGrupoDelQueYaSalio() {
        proyeccion.add(EXMENTOR);          // conserva la fila vieja
        integrantesReales.remove(EXMENTOR); // pero ya no es integrante vigente

        servicio.seConecto(EXMENTOR);

        assertThat(publicados).containsExactly(DIRECTA.value() + ":" + EXMENTOR.value() + ":true");
    }

    @Test
    @DisplayName("renovar no genera avisos: cada 45 s en todos los sockets seria ruido puro")
    void renovarNoAvisa() {
        servicio.seConecto(OTRO);
        publicados.clear();

        servicio.sigueConectado(OTRO);

        assertThat(publicados).isEmpty();
        assertThat(servicio.enLineaEn(DIRECTA, YO)).containsExactly(OTRO);
    }
}
