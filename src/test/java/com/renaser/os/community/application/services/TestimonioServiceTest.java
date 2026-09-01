package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase.CrearTestimonioCommand;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase.PromoverPublicacionCommand;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.testimonio.LoadTestimonioPort;
import com.renaser.os.community.application.ports.out.testimonio.SaveTestimonioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort.PerfilUsuario;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoPublicacion;
import com.renaser.os.community.domain.model.testimonio.Testimonio;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.infrastructure.storage.NoOpAlmacenamientoAdapter;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestimonioServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, la factoria del agregado ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadTestimonioPort loadTestimonioPort;
    @Mock
    private SaveTestimonioPort saveTestimonioPort;
    @Mock
    private LoadPublicacionPort loadPublicacionPort;
    @Mock
    private ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private IdGenerator idGenerator;

    private TestimonioService service;

    private final UserId autor = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new TestimonioService(loadTestimonioPort, saveTestimonioPort, loadPublicacionPort,
                consultarPerfilUsuarioPort, new NoOpAlmacenamientoAdapter(), userSummaryFinder, CLOCK,
                idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(saveTestimonioPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void crearSinSesionFunciona() {
        var command = new CrearTestimonioCommand(null, "Ana", null, "Cambio mi vida", 5);
        var vista = service.crear(command);
        assertThat(vista.testimonio().usuarioId()).isNull();
        assertThat(vista.testimonio().destacado()).isTrue();
    }

    @Test
    void promoverSinSerAdminFalla() {
        UserId trainee = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(trainee))
                .thenReturn(Optional.of(new UserSummary(trainee, "T", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        var command = new PromoverPublicacionCommand(trainee, PublicacionId.of(UUID.randomUUID()), 5);
        assertThatThrownBy(() -> service.promover(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveTestimonioPort, never()).save(any());
    }

    @Test
    void promoverUsaLaPrimeraFotoDelCarrusel() {
        when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        Publicacion publicacion = Publicacion.rehydrate(PublicacionId.of(UUID.randomUUID()), autor,
                TipoPublicacion.MANUAL, null,
                "que gran dia", List.of(new MediaPublicacion("wall", "muro/x/2.jpg", "image/jpeg", 1),
                        new MediaPublicacion("wall", "muro/x/1.jpg", "image/jpeg", 0)), false, CLOCK.now(),
                CLOCK.now());
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        when(consultarPerfilUsuarioPort.porId(autor)).thenReturn(Optional.of(new PerfilUsuario(autor, "Autor",
                "https://avatar")));
        when(userSummaryFinder.findById(autor))
                .thenReturn(Optional.of(new UserSummary(autor, "Autor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));

        var command = new PromoverPublicacionCommand(admin, publicacion.id(), 5);
        var vista = service.promover(command);

        Testimonio testimonio = vista.testimonio();
        assertThat(testimonio.fotoEventoRuta()).isEqualTo("muro/x/1.jpg");
        assertThat(testimonio.texto()).isEqualTo("que gran dia");
    }

    @Test
    @DisplayName("promover(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void promoverConAdminSuspendidoFalla() {
        UserId adminSuspendido = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(adminSuspendido)).thenReturn(Optional.of(new UserSummary(
                adminSuspendido, "Admin suspendido", null, UserRole.ADMIN, UserStatus.SUSPENDED)));

        var command = new PromoverPublicacionCommand(adminSuspendido, PublicacionId.of(UUID.randomUUID()), 5);
        assertThatThrownBy(() -> service.promover(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveTestimonioPort, never()).save(any());
    }
}
