package com.renaser.os.plataforma.domain.model.catalogo;

import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 044 en el catálogo de Soporte TI: el interruptor nuevo y quiénes usan la asistencia y el soporte. */
class CatalogoDeLaSpec044Test {

    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();

    // spec 044 FR-028
    @Test
    @DisplayName("FR-028 (P5): eventos.convocar es hija de eventos, nace prendida y la usan el mentor, la guía, el líder y Administración")
    void convocarNacePrendida() {
        DefinicionDeFuncionalidad convocar = catalogo.definicion("eventos.convocar").orElseThrow();

        assertThat(convocar.madre()).isEqualTo("eventos");
        assertThat(convocar.porDefecto()).isEqualTo(ValorPorDefecto.PRENDIDA);
        assertThat(convocar.rolesQueLaUsan()).contains(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD, UserRole.ADMIN,
                UserRole.ALCHEMIST).doesNotContain(UserRole.TRAINEE);
    }

    // spec 044 FR-027
    @Test
    @DisplayName("FR-027: la asistencia la usan también el mentor y la guía (la de sus propios eventos)")
    void asistenciaDelCreador() {
        assertThat(catalogo.definicion("eventos.asistencia").orElseThrow().rolesQueLaUsan()).contains(UserRole.MENTOR, UserRole.TRAINEE_GUIDE);
    }

    // spec 044 FR-041
    @Test
    @DisplayName("FR-041: chat.soporte suma al líder de mentores; el mentor sigue sin estar")
    void soporteConElLider() {
        assertThat(catalogo.definicion("chat.soporte").orElseThrow().rolesQueLaUsan()).contains(UserRole.MENTOR_LEAD)
                .doesNotContain(UserRole.MENTOR);
    }
}
