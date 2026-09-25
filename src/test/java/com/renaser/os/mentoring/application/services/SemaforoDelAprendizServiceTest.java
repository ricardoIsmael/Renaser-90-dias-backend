package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelAprendizAdministrativoUseCase.ConsultaDetalleAdministrativa;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelAprendizUseCase.ConsultaDetalleDelAprendiz;
import com.renaser.os.mentoring.application.services.BancoDelSemaforo.DetallePedido;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.DESDE_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.LIMA;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El detalle del semáforo de una persona (§4.1), por la puerta del mentor y por la administrativa. */
class SemaforoDelAprendizServiceTest {

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final UserId MENTORA = id("a1");
    private static final UserId GUIA = id("a3");
    private static final UserId ADMIN = id("d1");
    private static final UserId ANA = id("b1");
    private static final UserId DE_AURORA = id("b9");

    private final BancoDelSemaforo banco = new BancoDelSemaforo();
    private SemaforoDelAprendizService servicio;
    private DetalleDelSemaforo detalleDeAna;

    @BeforeEach
    void preparar() {
        servicio = banco.servicioDeDetalle();
        banco.grupo(FENIX, "Grupo Fénix");
        banco.grupo(AURORA, "Grupo Aurora");
        banco.mentor(FENIX, MENTORA);
        banco.usuario(MENTORA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.aprendiz(FENIX, ANA);
        banco.persona(ANA, "Ana Pérez");
        banco.aprendiz(AURORA, DE_AURORA);
        banco.persona(DE_AURORA, "Pia Luna");

        detalleDeAna = new DetalleDelSemaforo(true, true, LIMA, null,
                ventanaPareja(DESDE_VIGENTE, false, 75), List.of(), Instant.parse("2026-09-25T05:25:03Z"));
        banco.detalle(ANA, detalleDeAna);
    }

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    // ── mentor ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("el mentor ve el detalle de un aprendiz de su grupo: el de points, tal cual")
    void mentorVeElDetalle() {
        DetalleDelSemaforo detalle = servicio.detalleDe(new ConsultaDetalleDelAprendiz(MENTORA, FENIX, ANA.value(), 8));

        assertThat(detalle).isSameAs(detalleDeAna);
        assertThat(banco.detallesPedidos).containsExactly(new DetallePedido(ANA, 8));
    }

    @Test
    @DisplayName("semanas se acota a 1..13 antes de llegar al semaforo")
    void semanasAcotadas() {
        servicio.detalleDe(new ConsultaDetalleDelAprendiz(MENTORA, FENIX, ANA.value(), 50));
        servicio.detalleDe(new ConsultaDetalleDelAprendiz(MENTORA, FENIX, ANA.value(), 0));

        assertThat(banco.detallesPedidos).extracting(DetallePedido::semanas).containsExactly(13, 1);
        assertThat(new ConsultaDetalleAdministrativa(ADMIN, ANA.value(), -3).semanas()).isEqualTo(1);
    }

    @Test
    @DisplayName("un aprendiz de OTRO grupo no se ve pasando el id del grupo propio (V12)")
    void aprendizDeOtroGrupo() {
        assertThatThrownBy(() -> servicio.detalleDe(
                new ConsultaDetalleDelAprendiz(MENTORA, FENIX, DE_AURORA.value(), 8)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(banco.detallesPedidos).isEmpty();
    }

    @Test
    @DisplayName("un grupo que no acompaña no devuelve el detalle, aunque el aprendiz sea de ese grupo")
    void grupoQueNoAcompana() {
        assertThatThrownBy(() -> servicio.detalleDe(
                new ConsultaDetalleDelAprendiz(MENTORA, AURORA, DE_AURORA.value(), 8)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un exmentor ya no ve el detalle de sus exalumnos")
    void exmentor() {
        UserId exmentor = id("a2");
        banco.usuario(exmentor, "Exmentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.exmentor(AURORA, exmentor, Instant.parse("2026-09-20T05:00:00Z"));

        assertThatThrownBy(() -> servicio.detalleDe(
                new ConsultaDetalleDelAprendiz(exmentor, AURORA, DE_AURORA.value(), 8)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("el mentor que ademas cursa no es 'aprendiz del grupo' para quien lo acompaña con el")
    void mentorQueCursaNoEsAprendizDelGrupo() {
        banco.guia(FENIX, GUIA);
        banco.usuario(GUIA, "Guia", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.aprendiz(FENIX, MENTORA);

        assertThatThrownBy(() -> servicio.detalleDe(
                new ConsultaDetalleDelAprendiz(GUIA, FENIX, MENTORA.value(), 8)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── administración ──────────────────────────────────────────────────────

    @Test
    @DisplayName("el administrador ve el detalle de cualquier persona, sin relacion con su grupo")
    void administradorVeCualquiera() {
        DetalleDelSemaforo detalle = servicio.detalleDe(new ConsultaDetalleAdministrativa(ADMIN, ANA.value(), 8));

        assertThat(detalle).isSameAs(detalleDeAna);
    }

    @Test
    @DisplayName("un alquimista tambien; un mentor, un lider o un admin suspendido no")
    void soloAdministracionActiva() {
        UserId alquimista = id("d2");
        UserId lider = id("c1");
        UserId suspendido = id("d3");
        banco.usuario(alquimista, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.usuario(lider, "Lider", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(suspendido, "Suspendido", UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThat(servicio.detalleDe(new ConsultaDetalleAdministrativa(alquimista, ANA.value(), 8)))
                .isSameAs(detalleDeAna);
        for (UserId sinPermiso : List.of(MENTORA, lider, suspendido)) {
            assertThatThrownBy(() -> servicio.detalleDe(new ConsultaDetalleAdministrativa(sinPermiso, ANA.value(), 8)))
                    .isInstanceOf(NotAuthorizedException.class);
        }
    }

    @Test
    @DisplayName("una persona que no existe es 404, no un 'no aplica' que la haga parecer real")
    void personaInexistente() {
        UUID nadie = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

        assertThatThrownBy(() -> servicio.detalleDe(new ConsultaDetalleAdministrativa(ADMIN, nadie, 8)))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(banco.detallesPedidos).isEmpty();
    }
}
