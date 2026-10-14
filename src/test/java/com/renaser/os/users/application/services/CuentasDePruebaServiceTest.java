package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentaDePrueba;
import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.RolPersonalizadoCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.user.BloquearCuentasDePruebaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado.Datos;
import com.renaser.os.users.domain.model.user.CorreoDeCuentaDePrueba;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Las cuentas de prueba de «ver como» (D-270): quién las pide, cómo se crean y qué NO se dispara. Sin Spring. */
@ExtendWith(MockitoExtension.class)
class CuentasDePruebaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final FixedClock CLOCK = FixedClock.at(AHORA);

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private CargarRolesPersonalizadosPort roles;
    @Mock
    private LoadParticipacionProgramaPort loadParticipacion;
    @Mock
    private SaveParticipacionProgramaPort saveParticipacion;
    @Mock
    private LoadMentorProfilePort loadMentorProfile;
    @Mock
    private SaveMentorProfilePort saveMentorProfile;
    @Mock
    private BloquearCuentasDePruebaPort candado;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private ApplicationEventPublisher eventos;

    private CuentasDePruebaService servicio;
    private final UserId soporte = UserId.of(UUID.randomUUID());

    @BeforeEach
    void armar() {
        servicio = new CuentasDePruebaService(loadUserPort, saveUserPort, roles, loadParticipacion, saveParticipacion,
                loadMentorProfile, saveMentorProfile, candado, idGenerator, CLOCK, eventos);
        lenient().when(loadUserPort.byId(soporte)).thenReturn(Optional.of(cuenta(soporte, UserRole.IT_SUPPORT,
                UserStatus.ACTIVE)));
        lenient().when(idGenerator.newId()).thenAnswer(invocacion -> UUID.randomUUID());
        lenient().when(saveUserPort.registrarNueva(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(saveUserPort.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static User cuenta(UserId id, UserRole rol, UserStatus estado) {
        return User.rehydrate(id, new Email(id.value() + "@renaser.test"), rol, estado, "Persona", null, null, null,
                null);
    }

    @Test
    @DisplayName("la primera vez crea la cuenta del rol, sin UsuarioRegistradoEvent (no entra a chats ni a recepción)")
    void creaSinUsuarioRegistrado() {
        when(loadUserPort.byEmail(CorreoDeCuentaDePrueba.deRol(UserRole.ADMIN))).thenReturn(Optional.empty());

        CuentaDePrueba cuenta = servicio.deRol(soporte, UserRole.ADMIN);

        ArgumentCaptor<User> creada = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).registrarNueva(creada.capture());
        assertThat(creada.getValue().esCuentaDePrueba()).isTrue();
        assertThat(creada.getValue().role()).isEqualTo(UserRole.ADMIN);
        assertThat(cuenta.nombre()).isEqualTo("Prueba · Administrador");
        assertThat(cuenta.rolPersonalizado()).isNull();
        verify(eventos, never()).publishEvent(any(UsuarioRegistradoEvent.class));
        verifyNoInteractions(saveParticipacion, saveMentorProfile);
    }

    @Test
    @DisplayName("el candado entre instancias va antes de buscar o crear la cuenta")
    void candadoPrimero() {
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());

        servicio.deRol(soporte, UserRole.ALCHEMIST);

        InOrder orden = inOrder(candado, loadUserPort, saveUserPort);
        orden.verify(candado).bloquear();
        orden.verify(loadUserPort).byEmail(any());
        orden.verify(saveUserPort).registrarNueva(any());
    }

    @Test
    @DisplayName("el aprendiz de prueba tiene su programa activo en el día 45")
    void aprendizConPrograma() {
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());

        CuentaDePrueba cuenta = servicio.deRol(soporte, UserRole.TRAINEE);

        ArgumentCaptor<ParticipacionPrograma> programa = ArgumentCaptor.forClass(ParticipacionPrograma.class);
        verify(saveParticipacion).save(programa.capture());
        assertThat(programa.getValue().participanteId()).isEqualTo(cuenta.id());
        assertThat(programa.getValue().diaVigente(CLOCK)).isEqualTo(45);
    }

    @Test
    @DisplayName("si el aprendiz de prueba sigue a mitad del programa, no se le toca el programa")
    void aprendizQueSigueAMitad() {
        UserId id = UserId.of(UUID.randomUUID());
        User existente = User.cuentaDePrueba(id, CorreoDeCuentaDePrueba.deRol(UserRole.TRAINEE), "Prueba · Aprendiz",
                UserRole.TRAINEE);
        when(loadUserPort.byEmail(any())).thenReturn(Optional.of(existente));
        when(loadParticipacion.byParticipanteId(id)).thenReturn(Optional.of(
                ParticipacionPrograma.aMitadDelProgramaParaCuentaDePrueba(id, null, CLOCK)));

        servicio.deRol(soporte, UserRole.TRAINEE);

        verify(saveParticipacion, never()).save(any());
        verify(saveUserPort, never()).save(any());
        verify(saveUserPort, never()).registrarNueva(any());
    }

    @Test
    @DisplayName("el mentor y la guía de prueba tienen su perfil de mentor (lo exige liderar un grupo)")
    void perfilDeMentor() {
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());
        when(loadMentorProfile.byUserId(any())).thenReturn(Optional.empty());

        servicio.deRol(soporte, UserRole.MENTOR);
        servicio.deRol(soporte, UserRole.TRAINEE_GUIDE);

        verify(saveMentorProfile, org.mockito.Mockito.times(2)).save(any(MentorProfile.class));
    }

    @Test
    @DisplayName("una cuenta de prueba tocada por fuera (suspendida, con otro rol) se repara")
    void reparaLaCuenta() {
        UserId id = UserId.of(UUID.randomUUID());
        User tocada = User.rehydrate(id, CorreoDeCuentaDePrueba.deRol(UserRole.ADMIN), UserRole.TRAINEE,
                UserStatus.SUSPENDED, "Otro", null, null, null, null, null, null, null, true);
        when(loadUserPort.byEmail(any())).thenReturn(Optional.of(tocada));

        servicio.deRol(soporte, UserRole.ADMIN);

        ArgumentCaptor<User> guardada = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(guardada.capture());
        assertThat(guardada.getValue().role()).isEqualTo(UserRole.ADMIN);
        assertThat(guardada.getValue().status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("si el correo lo usa una cuenta que no es de prueba, no se la apropia (409)")
    void correoOcupado() {
        UserId id = UserId.of(UUID.randomUUID());
        User real = User.rehydrate(id, CorreoDeCuentaDePrueba.deRol(UserRole.ADMIN), UserRole.ADMIN,
                UserStatus.ACTIVE, "Persona real", null, null, null, null);
        when(loadUserPort.byEmail(any())).thenReturn(Optional.of(real));

        assertThatThrownBy(() -> servicio.deRol(soporte, UserRole.ADMIN)).isInstanceOf(IllegalStateException.class);
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("la de un rol personalizado lleva su base y el rol, y avisa a la plataforma (RolPersonalizadoCambiadoEvent)")
    void rolPersonalizado() {
        RolPersonalizado junior = RolPersonalizado.crear(UUID.randomUUID(),
                new Datos("Mentor junior", null, UserRole.MENTOR, Set.of("muro")), soporte, AHORA);
        when(roles.porId(junior.id())).thenReturn(Optional.of(junior));
        when(loadUserPort.byEmail(CorreoDeCuentaDePrueba.deRolPersonalizado(junior.id()))).thenReturn(Optional.empty());
        when(loadMentorProfile.byUserId(any())).thenReturn(Optional.of(MentorProfile.create(soporte, CLOCK)));

        CuentaDePrueba cuenta = servicio.deRolPersonalizado(soporte, junior.id());

        assertThat(cuenta.rol()).isEqualTo(UserRole.MENTOR);
        assertThat(cuenta.rolPersonalizado()).isEqualTo(new RolPersonalizadoAsignado(junior.id(), "Mentor junior"));
        assertThat(cuenta.nombre()).isEqualTo("Prueba · Mentor junior");
        verify(eventos).publishEvent(any(RolPersonalizadoCambiadoEvent.class));
    }

    @Test
    @DisplayName("un rol personalizado que no existe es 404 y uno desactivado es 400, sin crear nada")
    void rolPersonalizadoInvalido() {
        UUID noExiste = UUID.randomUUID();
        RolPersonalizado inactivo = RolPersonalizado.crear(UUID.randomUUID(),
                new Datos("Viejo", null, UserRole.MENTOR, Set.of("muro")), soporte, AHORA)
                .editar(new Datos("Viejo", null, UserRole.MENTOR, Set.of("muro")), false, 0, soporte, AHORA);
        when(roles.porId(noExiste)).thenReturn(Optional.empty());
        when(roles.porId(inactivo.id())).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> servicio.deRolPersonalizado(soporte, noExiste))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.deRolPersonalizado(soporte, inactivo.id()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(saveUserPort, candado);
    }

    @Test
    @DisplayName("Soporte TI no se mira con «ver como» (400)")
    void soporteTiNo() {
        assertThatThrownBy(() -> servicio.deRol(soporte, UserRole.IT_SUPPORT))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(saveUserPort, candado);
    }

    @Test
    @DisplayName("solo Soporte TI activo las pide: ADMIN, ALQUIMISTA, MENTOR y un Soporte TI suspendido reciben 403")
    void soloSoporteTiActivo() {
        for (UserRole rol : new UserRole[]{UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.MENTOR, UserRole.TRAINEE}) {
            UserId otro = UserId.of(UUID.randomUUID());
            when(loadUserPort.byId(otro)).thenReturn(Optional.of(cuenta(otro, rol, UserStatus.ACTIVE)));
            assertThatThrownBy(() -> servicio.deRol(otro, UserRole.MENTOR)).as(rol.name())
                    .isInstanceOf(NotAuthorizedException.class);
        }
        UserId suspendido = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(suspendido)).thenReturn(Optional.of(cuenta(suspendido, UserRole.IT_SUPPORT,
                UserStatus.SUSPENDED)));
        assertThatThrownBy(() -> servicio.deRol(suspendido, UserRole.MENTOR))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(saveUserPort, candado);
    }
}
