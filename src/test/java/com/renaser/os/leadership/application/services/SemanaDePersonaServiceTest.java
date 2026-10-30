package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.cumplimiento.ProgramacionDelDia;
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
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_VIGENTE;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.HASTA_VIGENTE;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.VIERNES_FUTURO;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.conDia;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventana;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La ficha de una persona para el líder (spec 005, US1 y FR-016): la ventana que pide —la MISMA del resumen por grupos,
 * decisión del dueño del 2026-10-19—, lo que devuelve y, sobre todo, lo que registra.
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
        // La ventana vigente (lunes 12 a domingo 18; hoy es el lunes 19) y la ultima semana cerrada (10 al 16).
        banco.vigente(ANA, ventanaPareja(DESDE_VIGENTE, false, 70));
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
    @DisplayName("sin fecha abre en la ventana vigente del resumen: los 7 dias que terminan ayer, y devuelve siete dias")
    void ventanaVigentePorDefecto() {
        SemanaDeLaPersona semana = delLider(ANA, null);

        assertThat(banco.ventanasPedidasALaSemana).singleElement()
                .isEqualTo(new LocalDate[]{DESDE_VIGENTE, HASTA_VIGENTE});
        assertThat(semana.desde()).isEqualTo(DESDE_VIGENTE);
        assertThat(semana.hasta()).isEqualTo(HASTA_VIGENTE);
        // Hoy es lunes 19: la semana del 17 al 23 sigue abierta, asi que la ventana vigente no esta cerrada.
        assertThat(semana.cerrada()).isFalse();
        assertThat(semana.dias()).hasSize(7);
        assertThat(semana.dias().getFirst().fecha()).isEqualTo(DESDE_VIGENTE);
        assertThat(semana.dias().getLast().fecha()).isEqualTo(HASTA_VIGENTE);
        assertThat(semana.zona()).isEqualTo("America/Lima");
    }

    @Test
    @DisplayName("sin fecha el semaforo se lee como lo lee el resumen (la ventana vigente), no como una semana")
    void sinFechaLeeLaVentanaVigente() {
        delLider(ANA, null);

        assertThat(banco.lecturasDelSemaforo).singleElement().asString().startsWith("vigente ");
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
        assertThat(semana.dias()).extracting(DiaDeLaFicha::fecha).extracting(LocalDate::getDayOfWeek)
                .first().isEqualTo(java.time.DayOfWeek.SATURDAY);
        assertThat(banco.lecturasDelSemaforo).singleElement().asString().startsWith("semana:" + SEMANA_CERRADA);
    }

    @Test
    @DisplayName("regla 02: el «hoy» es el de la persona; con el reloj en la madrugada UTC la vigente es la de Lima")
    void madrugadaUtc() {
        // 03:00 UTC del sabado 17 = 22:00 del viernes 16 en Lima: para ella hoy es el 16 y la vigente termina el 15.
        banco.ahora = Instant.parse("2026-10-17T03:00:00Z");

        SemanaDeLaPersona semana = delLider(ANA, null);

        assertThat(semana.desde()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(semana.hasta()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(semana.cerrada()).isFalse();
    }

    @Test
    @DisplayName("un dia que no es viernes o un viernes que todavia no llego es 400 y no deja registro")
    void semanaInvalida() {
        assertThatThrownBy(() -> delLider(ANA, LocalDate.of(2026, 10, 15))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> delLider(ANA, LocalDate.of(2026, 10, 18))).isInstanceOf(IllegalArgumentException.class);
        // La semana que corre (17 al 23) ya no se ofrece: la ventana del resumen no llega al futuro.
        assertThatThrownBy(() -> delLider(ANA, VIERNES_FUTURO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> delLider(ANA, LocalDate.of(2026, 10, 30))).isInstanceOf(IllegalArgumentException.class);

        assertThat(banco.consultas).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
    }

    @Test
    @DisplayName("regla 02: un viernes que ya es hoy en UTC pero no en Lima sigue siendo futuro y es 400")
    void viernesFuturoEnLima() {
        // 03:00 UTC del viernes 23 = 22:00 del jueves 22 en Lima.
        banco.ahora = Instant.parse("2026-10-23T03:00:00Z");

        assertThatThrownBy(() -> delLider(ANA, VIERNES_FUTURO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(banco.consultas).isEmpty();
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
        // El color y el % son los de la ventana vigente: los mismos que el resumen lee para Ana.
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
    @DisplayName("la ventana vigente no trae dias futuros ni hoy: los siete dias ya cerraron y las acciones de despues no se ven")
    void laVigenteNoTraeFuturo() {
        ObligacionDelDia delDomingo = obligacion("Caminar", "COMPLETADO");
        ObligacionDelDia deHoy = obligacion("No es de esta ventana", "PENDIENTE");
        banco.obligacion(ANA, LocalDate.of(2026, 10, 18), delDomingo);
        banco.obligacion(ANA, LocalDate.of(2026, 10, 19), deHoy);
        banco.accion(ANA, new AccionDelDia(UUID.randomUUID(), LocalDate.of(2026, 10, 21), "Planificada para el 21",
                false, null));

        List<DiaDeLaFicha> dias = delLider(ANA, null).dias();

        assertThat(dias).extracting(DiaDeLaFicha::fecha).extracting(LocalDate::toString).containsExactly("2026-10-12",
                "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18");
        assertThat(dias).extracting(DiaDeLaFicha::motivo).containsOnlyNulls();
        assertThat(dias.getLast().obligaciones()).containsExactly(delDomingo);
        assertThat(dias).flatExtracting(DiaDeLaFicha::acciones).isEmpty();
    }

    @Test
    @DisplayName("el viernes de hoy se puede pedir: su ultimo dia es hoy y dice «en curso»; nada llega mas alla")
    void elViernesDeHoyDiceEnCurso() {
        // Viernes 23, 10:00 en Lima: la semana del 17 al 23 llega hasta hoy.
        banco.ahora = Instant.parse("2026-10-23T15:00:00Z");
        banco.semana(VIERNES_FUTURO, ANA, ventana(LocalDate.of(2026, 10, 17), false, 70, 70, 70, 70, 70, 70, null));

        SemanaDeLaPersona semana = delLider(ANA, VIERNES_FUTURO);

        assertThat(semana.cerrada()).isFalse();
        assertThat(semana.dias()).hasSize(7);
        assertThat(semana.dias().getLast().fecha()).isEqualTo(VIERNES_FUTURO);
        assertThat(semana.dias().getLast().motivo()).isEqualTo("EN_CURSO");
        assertThat(semana.dias().subList(0, 6)).extracting(DiaDeLaFicha::motivo).containsOnlyNulls();
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
        assertThat(semana.dias()).extracting(DiaDeLaFicha::motivo).containsOnly("NO_ACTIVADO").hasSize(7);
    }

    @Test
    @DisplayName("el mentor con programa propio sale «No se mide» con su %, y sin boton para avisar a su propio mentor")
    void elMentorTambienSeVe() {
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.vigente(MENTORA, ventanaPareja(DESDE_VIGENTE, false, 85));

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

    // ── «¿Programó?» (spec 018) ─────────────────────────────────────────────

    private static AccionDelDia accionDe(LocalDate fecha, String titulo, LocalTime hora) {
        return new AccionDelDia(UUID.randomUUID(), fecha, titulo, false, null, hora);
    }

    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

    @Test
    @DisplayName("spec 018: cuenta los habitos de hoy con la hora que ella eligio y las acciones de hoy con hora de inicio")
    void programoConHorasYAcciones() {
        banco.habitoDeHoy(ANA, "Ritual de la mañana", true);
        banco.habitoDeHoy(ANA, "Caminar", true);
        banco.habitoDeHoy(ANA, "Agua", false);
        banco.habitoDeHoy(ANA, "Dormir", false);
        banco.accion(ANA, accionDe(HOY, "Llamar a 3 clientes", LocalTime.of(9, 30)));
        banco.accion(ANA, accionDe(HOY, "Enviar la propuesta", LocalTime.of(11, 0)));

        ProgramacionDelDia programacion = delLider(ANA, null).programacion();

        assertThat(programacion).isEqualTo(new ProgramacionDelDia(2, 4, 2, 2));
        assertThat(programacion.programo()).isTrue();
    }

    @Test
    @DisplayName("spec 018: una accion de hoy sin hora la deja en «No»; las de otros dias no cuentan")
    void unaAccionSinHoraEsNo() {
        banco.habitoDeHoy(ANA, "Caminar", true);
        banco.accion(ANA, accionDe(HOY, "Con hora", LocalTime.of(9, 30)));
        banco.accion(ANA, accionDe(HOY, "Sin hora", null));
        banco.accion(ANA, accionDe(HOY.minusDays(1), "De ayer, sin hora", null));
        banco.accion(ANA, accionDe(HOY.plusDays(1), "De mañana, sin hora", null));

        ProgramacionDelDia programacion = delLider(ANA, null).programacion();

        assertThat(programacion).isEqualTo(new ProgramacionDelDia(1, 1, 1, 2));
        assertThat(programacion.programo()).isFalse();
    }

    @Test
    @DisplayName("spec 018: un habito apagado o en pausa no le toca hoy: no suma al total ni cuenta como sin hora")
    void apagadosYPausadosNoCuentan() {
        banco.habitoDeHoy(ANA, "Caminar", true);
        banco.habitoDeHoy(ANA, "Agua", false);
        banco.habitoDeHoy(ANA, "Apagado", false, true, false);
        banco.habitoDeHoy(ANA, "En pausa", false, false, true);
        banco.habitoDeHoy(ANA, "Apagado con hora", true, true, false);

        assertThat(delLider(ANA, null).programacion()).isEqualTo(new ProgramacionDelDia(1, 2, 0, 0));
    }

    @Test
    @DisplayName("spec 018: sin ningun habito que le toque hoy no hay pregunta: programacion nula, nunca un «No»")
    void sinHabitosNoHayProgramacion() {
        banco.accion(ANA, accionDe(HOY, "Sin hora", null));

        assertThat(delLider(ANA, null).programacion()).isNull();
    }

    @Test
    @DisplayName("spec 018: quien todavia no activo su programa no tiene un dia que programar y no se le pregunta a habits")
    void sinProgramaActivoNoSePregunta() {
        banco.aprendiz(FENIX, LUIS, "Luis Vega");
        banco.programa(LUIS, UserRole.TRAINEE, LocalDate.of(2026, 10, 20), false);
        banco.habitoDeHoy(LUIS, "Caminar", true);

        assertThat(delLider(LUIS, null).programacion()).isNull();
        assertThat(banco.horariosPedidos).isEmpty();
    }

    @Test
    @DisplayName("regla 02: «hoy» es el de la persona; con el reloj en la madrugada UTC cuenta las acciones del dia de Lima")
    void hoyEsElDeLaPersona() {
        // 03:00 UTC del martes 20 = 22:00 del lunes 19 en Lima.
        banco.ahora = Instant.parse("2026-10-20T03:00:00Z");
        banco.habitoDeHoy(ANA, "Caminar", true);
        banco.accion(ANA, accionDe(HOY, "De hoy en Lima", LocalTime.of(9, 0)));
        banco.accion(ANA, accionDe(HOY.plusDays(1), "Del martes, que en Lima aun no llega", null));

        ProgramacionDelDia programacion = delLider(ANA, null).programacion();

        assertThat(programacion).isEqualTo(new ProgramacionDelDia(1, 1, 1, 1));
        assertThat(banco.horariosPedidos).containsExactly(ANA.value() + " " + HOY);
    }

    @Test
    @DisplayName("spec 018: la programacion es la de HOY aunque se mire una semana cerrada del pasado")
    void noDependeDeLaVentana() {
        banco.habitoDeHoy(ANA, "Caminar", true);

        assertThat(delLider(ANA, SEMANA_CERRADA).programacion()).isEqualTo(new ProgramacionDelDia(1, 1, 0, 0));
        assertThat(banco.horariosPedidos).containsExactly(ANA.value() + " " + HOY);
    }

    @Test
    @DisplayName("spec 018: el mentor con programa propio tambien tiene su programacion, aunque no se mida en el semaforo")
    void elMentorTambienProgramo() {
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.vigente(MENTORA, ventanaPareja(DESDE_VIGENTE, false, 85));
        banco.habitoDeHoy(MENTORA, "Caminar", false);

        SemanaDeLaPersona semana = delLider(MENTORA, null);

        assertThat(semana.color()).isEqualTo(ColorDeLaFila.NO_SE_MIDE);
        assertThat(semana.programacion().programo()).isFalse();
    }

    @Test
    @DisplayName("spec 018: quien no puede abrir la ficha (sin permiso, otro grupo, suspendido) no dispara ninguna lectura de habitos")
    void sinPermisoNoSeLeeLaProgramacion() {
        banco.habitoDeHoy(ANA, "Caminar", true);
        banco.usuario(AJENA, "Ana de otro grupo", UserRole.TRAINEE, UserStatus.ACTIVE);
        banco.programa(AJENA, UserRole.TRAINEE, LocalDate.of(2026, 8, 17), true);

        assertThatThrownBy(() -> servicio.semanaDe(new ConsultaSemanaDePersona(ANA, FENIX, ANA.value(), null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> delLider(AJENA, null)).isInstanceOf(NoSuchElementException.class);
        banco.usuario(ANA, "Ana Pérez", UserRole.TRAINEE, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> delLider(ANA, null)).isInstanceOf(NoSuchElementException.class);

        assertThat(banco.horariosPedidos).isEmpty();
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
            assertThat(c.semanaDesde()).isEqualTo(DESDE_VIGENTE);
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
                .containsExactly(DESDE_VIGENTE, DESDE_SEMANA_CERRADA, DESDE_SEMANA_CERRADA);
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
