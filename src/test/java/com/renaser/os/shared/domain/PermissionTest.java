package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    @ParameterizedTest
    @EnumSource(value = Permission.class, names = "OPEN_SUPPORT_TICKET")
    @DisplayName("OPEN_SUPPORT_TICKET tolera cuenta suspendida (alguien suspendido puede reclamar su suspension)")
    void openSupportTicketToleraSuspendido(Permission permiso) {
        assertThat(permiso.toleraCuentaSuspendida()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Permission.class, mode = EnumSource.Mode.EXCLUDE, names = "OPEN_SUPPORT_TICKET")
    @DisplayName("ningun otro permiso tolera cuenta suspendida")
    void ningunOtroPermisoToleraSuspendido(Permission permiso) {
        assertThat(permiso.toleraCuentaSuspendida()).isFalse();
    }
}
