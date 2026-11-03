package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.accountrequest.ReglasDeIngreso.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas de aceptar a alguien en una formación (spec 025, FR-002, FR-003, FR-013): lógica pura, sin Spring. */
class ReglasDeIngresoTest {

    private static Map<Formacion, FormacionConfigurada> catalogo(boolean diplomadoActivo) {
        Map<Formacion, FormacionConfigurada> catalogo = new EnumMap<>(Formacion.class);
        catalogo.put(Formacion.ESCUELA, new FormacionConfigurada(Formacion.ESCUELA, "Escuela Renaser", "", true, List.of(90), 1));
        catalogo.put(Formacion.DIPLOMADO, new FormacionConfigurada(Formacion.DIPLOMADO, "Diplomado Renaser", "", diplomadoActivo, List.of(90, 365), 1));
        catalogo.put(Formacion.CERTIFICACION, new FormacionConfigurada(Formacion.CERTIFICACION, "Certificación Renaser", "", true, List.of(90), 1));
        return catalogo;
    }

    private static PedidoDeIngreso pedido(Integer duracion, boolean conDiagnostico, Formacion... formaciones) {
        return new PedidoDeIngreso(Set.of(formaciones), duracion, conDiagnostico);
    }

    @Test
    @DisplayName("un aprendiz del Diplomado elige 1 año: la inscripción es del Diplomado a 365 días y la foto lo recuerda")
    void aprendizDelDiplomadoDeUnAnio() {
        Resultado r = ReglasDeIngreso.decidir(UserRole.TRAINEE, pedido(365, true, Formacion.DIPLOMADO), catalogo(true));

        assertThat(r.formacionDelPrograma()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(r.duracionDelProgramaDias()).isEqualTo(365);
        assertThat(r.eleccion().formaciones()).containsExactly(Formacion.DIPLOMADO);
        assertThat(r.eleccion().duracionElegidaDias()).isEqualTo(365);
        assertThat(r.eleccion().conDiagnostico()).isTrue();
        assertThat(r.eleccion().rol()).isEqualTo(UserRole.TRAINEE);
    }

    @Test
    @DisplayName("una formación con una sola duración no la pregunta: se aplica sola (Escuela y Certificación, 90 días)")
    void conUnaSolaDuracionNoSePregunta() {
        Resultado escuela = ReglasDeIngreso.decidir(UserRole.TRAINEE, pedido(null, false, Formacion.ESCUELA), catalogo(true));
        Resultado certificacion = ReglasDeIngreso.decidir(UserRole.TRAINEE, pedido(null, false, Formacion.CERTIFICACION), catalogo(true));

        assertThat(escuela.duracionDelProgramaDias()).isEqualTo(90);
        assertThat(certificacion.duracionDelProgramaDias()).isEqualTo(90);
        assertThat(certificacion.formacionDelPrograma()).isEqualTo(Formacion.CERTIFICACION);
    }

    @Test
    @DisplayName("en el Diplomado hay que elegir la duración, y la elegida tiene que ser una de las que ofrece")
    void elDiplomadoPideLaDuracion() {
        assertThatThrownBy(() -> ReglasDeIngreso.decidir(UserRole.TRAINEE, pedido(null, false, Formacion.DIPLOMADO), catalogo(true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Elige la duración");
        assertThatThrownBy(() -> ReglasDeIngreso.decidir(UserRole.TRAINEE, pedido(120, false, Formacion.DIPLOMADO), catalogo(true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no ofrece esa duración");
        assertThatThrownBy(() -> ReglasDeIngreso.decidir(UserRole.TRAINEE, pedido(365, false, Formacion.ESCUELA), catalogo(true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no ofrece esa duración");
    }

    @Test
    @DisplayName("un aprendiz entra a una sola formación")
    void elAprendizEntraAUna() {
        assertThatThrownBy(() -> ReglasDeIngreso.decidir(UserRole.TRAINEE, pedido(null, false, Formacion.ESCUELA, Formacion.CERTIFICACION), catalogo(true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("una sola formación");
    }

    @Test
    @DisplayName("el personal entra a una o varias formaciones; su programa propio es el de la primera, con la duración más corta, y no elige duración")
    void elPersonalEntraAVarias() {
        for (UserRole rol : List.of(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD)) {
            Resultado r = ReglasDeIngreso.decidir(rol, pedido(365, false, Formacion.CERTIFICACION, Formacion.DIPLOMADO), catalogo(true));

            assertThat(r.eleccion().formaciones()).as("%s", rol).containsExactly(Formacion.DIPLOMADO, Formacion.CERTIFICACION);
            assertThat(r.formacionDelPrograma()).isEqualTo(Formacion.DIPLOMADO);
            assertThat(r.duracionDelProgramaDias()).as("la más corta, aunque pidan 365").isEqualTo(90);
            assertThat(r.eleccion().duracionElegidaDias()).isNull();
        }
    }

    @Test
    @DisplayName("Administrador y Alquimista no eligen formación (ven las tres): se ignora lo que manden y su programa propio es el de la Escuela")
    void administracionNoEligeFormacion() {
        for (UserRole rol : List.of(UserRole.ADMIN, UserRole.ALCHEMIST)) {
            Resultado r = ReglasDeIngreso.decidir(rol, pedido(365, false, Formacion.DIPLOMADO), catalogo(true));

            assertThat(r.eleccion().formaciones()).as("%s", rol).isEmpty();
            assertThat(r.formacionDelPrograma()).isEqualTo(Formacion.ESCUELA);
            assertThat(r.duracionDelProgramaDias()).isEqualTo(90);
            assertThat(r.eleccion().duracionElegidaDias()).isNull();
        }
    }

    @Test
    @DisplayName("«con diagnóstico» vale para cualquier rol (decisión 025-1 B)")
    void elDiagnosticoVaParaCualquierRol() {
        for (UserRole rol : List.of(UserRole.TRAINEE, UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST)) {
            PedidoDeIngreso pedido = pedido(null, true, Formacion.ESCUELA);
            assertThat(ReglasDeIngreso.decidir(rol, pedido, catalogo(true)).eleccion().conDiagnostico()).as("%s", rol).isTrue();
        }
    }

    @Test
    @DisplayName("una formación desactivada no recibe personas nuevas")
    void laFormacionDesactivadaNoRecibe() {
        assertThatThrownBy(() -> ReglasDeIngreso.decidir(UserRole.TRAINEE, pedido(90, false, Formacion.DIPLOMADO), catalogo(false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("desactivada");
        assertThatThrownBy(() -> ReglasDeIngreso.decidir(UserRole.MENTOR, pedido(null, false, Formacion.ESCUELA, Formacion.DIPLOMADO), catalogo(false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("desactivada");
    }

    @Test
    @DisplayName("FR-013: una app anterior (solo manda el rol) entra a la Escuela con su duración y sin diagnóstico, aunque el catálogo haya cambiado")
    void unaAppAnteriorEntraALaEscuela() {
        Map<Formacion, FormacionConfigurada> conEscuelaApagada = catalogo(true);
        conEscuelaApagada.put(Formacion.ESCUELA, new FormacionConfigurada(Formacion.ESCUELA, "Escuela Renaser", "", false, List.of(90), 1));

        Resultado aprendiz = ReglasDeIngreso.decidir(UserRole.TRAINEE, PedidoDeIngreso.deSiempre(), conEscuelaApagada);
        Resultado mentor = ReglasDeIngreso.decidir(UserRole.MENTOR, PedidoDeIngreso.deSiempre(), catalogo(true));

        assertThat(aprendiz.formacionDelPrograma()).isEqualTo(Formacion.ESCUELA);
        assertThat(aprendiz.duracionDelProgramaDias()).isEqualTo(90);
        assertThat(aprendiz.eleccion().conDiagnostico()).isFalse();
        assertThat(aprendiz.eleccion().duracionElegidaDias()).isNull();
        assertThat(mentor.eleccion().formaciones()).containsExactly(Formacion.ESCUELA);
    }
}
