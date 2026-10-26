package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado.Datos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las cuentas de prueba de «ver como rol» en el agregado {@link User} (spec 002, fase 2, US5; D-273). Sin Spring. */
class CuentaDePruebaTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    private static User deRol(UserRole rol) {
        return User.cuentaDePrueba(UserId.of(UUID.randomUUID()), CorreoDeCuentaDePrueba.deRol(rol),
                CorreoDeCuentaDePrueba.nombre(rol.nombreVisible()), rol);
    }

    private static RolPersonalizado rolPersonalizado(String nombre, UserRole base, boolean activo) {
        RolPersonalizado rol = RolPersonalizado.crear(UUID.randomUUID(), new Datos(nombre, null, base, Set.of("muro")),
                UserId.of(UUID.randomUUID()), AHORA);
        return activo ? rol : rol.editar(new Datos(nombre, null, base, Set.of("muro")), false, 0,
                UserId.of(UUID.randomUUID()), AHORA);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "IT_SUPPORT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("una por rol base: activa, marcada, con el correo reservado y «Prueba · <rol>»")
    void unaPorRolBase(UserRole rol) {
        User cuenta = deRol(rol);

        assertThat(cuenta.esCuentaDePrueba()).isTrue();
        assertThat(cuenta.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(cuenta.role()).isEqualTo(rol);
        assertThat(cuenta.email().value()).startsWith("ver-como+").endsWith("@renaser.invalid");
        assertThat(cuenta.fullName()).isEqualTo("Prueba · " + rol.nombreVisible());
    }

    @Test
    @DisplayName("el correo del rol base es el nombre de la API en minúsculas con guiones, y el del personalizado lleva el id")
    void correos() {
        UUID id = UUID.fromString("11111111-2222-4333-8444-555555555555");

        assertThat(CorreoDeCuentaDePrueba.deRol(UserRole.MENTOR_LEAD).value())
                .isEqualTo("ver-como+mentor-lead@renaser.invalid");
        assertThat(CorreoDeCuentaDePrueba.deRolPersonalizado(id).value())
                .isEqualTo("ver-como+rp-11111111-2222-4333-8444-555555555555@renaser.invalid");
        assertThat(CorreoDeCuentaDePrueba.loEs(new Email("persona@renaser.test"))).isFalse();
    }

    @Test
    @DisplayName("nunca Soporte TI: «ver como» no abre la consola (400)")
    void nuncaSoporteTi() {
        assertThatThrownBy(() -> User.cuentaDePrueba(UserId.of(UUID.randomUUID()),
                new Email("ver-como+it-support@renaser.invalid"), "Prueba · Soporte TI", UserRole.IT_SUPPORT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("solo con el correo reservado: una cuenta real no se convierte en cuenta de prueba")
    void soloConElCorreoReservado() {
        assertThatThrownBy(() -> User.cuentaDePrueba(UserId.of(UUID.randomUUID()), new Email("persona@renaser.pe"),
                "Prueba · Mentor", UserRole.MENTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("prepararla la repara: rol, nombre, activa y sin baja; dice si cambió algo")
    void prepararLaRepara() {
        User cuenta = User.rehydrate(UserId.of(UUID.randomUUID()), CorreoDeCuentaDePrueba.deRol(UserRole.MENTOR),
                UserRole.TRAINEE, UserStatus.SUSPENDED, "Otro nombre", null, null, null, null, AHORA, null, null,
                true);

        assertThat(cuenta.prepararComoCuentaDePrueba("Prueba · Mentor", UserRole.MENTOR, null)).isTrue();

        assertThat(cuenta.role()).isEqualTo(UserRole.MENTOR);
        assertThat(cuenta.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(cuenta.fullName()).isEqualTo("Prueba · Mentor");
        assertThat(cuenta.bajaPendiente()).isFalse();
        assertThat(cuenta.prepararComoCuentaDePrueba("Prueba · Mentor", UserRole.MENTOR, null))
                .as("la segunda vez no cambia nada").isFalse();
    }

    @Test
    @DisplayName("con un rol personalizado activo de su base, lo lleva (sin pasar por un ADMIN)")
    void conRolPersonalizado() {
        RolPersonalizado junior = rolPersonalizado("Mentor junior", UserRole.MENTOR, true);
        User cuenta = deRol(UserRole.MENTOR);

        assertThat(cuenta.prepararComoCuentaDePrueba("Prueba · Mentor junior", UserRole.MENTOR, junior)).isTrue();

        assertThat(cuenta.rolPersonalizado()).contains(new RolPersonalizadoAsignado(junior.id(), "Mentor junior"));
    }

    @Test
    @DisplayName("un rol personalizado desactivado o de otra base no se le pone (400)")
    void rolPersonalizadoInvalido() {
        User cuenta = deRol(UserRole.MENTOR);

        assertThatThrownBy(() -> cuenta.prepararComoCuentaDePrueba("Prueba · X", UserRole.MENTOR,
                rolPersonalizado("Inactivo", UserRole.MENTOR, false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cuenta.prepararComoCuentaDePrueba("Prueba · X", UserRole.MENTOR,
                rolPersonalizado("De aprendiz", UserRole.TRAINEE, true))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una cuenta real no se «prepara» como cuenta de prueba")
    void unaCuentaRealNoSePrepara() {
        User real = User.rehydrate(UserId.of(UUID.randomUUID()), new Email("persona@renaser.test"), UserRole.MENTOR,
                UserStatus.ACTIVE, "Persona", null, null, null, null);

        assertThatThrownBy(() -> real.prepararComoCuentaDePrueba("Prueba · Mentor", UserRole.MENTOR, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Administración no le cambia el rol ni le asigna un rol personalizado (403)")
    void administracionNoLaCambia() {
        User admin = User.rehydrate(UserId.of(UUID.randomUUID()), new Email("admin@renaser.test"), UserRole.ADMIN,
                UserStatus.ACTIVE, "Admin", null, null, null, null);
        User cuenta = deRol(UserRole.MENTOR);

        assertThatThrownBy(() -> cuenta.changeRole(UserRole.TRAINEE, admin)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> cuenta.asignarRolPersonalizado(rolPersonalizado("Mentor junior", UserRole.MENTOR, true),
                admin)).isInstanceOf(NotAuthorizedException.class);
        assertThat(cuenta.role()).isEqualTo(UserRole.MENTOR);
    }

    @Test
    @DisplayName("las altas de siempre no son cuentas de prueba")
    void lasAltasDeSiempreNoLoSon() {
        User aprendiz = User.registerTrainee(UserId.of(UUID.randomUUID()), new Email("a@renaser.test"), "Ana");

        assertThat(aprendiz.esCuentaDePrueba()).isFalse();
    }
}
