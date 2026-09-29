package com.renaser.os.chat.domain.model.conversacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-221: los tres nombres que pidió el dueño el 29/09. */
class NombreDelChatTest {

    @Test
    @DisplayName("el grupo con mentor: «<primer nombre del mentor> y sus aprendices», con la regla de D-173")
    void grupoConMentor() {
        assertThat(NombreDelChat.deGrupo("Luisa Fernanda Quispe", "Fenix")).isEqualTo("Luisa y sus aprendices");
        assertThat(NombreDelChat.deGrupo("  maría josé ñahui ", "Fenix")).isEqualTo("María y sus aprendices");
    }

    @Test
    @DisplayName("sin mentor (o sin nombre legible) cae al nombre del grupo; sin nada, null y la app pone el suyo")
    void grupoSinMentor() {
        assertThat(NombreDelChat.deGrupo(null, "Fenix")).isEqualTo("Fenix");
        assertThat(NombreDelChat.deGrupo("   ", " Fenix ")).isEqualTo("Fenix");
        assertThat(NombreDelChat.deGrupo(null, " ")).isNull();
    }

    @Test
    @DisplayName("el soporte: «<primer nombre> – Formación Renaser»; sin nombre, «Formación Renaser»")
    void soporte() {
        assertThat(NombreDelChat.deSoporte("Pedro Castillo")).isEqualTo("Pedro – Formación Renaser");
        assertThat(NombreDelChat.deSoporte(null)).isEqualTo("Formación Renaser");
    }

    @Test
    @DisplayName("la comunidad nace llamándose «Formación Renaser Global»")
    void comunidad() {
        assertThat(Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), Instant.EPOCH).nombre())
                .isEqualTo("Formación Renaser Global");
    }
}
