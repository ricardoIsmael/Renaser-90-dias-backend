package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase.ConsultaGrupoDelLider;
import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase.GrupoDelLider;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.cumplimiento.PersonaDeLaLista;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La lista de un grupo para el líder (spec 005, US1): quiénes, con qué color y en qué orden, contra los dobles de las
 * APIs de los otros módulos.
 */
class GrupoDelLiderServiceTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId BETO = id("b2");
    private static final UserId CARLA = id("b3");
    private static final UserId DORA = id("b4");
    private static final UserId ELSA = id("b5");
    private static final UUID GUIADO = UUID.fromString("00000000-0000-0000-0000-00000000f003");

    private final BancoDeCumplimiento banco = new BancoDeCumplimiento();
    private GrupoDelLiderService servicio;

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        servicio = banco.grupoService();
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
    }

    private void aprendiz(UserId id, String nombre, Integer porcentajeParejo) {
        banco.aprendiz(FENIX, id, nombre);
        if (porcentajeParejo != null) {
            banco.semana(SEMANA_CERRADA, id, ventanaPareja(DESDE_SEMANA_CERRADA, true, porcentajeParejo));
        }
    }

    private GrupoDelLider delLider(LocalDate semanaHasta) {
        return servicio.grupoDe(new ConsultaGrupoDelLider(LIDER, FENIX, semanaHasta));
    }

    // ── autorización ────────────────────────────────────────────────────────

    @Test
    @DisplayName("el lider, el administrador y el alquimista abren el grupo")
    void liderazgoAbre() {
        aprendiz(ANA, "Ana Pérez", 90);

        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            assertThat(servicio.grupoDe(new ConsultaGrupoDelLider(actor, FENIX, null)).personas()).hasSize(1);
        }
    }

    @Test
    @DisplayName("un mentor, un aprendiz o un lider suspendido no abren el grupo, y no se lee nada")
    void sinPermisoNoSeLeeNada() {
        UserId otroMentor = id("a2");
        banco.usuario(otroMentor, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        aprendiz(ANA, "Ana Pérez", 90);

        for (UserId actor : List.of(otroMentor, ANA, MENTORA)) {
            assertThatThrownBy(() -> servicio.grupoDe(new ConsultaGrupoDelLider(actor, FENIX, null)))
                    .isInstanceOf(NotAuthorizedException.class);
        }
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> delLider(null)).isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("suspendida");
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    @Test
    @DisplayName("un grupo sin mentor, la recepcion o un id que no existe son 404, no una lista vacia")
    void grupoSinMentorNoSeAbre() {
        assertThatThrownBy(() -> servicio.grupoDe(new ConsultaGrupoDelLider(LIDER, SIN_MENTOR, null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.grupoDe(new ConsultaGrupoDelLider(LIDER, UUID.randomUUID(), null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── armado y orden ──────────────────────────────────────────────────────

    @Test
    @DisplayName("FR-007: de rojo a verde, el menor % primero dentro de cada color, y quien lleva su programa al final")
    void ordenDelLider() {
        aprendiz(ANA, "Ana Pérez", 90);
        aprendiz(BETO, "Beto Paz", 40);
        aprendiz(CARLA, "Carla Soto", 70);
        aprendiz(DORA, "Dora Vega", null);
        aprendiz(ELSA, "Elsa Mora", 20);
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.semana(SEMANA_CERRADA, MENTORA, ventanaPareja(DESDE_SEMANA_CERRADA, true, 75));

        List<PersonaDeLaLista> personas = delLider(null).personas();

        assertThat(personas).extracting(PersonaDeLaLista::nombre).containsExactly(
                "Elsa Mora", "Beto Paz", "Carla Soto", "Dora Vega", "Ana Pérez", "Luisa Rojas");
        assertThat(personas).extracting(PersonaDeLaLista::color).containsExactly(ColorDeLaFila.ROJO,
                ColorDeLaFila.ROJO, ColorDeLaFila.AMARILLO, ColorDeLaFila.SIN_DATOS, ColorDeLaFila.VERDE,
                ColorDeLaFila.NO_SE_MIDE);
    }

    @Test
    @DisplayName("el mentor con programa propio sale con su % y «No se mide», sin barras de colores")
    void mentorConProgramaPropio() {
        aprendiz(ANA, "Ana Pérez", 90);
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.semana(SEMANA_CERRADA, MENTORA, ventanaPareja(DESDE_SEMANA_CERRADA, true, 75));

        PersonaDeLaLista luisa = delLider(null).personas().getLast();

        assertThat(luisa.personaId()).isEqualTo(MENTORA.value());
        assertThat(luisa.rol()).isEqualTo(UserRole.MENTOR);
        assertThat(luisa.color()).isEqualTo(ColorDeLaFila.NO_SE_MIDE);
        assertThat(luisa.color().etiqueta()).isEqualTo("No se mide en el semáforo");
        assertThat(luisa.porcentaje()).isEqualByComparingTo(new BigDecimal("75.0"));
        assertThat(luisa.diasConDatos()).isEqualTo(7);
        assertThat(luisa.dias()).isEmpty();
        assertThat(luisa.motivo()).isNull();
    }

    @Test
    @DisplayName("una guia de aprendiz al frente del grupo cuenta igual que un mentor")
    void guiaDeAprendiz() {
        UserId guia = id("a3");
        banco.grupoConMentor(GUIADO, "Grupo Guiado", guia, "Gloria Guía");
        banco.usuario(guia, "Gloria Guía", UserRole.TRAINEE_GUIDE, UserStatus.ACTIVE);
        banco.programa(guia, UserRole.TRAINEE_GUIDE, LocalDate.of(2026, 9, 1), true);

        List<PersonaDeLaLista> personas = servicio
                .grupoDe(new ConsultaGrupoDelLider(LIDER, GUIADO, null)).personas();

        assertThat(personas).singleElement().satisfies(p -> {
            assertThat(p.rol()).isEqualTo(UserRole.TRAINEE_GUIDE);
            assertThat(p.color()).isEqualTo(ColorDeLaFila.NO_SE_MIDE);
            assertThat(p.porcentaje()).isNull();
        });
    }

    @Test
    @DisplayName("el acompanante que no lleva programa propio (sin activar) no aparece")
    void acompananteSinProgramaNoAparece() {
        aprendiz(ANA, "Ana Pérez", 90);
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), false);

        assertThat(delLider(null).personas()).extracting(PersonaDeLaLista::nombre).containsExactly("Ana Pérez");
    }

    @Test
    @DisplayName("SC-006: una cuenta de prueba no aparece en la lista ni suma en el resumen; tampoco un mentor de prueba")
    void cuentasDePruebaFuera() {
        aprendiz(ANA, "Ana Pérez", 90);
        aprendiz(BETO, "Beto Paz", 40);
        banco.cuentasDePrueba.add(BETO);
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.cuentasDePrueba.add(MENTORA);

        GrupoDelLider grupo = delLider(null);

        assertThat(grupo.personas()).extracting(PersonaDeLaLista::nombre).containsExactly("Ana Pérez");
        assertThat(grupo.resumen().total()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-002: una cuenta suspendida o cerrada no aparece")
    void cuentasSuspendidasFuera() {
        aprendiz(ANA, "Ana Pérez", 90);
        aprendiz(BETO, "Beto Paz", 40);
        banco.usuario(BETO, "Beto Paz", UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThat(delLider(null).personas()).extracting(PersonaDeLaLista::nombre).containsExactly("Ana Pérez");
    }

    @Test
    @DisplayName("el resumen cuenta los colores de quien se mide y no a quien no se mide")
    void resumenSinLosQueNoSeMiden() {
        aprendiz(ANA, "Ana Pérez", 90);
        aprendiz(BETO, "Beto Paz", 40);
        aprendiz(CARLA, "Carla Soto", 70);
        aprendiz(DORA, "Dora Vega", null);
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);

        var resumen = delLider(null).resumen();

        assertThat(resumen.verde()).isEqualTo(1);
        assertThat(resumen.amarillo()).isEqualTo(1);
        assertThat(resumen.rojo()).isEqualTo(1);
        assertThat(resumen.sinDatos()).isEqualTo(1);
        assertThat(resumen.total()).isEqualTo(4);
    }

    @Test
    @DisplayName("un grupo sin personas devuelve la lista vacia, no un error")
    void grupoSinPersonas() {
        assertThat(delLider(null).personas()).isEmpty();
    }

    @Test
    @DisplayName("sin fecha abre en la ultima semana cerrada del semaforo (de sabado a viernes); la pasada, cerrada tambien")
    void encabezado() {
        aprendiz(ANA, "Ana Pérez", 90);

        GrupoDelLider sinFecha = delLider(null);

        assertThat(sinFecha.grupoNombre()).isEqualTo("Grupo Fénix");
        assertThat(sinFecha.desde()).isEqualTo(DESDE_SEMANA_CERRADA);
        assertThat(sinFecha.hasta()).isEqualTo(SEMANA_CERRADA);
        assertThat(sinFecha.cerrada()).isTrue();
        assertThat(banco.lecturasDelSemaforo).first().isEqualTo("tabla:" + SEMANA_CERRADA);
        assertThat(delLider(LocalDate.of(2026, 10, 9)).hasta()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    @DisplayName("E-629: la ultima semana cerrada es «cerrada» aunque alguien del grupo haya empezado despues y no tenga su foto")
    void unaPersonaNuevaNoDejaLaSemanaCerradaSinCerrar() {
        aprendiz(ANA, "Ana Pérez", 90);
        // Dora empezo su programa despues de esa semana: el cierre semanal no le dejo foto y su ventana no esta cerrada.
        aprendiz(DORA, "Dora Vega", null);

        GrupoDelLider lista = delLider(null);

        assertThat(lista.hasta()).isEqualTo(SEMANA_CERRADA);
        assertThat(lista.cerrada()).as("la semana ya paso: no depende de que cada persona tenga su foto").isTrue();
        assertThat(delLider(LocalDate.of(2026, 10, 9)).cerrada()).isTrue();
    }

    // ── la semana pedida ────────────────────────────────────────────────────

    @Test
    @DisplayName("la semana pedida llega a la tabla y al programa propio del acompanante; sin fecha, la ultima cerrada")
    void laSemanaPedidaLlegaATodos() {
        LocalDate anterior = LocalDate.of(2026, 10, 9);
        aprendiz(ANA, "Ana Pérez", null);
        banco.semana(anterior, ANA, ventanaPareja(LocalDate.of(2026, 10, 3), true, 55));
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.semana(anterior, MENTORA, ventanaPareja(LocalDate.of(2026, 10, 3), true, 85));

        GrupoDelLider pasada = delLider(anterior);

        assertThat(banco.lecturasDelSemaforo).contains("tabla:" + anterior);
        assertThat(banco.lecturasDelSemaforo).anyMatch(l -> l.startsWith("semana:" + anterior + " ["));
        assertThat(pasada.personas().getLast().porcentaje()).isEqualByComparingTo("85");

        banco.lecturasDelSemaforo.clear();
        delLider(null);
        assertThat(banco.lecturasDelSemaforo).contains("tabla:" + SEMANA_CERRADA);
        assertThat(banco.lecturasDelSemaforo).anyMatch(l -> l.startsWith("semana:" + SEMANA_CERRADA + " ["));
    }

    @Test
    @DisplayName("un dia que no es viernes o una semana que no empezo es 400, y no se lee el semaforo")
    void semanaInvalida() {
        assertThatThrownBy(() -> delLider(LocalDate.of(2026, 10, 15))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> delLider(LocalDate.of(2026, 10, 30))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futuras");
        // La semana en curso (la del 17 al 23) tampoco es «futura», pero la lista solo muestra semanas con resultado.
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    @Test
    @DisplayName("regla 02: con el reloj en la madrugada UTC la semana que empieza «manana» en UTC aun es futura en Lima")
    void madrugadaUtc() {
        // 03:00 UTC del sabado 17 = 22:00 del viernes 16 en Lima. La semana que cierra el 23 abre el 17: en UTC ya
        // empezo, en Lima todavia no.
        banco.ahora = Instant.parse("2026-10-17T03:00:00Z");

        assertThatThrownBy(() -> delLider(LocalDate.of(2026, 10, 23))).isInstanceOf(IllegalArgumentException.class);
        // Sin fecha: la ultima cerrada para Lima (viernes 16 todavia corre) es la del 3 al 9, no la del 10 al 16.
        assertThat(delLider(null).hasta()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(delLider(LocalDate.of(2026, 10, 16)).hasta()).isEqualTo(LocalDate.of(2026, 10, 16));
    }
}
