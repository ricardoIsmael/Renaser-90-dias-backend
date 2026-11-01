package com.renaser.os.users.domain.model.formacion;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Quién ve qué formación (spec 022): la regla pura que usan todos los módulos a través de {@code AlcanceDeFormacionFinder}. */
class ReglaDeAlcanceTest {

    @Test
    @DisplayName("Administración, Alquimista y Soporte TI ven las tres formaciones")
    void veTodas() {
        for (UserRole rol : new UserRole[]{UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.IT_SUPPORT}) {
            assertThat(ReglaDeAlcance.alcanceDe(rol, null, List.of())).isEqualTo(AlcanceDeFormacion.todas());
            assertThat(ReglaDeAlcance.veTodas(rol)).isTrue();
        }
    }

    @Test
    @DisplayName("Un aprendiz ve solo la suya, y sin participación cae en la Escuela")
    void elAprendizVeLaSuya() {
        assertThat(ReglaDeAlcance.alcanceDe(UserRole.TRAINEE, Formacion.DIPLOMADO, List.of()).formaciones()).containsExactly(Formacion.DIPLOMADO);
        assertThat(ReglaDeAlcance.alcanceDe(UserRole.TRAINEE, null, List.of()).formaciones()).containsExactly(Formacion.ESCUELA);
    }

    @Test
    @DisplayName("El personal con dos formaciones ve solo la activa")
    void elPersonalVeLaActiva() {
        List<Asignacion> dos = List.of(new Asignacion(Formacion.ESCUELA, false), new Asignacion(Formacion.DIPLOMADO, true));
        for (UserRole rol : new UserRole[]{UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD}) {
            assertThat(ReglaDeAlcance.alcanceDe(rol, null, dos).formaciones()).containsExactly(Formacion.DIPLOMADO);
            assertThat(ReglaDeAlcance.esPersonalConFormaciones(rol)).isTrue();
        }
    }

    @Test
    @DisplayName("Sin ninguna marcada como activa vale la primera en el orden del catálogo; sin asignación no ve nada")
    void sinActivaYSinAsignacion() {
        List<Asignacion> sinMarcar = List.of(new Asignacion(Formacion.CERTIFICACION, false), new Asignacion(Formacion.DIPLOMADO, false));
        assertThat(ReglaDeAlcance.formacionActiva(sinMarcar)).contains(Formacion.DIPLOMADO);
        assertThat(ReglaDeAlcance.alcanceDe(UserRole.MENTOR, null, List.of()).esVacio()).isTrue();
        assertThat(ReglaDeAlcance.formacionActiva(List.of())).isEmpty();
    }

    @Test
    @DisplayName("El aprendiz, Administración y Soporte TI no son «personal con formaciones asignadas»")
    void noEsPersonal() {
        assertThat(ReglaDeAlcance.esPersonalConFormaciones(UserRole.TRAINEE)).isFalse();
        assertThat(ReglaDeAlcance.esPersonalConFormaciones(UserRole.ADMIN)).isFalse();
        assertThat(ReglaDeAlcance.veTodas(UserRole.MENTOR)).isFalse();
    }
}
