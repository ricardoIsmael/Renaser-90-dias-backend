package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.mentoring.api.EvidenciaDeObligacion;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.rocks.api.AccionesDelDiaFinder.AccionDelDia;
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

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_EN_CURSO;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.HASTA_EN_CURSO;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.conDia;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventana;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La ficha de una persona para el líder (spec 005, US1 y FR-016): la ventana que pide, lo que devuelve y, sobre todo,
 * lo que registra.
 */
class SemanaDePersonaServiceTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId LUIS = id("b2");
    private static final UserId AJENA = id("b9");

    private final BancoDeCumplimiento banco = new BancoDeCumplimiento();
    private SemanaDePersonaService servicio;

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        servicio = banco.semanaService();
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        banco.aprendiz(FENIX, ANA, "Ana Pérez");
        // La semana en curso (sabado 17 a viernes 23): el semaforo solo mide los dias cerrados, 17 y 18; hoy es el 19.
        banco.semana(HASTA_EN_CURSO, ANA, ventana(DESDE_EN_CURSO, false, 70, 70, null, null, null, null, null));
        banco.semana(SEMANA_CERRADA, ANA, ventanaPareja(DESDE_SEMANA_CERRADA, true, 55));
    }

    private static ObligacionDelDia obligacion(String titulo, String estado) {
        return new ObligacionDelDia(UUID.randomUUID(), titulo, estado, false, "NO_REQUERIDA", null, null, null, null,
                null, List.of());
    }

    private SemanaDeLaPersona delLider(UserId persona, LocalDate semanaHasta) {
        return servicio.semanaDe(new ConsultaSemanaDePersona(LIDER, FENIX, persona.value(), semanaHasta));
    }

    // ── la ventana ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("sin fecha abre en la semana en curso del semaforo, de sabado a viernes, y devuelve siete dias")
    void semanaEnCursoPorDefecto() {
        SemanaDeLaPersona semana = delLider(ANA, null);

        assertThat(banco.ventanasPedidasALaSemana).singleElement()
                .isEqualTo(new LocalDate[]{DESDE_EN_CURSO, HASTA_EN_CURSO});
        assertThat(semana.desde()).isEqualTo(DESDE_EN_CURSO);
        assertThat(semana.hasta()).isEqualTo(HASTA_EN_CURSO);
        assertThat(semana.cerrada()).isFalse();
        assertThat(semana.dias()).hasSize(7);
        assertThat(semana.dias().getFirst().fecha()).isEqualTo(DESDE_EN_CURSO);
        assertThat(semana.zona()).isEqualTo("America/Lima");
    }

    @Test
    @DisplayName("con un viernes pide esa semana de sabado a viernes, y la lectura del semaforo es la de esa semana")
    void semanaDeSabadoAViernes() {
        SemanaDeLaPersona semana = delLider(ANA, SEMANA_CERRADA);

        assertThat(banco.ventanasPedidasALaSemana).singleElement()
                .isEqualTo(new LocalDate[]{DESDE_SEMANA_CERRADA, SEMANA_CERRADA});
        assertThat(semana.cerrada()).isTrue();
        assertThat(semana.color()).isEqualTo(ColorDeLaFila.ROJO);
        assertThat(semana.porcentaje()).isEqualByComparingTo("55");
        assertThat(banco.lecturasDelSemaforo).singleElement().asString().startsWith("semana:" + SEMANA_CERRADA);
    }

    @Test
    @DisplayName("regla 02: el «hoy» es el de la persona; con el reloj en la madrugada UTC la semana en curso es la de Lima")
    void madrugadaUtc() {
        // 03:00 UTC del sabado 17 = 22:00 del viernes 16 en Lima: para ella todavia corre la semana del 10 al 16.
        banco.ahora = Instant.parse("2026-10-17T03:00:00Z");

        SemanaDeLaPersona semana = delLider(ANA, null);

        assertThat(semana.desde()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(semana.hasta()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(semana.cerrada()).isFalse();
    }

    @Test
    @DisplayName("un dia que no es viernes o una semana que no empezo es 400 y no deja registro")
    void semanaInvalida() {
        assertThatThrownBy(() -> delLider(ANA, LocalDate.of(2026, 10, 15))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> delLider(ANA, LocalDate.of(2026, 10, 30))).isInstanceOf(IllegalArgumentException.class);

        assertThat(banco.consultas).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
    }

    // ── lo que trae ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("trae a la persona, su color y porcentaje de esa ventana, su mentor y su primera semana")
    void encabezado() {
        SemanaDeLaPersona semana = delLider(ANA, null);

        assertThat(semana.personaId()).isEqualTo(ANA.value());
        assertThat(semana.grupoId()).isEqualTo(FENIX);
        assertThat(semana.nombre()).isEqualTo("Ana Pérez");
        assertThat(semana.rol()).isEqualTo(UserRole.TRAINEE);
        assertThat(semana.color()).isEqualTo(ColorDeLaFila.AMARILLO);
        assertThat(semana.porcentaje()).isEqualByComparingTo("70");
        assertThat(semana.mentor().id()).isEqualTo(MENTORA.value());
        assertThat(semana.mentor().nombre()).isEqualTo("Luisa Rojas");
        // Su Dia 1 fue el lunes 17 de agosto: la primera semana abre el sabado 15.
        assertThat(semana.primeraSemana()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    @DisplayName("FR-019: un dia sin nada que medir dice su motivo en palabras y no baja el porcentaje")
    void diasConMotivo() {
        banco.semana(SEMANA_CERRADA, ANA, conDia(ventana(DESDE_SEMANA_CERRADA, true, 100, 100, null, 80, 80, 80, 80),
                LocalDate.of(2026, 10, 11), EstadoDiaSemaforo.PAUSADO));

        List<DiaDeLaFicha> dias = delLider(ANA, SEMANA_CERRADA).dias();

        assertThat(dias).extracting(DiaDeLaFicha::motivo).containsExactly(null, "PAUSADO", "SIN_NADA_PLANIFICADO",
                null, null, null, null);
    }

    @Test
    @DisplayName("la semana en curso: hoy dice «en curso»; los dias que vienen estan, con motivo FUTURO y sin nada adentro")
    void semanaEnCursoConDiasFuturos() {
        ObligacionDelDia deHoy = obligacion("Lectura", "PENDIENTE");
        ObligacionDelDia delSabado = obligacion("Caminar", "COMPLETADO");
        ObligacionDelDia adelantada = obligacion("No deberia verse", "PENDIENTE");
        banco.obligacion(ANA, LocalDate.of(2026, 10, 17), delSabado);
        banco.obligacion(ANA, LocalDate.of(2026, 10, 19), deHoy);
        banco.obligacion(ANA, LocalDate.of(2026, 10, 21), adelantada);
        banco.accion(ANA, new AccionDelDia(UUID.randomUUID(), LocalDate.of(2026, 10, 21), "Planificada para el 21",
                false, null));

        List<DiaDeLaFicha> dias = delLider(ANA, null).dias();

        assertThat(dias).extracting(DiaDeLaFicha::fecha).extracting(LocalDate::toString).containsExactly("2026-10-17",
                "2026-10-18", "2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23");
        assertThat(dias).extracting(DiaDeLaFicha::motivo).containsExactly(null, null, "EN_CURSO", "FUTURO", "FUTURO",
                "FUTURO", "FUTURO");
        assertThat(dias.get(0).obligaciones()).containsExactly(delSabado);
        assertThat(dias.get(2).obligaciones()).containsExactly(deHoy);
        assertThat(dias.subList(3, 7)).allSatisfy(dia -> {
            assertThat(dia.obligaciones()).isEmpty();
            assertThat(dia.acciones()).isEmpty();
            assertThat(dia.diaDePrograma()).isNull();
        });
    }

    @Test
    @DisplayName("regla 02: «hoy» y «futuro» se deciden con el dia de la persona, no con el de UTC")
    void futuroSegunLaZonaDeLaPersona() {
        // 03:00 UTC del domingo 18 = 22:00 del sabado 17 en Lima: el 18 todavia es futuro para ella.
        banco.ahora = Instant.parse("2026-10-18T03:00:00Z");
        banco.semana(HASTA_EN_CURSO, ANA, ventana(DESDE_EN_CURSO, false, null, null, null, null, null, null, null));

        List<DiaDeLaFicha> dias = delLider(ANA, null).dias();

        assertThat(dias.get(0).motivo()).isEqualTo("EN_CURSO");
        assertThat(dias.get(1).motivo()).isEqualTo("FUTURO");
    }

    @Test
    @DisplayName("quien el semaforo no mide (todavia no activo su programa) sale sin color y con motivo NO_ACTIVADO")
    void personaSinSemaforo() {
        banco.aprendiz(FENIX, LUIS, "Luis Vega");
        banco.programa(LUIS, UserRole.TRAINEE, LocalDate.of(2026, 10, 20), false);

        SemanaDeLaPersona semana = delLider(LUIS, null);

        assertThat(semana.color()).isEqualTo(ColorDeLaFila.SIN_DATOS);
        assertThat(semana.porcentaje()).isNull();
        assertThat(semana.primeraSemana()).isNull();
        assertThat(semana.dias()).extracting(DiaDeLaFicha::motivo).containsExactly("NO_ACTIVADO", "NO_ACTIVADO",
                "NO_ACTIVADO", "FUTURO", "FUTURO", "FUTURO", "FUTURO");
    }

    @Test
    @DisplayName("el mentor con programa propio sale «No se mide» con su %, y sin boton para avisar a su propio mentor")
    void elMentorTambienSeVe() {
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.semana(HASTA_EN_CURSO, MENTORA, ventanaPareja(DESDE_EN_CURSO, false, 85));

        SemanaDeLaPersona semana = delLider(MENTORA, null);

        assertThat(semana.rol()).isEqualTo(UserRole.MENTOR);
        assertThat(semana.color()).isEqualTo(ColorDeLaFila.NO_SE_MIDE);
        assertThat(semana.porcentaje()).isEqualByComparingTo("85");
        assertThat(semana.mentor()).isNull();
    }

    @Test
    @DisplayName("las obligaciones del dia viajan tal cual: respuesta, evidencias, publicacion")
    void obligacionesTalCual() {
        UUID foto = UUID.randomUUID();
        ObligacionDelDia ritual = new ObligacionDelDia(UUID.randomUUID(), "Ritual de la mañana", "COMPLETADO", true,
                "ENTREGADA", Instant.parse("2026-10-14T14:00:00Z"), "VALIDA", foto, null, "Me sentí con energía",
                List.of(new EvidenciaDeObligacion(foto, "FOTO", Instant.parse("2026-10-14T14:00:00Z"))));
        banco.obligacion(ANA, LocalDate.of(2026, 10, 14), ritual);

        DiaDeLaFicha miercoles = delLider(ANA, SEMANA_CERRADA).dias().get(4);

        assertThat(miercoles.fecha()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(miercoles.obligaciones()).containsExactly(ritual);
    }

    @Test
    @DisplayName("las acciones del dia (con la foto con la que se cumplieron) van en su dia; las de otros dias, no")
    void accionesDelDia() {
        UUID accion = UUID.randomUUID();
        UUID evidencia = UUID.randomUUID();
        banco.accion(ANA, new AccionDelDia(accion, LocalDate.of(2026, 10, 14), "Llamar a 3 clientes", true, evidencia));
        banco.accion(ANA, new AccionDelDia(UUID.randomUUID(), LocalDate.of(2026, 10, 9), "De la semana anterior", true,
                UUID.randomUUID()));
        banco.accion(LUIS, new AccionDelDia(UUID.randomUUID(), LocalDate.of(2026, 10, 14), "De otra persona", true,
                UUID.randomUUID()));

        List<DiaDeLaFicha> dias = delLider(ANA, SEMANA_CERRADA).dias();

        assertThat(dias.get(4).acciones()).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(accion);
            assertThat(a.titulo()).isEqualTo("Llamar a 3 clientes");
            assertThat(a.completada()).isTrue();
            assertThat(a.evidenciaId()).isEqualTo(evidencia);
        });
        assertThat(dias.stream().filter(d -> !d.fecha().equals(LocalDate.of(2026, 10, 14)))
                .flatMap(d -> d.acciones().stream())).isEmpty();
    }

    // ── alcance ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RL-08: una persona que no esta hoy en ese grupo es 404, aunque exista, y no deja registro")
    void personaFueraDelGrupo() {
        banco.usuario(AJENA, "Ana de otro grupo", UserRole.TRAINEE, UserStatus.ACTIVE);
        banco.programa(AJENA, UserRole.TRAINEE, LocalDate.of(2026, 8, 17), true);

        assertThatThrownBy(() -> delLider(AJENA, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> delLider(id("ff"), null)).isInstanceOf(NoSuchElementException.class);
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("un grupo sin mentor o la recepcion es 404")
    void grupoSinMentor() {
        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaSemanaDePersona(LIDER, SIN_MENTOR, ANA.value(), null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("SC-006: una cuenta de prueba, suspendida o cerrada no se abre")
    void cuentasQueNoSeAbren() {
        banco.aprendiz(FENIX, LUIS, "Luis Vega");
        banco.cuentasDePrueba.add(LUIS);
        assertThatThrownBy(() -> delLider(LUIS, null)).isInstanceOf(NoSuchElementException.class);

        banco.usuario(ANA, "Ana Pérez", UserRole.TRAINEE, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> delLider(ANA, null)).isInstanceOf(NoSuchElementException.class);
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("un mentor, un aprendiz o un lider suspendido no abren la ficha, y no queda registro")
    void sinPermiso() {
        UserId otroMentor = id("a2");
        banco.usuario(otroMentor, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);

        for (UserId actor : List.of(otroMentor, MENTORA, ANA)) {
            assertThatThrownBy(() -> servicio.semanaDe(new ConsultaSemanaDePersona(actor, FENIX, ANA.value(), null)))
                    .isInstanceOf(NotAuthorizedException.class);
        }
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> delLider(ANA, null)).isInstanceOf(NotAuthorizedException.class);

        assertThat(banco.consultas).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
    }

    // ── el registro (FR-016) ────────────────────────────────────────────────

    @Test
    @DisplayName("FR-016: cada llamada registra quien, con que rol, a quien, de que grupo, a que mentor, la semana y cuando")
    void registraLaConsulta() {
        delLider(ANA, null);

        assertThat(banco.consultas).singleElement().satisfies((ConsultaDeFicha c) -> {
            assertThat(c.consultorId()).isEqualTo(LIDER);
            assertThat(c.rolConsultor()).isEqualTo(UserRole.MENTOR_LEAD);
            assertThat(c.personaId()).isEqualTo(ANA);
            assertThat(c.grupoId()).isEqualTo(FENIX);
            assertThat(c.mentorId()).isEqualTo(MENTORA);
            assertThat(c.semanaDesde()).isEqualTo(DESDE_EN_CURSO);
            assertThat(c.consultadaEn()).isEqualTo(BancoDeCumplimiento.AHORA);
        });
    }

    @Test
    @DisplayName("cambiar de semana es otra llamada y otra fila; abrir la misma dos veces tambien")
    void unaFilaPorLlamada() {
        delLider(ANA, null);
        delLider(ANA, SEMANA_CERRADA);
        delLider(ANA, SEMANA_CERRADA);

        assertThat(banco.consultas).extracting(ConsultaDeFicha::semanaDesde)
                .containsExactly(DESDE_EN_CURSO, DESDE_SEMANA_CERRADA, DESDE_SEMANA_CERRADA);
        assertThat(banco.consultas).extracting(ConsultaDeFicha::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Administracion tambien deja su consulta, con su rol; el resumen al mentor la ignora despues")
    void administracionTambienQuedaRegistrada() {
        servicio.semanaDe(new ConsultaSemanaDePersona(ADMIN, FENIX, ANA.value(), null));

        assertThat(banco.consultas).singleElement().satisfies(c -> assertThat(c.rolConsultor()).isEqualTo(UserRole.ADMIN));
    }

    @Test
    @DisplayName("la hora de la consulta es el instante del reloj, tambien en la madrugada UTC")
    void horaDeLaConsulta() {
        banco.ahora = Instant.parse("2026-10-19T03:00:00Z");

        delLider(ANA, null);

        assertThat(banco.consultas.getFirst().consultadaEn()).isEqualTo(Instant.parse("2026-10-19T03:00:00Z"));
    }
}
