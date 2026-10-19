package com.renaser.os.community.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarPublicacionUseCase.PublicacionParaLeer;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort.PerfilUsuario;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.infrastructure.storage.NoOpAlmacenamientoAdapter;
import com.renaser.os.users.api.CuentasCerradasFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * «Ver publicación» en las fichas del líder, del mentor y de administración (spec 005, R-6): quién puede leer UNA
 * publicación del Muro por su id. Autorización negativa: un aprendiz ajeno, un mentor que no acompaña al autor y
 * cualquier cuenta suspendida reciben 403.
 */
@ExtendWith(MockitoExtension.class)
class ConsultarPublicacionServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-19T03:00:00Z"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final UserId LUIS = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
    private static final UserId LIDER = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UserId ADMIN = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000d1"));
    private static final UserId ALQUIMISTA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000d2"));
    private static final UserId MENTORA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId OTRO_MENTOR = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final CelulaId FENIX = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-00000000f001"));

    @Mock
    private LoadPublicacionPort publicaciones;
    @Mock
    private ConsultarPerfilUsuarioPort perfiles;
    @Mock
    private UserSummaryFinder usuarios;
    @Mock
    private LoadAsignacionesPort asignaciones;
    @Mock
    private AcompanamientoFinder acompanamiento;
    @Mock
    private CuentasCerradasFinder cuentasCerradas;

    private ConsultarPublicacionService servicio;
    private Publicacion deAna;

    @BeforeEach
    void preparar() {
        servicio = new ConsultarPublicacionService(publicaciones, perfiles, new NoOpAlmacenamientoAdapter(), usuarios,
                asignaciones, acompanamiento, cuentasCerradas, CLOCK);
        deAna = Publicacion.publicar(PublicacionId.of(UUID.randomUUID()), ANA, "Hoy completé mi ritual",
                List.of(new MediaPublicacion(MediaPublicacion.BUCKET_DEFAULT, "muro/fotos/" + ANA + "/a.jpg",
                        "image/jpeg", 1), new MediaPublicacion(MediaPublicacion.BUCKET_DEFAULT,
                        "muro/fotos/" + ANA + "/b.jpg", "image/png", 0)), null, Instant.parse("2026-10-14T16:00:00Z"));
        lenient().when(publicaciones.porId(deAna.id())).thenReturn(Optional.of(deAna));
        lenient().when(perfiles.porId(ANA)).thenReturn(Optional.of(new PerfilUsuario(ANA, "Ana Pérez", "https://avatar")));
        lenient().when(cuentasCerradas.cerradasEntre(any())).thenReturn(Set.of());
    }

    private void actor(UserId id, UserRole rol, UserStatus estado) {
        lenient().when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Actor", null, rol, estado)));
    }

    private void mentorAcompanaAAna(UserId mentor) {
        lenient().when(asignaciones.porUsuario(ANA)).thenReturn(List.of(AsignacionCelula.abrir(
                AsignacionId.of(UUID.randomUUID()), FENIX, ANA, FuncionAcompanamiento.APRENDIZ,
                Instant.parse("2026-09-01T05:00:00Z"), MotivoAsignacion.ADMINISTRATIVO, null, "k-1")));
        lenient().when(acompanamiento.acompanaVigente(mentor, FENIX.value(), CLOCK.now())).thenReturn(true);
    }

    @Test
    @DisplayName("quien la publico la lee")
    void elAutorLaLee() {
        actor(ANA, UserRole.TRAINEE, UserStatus.ACTIVE);

        PublicacionParaLeer leida = servicio.publicacionDe(ANA, deAna.id());

        assertThat(leida.id()).isEqualTo(deAna.id().value());
        assertThat(leida.texto()).isEqualTo("Hoy completé mi ritual");
    }

    @Test
    @DisplayName("el lider, el administrador y el alquimista la leen: trae autor, fecha y la media firmada en su orden")
    void liderazgoLaLee() {
        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            actor(actor, actor.equals(LIDER) ? UserRole.MENTOR_LEAD : actor.equals(ADMIN) ? UserRole.ADMIN
                    : UserRole.ALCHEMIST, UserStatus.ACTIVE);

            PublicacionParaLeer leida = servicio.publicacionDe(actor, deAna.id());

            assertThat(leida.autorId()).isEqualTo(ANA);
            assertThat(leida.autorNombre()).isEqualTo("Ana Pérez");
            assertThat(leida.autorAvatarUrl()).isEqualTo("https://avatar");
            assertThat(leida.creadaEn()).isEqualTo(Instant.parse("2026-10-14T16:00:00Z"));
            assertThat(leida.media()).extracting(m -> m.mime()).containsExactly("image/png", "image/jpeg");
            assertThat(leida.media()).allSatisfy(m -> assertThat(m.url()).isNotNull());
        }
    }

    @Test
    @DisplayName("el mentor (o la guia) que acompana hoy al grupo del autor la lee")
    void mentorQueAcompanaLaLee() {
        actor(MENTORA, UserRole.MENTOR, UserStatus.ACTIVE);
        mentorAcompanaAAna(MENTORA);

        assertThat(servicio.publicacionDe(MENTORA, deAna.id()).id()).isEqualTo(deAna.id().value());

        UserId guia = UserId.of(UUID.randomUUID());
        actor(guia, UserRole.TRAINEE_GUIDE, UserStatus.ACTIVE);
        mentorAcompanaAAna(guia);
        assertThat(servicio.publicacionDe(guia, deAna.id()).id()).isEqualTo(deAna.id().value());
    }

    @Test
    @DisplayName("autorizacion negativa: un mentor que no acompana al autor recibe 403")
    void mentorQueNoAcompanaNoLaLee() {
        actor(OTRO_MENTOR, UserRole.MENTOR, UserStatus.ACTIVE);
        mentorAcompanaAAna(MENTORA);

        assertThatThrownBy(() -> servicio.publicacionDe(OTRO_MENTOR, deAna.id()))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("autorizacion negativa: un aprendiz de otro grupo recibe 403 por este camino, aunque el feed lo muestre")
    void aprendizAjenoNoLaLee() {
        actor(LUIS, UserRole.TRAINEE, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.publicacionDe(LUIS, deAna.id())).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("autorizacion negativa: una cuenta SUSPENDIDA recibe 403, sea cual sea su rol")
    void cuentaSuspendidaNoLee() {
        for (UserRole rol : List.of(UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.MENTOR,
                UserRole.TRAINEE)) {
            actor(LIDER, rol, UserStatus.SUSPENDED);

            assertThatThrownBy(() -> servicio.publicacionDe(LIDER, deAna.id()))
                    .as(rol.name()).isInstanceOf(NotAuthorizedException.class);
        }
        actor(ANA, UserRole.TRAINEE, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> servicio.publicacionDe(ANA, deAna.id())).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("una publicacion oculta o borrada, o que no existe, es 404 para todos, tambien para el lider")
    void ocultaOInexistente() {
        actor(LIDER, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        deAna.ocultar(Instant.parse("2026-10-15T10:00:00Z"));

        assertThatThrownBy(() -> servicio.publicacionDe(LIDER, deAna.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.publicacionDe(LIDER, PublicacionId.of(UUID.randomUUID())))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("la publicacion de una cuenta cerrada esperando su borrado es 404: nadie la ve")
    void cuentaCerrada() {
        actor(LIDER, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        when(cuentasCerradas.cerradasEntre(any())).thenReturn(Set.of(ANA));

        assertThatThrownBy(() -> servicio.publicacionDe(LIDER, deAna.id())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("un actor que no existe es 404, no una ficha vacia")
    void actorInexistente() {
        UserId fantasma = UserId.of(UUID.randomUUID());
        lenient().when(usuarios.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.publicacionDe(fantasma, deAna.id())).isInstanceOf(NoSuchElementException.class);
    }
}
