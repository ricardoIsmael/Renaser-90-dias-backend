package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.ConsultaResumenPorGrupos;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.GrupoDelResumen;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.ResumenPorGrupos;
import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.DESDE_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.HASTA_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.SEMANA_CERRADA;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventana;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El resumen por grupos (§4.4): lo que ve el líder, sin un solo nombre de aprendiz. */
class SemaforoPorGruposServiceTest {

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final UserId LUISA = id("a1");
    private static final UserId RAUL = id("a2");
    private static final UserId LIDER = id("c1");
    private static final UserId ANA = id("b1");
    private static final UserId LUIS = id("b2");
    private static final UserId MARIO = id("b3");
    private static final UserId PIA = id("b4");

    private final BancoDelSemaforo banco = new BancoDelSemaforo();
    private SemaforoPorGruposService servicio;

    @BeforeEach
    void preparar() {
        servicio = banco.servicioPorGrupos();
        banco.usuario(LIDER, "Lider", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        banco.grupo(FENIX, "Grupo Fénix");
        banco.mentor(FENIX, LUISA);
        banco.usuario(LUISA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        aprendiz(FENIX, ANA, "Ana Pérez", 90);
        aprendiz(FENIX, LUIS, "Luis Díaz", 50);
        aprendiz(FENIX, MARIO, "Mario Paz", null);

        banco.grupo(AURORA, "Grupo Aurora");
        banco.mentor(AURORA, RAUL);
        banco.usuario(RAUL, "Raúl Soto", UserRole.MENTOR, UserStatus.ACTIVE);
        aprendiz(AURORA, PIA, "Pia Luna", 70);
    }

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private void aprendiz(UUID grupo, UserId id, String nombre, Integer porcentajeParejo) {
        banco.aprendiz(grupo, id);
        banco.persona(id, nombre);
        if (porcentajeParejo != null) {
            banco.vigente(id, ventanaPareja(DESDE_VIGENTE, false, porcentajeParejo));
        }
    }

    private ResumenPorGrupos resumenDelLider() {
        return servicio.resumenDe(new ConsultaResumenPorGrupos(LIDER, null));
    }

    private static GrupoDelResumen grupo(ResumenPorGrupos resumen, UUID grupoId) {
        return resumen.grupos().stream().filter(g -> g.grupoId().equals(grupoId)).findFirst().orElseThrow();
    }

    // ── padrón: solo cuentas activas ────────────────────────────────────────

    @Test
    @DisplayName("un aprendiz suspendido no suma en su grupo ni en los totales, ni entra al promedio")
    void suspendidoFueraDelResumen() {
        banco.usuario(LUIS, "Luis Díaz", UserRole.TRAINEE, UserStatus.SUSPENDED);

        ResumenPorGrupos resumen = resumenDelLider();

        // Fénix queda con Ana (90) y Mario (sin datos): 90 de promedio, no (90 + 50) / 2.
        assertThat(grupo(resumen, FENIX).resumen().conteo()).isEqualTo(new ConteoPorColor(1, 0, 0, 1));
        assertThat(grupo(resumen, FENIX).resumen().promedio()).isEqualByComparingTo("90.0");
        assertThat(resumen.totales().total()).isEqualTo(3);
        assertThat(banco.lecturasDelSemaforo.getFirst()).doesNotContain(LUIS);
    }

    // ── autorización ────────────────────────────────────────────────────────

    @Test
    @DisplayName("MENTOR y TRAINEE no ven el resumen: el permiso USE_APP no alcanza, decide el guard")
    void soloLiderazgo() {
        UserId aprendiz = id("b8");
        banco.usuario(aprendiz, "Aprendiz", UserRole.TRAINEE, UserStatus.ACTIVE);

        for (UserId sinPermiso : List.of(LUISA, aprendiz)) {
            assertThatThrownBy(() -> servicio.resumenDe(new ConsultaResumenPorGrupos(sinPermiso, null)))
                    .isInstanceOf(NotAuthorizedException.class);
        }
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    @Test
    @DisplayName("un lider suspendido recibe 403: el interceptor lo deja pasar en modo sombra")
    void liderSuspendido() {
        banco.usuario(LIDER, "Lider", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        assertThatThrownBy(this::resumenDelLider).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("lider, admin y alquimista activos lo ven")
    void liderazgoActivo() {
        UserId admin = id("d1");
        UserId alquimista = id("d2");
        banco.usuario(admin, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(alquimista, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);

        for (UserId actor : List.of(LIDER, admin, alquimista)) {
            assertThat(servicio.resumenDe(new ConsultaResumenPorGrupos(actor, null)).grupos()).hasSize(2);
        }
    }

    // ── armado ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cada grupo trae su conteo por color y los totales son la suma de los grupos")
    void conteosYTotales() {
        ResumenPorGrupos resumen = resumenDelLider();

        assertThat(grupo(resumen, FENIX).resumen().conteo()).isEqualTo(new ConteoPorColor(1, 0, 1, 1));
        assertThat(grupo(resumen, AURORA).resumen().conteo()).isEqualTo(new ConteoPorColor(0, 1, 0, 0));
        assertThat(resumen.totales()).isEqualTo(new ConteoPorColor(1, 1, 1, 1));
        assertThat(resumen.totales().total()).isEqualTo(4);
    }

    @Test
    @DisplayName("el promedio es el de los aprendices CON datos; sin ninguno es null, no cero")
    void promedioConYSinDatos() {
        UUID sinDatos = UUID.fromString("00000000-0000-0000-0000-00000000f003");
        UserId mentorSinDatos = id("a4");
        banco.grupo(sinDatos, "Grupo Brisa");
        banco.mentor(sinDatos, mentorSinDatos);
        aprendiz(sinDatos, id("b5"), "Nadie Medido", null);

        ResumenPorGrupos resumen = resumenDelLider();

        // (90 + 50) / 2 = 70.0: Mario, sin datos, no entra al promedio (tampoco como cero).
        assertThat(grupo(resumen, FENIX).resumen().promedio()).isEqualByComparingTo("70.0");
        assertThat(grupo(resumen, sinDatos).resumen().promedio()).isNull();
        assertThat(grupo(resumen, sinDatos).resumen().conteo()).isEqualTo(new ConteoPorColor(0, 0, 0, 1));
    }

    @Test
    @DisplayName("no devuelve nombres de aprendices; sus cuentas se leen una sola vez, solo para saber quien esta activo")
    void sinNombresDeAprendices() {
        ResumenPorGrupos resumen = resumenDelLider();

        // Una lectura para las cuentas de TODOS los aprendices (deja fuera a quien no esta activo) y
        // otra para los nombres de los mentores. Nunca una por grupo ni por persona.
        assertThat(banco.nombresPedidos).hasSize(2);
        assertThat(banco.nombresPedidos.getFirst()).containsExactlyInAnyOrder(ANA, LUIS, MARIO, PIA);
        assertThat(banco.nombresPedidos.get(1)).containsExactlyInAnyOrder(LUISA, RAUL);
        // Aurora va antes que Fénix: los grupos se ordenan por nombre.
        assertThat(resumen.grupos()).extracting(GrupoDelResumen::mentorNombre)
                .containsExactly("Raúl Soto", "Luisa Rojas");
        assertThat(resumen.toString()).doesNotContain("Ana", "Luis Díaz", "Mario", "Pia");
    }

    @Test
    @DisplayName("el semaforo se lee UNA vez para los aprendices de todos los grupos")
    void unaSolaLecturaDelSemaforo() {
        resumenDelLider();

        assertThat(banco.lecturasDelSemaforo).hasSize(1);
        assertThat(banco.lecturasDelSemaforo.getFirst()).containsExactlyInAnyOrder(ANA, LUIS, MARIO, PIA);
    }

    @Test
    @DisplayName("el mentor que ademas cursa en su grupo no cuenta en el resumen de ese grupo")
    void mentorQueCursaNoCuenta() {
        banco.aprendiz(FENIX, LUISA);
        banco.vigente(LUISA, ventanaPareja(DESDE_VIGENTE, false, 40));

        ResumenPorGrupos resumen = resumenDelLider();

        assertThat(grupo(resumen, FENIX).resumen().conteo().total()).isEqualTo(3);
        assertThat(banco.lecturasDelSemaforo.getFirst()).doesNotContain(LUISA);
    }

    @Test
    @DisplayName("los grupos van por nombre y el encabezado sale de las ventanas")
    void ordenYPeriodo() {
        ResumenPorGrupos resumen = resumenDelLider();

        assertThat(resumen.grupos()).extracting(GrupoDelResumen::grupoNombre)
                .containsExactly("Grupo Aurora", "Grupo Fénix");
        assertThat(resumen.periodo().desde()).isEqualTo(DESDE_VIGENTE);
        assertThat(resumen.periodo().hasta()).isEqualTo(HASTA_VIGENTE);
        assertThat(resumen.periodo().cerrada()).isFalse();
    }

    @Test
    @DisplayName("con semanaHasta lee esa semana cerrada para todos")
    void semanaPedida() {
        banco.semana(SEMANA_CERRADA, ANA, ventana(LocalDate.of(2026, 9, 12), true, 60, 60, 60, 60, 60, 60, 60));

        ResumenPorGrupos resumen = servicio.resumenDe(new ConsultaResumenPorGrupos(LIDER, SEMANA_CERRADA));

        assertThat(banco.semanasPedidas).containsExactly(SEMANA_CERRADA);
        assertThat(resumen.totales()).isEqualTo(new ConteoPorColor(0, 1, 0, 3));
        assertThat(resumen.periodo().cerrada()).isTrue();
        assertThat(Set.copyOf(banco.lecturasDelSemaforo.getFirst())).containsExactlyInAnyOrder(ANA, LUIS, MARIO, PIA);
    }

    @Test
    @DisplayName("semanaHasta tiene que ser viernes")
    void semanaHastaNoViernes() {
        assertThatThrownBy(() -> new ConsultaResumenPorGrupos(LIDER, LocalDate.of(2026, 9, 19)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
