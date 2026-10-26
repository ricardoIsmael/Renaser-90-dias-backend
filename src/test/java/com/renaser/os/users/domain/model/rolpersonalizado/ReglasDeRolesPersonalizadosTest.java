package com.renaser.os.users.domain.model.rolpersonalizado;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolPersonalizadoRechazadoException;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado.Datos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas que miran más de un rol: nombre libre, cupo de 20 activos y destino del reemplazo. Sin Spring. */
class ReglasDeRolesPersonalizadosTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final UserId SOPORTE = UserId.of(UUID.randomUUID());

    private static RolPersonalizado rol(String nombre, UserRole base, boolean activo) {
        RolPersonalizado creado = RolPersonalizado.crear(UUID.randomUUID(), new Datos(nombre, null, base, Set.of()),
                SOPORTE, AHORA);
        return activo ? creado : creado.editar(new Datos(nombre, null, null, Set.of()), false, 0, SOPORTE, AHORA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mentor", "mentor", "MÉNTOR", "Aprendiz", "Guía de aprendiz", "guia de aprendiz",
            "Líder de mentores", "Administrador", "Alquimista", "Soporte TI"})
    @DisplayName("el nombre de un rol base, con o sin tildes y mayúsculas, se rechaza: 400")
    void nombreDeRolBase(String nombre) {
        assertThatThrownBy(() -> ReglasDeRolesPersonalizados.exigirNombreLibre(rol(nombre, UserRole.MENTOR, true),
                List.of(), null)).isInstanceOf(RolPersonalizadoRechazadoException.class).hasMessageContaining("rol base")
                .extracting(e -> ((RolPersonalizadoRechazadoException) e).codigo()).isEqualTo("NOMBRE_REPETIDO");
    }

    @Test
    @DisplayName("un nombre repetido se rechaza (400), salvo el propio al editar")
    void nombreRepetido() {
        RolPersonalizado existente = rol("Mentor junior", UserRole.MENTOR, false);
        RolPersonalizado candidato = rol("mentor JÚNIOR", UserRole.TRAINEE, true);

        assertThatThrownBy(() -> ReglasDeRolesPersonalizados.exigirNombreLibre(candidato, List.of(existente), null))
                .isInstanceOf(RolPersonalizadoRechazadoException.class).hasMessageContaining("Ya hay un rol")
                .extracting(e -> ((RolPersonalizadoRechazadoException) e).codigo()).isEqualTo("NOMBRE_REPETIDO");
        assertThatCode(() -> ReglasDeRolesPersonalizados.exigirNombreLibre(existente, List.of(existente),
                existente.id())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("hasta 20 activos (FR-034): el 21 es 400; los inactivos no cuentan; reactivar el propio no se cuenta")
    void cupoDeVeinte() {
        List<RolPersonalizado> roles = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            roles.add(rol("Rol " + i, UserRole.TRAINEE, true));
        }
        roles.add(rol("Inactivo", UserRole.TRAINEE, false));
        assertThatCode(() -> ReglasDeRolesPersonalizados.exigirCupo(roles, null)).doesNotThrowAnyException();

        RolPersonalizado vigesimo = rol("Rol 19", UserRole.TRAINEE, true);
        roles.add(vigesimo);

        assertThatThrownBy(() -> ReglasDeRolesPersonalizados.exigirCupo(roles, null))
                .isInstanceOf(RolPersonalizadoRechazadoException.class).hasMessageContaining("20")
                .extracting(e -> ((RolPersonalizadoRechazadoException) e).codigo()).isEqualTo("MAXIMO_DE_ROLES");
        assertThatCode(() -> ReglasDeRolesPersonalizados.exigirCupo(roles, vigesimo.id()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el destino de un reemplazo en bloque es otro rol, activo y con la misma base: si no, 400")
    void destinoDelReemplazo() {
        RolPersonalizado origen = rol("Mentor junior", UserRole.MENTOR, true);

        assertThatCode(() -> ReglasDeRolesPersonalizados.exigirDestinoValido(origen,
                rol("Mentor inicial", UserRole.MENTOR, true))).doesNotThrowAnyException();
        assertThatThrownBy(() -> ReglasDeRolesPersonalizados.exigirDestinoValido(origen, origen))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglasDeRolesPersonalizados.exigirDestinoValido(origen,
                rol("Mentor apagado", UserRole.MENTOR, false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglasDeRolesPersonalizados.exigirDestinoValido(origen,
                rol("Guía inicial", UserRole.TRAINEE_GUIDE, true))).isInstanceOf(IllegalArgumentException.class);
    }
}
