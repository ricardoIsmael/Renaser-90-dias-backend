package com.renaser.os.users;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.ObjetoDeOtraFormacionException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase.AsignarFormacionesCommand;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.FormacionDeLaSesion;
import com.renaser.os.users.application.ports.in.formacion.ElegirFormacionActivaUseCase;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase.TrasladarCommand;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ListTraineesCommand;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.PaginaTrainees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las formaciones (spec 022) contra Postgres real: lo que solo se ve con la base de verdad. La migración (V125–V128: el
 * catálogo, lo existente que pasa a la Escuela, el tope del día por duración, la formación activa única del personal y el
 * historial append-only), el alcance de cada persona, el aislamiento entre las tres formaciones en TODOS los pares, el
 * personal con dos formaciones que cambia la activa y el traslado de un aprendiz con su historial.
 *
 * <p>Todo es lo real (users, community, points); solo el reloj es el de pruebas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FormacionesIT {

    private static final Instant AHORA = Instant.parse("2026-10-31T15:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private AlcanceDeFormacionFinder alcance;
    @Autowired
    private ListTraineesUseCase padron;
    @Autowired
    private AsignarFormacionesDelPersonalUseCase asignar;
    @Autowired
    private ElegirFormacionActivaUseCase elegir;
    @Autowired
    private ConsultarFormacionDeLaSesionUseCase sesion;
    @Autowired
    private TrasladarAprendizDeFormacionUseCase trasladar;

    private UUID admin;

    @BeforeEach
    void sembrar() {
        reloj.fijar(AHORA);
        // Un traslado de la prueba anterior sigue moviendo al aprendiz de comunidad y de soporte (spec 024): no se le borra la cuenta a la mitad.
        com.renaser.os.EventosPendientes.esperarQueTerminen(jdbc);
        jdbc.update("DELETE FROM renaser.usuarios WHERE email LIKE '%@formaciones.test'");
        admin = usuario("ADMIN", "Admin Uno");
    }

    // ---------------------------------------------------------------------------------------------------------- migración

    @Test
    @DisplayName("El catálogo trae las tres formaciones con sus duraciones")
    void elCatalogoTraeLasTres() {
        List<String> filas = jdbc.query("SELECT codigo || ':' || array_to_string(duraciones_dias, ',') FROM renaser.formaciones ORDER BY orden",
                (rs, i) -> rs.getString(1));
        assertThat(filas).containsExactly("ESCUELA:90", "DIPLOMADO:90,365", "CERTIFICACION:90");
    }

    @Test
    @DisplayName("Lo que ya existía (y lo que sigue creándose sin decir formación) es de la Escuela y de 90 días")
    void loExistenteEsDeLaEscuela() {
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        programa(ana, 5, "ESCUELA", 90);
        jdbc.update("DELETE FROM renaser.participantes_programa WHERE usuario_id = ?", ana);
        // El camino de siempre (la app 1.6.0 y los que todavía no mandan formación): no nombra las columnas nuevas.
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone) "
                + "VALUES (?, 5, DATE '2026-10-26', TIMESTAMPTZ '2026-10-25 12:00:00+00', 'America/Lima')", ana);
        assertThat(jdbc.queryForObject("SELECT formacion || ':' || duracion_dias FROM renaser.participantes_programa WHERE usuario_id = ?",
                String.class, ana)).isEqualTo("ESCUELA:90");

        UUID cohorte = UUID.randomUUID();
        UUID celula = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte de formaciones', DATE '2026-10-01')", cohorte);
        jdbc.update("INSERT INTO renaser.celulas (id, nombre, cohorte_id) VALUES (?, 'Grupo sin formación', ?)", celula, cohorte);
        assertThat(jdbc.queryForObject("SELECT formacion FROM renaser.celulas WHERE id = ?", String.class, celula)).isEqualTo("ESCUELA");
        jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", celula);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("El día de una persona no pasa de SU duración: 90 en la Escuela, 365 en el Diplomado de un año")
    void elDiaSeAcotaConLaDuracionDeLaPersona() {
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        programa(ana, 10, "ESCUELA", 90);
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.participantes_programa SET dia_programa = 91 WHERE usuario_id = ?", ana))
                .isInstanceOf(DataAccessException.class);

        UUID beto = usuario("APRENDIZ", "Beto Ruiz");
        programa(beto, 10, "DIPLOMADO", 365);
        jdbc.update("UPDATE renaser.participantes_programa SET dia_programa = 365 WHERE usuario_id = ?", beto);
        assertThat(jdbc.queryForObject("SELECT dia_programa FROM renaser.participantes_programa WHERE usuario_id = ?", Integer.class, beto))
                .isEqualTo(365);
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.participantes_programa SET dia_programa = 366 WHERE usuario_id = ?", beto))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Los registros y los ajustes de día admiten hasta el día 365")
    void lasTablasDelDiaAdmitenHastaElAnio() {
        UUID beto = usuario("APRENDIZ", "Beto Ruiz");
        programa(beto, 200, "DIPLOMADO", 365);
        UUID habito = jdbc.queryForObject("SELECT id FROM renaser.habitos WHERE clave_sistema = 'COMMUNITY_POST'", UUID.class);
        jdbc.update("INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa, tipo_dia) "
                + "VALUES (?, ?, ?, DATE '2026-10-30', 200, CAST('TODOS' AS renaser.tipo_dia))", UUID.randomUUID(), beto, habito);
        jdbc.update("INSERT INTO renaser.ajustes_dia_programa (id, participante_id, dia_anterior, dia_nuevo, dias_ajuste_anterior, "
                + "dias_ajuste_nuevo, motivo, ajustado_por) VALUES (?, ?, 200, 300, 0, -100, 'corrección', ?)", UUID.randomUUID(), beto, admin);
        assertThat(jdbc.queryForObject("SELECT max(dia_nuevo) FROM renaser.ajustes_dia_programa WHERE participante_id = ?", Integer.class, beto))
                .isEqualTo(300);
    }

    @Test
    @DisplayName("El personal tiene una sola formación activa; las asignadas pueden ser varias")
    void unaSolaActivaPorPersona() {
        UUID mentor = usuario("MENTOR", "Marta Luna");
        jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, 'ESCUELA', true)", mentor);
        jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, 'DIPLOMADO', false)", mentor);
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.formaciones_personal SET activa = true WHERE usuario_id = ? AND formacion = 'DIPLOMADO'", mentor))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("El historial de formaciones no se edita, pero se va con la cuenta")
    void elHistorialEsAppendOnly() {
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        UUID fila = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.participaciones_anteriores (id, usuario_id, formacion, duracion_dias, fecha_fin, dia_final, resultado, "
                + "trasladado_a) VALUES (?, ?, 'ESCUELA', 90, DATE '2026-10-30', 90, 'TERMINADA', 'DIPLOMADO')", fila, ana);
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.participaciones_anteriores SET dia_final = 80 WHERE id = ?", fila))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", ana);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.participaciones_anteriores WHERE id = ?", Integer.class, fila)).isZero();
    }

    // ---------------------------------------------------------------------------------------------------------- aislamiento

    @Test
    @DisplayName("Una persona de otra formación no se ve: en los seis pares, y la propia formación sí")
    void aislamientoEnTodosLosPares() {
        for (Formacion delLider : Formacion.values()) {
            UUID lider = usuario("LIDER_MENTORES", "Líder " + delLider);
            asignar(lider, Set.of(delLider), delLider);
            for (Formacion delAprendiz : Formacion.values()) {
                UUID aprendiz = usuario("APRENDIZ", "Aprendiz de " + delAprendiz + " para " + delLider);
                programa(aprendiz, 3, delAprendiz.name(), 90);
                Set<UserId> visibles = alcance.dentroDelAlcance(List.of(UserId.of(aprendiz)), alcance.de(UserId.of(lider)));
                if (delLider == delAprendiz) {
                    assertThat(visibles).as("%s ve a su propia formación", delLider).containsExactly(UserId.of(aprendiz));
                    alcance.exigirQueLaVea(UserId.of(lider), UserId.of(aprendiz));
                } else {
                    assertThat(visibles).as("%s no ve a %s", delLider, delAprendiz).isEmpty();
                    assertThatThrownBy(() -> alcance.exigirQueLaVea(UserId.of(lider), UserId.of(aprendiz)))
                            .isInstanceOf(ObjetoDeOtraFormacionException.class);
                }
            }
        }
    }

    @Test
    @DisplayName("Administración ve todas las formaciones; un aprendiz, solo la suya")
    void administracionVeTodasYElAprendizLaSuya() {
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        programa(ana, 3, "ESCUELA", 90);
        UUID beto = usuario("APRENDIZ", "Beto Ruiz");
        programa(beto, 3, "DIPLOMADO", 365);

        assertThat(alcance.de(UserId.of(admin))).isEqualTo(AlcanceDeFormacion.todas());
        alcance.exigirQueLaVea(UserId.of(admin), UserId.of(beto));
        assertThat(alcance.de(UserId.of(ana)).formaciones()).containsExactly(Formacion.ESCUELA);
        assertThat(alcance.formacionDe(UserId.of(beto))).contains(Formacion.DIPLOMADO);
    }

    @Test
    @DisplayName("El padrón de Administración se puede acotar a una formación, y sin acotar trae todas")
    void elPadronSeFiltraPorFormacion() {
        UUID ana = usuario("APRENDIZ", "Ana Escuela");
        programa(ana, 3, "ESCUELA", 90);
        UUID beto = usuario("APRENDIZ", "Beto Diplomado");
        programa(beto, 3, "DIPLOMADO", 365);
        UUID cris = usuario("APRENDIZ", "Cris Certificación");
        programa(cris, 3, "CERTIFICACION", 90);

        assertThat(idsDe(padron(null))).contains(ana, beto, cris);
        assertThat(idsDe(padron(Formacion.DIPLOMADO))).contains(beto).doesNotContain(ana, cris);
        assertThat(idsDe(padron(Formacion.ESCUELA))).contains(ana).doesNotContain(beto, cris);
        assertThat(idsDe(padron(Formacion.CERTIFICACION))).contains(cris).doesNotContain(ana, beto);
        var filaDeBeto = padron(Formacion.DIPLOMADO).contenido().stream().filter(f -> f.id().value().equals(beto)).findFirst().orElseThrow();
        assertThat(filaDeBeto.formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(filaDeBeto.duracionDias()).isEqualTo(365);
    }

    // ---------------------------------------------------------------------------------------------------------- personal

    @Test
    @DisplayName("El personal con dos formaciones ve solo la activa y la cambia al elegir; con una sola no puede elegir otra")
    void elPersonalCambiaDeFormacionActiva() {
        UUID mentor = usuario("MENTOR", "Marta Luna");
        asignar(mentor, Set.of(Formacion.ESCUELA, Formacion.DIPLOMADO), Formacion.ESCUELA);
        assertThat(alcance.de(UserId.of(mentor)).formaciones()).containsExactly(Formacion.ESCUELA);

        FormacionDeLaSesion antes = sesion.consultar(UserId.of(mentor));
        assertThat(antes.puedeElegir()).isTrue();
        assertThat(antes.activa().codigo()).isEqualTo("ESCUELA");
        assertThat(antes.disponibles()).extracting(o -> o.codigo()).containsExactlyInAnyOrder("ESCUELA", "DIPLOMADO");

        FormacionDeLaSesion despues = elegir.elegir(UserId.of(mentor), Formacion.DIPLOMADO);
        assertThat(despues.activa().codigo()).isEqualTo("DIPLOMADO");
        assertThat(alcance.de(UserId.of(mentor)).formaciones()).containsExactly(Formacion.DIPLOMADO);

        assertThatThrownBy(() -> elegir.elegir(UserId.of(mentor), Formacion.CERTIFICACION)).isInstanceOf(RuntimeException.class);

        UUID sola = usuario("MENTOR", "Sara Solís");
        asignar(sola, Set.of(Formacion.CERTIFICACION), Formacion.CERTIFICACION);
        assertThat(sesion.consultar(UserId.of(sola)).puedeElegir()).isFalse();
        assertThatThrownBy(() -> elegir.elegir(UserId.of(sola), Formacion.ESCUELA)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Un mentor nuevo sin formación asignada no ve ninguna, y Administración ve todas en su sesión")
    void sinFormacionNoSeVeNada() {
        UUID mentor = usuario("MENTOR", "Mario Sin");
        jdbc.update("DELETE FROM renaser.formaciones_personal WHERE usuario_id = ?", mentor);
        assertThat(alcance.de(UserId.of(mentor)).esVacio()).isTrue();
        assertThat(sesion.consultar(UserId.of(mentor)).sinFormacion()).isTrue();
        assertThat(sesion.consultar(UserId.of(admin)).veTodas()).isTrue();
    }

    // ---------------------------------------------------------------------------------------------------------- traslado

    @Test
    @DisplayName("Trasladar a un aprendiz que terminó: reinicia su programa en la otra formación y deja su historial")
    void trasladarAlQueTermino() {
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        programa(ana, 90, "ESCUELA", 90);
        // El día de una persona se deriva de las fechas (no de la columna): el Día 1 fue hace 90 días, así que hoy es su día 90.
        jdbc.update("UPDATE renaser.participantes_programa SET programa_completado = true, fecha_inicio = DATE '2026-08-03' WHERE usuario_id = ?", ana);

        trasladar.trasladar(new TrasladarCommand(UserId.of(admin), UserId.of(ana), Formacion.DIPLOMADO, 365, false, null));

        assertThat(jdbc.queryForObject("SELECT formacion || ':' || duracion_dias || ':' || dia_programa || ':' || programa_completado "
                + "FROM renaser.participantes_programa WHERE usuario_id = ?", String.class, ana)).isEqualTo("DIPLOMADO:365:0:false");
        assertThat(jdbc.queryForObject("SELECT formacion || ':' || resultado || ':' || trasladado_a || ':' || dia_final "
                + "FROM renaser.participaciones_anteriores WHERE usuario_id = ?", String.class, ana))
                .isEqualTo("ESCUELA:TERMINADA:DIPLOMADO:90");
        assertThat(alcance.formacionDe(UserId.of(ana))).contains(Formacion.DIPLOMADO);
    }

    @Test
    @DisplayName("Trasladar a quien no terminó pide darlo por terminado con un motivo; sin motivo no pasa")
    void trasladarAlQueNoTerminoPideMotivo() {
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        programa(ana, 20, "ESCUELA", 90);

        assertThatThrownBy(() -> trasladar.trasladar(new TrasladarCommand(UserId.of(admin), UserId.of(ana), Formacion.CERTIFICACION, 90, false, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> trasladar.trasladar(new TrasladarCommand(UserId.of(admin), UserId.of(ana), Formacion.CERTIFICACION, 90, true, " ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(alcance.formacionDe(UserId.of(ana))).contains(Formacion.ESCUELA);

        trasladar.trasladar(new TrasladarCommand(UserId.of(admin), UserId.of(ana), Formacion.CERTIFICACION, 90, true, "Cambió de programa"));
        assertThat(alcance.formacionDe(UserId.of(ana))).contains(Formacion.CERTIFICACION);
        assertThat(jdbc.queryForObject("SELECT resultado || ':' || motivo FROM renaser.participaciones_anteriores WHERE usuario_id = ?",
                String.class, ana)).isEqualTo("DADA_POR_TERMINADA:Cambió de programa");
    }

    @Test
    @DisplayName("Una duración que la formación de destino no ofrece no se acepta")
    void laDuracionTieneQueSerDelDestino() {
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        programa(ana, 90, "ESCUELA", 90);
        jdbc.update("UPDATE renaser.participantes_programa SET programa_completado = true WHERE usuario_id = ?", ana);
        assertThatThrownBy(() -> trasladar.trasladar(new TrasladarCommand(UserId.of(admin), UserId.of(ana), Formacion.CERTIFICACION, 365, false, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Tras el traslado el líder de la formación vieja ya no ve a la persona y el de la nueva sí")
    void despuesDelTrasladoCambiaQuienLaVe() {
        UUID liderEscuela = usuario("LIDER_MENTORES", "Líder Escuela");
        asignar(liderEscuela, Set.of(Formacion.ESCUELA), Formacion.ESCUELA);
        UUID liderDiplomado = usuario("LIDER_MENTORES", "Líder Diplomado");
        asignar(liderDiplomado, Set.of(Formacion.DIPLOMADO), Formacion.DIPLOMADO);
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        programa(ana, 90, "ESCUELA", 90);
        jdbc.update("UPDATE renaser.participantes_programa SET programa_completado = true WHERE usuario_id = ?", ana);

        alcance.exigirQueLaVea(UserId.of(liderEscuela), UserId.of(ana));
        trasladar.trasladar(new TrasladarCommand(UserId.of(admin), UserId.of(ana), Formacion.DIPLOMADO, 90, false, null));

        assertThatThrownBy(() -> alcance.exigirQueLaVea(UserId.of(liderEscuela), UserId.of(ana))).isInstanceOf(ObjetoDeOtraFormacionException.class);
        alcance.exigirQueLaVea(UserId.of(liderDiplomado), UserId.of(ana));
    }

    // ---------------------------------------------------------------------------------------------------------- apoyo

    private PaginaTrainees padron(Formacion formacion) {
        return padron.listar(new ListTraineesCommand(UserId.of(admin), 0, 200, null, false, null, formacion));
    }

    private static Set<UUID> idsDe(PaginaTrainees pagina) {
        return pagina.contenido().stream().map(f -> f.id().value()).collect(Collectors.toSet());
    }

    private void asignar(UUID persona, Set<Formacion> formaciones, Formacion activa) {
        asignar.asignar(new AsignarFormacionesCommand(UserId.of(admin), UserId.of(persona), formaciones, activa));
    }

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario))",
                id, id + "@formaciones.test", nombre, rol);
        return id;
    }

    private void programa(UUID id, int dia, String formacion, int duracion) {
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone, "
                + "formacion, duracion_dias) VALUES (?, ?, DATE '2026-10-01', TIMESTAMPTZ '2026-09-30 12:00:00+00', 'America/Lima', ?, ?)",
                id, dia, formacion, duracion);
    }
}
