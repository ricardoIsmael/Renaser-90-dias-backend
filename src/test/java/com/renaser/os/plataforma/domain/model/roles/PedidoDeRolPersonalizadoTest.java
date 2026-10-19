package com.renaser.os.plataforma.domain.model.roles;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lo que la consola valida de un pedido de rol personalizado contra el catálogo (D-272). Sin Spring. */
class PedidoDeRolPersonalizadoTest {

    private static final CatalogoDeFuncionalidades CATALOGO = CatalogoDeFuncionalidades.inicial();

    @Test
    @DisplayName("subconjunto de la base: solo claves del catálogo que el rol base usa; sin repetir y ordenadas")
    void subconjuntoDeLaBase() {
        assertThat(PedidoDeRolPersonalizado.clavesValidas(List.of("muro", "chat", "muro"), UserRole.MENTOR, CATALOGO))
                .containsExactly("chat", "muro");
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.clavesValidas(List.of("eventos.gestion"), UserRole.MENTOR,
                CATALOGO)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no la usa Mentor");
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.clavesValidas(List.of("admin"), UserRole.TRAINEE,
                CATALOGO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.clavesValidas(List.of("no.existe"), UserRole.ADMIN,
                CATALOGO)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("catálogo");
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.clavesValidas(null, UserRole.ADMIN, CATALOGO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PedidoDeRolPersonalizado.clavesValidas(List.of(), UserRole.ADMIN, CATALOGO)).isEmpty();
    }

    @Test
    @DisplayName("el rol base: un nombre de la API, nunca Soporte TI; uno que no existe es 400")
    void rolBase() {
        assertThat(PedidoDeRolPersonalizado.rolBaseValido("TRAINEE_GUIDE")).isEqualTo(UserRole.TRAINEE_GUIDE);
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.rolBaseValido("IT_SUPPORT"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Soporte TI");
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.rolBaseValido("MENTORA"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.rolBaseValido(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el motivo es obligatorio y de hasta 500 caracteres")
    void motivo() {
        assertThat(PedidoDeRolPersonalizado.motivoValido("  Pedido del dueño ")).isEqualTo("Pedido del dueño");
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.motivoValido(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PedidoDeRolPersonalizado.motivoValido("x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
