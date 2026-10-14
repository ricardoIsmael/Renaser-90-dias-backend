package com.renaser.os.plataforma.domain.model.roles;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.catalogo.PoliticaDeProcesos;
import com.renaser.os.plataforma.domain.model.catalogo.ValorPorDefecto;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** La regla del rol personalizado (contrato de la fase 2, §4.4; D-269), sobre el catálogo real. Sin Spring. */
class DisponibilidadPorRolPersonalizadoTest {

    private static final CatalogoDeFuncionalidades CATALOGO = CatalogoDeFuncionalidades.inicial();

    /** «Mentor junior»: todo lo que MENTOR usa menos {@code eventos} (y por eso sus hijas). */
    private static RolEnMemoria mentorJuniorSinEventos() {
        Set<String> marcadas = new java.util.HashSet<>();
        for (DefinicionDeFuncionalidad definicion : CATALOGO.todas()) {
            if (definicion.rolesQueLaUsan().contains(UserRole.MENTOR) && !definicion.clave().startsWith("eventos")) {
                marcadas.add(definicion.clave());
            }
        }
        return new RolEnMemoria(UserRole.MENTOR, marcadas);
    }

    private static List<String> cadena(String clave) {
        return CATALOGO.cadena(clave).stream().map(DefinicionDeFuncionalidad::clave).toList();
    }

    @Test
    @DisplayName("lo marcado le corresponde; lo que la base usa y no está marcado queda no disponible en esa clave")
    void marcadoYNoMarcado() {
        RolEnMemoria rol = mentorJuniorSinEventos();

        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("muro.publicar"), rol, CATALOGO))
                .isEmpty();
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("eventos"), rol, CATALOGO))
                .contains("eventos");
    }

    @Test
    @DisplayName("con la madre sin marcar quedan fuera las hijas, aunque la base no use la hija (eventos.asistencia)")
    void laMadreSinMarcarDejaFueraALasHijas() {
        RolEnMemoria rol = mentorJuniorSinEventos();

        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("eventos.asistencia"), rol, CATALOGO))
                .contains("eventos.asistencia");
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("eventos.gestion"), rol, CATALOGO))
                .contains("eventos.gestion");
    }

    @Test
    @DisplayName("marcar una madre cubre a sus hijas, también las que la base no usa (chat cubre chat.soporte)")
    void laMadreCubreALasHijas() {
        RolEnMemoria soloChat = new RolEnMemoria(UserRole.MENTOR, Set.of("chat"));

        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("chat.audio"), soloChat, CATALOGO))
                .isEmpty();
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("chat.soporte"), soloChat, CATALOGO))
                .isEmpty();
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("muro"), soloChat, CATALOGO))
                .contains("muro");
    }

    @Test
    @DisplayName("marcar una hija sola no da la madre: la hija sí, lo declarado en la madre no")
    void laHijaNoDaLaMadre() {
        RolEnMemoria soloGrupos = new RolEnMemoria(UserRole.MENTOR, Set.of("chat.grupos"));

        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("chat.grupos"), soloGrupos, CATALOGO))
                .isEmpty();
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("chat"), soloGrupos, CATALOGO))
                .contains("chat");
    }

    @Test
    @DisplayName("lo que la base no usa no lo decide el rol personalizado: caja y registro siguen como para MENTOR")
    void loQueLaBaseNoUsa() {
        RolEnMemoria nada = new RolEnMemoria(UserRole.MENTOR, Set.of());

        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("caja"), nada, CATALOGO)).isEmpty();
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("registro"), nada, CATALOGO)).isEmpty();
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("admin.equipo"), nada, CATALOGO))
                .isEmpty();
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena("perfil"), nada, CATALOGO))
                .as("una lista vacía deja fuera todo lo que la base usa").contains("perfil");
    }

    @Test
    @DisplayName("con TODO lo de la base marcado, nada queda fuera: el rol personalizado es igual a su base")
    void todoMarcadoEsLaBase() {
        for (UserRole base : List.of(UserRole.TRAINEE, UserRole.TRAINEE_GUIDE, UserRole.MENTOR, UserRole.MENTOR_LEAD,
                UserRole.ADMIN, UserRole.ALCHEMIST)) {
            Set<String> todas = new java.util.HashSet<>();
            CATALOGO.todas().stream().filter(d -> d.rolesQueLaUsan().contains(base))
                    .forEach(d -> todas.add(d.clave()));
            RolEnMemoria rol = new RolEnMemoria(base, todas);

            for (DefinicionDeFuncionalidad definicion : CATALOGO.todas()) {
                assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena(definicion.clave()), rol,
                        CATALOGO)).as("%s con base %s", definicion.clave(), base).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("una funcionalidad nueva del catálogo nace sin marcar: no disponible para el rol personalizado")
    void unaFuncionalidadNuevaNaceSinMarcar() {
        List<DefinicionDeFuncionalidad> conUnaNueva = new ArrayList<>(CATALOGO.todas());
        conUnaNueva.add(new DefinicionDeFuncionalidad("diario", null, "Programa", "Diario", "El diario nuevo.",
                Set.of(UserRole.MENTOR, UserRole.TRAINEE), false, ValorPorDefecto.PRENDIDA,
                PoliticaDeProcesos.NO_APLICA));
        CatalogoDeFuncionalidades catalogoNuevo = CatalogoDeFuncionalidades.de(conUnaNueva);

        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(List.of("diario"), mentorJuniorSinEventos(),
                catalogoNuevo)).contains("diario");
    }

    @Test
    @DisplayName("una cadena vacía (clave fuera del catálogo) no es asunto de la regla")
    void cadenaVacia() {
        assertThat(DisponibilidadPorRolPersonalizado.noDisponibleEn(List.of(), mentorJuniorSinEventos(), CATALOGO))
                .isEmpty();
    }
}
