package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoAdministrativoUseCase.ConsultaTablaAdministrativa;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase.ConsultaTablaDelGrupo;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase.FilaDelSemaforo;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase.TablaDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.points.api.ColorSemaforo;
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

import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.DESDE_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.HASTA_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.SEMANA_CERRADA;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventana;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La tabla del semáforo de un grupo (§4.3), para el mentor y para el administrador, con dobles de
 * {@code community}, {@code points} y {@code users}.
 */
class SemaforoDelGrupoServiceTest {

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final UserId MENTORA = id("a1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ANA = id("b1");
    private static final UserId BETO = id("b2");
    private static final UserId CARLA = id("b3");
    private static final UserId DORA = id("b4");
    private static final UserId ELSA = id("b5");
    private static final UserId ANGELA = id("b6");
    private static final UserId SIN_PERFIL = id("b7");

    private final BancoDelSemaforo banco = new BancoDelSemaforo();
    private SemaforoDelGrupoService servicio;

    @BeforeEach
    void preparar() {
        servicio = banco.servicioDeTablas();
        banco.grupo(FENIX, "Grupo Fénix");
        banco.grupo(AURORA, "Grupo Aurora");
        banco.mentor(FENIX, MENTORA);
        banco.usuario(MENTORA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
    }

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private void aprendiz(UserId id, String nombre, Integer porcentajeParejo) {
        banco.aprendiz(FENIX, id);
        if (nombre != null) {
            banco.persona(id, nombre);
        }
        if (porcentajeParejo != null) {
            banco.vigente(id, ventanaPareja(DESDE_VIGENTE, false, porcentajeParejo));
        }
    }

    private TablaDelSemaforo tablaDelMentor() {
        return servicio.tablaDe(new ConsultaTablaDelGrupo(MENTORA, FENIX, null));
    }

    // ── autorización del mentor ─────────────────────────────────────────────

    @Test
    @DisplayName("un grupo que no acompaña no devuelve nada")
    void grupoAjenoProhibido() {
        assertThatThrownBy(() -> servicio.tablaDe(new ConsultaTablaDelGrupo(MENTORA, AURORA, null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    @Test
    @DisplayName("un exmentor con el token todavia valido ya no ve la tabla de su antiguo grupo")
    void exmentorProhibido() {
        UserId exmentor = id("a2");
        banco.usuario(exmentor, "Exmentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.exmentor(AURORA, exmentor, Instant.parse("2026-09-20T05:00:00Z"));

        assertThatThrownBy(() -> servicio.tablaDe(new ConsultaTablaDelGrupo(exmentor, AURORA, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un mentor SUSPENDIDO no ve la tabla aunque siga asignado: el interceptor no lo frena (A-1)")
    void mentorSuspendidoProhibido() {
        banco.usuario(MENTORA, "Luisa Rojas", UserRole.MENTOR, UserStatus.SUSPENDED);

        assertThatThrownBy(this::tablaDelMentor).isInstanceOf(NotAuthorizedException.class);
    }

    // ── armado ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ordena rojo, amarillo, sin datos y verde; dentro de cada color por nombre, sin nombre al final")
    void ordenPorColorYNombre() {
        aprendiz(BETO, "Beto Paz", 90);
        aprendiz(ANA, "Ana Pérez", 85);
        aprendiz(CARLA, "Carla Soto", 40);
        aprendiz(DORA, "Dora Vega", 70);
        aprendiz(ELSA, "Elsa Mora", null);
        aprendiz(ANGELA, "Ángela Ruiz", 50);
        aprendiz(SIN_PERFIL, null, 30);

        List<FilaDelSemaforo> filas = tablaDelMentor().aprendices();

        assertThat(filas).extracting(FilaDelSemaforo::aprendizId).containsExactly(
                ANGELA.value(), CARLA.value(), SIN_PERFIL.value(), DORA.value(), ELSA.value(), ANA.value(),
                BETO.value());
        assertThat(filas).extracting(f -> f.medicion().color()).containsExactly(
                ColorSemaforo.ROJO, ColorSemaforo.ROJO, ColorSemaforo.ROJO, ColorSemaforo.AMARILLO,
                ColorSemaforo.SIN_DATOS, ColorSemaforo.VERDE, ColorSemaforo.VERDE);
    }

    @Test
    @DisplayName("un aprendiz que el semaforo no mide aparece igual: sin datos, sin porcentaje, sin dias; nunca cero ni verde")
    void aprendizSinDatos() {
        aprendiz(ANA, "Ana Pérez", 85);
        aprendiz(ELSA, "Elsa Mora", null);

        FilaDelSemaforo elsa = tablaDelMentor().aprendices().stream()
                .filter(f -> f.aprendizId().equals(ELSA.value())).findFirst().orElseThrow();

        assertThat(elsa.nombre()).isEqualTo("Elsa Mora");
        assertThat(elsa.medicion()).isEqualTo(MedicionDelAprendiz.SIN_MEDICION);
        assertThat(elsa.medicion().porcentaje()).isNull();
        assertThat(elsa.medicion().diasConDatos()).isZero();
        assertThat(elsa.medicion().dias()).isEmpty();
    }

    @Test
    @DisplayName("el mentor que ademas cursa en su propio grupo no aparece en la tabla ni en el resumen")
    void mentorExcluidoDeSuTabla() {
        aprendiz(ANA, "Ana Pérez", 85);
        banco.aprendiz(FENIX, MENTORA);
        banco.vigente(MENTORA, ventanaPareja(DESDE_VIGENTE, false, 40));

        TablaDelSemaforo tabla = tablaDelMentor();

        assertThat(tabla.aprendices()).extracting(FilaDelSemaforo::aprendizId).containsExactly(ANA.value());
        assertThat(tabla.resumen().total()).isEqualTo(1);
        assertThat(banco.lecturasDelSemaforo).containsExactly(List.of(ANA));
    }

    @Test
    @DisplayName("el resumen cuenta los colores de la tabla, incluido el sin datos")
    void resumenPorColor() {
        aprendiz(ANA, "Ana Pérez", 85);
        aprendiz(BETO, "Beto Paz", 90);
        aprendiz(DORA, "Dora Vega", 70);
        aprendiz(CARLA, "Carla Soto", 40);
        aprendiz(ELSA, "Elsa Mora", null);

        assertThat(tablaDelMentor().resumen()).isEqualTo(new ConteoPorColor(2, 1, 1, 1));
    }

    @Test
    @DisplayName("el semaforo y los nombres se leen UNA vez para todo el grupo, nunca por persona")
    void lecturasEnLote() {
        aprendiz(ANA, "Ana Pérez", 85);
        aprendiz(BETO, "Beto Paz", 90);
        aprendiz(ELSA, "Elsa Mora", null);

        tablaDelMentor();

        assertThat(banco.lecturasDelSemaforo).containsExactly(List.of(ANA, BETO, ELSA));
        assertThat(banco.nombresPedidos).containsExactly(List.of(ANA, BETO, ELSA));
    }

    @Test
    @DisplayName("las fechas del encabezado salen de las ventanas de los aprendices")
    void periodoDeLasVentanas() {
        aprendiz(ANA, "Ana Pérez", 85);

        TablaDelSemaforo tabla = tablaDelMentor();

        assertThat(tabla.periodo().desde()).isEqualTo(DESDE_VIGENTE);
        assertThat(tabla.periodo().hasta()).isEqualTo(HASTA_VIGENTE);
        assertThat(tabla.periodo().cerrada()).isFalse();
    }

    @Test
    @DisplayName("con semanaHasta lee ESA semana y no la vigente")
    void semanaPedida() {
        banco.aprendiz(FENIX, ANA);
        banco.persona(ANA, "Ana Pérez");
        banco.semana(SEMANA_CERRADA, ANA, ventana(LocalDate.of(2026, 9, 12), true, 80, 80, 85, 90, 80, 85, 80));

        TablaDelSemaforo tabla = servicio.tablaDe(new ConsultaTablaDelGrupo(MENTORA, FENIX, SEMANA_CERRADA));

        assertThat(banco.semanasPedidas).containsExactly(SEMANA_CERRADA);
        assertThat(tabla.periodo().desde()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(tabla.periodo().hasta()).isEqualTo(SEMANA_CERRADA);
        assertThat(tabla.periodo().cerrada()).isTrue();
        assertThat(tabla.aprendices().getFirst().medicion().color()).isEqualTo(ColorSemaforo.VERDE);
    }

    @Test
    @DisplayName("semanaHasta tiene que ser viernes: un jueves se rechaza antes de leer nada")
    void semanaHastaNoViernes() {
        assertThatThrownBy(() -> new ConsultaTablaDelGrupo(MENTORA, FENIX, LocalDate.of(2026, 9, 17)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsultaTablaAdministrativa(ADMIN, FENIX, LocalDate.of(2026, 9, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sin nadie medido, el periodo es la ventana vigente en la zona del GRUPO, no la del servidor")
    void periodoSinMedicionesEnLaZonaDelGrupo() {
        aprendiz(ELSA, "Elsa Mora", null);
        // Sabado 26, 03:00 UTC = viernes 25, 22:00 en Lima. En UTC ya seria sabado: la ventana
        // correria un dia (19..25) y se daria por cerrada una semana que en Lima todavia corre.
        banco.relojEn(Instant.parse("2026-09-26T03:00:00Z"));

        TablaDelSemaforo tabla = tablaDelMentor();

        assertThat(tabla.periodo().desde()).isEqualTo(DESDE_VIGENTE);
        assertThat(tabla.periodo().hasta()).isEqualTo(HASTA_VIGENTE);
        assertThat(tabla.periodo().cerrada()).isFalse();
    }

    @Test
    @DisplayName("un grupo sin aprendices devuelve la tabla vacia con el resumen en cero, no un error")
    void grupoVacio() {
        TablaDelSemaforo tabla = tablaDelMentor();

        assertThat(tabla.aprendices()).isEmpty();
        assertThat(tabla.resumen()).isEqualTo(ConteoPorColor.NINGUNO);
        assertThat(tabla.grupoNombre()).isEqualTo("Grupo Fénix");
    }

    // ── administración ──────────────────────────────────────────────────────

    @Test
    @DisplayName("el administrador ve exactamente la misma tabla que el mentor: un solo armado")
    void administradorVeLaMismaTabla() {
        aprendiz(ANA, "Ana Pérez", 85);
        aprendiz(CARLA, "Carla Soto", 40);
        aprendiz(ELSA, "Elsa Mora", null);

        TablaDelSemaforo delMentor = tablaDelMentor();
        TablaDelSemaforo delAdmin = servicio.tablaDe(new ConsultaTablaAdministrativa(ADMIN, FENIX, null));

        assertThat(delAdmin).isEqualTo(delMentor);
    }

    @Test
    @DisplayName("la puerta administrativa no se abre por acompañar: el mentor del grupo recibe 403 ahi")
    void mentorNoUsaLaPuertaAdministrativa() {
        assertThatThrownBy(() -> servicio.tablaDe(new ConsultaTablaAdministrativa(MENTORA, FENIX, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("el lider de mentores no ve la tabla con nombres: solo el resumen por grupos (RL-07)")
    void liderNoVeLaTablaConNombres() {
        UserId lider = id("c1");
        banco.usuario(lider, "Lider", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.tablaDe(new ConsultaTablaAdministrativa(lider, FENIX, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un administrador suspendido recibe 403")
    void adminSuspendido() {
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.tablaDe(new ConsultaTablaAdministrativa(ADMIN, FENIX, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un grupo que no existe es 404 para el administrador")
    void grupoInexistente() {
        UUID inexistente = UUID.fromString("00000000-0000-0000-0000-00000000f0ff");

        assertThatThrownBy(() -> servicio.tablaDe(new ConsultaTablaAdministrativa(ADMIN, inexistente, null)))
                .isInstanceOf(NoSuchElementException.class);
    }
}
