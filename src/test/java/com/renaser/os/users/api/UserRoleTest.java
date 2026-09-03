package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La matriz rol -> permiso de A-1 (CLAUDE.MD §5.3.2, docs/ENDPOINTS_FALTANTES.md fila A-1).
 * Domain puro: sin Spring, prueba unitaria sin contexto (CLAUDE.MD §0.2).
 */
class UserRoleTest {

    /**
     * Los 8 permisos que hoy tiene evidencia en el codigo (guards existentes) de que
     * corresponden a TRAINEE — ver el javadoc de {@code UserRole.PERMISOS_TRAINEE}.
     */
    private static final Set<Permission> PERMISOS_TRAINEE_ESPERADOS = EnumSet.of(
            Permission.USE_APP,
            Permission.FOLLOW_OWN_PROGRAM,
            Permission.PUBLISH_ON_WALL,
            Permission.OPEN_SUPPORT_TICKET,
            Permission.USE_MENTOR_TICKETS,
            Permission.OPEN_MENTOR_TICKET,
            Permission.VIEW_OWN_PHASE_CONTRACTS,
            Permission.SIGN_PHASE_CONTRACT);

    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {
            "USE_APP", "FOLLOW_OWN_PROGRAM", "PUBLISH_ON_WALL", "OPEN_SUPPORT_TICKET",
            "USE_MENTOR_TICKETS", "OPEN_MENTOR_TICKET", "VIEW_OWN_PHASE_CONTRACTS", "SIGN_PHASE_CONTRACT"})
    @DisplayName("TRAINEE tiene exactamente los 8 permisos con evidencia en el codigo")
    void traineeTieneLosOchoPermisosConEvidencia(Permission permiso) {
        assertThat(UserRole.TRAINEE.can(permiso)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Permission.class, mode = EnumSource.Mode.EXCLUDE, names = {
            "USE_APP", "FOLLOW_OWN_PROGRAM", "PUBLISH_ON_WALL", "OPEN_SUPPORT_TICKET",
            "USE_MENTOR_TICKETS", "OPEN_MENTOR_TICKET", "VIEW_OWN_PHASE_CONTRACTS", "SIGN_PHASE_CONTRACT"})
    @DisplayName("TRAINEE NO tiene ninguno de los otros 22 permisos (MANAGE_*, MODERATE_WALL, etc)")
    void traineeNoTieneLosOtrosPermisos(Permission permiso) {
        assertThat(UserRole.TRAINEE.can(permiso)).isFalse();
    }

    @Test
    @DisplayName("la matriz de TRAINEE tiene exactamente 8 permisos, ni uno mas ni uno menos")
    void laMatrizDeTraineeTieneOchoPermisos() {
        long otorgados = EnumSet.allOf(Permission.class).stream()
                .filter(UserRole.TRAINEE::can)
                .count();
        assertThat(otorgados).isEqualTo(PERMISOS_TRAINEE_ESPERADOS.size());
    }

    /**
     * HUECO DE SEGURIDAD DELIBERADO Y TEMPORAL (A-1): para MENTOR, MENTOR_LEAD, ADMIN y
     * ALCHEMIST, can() falla-abierto (devuelve true) para CUALQUIER permiso, porque el dueño
     * del proyecto todavia no definio la matriz real de esos 4 roles. Este test documenta el
     * hueco a proposito — si algun dia se define la matriz real, este test tiene que dejar de
     * pasar y avisar que hay que actualizarlo, no quedar en verde silenciosamente.
     */
    @ParameterizedTest
    @EnumSource(UserRole.class)
    @DisplayName("TEMPORAL: los 4 roles sin matriz definida pasan cualquier permiso (fail-open, no verificado todavia)")
    void rolesSinMatrizDefinidaFallanAbiertoParaCualquierPermiso(UserRole rol) {
        if (rol == UserRole.TRAINEE) {
            return; // TRAINEE tiene matriz real, cubierto por los tests de arriba
        }
        for (Permission permiso : Permission.values()) {
            assertThat(rol.can(permiso))
                    .as("%s deberia pasar %s porque su matriz todavia no esta definida (hueco temporal A-1)",
                            rol, permiso)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("canManageRoles() no cambio: sigue siendo ADMIN/ALCHEMIST")
    void canManageRolesSigueIgual() {
        assertThat(UserRole.ADMIN.canManageRoles()).isTrue();
        assertThat(UserRole.ALCHEMIST.canManageRoles()).isTrue();
        assertThat(UserRole.TRAINEE.canManageRoles()).isFalse();
        assertThat(UserRole.MENTOR.canManageRoles()).isFalse();
        assertThat(UserRole.MENTOR_LEAD.canManageRoles()).isFalse();
    }
}
