package com.renaser.os.plataforma.domain.model.catalogo;

import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La tabla «Catálogo inicial de funcionalidades» de la spec 002, verificada clave por clave. Sin Spring. */
class CatalogoDeFuncionalidadesTest {

    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();

    /** Las 52 claves de la tabla de la spec 002 y las que se sumaron después, en su orden. Una clave nueva se suma acá y en el catálogo. */
    private static final List<String> CLAVES_DE_LA_SPEC = List.of("registro", "onboarding", "dia_uno", "radar",
            "mapa", "plan", "habitos", "habitos.foto", "habitos.km", "habitos.gimnasio", "habitos.espiritu",
            "habitos.ajustes", "clase_diaria", "muro", "muro.publicar", "muro.comentarios", "muro.reacciones", "chat",
            "chat.grupos", "chat.global", "chat.soporte", "chat.directos", "chat.audio", "chat.fotos", "eventos",
            "eventos.gestion", "eventos.asistencia", "cursos", "ranking", "ranking.podio_semanal", "semaforo", "ia",
            "ia.chat", "ia.voz", "ia.propuestas", "evidencias", "caja", "emergencia", "cuenta.eliminar",
            "notificaciones", "mentor.mi_grupo", "lider", "lider.cumplimiento", "tickets", "admin", "admin.solicitudes", "admin.equipo",
            "admin.grupos", "admin.personas", "admin.habitos", "admin.bienvenida", "admin.fases", "perfil");

    @Test
    @DisplayName("tiene todas las claves de la tabla de la spec, ni una más ni una menos")
    void tieneTodasLasClavesDeLaSpec() {
        assertThat(catalogo.todas()).extracting(DefinicionDeFuncionalidad::clave)
                .containsExactlyInAnyOrderElementsOf(CLAVES_DE_LA_SPEC);
    }

    @Test
    @DisplayName("las críticas son exactamente las siete del contrato §3")
    void lasCriticasSonLasDelContrato() {
        assertThat(catalogo.todas()).filteredOn(DefinicionDeFuncionalidad::critica)
                .extracting(DefinicionDeFuncionalidad::clave)
                .containsExactlyInAnyOrder("chat", "muro", "ia", "habitos", "registro", "notificaciones", "admin");
    }

    @Test
    @DisplayName("todas nacen prendidas (ninguna está marcada «nace apagada» en la tabla)")
    void todasNacenPrendidas() {
        assertThat(catalogo.todas()).allMatch(f -> f.porDefecto() == ValorPorDefecto.PRENDIDA);
    }

    @Test
    @DisplayName("la política de procesos es la de la columna «Proceso» de la spec")
    void politicaDeProcesosDeLaSpec() {
        Map<String, PoliticaDeProcesos> esperadas = Map.ofEntries(
                Map.entry("plan", PoliticaDeProcesos.SALTA), Map.entry("habitos", PoliticaDeProcesos.SALTA),
                Map.entry("habitos.km", PoliticaDeProcesos.SALTA), Map.entry("muro", PoliticaDeProcesos.SALTA),
                Map.entry("chat", PoliticaDeProcesos.POSPONE), Map.entry("chat.global", PoliticaDeProcesos.POSPONE),
                Map.entry("chat.soporte", PoliticaDeProcesos.SALTA), Map.entry("eventos", PoliticaDeProcesos.SALTA),
                Map.entry("ranking", PoliticaDeProcesos.NO_APLICA),
                Map.entry("ranking.podio_semanal", PoliticaDeProcesos.POSPONE),
                Map.entry("semaforo", PoliticaDeProcesos.POSPONE), Map.entry("ia", PoliticaDeProcesos.SALTA),
                Map.entry("evidencias", PoliticaDeProcesos.POSPONE), Map.entry("caja", PoliticaDeProcesos.SALTA),
                Map.entry("cuenta.eliminar", PoliticaDeProcesos.NO_APLICA),
                Map.entry("notificaciones", PoliticaDeProcesos.POSPONE),
                Map.entry("mentor.mi_grupo", PoliticaDeProcesos.SALTA),
                Map.entry("lider.cumplimiento", PoliticaDeProcesos.SALTA),
                Map.entry("admin.grupos", PoliticaDeProcesos.SALTA),
                Map.entry("admin.bienvenida", PoliticaDeProcesos.SALTA));
        esperadas.forEach((clave, politica) ->
                assertThat(catalogo.definicion(clave).orElseThrow().procesos()).as(clave).isEqualTo(politica));
    }

    @Test
    @DisplayName("la cadena va de la raíz a la clave; la madre sale de la clave")
    void cadenaDeLaRaizALaClave() {
        assertThat(catalogo.cadena("chat.grupos")).extracting(DefinicionDeFuncionalidad::clave)
                .containsExactly("chat", "chat.grupos");
        assertThat(catalogo.definicion("chat.grupos").orElseThrow().madre()).isEqualTo("chat");
        assertThat(catalogo.definicion("chat").orElseThrow().madre()).isNull();
        assertThat(catalogo.cadena("no.existe")).isEmpty();
        assertThat(catalogo.definicion("cuenta.eliminar").orElseThrow().madre())
                .as("«cuenta» no es una funcionalidad: cuenta.eliminar es raíz").isNull();
        assertThat(catalogo.definicion("mentor.mi_grupo").orElseThrow().madre()).isNull();
    }

    @Test
    @DisplayName("lo anti-bloqueo no está en el catálogo: no tiene interruptor (FR-004)")
    void loAntiBloqueoNoEstaEnElCatalogo() {
        for (String clave : List.of("login", "sesion", "funcionalidades", "soporte_ti", "consola", "health")) {
            assertThat(catalogo.existe(clave)).as(clave).isFalse();
        }
    }

    @Test
    @DisplayName("«Mi grupo» es de quien acompaña grupos; Soporte TI figura en las demás (FR-023)")
    void rolesQueLaUsan() {
        assertThat(catalogo.definicion("mentor.mi_grupo").orElseThrow().rolesQueLaUsan())
                .containsExactlyInAnyOrder(UserRole.MENTOR, UserRole.TRAINEE_GUIDE);
        assertThat(catalogo.definicion("registro").orElseThrow().rolesQueLaUsan()).isEmpty();
        assertThat(catalogo.todas()).filteredOn(f -> !f.clave().equals("registro")
                        && !f.clave().equals("mentor.mi_grupo"))
                .allMatch(f -> f.rolesQueLaUsan().contains(UserRole.IT_SUPPORT));
    }

    @Test
    @DisplayName("un catálogo a medida rechaza claves repetidas y una hija sin madre")
    void catalogoAMedidaSeValida() {
        DefinicionDeFuncionalidad hija = definicion("a.b");
        assertThatThrownBy(() -> CatalogoDeFuncionalidades.de(List.of(hija)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogoDeFuncionalidades.de(List.of(definicion("a"), definicion("a"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> definicion("Mal Clave")).isInstanceOf(IllegalArgumentException.class);
    }

    static DefinicionDeFuncionalidad definicion(String clave) {
        String madre = clave.contains(".") ? clave.substring(0, clave.lastIndexOf('.')) : null;
        return new DefinicionDeFuncionalidad(clave, madre, "Área", clave, "Prueba", java.util.Set.of(), false,
                ValorPorDefecto.PRENDIDA, PoliticaDeProcesos.NO_APLICA);
    }
}
