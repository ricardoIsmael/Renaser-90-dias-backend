package com.renaser.os.plataforma.domain.model.vercomo;

import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El pedido, el rol elegido, la sesión y la auditoría de «ver como» (contrato fase-2.md §5; D-270). Sin Spring. */
class VerComoDominioTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    @Test
    @DisplayName("un rol base por su nombre de la API, sin importar mayúsculas")
    void rolBase() {
        PedidoDeVerComo pedido = PedidoDeVerComo.de(" mentor_lead ", null);

        assertThat(pedido.esDeUnRolBase()).isTrue();
        assertThat(pedido.rol()).isEqualTo(UserRole.MENTOR_LEAD);
    }

    @Test
    @DisplayName("un rol personalizado por su id")
    void rolPersonalizado() {
        UUID id = UUID.randomUUID();

        PedidoDeVerComo pedido = PedidoDeVerComo.de(null, id);

        assertThat(pedido.esDeUnRolBase()).isFalse();
        assertThat(pedido.rolPersonalizadoId()).isEqualTo(id);
    }

    @Test
    @DisplayName("400: ninguno, los dos, un rol que no existe o Soporte TI")
    void invalidos() {
        assertThatThrownBy(() -> PedidoDeVerComo.de(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PedidoDeVerComo.de(" ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PedidoDeVerComo.de("MENTOR", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PedidoDeVerComo.de("JEFE", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PedidoDeVerComo.de("IT_SUPPORT", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RolElegido.Base(UserRole.IT_SUPPORT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la foto de la auditoría tiene la forma que lee la app: {rol} o {rolPersonalizado: {id, nombre}}")
    void fotos() {
        UUID id = UUID.fromString("11111111-2222-4333-8444-555555555555");

        assertThat(new RolElegido.Base(UserRole.MENTOR).foto()).isEqualTo(Map.of("rol", "MENTOR"));
        assertThat(new RolElegido.Personalizado(id, "Mentor junior").foto()).isEqualTo(Map.of("rolPersonalizado",
                Map.of("id", id.toString(), "nombre", "Mentor junior")));
    }

    @Test
    @DisplayName("la sesión vence a los 30 minutos de abrirse")
    void venceALaMediaHora() {
        SesionDeVerComo sesion = SesionDeVerComo.abrir(UserId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                new RolElegido.Base(UserRole.TRAINEE), AHORA);

        assertThat(sesion.venceEn()).isEqualTo(AHORA.plusSeconds(1800));
    }

    @Test
    @DisplayName("auditoría VER_COMO: al entrar antes null y después el rol; al salir, al revés; sin clave")
    void auditoria() {
        UserId soporte = UserId.of(UUID.randomUUID());
        Map<String, Object> rol = new RolElegido.Base(UserRole.MENTOR).foto();

        CambioAuditado entrada = CambioAuditado.entradaAVerComo(UUID.randomUUID(), soporte, rol, "App/1.6", AHORA);
        CambioAuditado salida = CambioAuditado.salidaDeVerComo(UUID.randomUUID(), soporte, rol, "App/1.6", AHORA);

        assertThat(entrada.tipo()).isEqualTo(TipoDeCambio.VER_COMO);
        assertThat(entrada.actor()).isEqualTo(soporte);
        assertThat(entrada.clave()).isNull();
        assertThat(entrada.antes()).isNull();
        assertThat(entrada.despues()).isEqualTo(Map.of("rol", "MENTOR"));
        assertThat(salida.antes()).isEqualTo(Map.of("rol", "MENTOR"));
        assertThat(salida.despues()).isNull();
        assertThatThrownBy(() -> CambioAuditado.entradaAVerComo(UUID.randomUUID(), soporte, Map.of(), null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
