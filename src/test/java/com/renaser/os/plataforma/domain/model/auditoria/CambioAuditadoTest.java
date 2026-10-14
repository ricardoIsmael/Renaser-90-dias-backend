package com.renaser.os.plataforma.domain.model.auditoria;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La fila de auditoría (FR-007). Sin Spring. */
class CambioAuditadoTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T22:00:00Z");

    @Test
    @DisplayName("solo un cambio automático va sin actor")
    void soloLoAutomaticoVaSinActor() {
        assertThat(CambioAuditado.automatico(UUID.randomUUID(), "ia.chat", Map.of("estado", "CERRADO"),
                Map.of("estado", "ABIERTO"), "5 fallas en 2 min", AHORA).actor()).isNull();
        assertThatThrownBy(() -> new CambioAuditado(UUID.randomUUID(), TipoDeCambio.MANUAL, null, "chat", null, null,
                "m", null, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un acceso es un desbloqueo o una reautenticación, con su resultado")
    void accesoConResultado() {
        CambioAuditado acceso = CambioAuditado.acceso(UUID.randomUUID(), TipoDeCambio.DESBLOQUEO,
                UserId.of(UUID.randomUUID()), "OK", "x".repeat(500), AHORA);

        assertThat(acceso.despues()).containsEntry("resultado", "OK");
        assertThat(acceso.dispositivo()).hasSize(200);
        assertThatThrownBy(() -> CambioAuditado.acceso(UUID.randomUUID(), TipoDeCambio.MANUAL,
                UserId.of(UUID.randomUUID()), "OK", null, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un cambio de rol personalizado lleva actor y la clave rp:<id> (fase 2, D-269)")
    void rolPersonalizado() {
        UUID rol = UUID.randomUUID();
        CambioAuditado cambio = CambioAuditado.deRolPersonalizado(UUID.randomUUID(), UserId.of(UUID.randomUUID()),
                "rp:" + rol, null, Map.of("nombre", "Mentor junior"), "crearlo", null, AHORA);

        assertThat(cambio.tipo()).isEqualTo(TipoDeCambio.ROL_PERSONALIZADO);
        assertThat(cambio.clave()).isEqualTo("rp:" + rol);
        assertThatThrownBy(() -> CambioAuditado.deRolPersonalizado(UUID.randomUUID(), UserId.of(UUID.randomUUID()),
                "eventos", null, null, "m", null, AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CambioAuditado.deRolPersonalizado(UUID.randomUUID(), null, "rp:" + rol, null, null,
                "m", null, AHORA)).isInstanceOf(NullPointerException.class);
    }
}
