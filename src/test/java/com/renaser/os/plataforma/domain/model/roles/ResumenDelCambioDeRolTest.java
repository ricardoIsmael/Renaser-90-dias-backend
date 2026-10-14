package com.renaser.os.plataforma.domain.model.roles;

import com.renaser.os.plataforma.domain.model.roles.ResumenDelCambioDeRol.Foto;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Qué cambió de un rol con personas, para el push a Administración (D-269). Sin Spring. */
class ResumenDelCambioDeRolTest {

    private static final Foto ACTIVO = new Foto(UserRole.MENTOR, true, Set.of("muro", "chat"));

    @Test
    @DisplayName("desactivar, reactivar, cambiar funcionalidades o base; varias cosas a la vez van juntas")
    void cambios() {
        assertThat(ResumenDelCambioDeRol.deUnaEdicion(ACTIVO, new Foto(UserRole.MENTOR, false, Set.of("muro", "chat")),
                2)).contains("lo desactivó: quedan solo con su rol base (Mentor)");
        assertThat(ResumenDelCambioDeRol.deUnaEdicion(new Foto(UserRole.MENTOR, false, Set.of()),
                new Foto(UserRole.MENTOR, true, Set.of()), 2)).contains("lo volvió a activar");
        assertThat(ResumenDelCambioDeRol.deUnaEdicion(ACTIVO, new Foto(UserRole.MENTOR, true, Set.of("muro", "ranking",
                "cursos")), 1)).contains("sumó 2 funcionalidades y quitó 1 funcionalidad");
        assertThat(ResumenDelCambioDeRol.deUnaEdicion(ACTIVO, new Foto(UserRole.TRAINEE_GUIDE, false, Set.of("muro")),
                1)).contains("lo desactivó: quedan solo con su rol base (Guía de aprendiz); ahora hereda de Guía de "
                + "aprendiz; quitó 1 funcionalidad");
    }

    @Test
    @DisplayName("sin personas, o sin cambios que les importen (nombre, descripción), no hay aviso")
    void sinAviso() {
        assertThat(ResumenDelCambioDeRol.deUnaEdicion(ACTIVO, new Foto(UserRole.MENTOR, false, Set.of()), 0)).isEmpty();
        assertThat(ResumenDelCambioDeRol.deUnaEdicion(ACTIVO, ACTIVO, 5)).isEmpty();
        assertThat(ResumenDelCambioDeRol.deUnReemplazo("Mentor inicial", UserRole.MENTOR, 0)).isEmpty();
    }

    @Test
    @DisplayName("un reemplazo dice a dónde pasaron, o que quedaron con su rol base")
    void reemplazo() {
        assertThat(ResumenDelCambioDeRol.deUnReemplazo("Mentor inicial", UserRole.MENTOR, 3))
                .contains("las pasó a «Mentor inicial»");
        assertThat(ResumenDelCambioDeRol.deUnReemplazo(null, UserRole.MENTOR, 3))
                .contains("quedaron solo con su rol base (Mentor)");
    }
}
