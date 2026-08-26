package com.renaser.os.users.domain.model.identidadexterna;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentidadExternaTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T12:00:00Z");
    private static final Clock CLOCK_FIJO = new Clock() {
        @Override
        public Instant now() {
            return AHORA;
        }

        @Override
        public LocalDate today() {
            return AHORA.atZone(ZoneOffset.UTC).toLocalDate();
        }
    };

    @Test
    void vincularUsaElRelojParaLaFechaDeVinculacion() {
        UserId usuarioId = UserId.of(UUID.randomUUID());

        IdentidadExterna identidad = IdentidadExterna.vincular(ProveedorIdentidad.GOOGLE, "sub-1", usuarioId,
                "actor@renaser.dev", CLOCK_FIJO);

        assertThat(identidad.vinculadaEn()).isEqualTo(AHORA);
        assertThat(identidad.proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
        assertThat(identidad.sujetoProveedor()).isEqualTo("sub-1");
    }

    @Test
    void sujetoProveedorVacioEsRechazado() {
        UserId usuarioId = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> IdentidadExterna.vincular(ProveedorIdentidad.GOOGLE, " ", usuarioId,
                "actor@renaser.dev", CLOCK_FIJO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emailProveedorPuedeSerNuloPorqueEsSoloInformativo() {
        UserId usuarioId = UserId.of(UUID.randomUUID());

        IdentidadExterna identidad = IdentidadExterna.vincular(ProveedorIdentidad.APPLE, "sub-2", usuarioId, null,
                CLOCK_FIJO);

        assertThat(identidad.emailProveedor()).isNull();
    }

    @Test
    void usuarioIdNuloEsRechazado() {
        assertThatThrownBy(() -> IdentidadExterna.rehydrate(ProveedorIdentidad.GOOGLE, "sub-3", null,
                "actor@renaser.dev", AHORA))
                .isInstanceOf(NullPointerException.class);
    }
}
