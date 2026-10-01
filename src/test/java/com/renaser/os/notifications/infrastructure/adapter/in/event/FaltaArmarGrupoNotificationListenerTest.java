package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.community.api.FaltaArmarGrupoEvent;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A quién le llega cada aviso de armado de grupos (D-240). */
class FaltaArmarGrupoNotificationListenerTest {

    @Test
    @DisplayName("sin grupo en curso: administrador, alquimista y lider de mentores")
    void sinGrupoEnCurso() {
        assertThat(FaltaArmarGrupoNotificationListener.rolesPara(FaltaArmarGrupoEvent.Motivo.SIN_GRUPO_EN_CURSO))
                .containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.MENTOR_LEAD);
    }

    @Test
    @DisplayName("grupo sin mentor: solo el lider de mentores")
    void grupoSinMentor() {
        assertThat(FaltaArmarGrupoNotificationListener.rolesPara(FaltaArmarGrupoEvent.Motivo.GRUPO_SIN_MENTOR))
                .containsExactly(UserRole.MENTOR_LEAD);
    }
}
