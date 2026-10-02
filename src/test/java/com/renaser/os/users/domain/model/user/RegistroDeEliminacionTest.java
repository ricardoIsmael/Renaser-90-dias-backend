package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistroDeEliminacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");

    @Test
    @DisplayName("el cierre lleva la via y lo hace la propia persona")
    void cierreConVia() {
        User cuenta = User.registerTrainee(UserId.of(UUID.randomUUID()), new Email("a@renaser.com"), "Ana");

        var registro = RegistroDeEliminacion.cerrada(cuenta, RegistroDeEliminacion.Via.WEB, AHORA);

        assertThat(registro.accion()).isEqualTo(RegistroDeEliminacion.Accion.CERRADA);
        assertThat(registro.via()).isEqualTo(RegistroDeEliminacion.Via.WEB);
        assertThat(registro.actorId()).isEqualTo(cuenta.id());
        assertThat(registro.rol()).isEqualTo(UserRole.TRAINEE);
    }

    @Test
    @DisplayName("la via solo va al cerrar")
    void viaSoloAlCerrar() {
        UserId id = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new RegistroDeEliminacion(id, UserRole.TRAINEE,
                RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN, RegistroDeEliminacion.Via.APP, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistroDeEliminacion(id, UserRole.TRAINEE,
                RegistroDeEliminacion.Accion.CERRADA, null, id, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
