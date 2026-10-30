package com.renaser.os.leadership.application.services;

import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.CodigoRenaserDePersona;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.DiaDeLaPersona;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.MiRadarDeLaPersona;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.RegistroDelRadar;
import com.renaser.os.leadership.application.ports.in.ConsultarRadarDePersonaUseCase.ConsultaRadarDePersona;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El Codigo Renaser de una persona para el lider (spec 012, US4): quien puede abrirlo, que lee y que NO deja (FR-015:
 * ningun registro de consulta propio). La autorizacion es la de la ficha semanal; aca se prueba que se aplica igual.
 */
class RadarDePersonaServiceTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId SOPORTE_TI = id("e1");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId AJENA = id("b9");
    private static final Instant ANTES = Instant.parse("2026-10-12T15:00:00Z");

    private final BancoDeCumplimiento banco = new BancoDeCumplimiento();
    private RadarDePersonaService servicio;

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        servicio = banco.radarService();
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.usuario(SOPORTE_TI, "Soporte", UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        banco.aprendiz(FENIX, ANA, "Ana Pérez");
        banco.radar(ANA, new CodigoRenaserDePersona(new MiRadarDeLaPersona(true, List.of(9, 14, 20),
                List.of(new DiaDeLaPersona(6, List.of(11)), new DiaDeLaPersona(7, List.of()))), List.of(
                new RegistroDelRadar(UUID.randomUUID(), "Trabajando", "En la reunión", "Tranquila", 7, "El correo",
                        Instant.parse("2026-10-14T19:06:00Z"), 14)), ANTES));
    }

    private CodigoRenaserDePersona de(UserId actor, UUID grupo, UserId persona, Instant cursor) {
        return servicio.radarDe(new ConsultaRadarDePersona(actor, grupo, persona.value(), cursor));
    }

    // ── quien lo abre ───────────────────────────────────────────────────────

    @Test
    @DisplayName("el lider, Administracion y el alquimista abren el Radar de una persona de un grupo con mentor")
    void losTresRolesLoAbren() {
        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            CodigoRenaserDePersona radar = de(actor, FENIX, ANA, null);

            assertThat(radar.miRadar().encendido()).isTrue();
            assertThat(radar.miRadar().horas()).containsExactly(9, 14, 20);
            assertThat(radar.miRadar().dias()).extracting(DiaDeLaPersona::dia).containsExactly(6, 7);
            assertThat(radar.miRadar().dias().get(0).horas()).containsExactly(11);
            assertThat(radar.miRadar().dias().get(1).horas()).isEmpty();
            assertThat(radar.registros()).singleElement().satisfies(r -> {
                assertThat(r.queSiento()).isEqualTo("Tranquila");
                assertThat(r.horaPrevista()).isEqualTo(14);
            });
            assertThat(radar.siguienteCursor()).isEqualTo(ANTES);
        }
    }

    @Test
    @DisplayName("lee de a 84 (la primera semana entera) y pasa el cursor que le dieron; sin cursor, la mas reciente")
    void paginaDeOchentaYCuatro() {
        de(LIDER, FENIX, ANA, null);
        de(LIDER, FENIX, ANA, ANTES);

        assertThat(banco.lecturasDelRadar).containsExactly(
                new BancoDeCumplimiento.LecturaDelRadarPedida(ANA, null, 84),
                new BancoDeCumplimiento.LecturaDelRadarPedida(ANA, ANTES, 84));
    }

    @Test
    @DisplayName("quien lleva su propio programa como mentor del grupo tambien se abre, como en la ficha semanal")
    void elMentorDelGrupoConProgramaPropio() {
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);

        CodigoRenaserDePersona radar = de(LIDER, FENIX, MENTORA, null);

        assertThat(radar.miRadar().encendido()).isFalse();
        assertThat(radar.miRadar().dias()).isEmpty();
        assertThat(banco.lecturasDelRadar).singleElement().extracting("persona").isEqualTo(MENTORA);
    }

    @Test
    @DisplayName("FR-015: leer el Radar no deja ningun registro de consulta")
    void noDejaRegistro() {
        de(LIDER, FENIX, ANA, null);

        assertThat(banco.consultas).isEmpty();
    }

    // ── alcance (404) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RL-08: una persona que no esta hoy en ese grupo es 404, aunque exista, y no se lee su Radar")
    void personaFueraDelGrupo() {
        banco.usuario(AJENA, "Ana de otro grupo", UserRole.TRAINEE, UserStatus.ACTIVE);
        banco.programa(AJENA, UserRole.TRAINEE, LocalDate.of(2026, 8, 17), true);

        assertThatThrownBy(() -> de(LIDER, FENIX, AJENA, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> de(LIDER, FENIX, id("ff"), null)).isInstanceOf(NoSuchElementException.class);
        assertThat(banco.lecturasDelRadar).isEmpty();
    }

    @Test
    @DisplayName("un grupo sin mentor, la recepcion o un grupo inexistente es 404")
    void grupoQueNoSeAbre() {
        assertThatThrownBy(() -> de(LIDER, SIN_MENTOR, ANA, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> de(LIDER, UUID.randomUUID(), ANA, null)).isInstanceOf(NoSuchElementException.class);
        assertThat(banco.lecturasDelRadar).isEmpty();
    }

    @Test
    @DisplayName("una cuenta de prueba o suspendida no se abre (404)")
    void cuentasQueNoSeAbren() {
        banco.cuentasDePrueba.add(ANA);
        assertThatThrownBy(() -> de(LIDER, FENIX, ANA, null)).isInstanceOf(NoSuchElementException.class);

        banco.cuentasDePrueba.clear();
        banco.usuario(ANA, "Ana Pérez", UserRole.TRAINEE, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> de(LIDER, FENIX, ANA, null)).isInstanceOf(NoSuchElementException.class);
        assertThat(banco.lecturasDelRadar).isEmpty();
    }

    // ── sin permiso (403) ───────────────────────────────────────────────────

    @Test
    @DisplayName("SC-006: un mentor (tambien el de ese grupo), un aprendiz y Soporte TI reciben 403 y no se lee nada")
    void sinPermiso() {
        UserId otroMentor = id("a2");
        banco.usuario(otroMentor, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);

        for (UserId actor : List.of(otroMentor, MENTORA, ANA, SOPORTE_TI)) {
            assertThatThrownBy(() -> de(actor, FENIX, ANA, null)).as("actor %s", actor)
                    .isInstanceOf(NotAuthorizedException.class);
        }
        assertThat(banco.lecturasDelRadar).isEmpty();
    }

    @Test
    @DisplayName("SC-006: un lider, un administrador o un alquimista suspendidos reciben 403 aunque su token sea valido")
    void suspendidos() {
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.SUSPENDED);

        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            assertThatThrownBy(() -> de(actor, FENIX, ANA, null)).as("actor %s", actor)
                    .isInstanceOf(NotAuthorizedException.class);
        }
        assertThat(banco.lecturasDelRadar).isEmpty();
    }

    @Test
    @DisplayName("el guard de rol corre antes que el alcance: sin permiso no se sabe si la persona existe")
    void elRolCorreAntesQueElAlcance() {
        assertThatThrownBy(() -> de(ANA, FENIX, id("ff"), null)).isInstanceOf(NotAuthorizedException.class);
    }
}
