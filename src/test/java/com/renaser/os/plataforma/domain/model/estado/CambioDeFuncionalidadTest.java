package com.renaser.os.plataforma.domain.model.estado;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas del cambio de un interruptor (FR-005, FR-008). Sin Spring. */
class CambioDeFuncionalidadTest {

    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();

    @Test
    @DisplayName("el motivo es obligatorio")
    void motivoObligatorio() {
        assertThatThrownBy(() -> new CambioDeFuncionalidad("chat.grupos", true, Set.of(), null, "  ", 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
        assertThatThrownBy(() -> new CambioDeFuncionalidad("chat.grupos", true, Set.of(), null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el aviso tiene tope de 200 caracteres (un emoji es uno) y vacío es «sin aviso»")
    void avisoConTope() {
        String doscientos = "😀".repeat(200);

        assertThat(new CambioDeFuncionalidad("chat", true, Set.of(), doscientos, "m", 0).aviso()).isEqualTo(doscientos);
        assertThat(new CambioDeFuncionalidad("chat", true, Set.of(), "   ", "m", 0).aviso()).isNull();
        assertThatThrownBy(() -> new CambioDeFuncionalidad("chat", true, Set.of(), doscientos + "x", "m", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("crítico: toda funcionalidad crítica, o dejar cualquiera apagada para todos (FR-008)")
    void esCritico() {
        CambioDeFuncionalidad soloAprendices = new CambioDeFuncionalidad("chat.grupos", true, Set.of(UserRole.TRAINEE),
                null, "m", 0);
        CambioDeFuncionalidad paraTodos = new CambioDeFuncionalidad("chat.grupos", false, Set.of(), null, "m", 0);

        assertThat(soloAprendices.esCritico(catalogo.definicion("chat.grupos").orElseThrow())).isFalse();
        assertThat(paraTodos.esCritico(catalogo.definicion("chat.grupos").orElseThrow())).isTrue();
        assertThat(soloAprendices.esCritico(catalogo.definicion("chat").orElseThrow()))
                .as("chat es crítica: cualquier cambio").isTrue();
    }

    @Test
    @DisplayName("el resumen dice el estado nuevo en palabras, con los nombres visibles de los roles")
    void resumen() {
        assertThat(new CambioDeFuncionalidad("chat", false, Set.of(), null, "m", 0).resumen())
                .isEqualTo("Apagada para todos");
        assertThat(new CambioDeFuncionalidad("chat", true, Set.of(), null, "m", 0).resumen())
                .isEqualTo("Prendida para todos");
        assertThat(new CambioDeFuncionalidad("chat", true, Set.of(UserRole.TRAINEE), null, "m", 0).resumen())
                .isEqualTo("Apagada para Aprendiz");
    }
}
