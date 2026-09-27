package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.ConsultarAtencionDelSemaforoUseCase.AprendizQueNecesitaAtencion;
import com.renaser.os.mentoring.application.ports.in.ConsultarAtencionDelSemaforoUseCase.AtencionDelSemaforo;
import com.renaser.os.mentoring.application.ports.in.ConsultarAtencionDelSemaforoUseCase.ConsultaAtencion;
import com.renaser.os.mentoring.application.ports.in.ConsultarAtencionDelSemaforoUseCase.GrupoDelAprendiz;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.DESDE_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.HASTA_VIGENTE;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «¿A quién atiendo hoy?» (S-4): todo aprendiz activo en rojo o amarillo, esté donde esté. Antes,
 * la única vista de administración por grupos ({@code SemaforoPorGruposService}) solo miraba
 * grupos regulares con mentor vigente, y un aprendiz de la recepción, de un grupo sin mentor o sin
 * grupo no aparecía en ningún lado.
 */
class AtencionDelSemaforoServiceTest {

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID HUERFANO = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final UUID BIENVENIDA = UUID.fromString("00000000-0000-0000-0000-00000000f003");
    private static final UserId MENTORA = id("a1");
    private static final UserId ADMIN = id("d1");
    private static final UserId LIDER = id("c1");
    private static final UserId ANA = id("b1");
    private static final UserId BETO = id("b2");
    private static final UserId CARLA = id("b3");
    private static final UserId DORA = id("b4");
    private static final UserId ELSA = id("b5");
    private static final UserId FIDEL = id("b6");

    private final BancoDelSemaforo banco = new BancoDelSemaforo();
    private AtencionDelSemaforoService servicio;

    @BeforeEach
    void preparar() {
        servicio = banco.servicioDeAtencion();
        banco.grupo(FENIX, "Grupo Fénix");
        banco.mentor(FENIX, MENTORA);
        banco.grupo(HUERFANO, "Grupo Sin Mentor");
        banco.recepcion(BIENVENIDA, "Bienvenida");
        banco.usuario(MENTORA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(LIDER, "Lider", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        aprendiz(ANA, "Ana Pérez", FENIX, 55);          // rojo, grupo con mentor
        aprendiz(BETO, "Beto Paz", HUERFANO, 70);       // amarillo, grupo sin mentor
        aprendiz(CARLA, "Carla Díaz", BIENVENIDA, 40);  // rojo, recepción
        aprendiz(DORA, "Dora Luna", null, 65);          // amarillo, sin grupo
        aprendiz(ELSA, "Elsa Mora", FENIX, 90);         // verde: no necesita atención
    }

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private void aprendiz(UserId id, String nombre, UUID grupo, int porcentajeParejo) {
        if (grupo != null) {
            banco.aprendiz(grupo, id);
        }
        banco.persona(id, nombre);
        banco.vigente(id, ventanaPareja(DESDE_VIGENTE, false, porcentajeParejo));
    }

    private AtencionDelSemaforo atencion(UserId actor) {
        return servicio.atencionDe(new ConsultaAtencion(actor));
    }

    @Test
    @DisplayName("aparece todo activo en rojo o amarillo: grupo con mentor, sin mentor, recepción y sin grupo")
    void nadieQuedaAfuera() {
        AtencionDelSemaforo atencion = atencion(ADMIN);

        assertThat(atencion.aprendices()).extracting(AprendizQueNecesitaAtencion::nombre)
                .containsExactly("Ana Pérez", "Carla Díaz", "Beto Paz", "Dora Luna");
        assertThat(atencion.rojo()).isEqualTo(2);
        assertThat(atencion.amarillo()).isEqualTo(2);
        assertThat(atencion.periodo().desde()).isEqualTo(DESDE_VIGENTE);
        assertThat(atencion.periodo().hasta()).isEqualTo(HASTA_VIGENTE);
    }

    @Test
    @DisplayName("cada uno trae dónde está: su grupo y su mentor, la recepción, un grupo sin mentor, o ninguno")
    void cadaUnoConSusGrupos() {
        List<AprendizQueNecesitaAtencion> aprendices = atencion(ADMIN).aprendices();

        assertThat(de(aprendices, ANA).grupos())
                .containsExactly(new GrupoDelAprendiz(FENIX, "Grupo Fénix", false, "Luisa Rojas"));
        assertThat(de(aprendices, BETO).grupos())
                .containsExactly(new GrupoDelAprendiz(HUERFANO, "Grupo Sin Mentor", false, null));
        assertThat(de(aprendices, CARLA).grupos())
                .containsExactly(new GrupoDelAprendiz(BIENVENIDA, "Bienvenida", true, null));
        assertThat(de(aprendices, DORA).grupos()).isEmpty();
        assertThat(de(aprendices, ANA).medicion().color()).isEqualTo(ColorSemaforo.ROJO);
    }

    @Test
    @DisplayName("solo cuentas activas: un aprendiz suspendido en rojo no aparece")
    void suspendidoNoAparece() {
        aprendiz(FIDEL, "Fidel Soto", FENIX, 30);
        banco.usuario(FIDEL, "Fidel Soto", UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThat(atencion(ADMIN).aprendices()).extracting(AprendizQueNecesitaAtencion::aprendizId)
                .doesNotContain(FIDEL.value());
    }

    @Test
    @DisplayName("el semáforo se lee UNA vez para todo el padrón, nunca una por persona")
    void unaSolaLectura() {
        atencion(ADMIN);

        assertThat(banco.lecturasDelSemaforo).hasSize(1);
    }

    @Test
    @DisplayName("autorizacion negativa: MENTOR, MENTOR_LEAD y ADMIN suspendido reciben 403 sin leer el semáforo")
    void soloAdministracionActiva() {
        banco.usuario(id("d9"), "Admin suspendido", UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> atencion(MENTORA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> atencion(LIDER)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> atencion(id("d9"))).isInstanceOf(NotAuthorizedException.class);
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    private static AprendizQueNecesitaAtencion de(List<AprendizQueNecesitaAtencion> aprendices, UserId id) {
        return aprendices.stream().filter(a -> a.aprendizId().equals(id.value())).findFirst().orElseThrow();
    }
}
