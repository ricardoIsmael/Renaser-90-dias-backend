package com.renaser.os.rocks.domain.model.rocamaestra;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-247: la llave de la cadena de rocas, en un solo lugar (antes, cinco copias). */
class RocasMaestrasTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());

    private static RocaMaestra de(EjeObjetivo eje) {
        return RocaMaestra.definir(RocaMaestraId.of(UUID.randomUUID()), PERSONA, eje, "Objetivo", null,
                Instant.parse("2026-10-02T15:00:00Z"));
    }

    @Test
    @DisplayName("sin las tres, cerrada: ROCKS_LOCKED con el mismo mensaje de siempre")
    void sinLasTres() {
        RocasMaestras dos = RocasMaestras.de(List.of(de(EjeObjetivo.CUERPO), de(EjeObjetivo.TRABAJO)));

        assertThat(dos.completas()).isFalse();
        assertThat(RocasMaestras.de(List.of()).completas()).isFalse();
        assertThatThrownBy(dos::exigirCompletas).isInstanceOf(NotAuthorizedException.class)
                .hasMessage("ROCKS_LOCKED: completa tu onboarding antes de planificar rocas");
        assertThatThrownBy(() -> dos.exigirDelEje(EjeObjetivo.CUERPO)).isInstanceOf(NotAuthorizedException.class)
                .hasMessageStartingWith(RocasMaestras.CODIGO_BLOQUEO);
    }

    @Test
    @DisplayName("con las tres, abierta: una por eje")
    void conLasTres() {
        RocaMaestra cuerpo = de(EjeObjetivo.CUERPO);
        RocasMaestras tres = RocasMaestras.de(List.of(cuerpo, de(EjeObjetivo.TRABAJO), de(EjeObjetivo.RELACIONES)));

        assertThat(tres.completas()).isTrue();
        assertThat(tres.exigirCompletas()).hasSize(3).containsEntry(EjeObjetivo.CUERPO, cuerpo);
        assertThat(tres.exigirDelEje(EjeObjetivo.CUERPO)).isEqualTo(cuerpo);
    }
}
