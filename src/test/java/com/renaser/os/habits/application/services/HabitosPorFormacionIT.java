package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.categoria.CategoriaDelCatalogo;
import com.renaser.os.habits.application.ports.in.categoria.ConsultarCategoriasDeMiFormacionUseCase;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.CambiarActivaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.CrearCategoriaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.EliminarCategoriaCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarCatalogoAdminUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CopiarHabitoAOtraFormacionUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CopiarHabitoAOtraFormacionUseCase.CopiarHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase.CrearHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.HabitoDelCatalogo;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.ObjetoDeOtraFormacionException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 023 (D-300) de punta a punta contra Postgres real: cada formación tiene sus categorías y su catálogo, y nada se cruza entre
 * Escuela, Diplomado y Certificación (SC-002, SC-006); lo de hoy es de la Escuela sin cambiar (SC-003).
 *
 * <p>Fixture coherente (regla 03): el programa arrancó el 28/9, así que el 12/10 es el Día 15; el reloj va a las 15:00 UTC (10:00 en Lima).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HabitosPorFormacionIT {

    private static final Instant AHORA = Instant.parse("2026-10-12T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 12);
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 28);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private GenerarTracksDelDiaUseCase generar;
    @Autowired
    private GestionarCategoriasUseCase categorias;
    @Autowired
    private ConsultarCategoriasDeMiFormacionUseCase categoriasDeMiFormacion;
    @Autowired
    private CrearHabitoUseCase crearHabito;
    @Autowired
    private ConsultarCatalogoAdminUseCase catalogoAdmin;
    @Autowired
    private CopiarHabitoAOtraFormacionUseCase copiar;
    @Autowired
    private ConsultarMisHabitosUseCase misHabitos;
    @Autowired
    private ElegirHabitoUseCase elegir;
    @Autowired
    private LoadHabitoPort loadHabitoPort;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();
    private final List<String> categoriasCreadas = new ArrayList<>();
    private UserId admin;
    private UserId mentor;
    private UserId deLaEscuela;
    private UserId delDiplomado;
    private HabitoId habitoDeLaEscuela;

    @BeforeEach
    void preparar() {
        reloj.fijar(AHORA);
        admin = usuario("ADMIN", null);
        mentor = usuario("LIDER_MENTORES", null);
        deLaEscuela = usuario("APRENDIZ", "ESCUELA");
        delDiplomado = usuario("APRENDIZ", "DIPLOMADO");
        habitoDeLaEscuela = habitoSql("Hábito solo de la Escuela", "CUERPO", "ESCUELA", null);
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            jdbc.update("DELETE FROM renaser.desbloqueos_habito WHERE participante_id = ?", id);
            jdbc.update("DELETE FROM renaser.formaciones_personal WHERE usuario_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // La base de la suite es descartable.
            }
        });
        habitos.forEach(id -> {
            jdbc.update("DELETE FROM renaser.audiencias_habito WHERE habito_id = ?", id);
            jdbc.update("DELETE FROM renaser.horarios_habito WHERE habito_id = ?", id);
            jdbc.update("DELETE FROM renaser.guias_habito WHERE habito_id = ?", id);
            jdbc.update("DELETE FROM renaser.registros_habito WHERE habito_id = ?", id);
            jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id);
        });
        categoriasCreadas.forEach(clave -> jdbc.update("DELETE FROM renaser.categorias_habito WHERE clave = ?", clave));
    }

    // ── Migración: lo de hoy es de la Escuela, sin cambiar (SC-003) ─────────────

    @Test
    @DisplayName("las cuatro categorías de siempre son de la Escuela, con su nombre, su orden y su ícono")
    void lasCuatroSonDeLaEscuela() {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT clave, formacion, nombre, orden, icono, activa FROM renaser.categorias_habito WHERE clave IN "
                        + "('CUERPO','MENTE','CONSCIENCIA','ESPIRITU') ORDER BY orden");

        assertThat(filas).extracting(f -> f.get("clave")).containsExactly("CUERPO", "MENTE", "CONSCIENCIA", "ESPIRITU");
        assertThat(filas).extracting(f -> f.get("formacion")).containsOnly("ESCUELA");
        assertThat(filas).extracting(f -> f.get("nombre")).containsExactly("Cuerpo", "Mente", "Emociones", "Espíritu");
        assertThat(filas).extracting(f -> f.get("icono")).containsExactly("activity", "brain", "heart", "sparkles");
        assertThat(filas).extracting(f -> f.get("activa")).containsOnly(true);
    }

    @Test
    @DisplayName("los hábitos del catálogo que ya existían son de la Escuela y el Diplomado y la Certificación arrancan sin ninguno")
    void catalogoExistenteEsDeLaEscuela() {
        Integer sinFormacion = jdbc.queryForObject(
                "SELECT COUNT(*) FROM renaser.habitos WHERE ambito = 'SISTEMA' AND formacion IS NULL", Integer.class);
        Integer sembradosEnOtra = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.habitos WHERE ambito = 'SISTEMA' "
                + "AND formacion <> 'ESCUELA' AND clave_sistema IS NOT NULL", Integer.class);
        Integer clasesDiarias = jdbc.queryForObject(
                "SELECT COUNT(*) FROM renaser.habitos WHERE clave_sistema = 'DAILY_CLASS' AND formacion = 'ESCUELA'", Integer.class);

        assertThat(sinFormacion).isZero();
        assertThat(sembradosEnOtra).isZero();
        assertThat(clasesDiarias).isEqualTo(1);
    }

    // ── La base impone el aislamiento (FR-001, FR-002, FR-006) ──────────────────

    @Test
    @DisplayName("un hábito del catálogo no puede estar en la categoría de otra formación (llave compuesta)")
    void categoriaDeSuFormacion() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO renaser.habitos (id, titulo, categoria_clave, formacion) VALUES (?, 'Cruzado', 'CUERPO', 'DIPLOMADO')
                """, UUID.randomUUID())).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("el título es único dentro de la formación (sin tildes ni mayúsculas) y válido en otra")
    void tituloPorFormacion() {
        String clave = categoria(Formacion.DIPLOMADO, "Estudio profundo");
        HabitoId enDiplomado = habitoSql("Meditación guiada", clave, "DIPLOMADO", null);
        habitoSql("Meditación guiada", "CUERPO", "ESCUELA", null);

        assertThat(enDiplomado).isNotNull();
        assertThatThrownBy(() -> habitoSql("MEDITACION GUIADA", clave, "DIPLOMADO", null)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("la clave funcional es única por formación: la copia de un hábito de flujo propio conserva la suya")
    void claveFuncionalPorFormacion() {
        String clave = categoria(Formacion.DIPLOMADO, "Flujos");
        String claveCertificacion = categoria(Formacion.CERTIFICACION, "Flujos");
        String funcion = "FUNCION_" + UUID.randomUUID().toString().substring(0, 8);
        habitoSql("Flujo en la Escuela", "CUERPO", "ESCUELA", funcion);
        habitoSql("Flujo en el Diplomado", clave, "DIPLOMADO", funcion);
        habitoSql("Flujo en la Certificación", claveCertificacion, "CERTIFICACION", funcion);

        assertThatThrownBy(() -> habitoSql("Otro flujo en el Diplomado", clave, "DIPLOMADO", funcion)).isInstanceOf(DataAccessException.class);
        assertThat(loadHabitoPort.porClaveSistema(funcion, Formacion.DIPLOMADO)).get()
                .extracting(h -> h.titulo()).isEqualTo("Flujo en el Diplomado");
        assertThat(loadHabitoPort.porClaveSistema(funcion, Formacion.CERTIFICACION)).get()
                .extracting(h -> h.titulo()).isEqualTo("Flujo en la Certificación");
    }

    // ── Lo que ve cada persona (SC-002) ─────────────────────────────────────────

    @Test
    @DisplayName("un aprendiz del Diplomado y uno de la Escuela generan y ven cada uno solo los hábitos de su formación")
    void aislamientoDeLaGeneracionYDeLaLista() {
        String clave = categoria(Formacion.DIPLOMADO, "Estudio");
        HabitoId delaDiplomado = habitoSql("Hábito solo del Diplomado", clave, "DIPLOMADO", null);

        generar.generar(deLaEscuela, HOY);
        generar.generar(delDiplomado, HOY);

        assertThat(generadosPara(deLaEscuela)).contains(habitoDeLaEscuela).doesNotContain(delaDiplomado);
        assertThat(generadosPara(delDiplomado)).containsExactly(delaDiplomado);
        assertThat(misHabitos.consultar(deLaEscuela)).extracting(v -> v.habito().id()).contains(habitoDeLaEscuela)
                .doesNotContain(delaDiplomado);
        assertThat(misHabitos.consultar(delDiplomado)).extracting(v -> v.habito().id()).containsExactly(delaDiplomado);
    }

    @Test
    @DisplayName("una formación sin hábitos no es un error: la persona no recibe ninguno")
    void formacionSinHabitos() {
        UserId deLaCertificacion = usuario("APRENDIZ", "CERTIFICACION");

        generar.generar(deLaCertificacion, HOY);

        assertThat(generadosPara(deLaCertificacion)).isEmpty();
        assertThat(misHabitos.consultar(deLaCertificacion)).isEmpty();
    }

    @Test
    @DisplayName("pedir por id un hábito del catálogo de otra formación responde como si no existiera (SC-006)")
    void porIdDeOtraFormacion() {
        String clave = categoria(Formacion.DIPLOMADO, "Estudio");
        HabitoId delaDiplomado = habitoSql("Hábito solo del Diplomado", clave, "DIPLOMADO", null);

        assertThatThrownBy(() -> elegir.elegir(new ElegirHabitoCommand(delDiplomado, habitoDeLaEscuela, null)))
                .isInstanceOf(ObjetoDeOtraFormacionException.class).hasMessage(ObjetoDeOtraFormacionException.MENSAJE);
        assertThatThrownBy(() -> elegir.elegir(new ElegirHabitoCommand(deLaEscuela, delaDiplomado, null)))
                .isInstanceOf(ObjetoDeOtraFormacionException.class);
        assertThat(elegir.elegir(new ElegirHabitoCommand(delDiplomado, delaDiplomado, null))).isNotNull();
    }

    @Test
    @DisplayName("las dimensiones de Training: cada persona recibe las categorías activas de SU formación, en el orden de Administración")
    void categoriasDeMiFormacion() {
        String estudio = categoria(Formacion.DIPLOMADO, "Estudio");
        String practica = categoria(Formacion.DIPLOMADO, "Práctica");
        categorias.cambiarActiva(new CambiarActivaCommand(admin, practica, false));

        var delDiplomadoVen = categoriasDeMiFormacion.consultar(delDiplomado, null);
        var delaEscuelaVen = categoriasDeMiFormacion.consultar(deLaEscuela, null);

        assertThat(delDiplomadoVen.formacion()).contains(Formacion.DIPLOMADO);
        assertThat(delDiplomadoVen.categorias()).extracting(c -> c.clave()).containsExactly(estudio);
        assertThat(delaEscuelaVen.formacion()).contains(Formacion.ESCUELA);
        assertThat(delaEscuelaVen.categorias()).extracting(c -> c.clave()).contains("CUERPO", "MENTE", "CONSCIENCIA", "ESPIRITU")
                .doesNotContain(estudio, practica);
        assertThatThrownBy(() -> categoriasDeMiFormacion.consultar(deLaEscuela, Formacion.DIPLOMADO))
                .isInstanceOf(ObjetoDeOtraFormacionException.class);
    }

    // ── Administración (SC-001, SC-005, SC-009) ─────────────────────────────────

    @Test
    @DisplayName("Administración crea una categoría y un hábito en el Diplomado; el aprendiz del Diplomado lo recibe y el de la Escuela no")
    void adminArmaLaFormacion() {
        CategoriaDelCatalogo estudio = categorias.crear(new CrearCategoriaCommand(admin, Formacion.DIPLOMADO, "Estudio", "bookOpen"));
        categoriasCreadas.add(estudio.categoria().clave());
        HabitoDelCatalogo creado = crearHabito.crear(new CrearHabitoCommand(admin, "Leer treinta minutos", TipoHabito.CHECKBOX,
                new DetallesHabito("desc", estudio.categoria().clave(), ExigenciaEvidencia.OPCIONAL, false, false), Audiencia.PARA_TODOS,
                null, Formacion.DIPLOMADO));
        habitos.add(creado.habito().id().value());

        generar.generar(delDiplomado, HOY);
        generar.generar(deLaEscuela, HOY);

        assertThat(creado.habito().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(generadosPara(delDiplomado)).containsExactly(creado.habito().id());
        assertThat(generadosPara(deLaEscuela)).doesNotContain(creado.habito().id());
        assertThat(catalogoAdmin.listar(admin, null, Formacion.DIPLOMADO)).extracting(h -> h.habito().id()).contains(creado.habito().id());
        assertThat(catalogoAdmin.listar(admin, null, Formacion.ESCUELA)).extracting(h -> h.habito().id()).doesNotContain(creado.habito().id());
        assertThat(catalogoAdmin.listar(admin, null, null)).extracting(h -> h.habito().id())
                .contains(creado.habito().id(), habitoDeLaEscuela);
    }

    @Test
    @DisplayName("la categoría con hábitos activos no se desactiva ni se elimina; sin ellos se desactiva y, vacía, se elimina")
    void reglasDeLaCategoria() {
        CategoriaDelCatalogo estudio = categorias.crear(new CrearCategoriaCommand(admin, Formacion.DIPLOMADO, "Estudio", null));
        categoriasCreadas.add(estudio.categoria().clave());
        String clave = estudio.categoria().clave();
        HabitoId habito = habitoSql("Hábito de estudio", clave, "DIPLOMADO", null);

        assertThatThrownBy(() -> categorias.cambiarActiva(new CambiarActivaCommand(admin, clave, false)))
                .hasMessage(CategoriasDeHabitoAdminService.CON_HABITOS_ACTIVOS);
        assertThatThrownBy(() -> categorias.eliminar(new EliminarCategoriaCommand(admin, clave)))
                .hasMessage(CategoriasDeHabitoAdminService.CON_HABITOS_ACTIVOS);

        jdbc.update("UPDATE renaser.habitos SET activo = false WHERE id = ?", habito.value());
        assertThatThrownBy(() -> categorias.eliminar(new EliminarCategoriaCommand(admin, clave)))
                .hasMessage(CategoriasDeHabitoAdminService.CON_HISTORIAL);
        assertThat(categorias.cambiarActiva(new CambiarActivaCommand(admin, clave, false)).categoria().activa()).isFalse();

        jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", habito.value());
        categorias.eliminar(new EliminarCategoriaCommand(admin, clave));
        Integer quedan = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.categorias_habito WHERE clave = ?", Integer.class, clave);
        assertThat(quedan).isZero();
    }

    @Test
    @DisplayName("el mismo nombre de categoría en otra formación es válido; en la misma, no")
    void nombreDeCategoriaPorFormacion() {
        String enDiplomado = categoria(Formacion.DIPLOMADO, "Estudio");
        String enCertificacion = categoria(Formacion.CERTIFICACION, "Estudio");

        assertThat(enDiplomado).isEqualTo("DIPLOMADO_ESTUDIO");
        assertThat(enCertificacion).isEqualTo("CERTIFICACION_ESTUDIO");
        assertThatThrownBy(() -> categorias.crear(new CrearCategoriaCommand(admin, Formacion.DIPLOMADO, "ESTUDIO", null)))
                .hasMessage(CategoriasDeHabitoAdminService.NOMBRE_REPETIDO);
    }

    @Test
    @DisplayName("un líder de mentores no configura categorías ni hábitos de ninguna formación (403) y no se escribe nada")
    void sinPermiso() {
        assertThatThrownBy(() -> categorias.crear(new CrearCategoriaCommand(mentor, Formacion.DIPLOMADO, "Estudio", null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> catalogoAdmin.listar(mentor, null, Formacion.DIPLOMADO)).isInstanceOf(NotAuthorizedException.class);
        Integer creadas = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.categorias_habito WHERE formacion = 'DIPLOMADO' AND nombre = 'Estudio'",
                Integer.class);
        assertThat(creadas).isZero();
    }

    @Test
    @DisplayName("copiar a otra formación: queda un hábito independiente con su horario, y cambiar uno no cambia el otro")
    void copiarEsIndependiente() {
        String estudio = categoria(Formacion.DIPLOMADO, "Estudio");

        HabitoDelCatalogo copia = copiar.copiar(new CopiarHabitoCommand(admin, habitoDeLaEscuela, Formacion.DIPLOMADO, estudio));
        habitos.add(copia.habito().id().value());

        assertThat(copia.habito().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(copia.habito().id()).isNotEqualTo(habitoDeLaEscuela);
        Integer horarios = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.horarios_habito WHERE habito_id = ?", Integer.class,
                copia.habito().id().value());
        assertThat(horarios).isEqualTo(1);
        jdbc.update("UPDATE renaser.habitos SET activo = false WHERE id = ?", copia.habito().id().value());
        Boolean originalActivo = jdbc.queryForObject("SELECT activo FROM renaser.habitos WHERE id = ?", Boolean.class, habitoDeLaEscuela.value());
        assertThat(originalActivo).isTrue();
        Integer auditoria = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.auditoria_catalogo_habitos WHERE accion = 'COPIADO' AND clave = ?",
                Integer.class, copia.habito().id().value().toString());
        assertThat(auditoria).isEqualTo(1);
    }

    @Test
    @DisplayName("la auditoría de solo agregar: no se puede cambiar ni borrar una fila")
    void auditoriaDeSoloAgregar() {
        String clave = categoria(Formacion.DIPLOMADO, "Estudio");

        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.auditoria_catalogo_habitos SET accion = 'ELIMINADA' WHERE clave = ?", clave))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM renaser.auditoria_catalogo_habitos WHERE clave = ?", clave))
                .isInstanceOf(DataAccessException.class);
    }

    // ── Fixture ─────────────────────────────────────────────────────────────────

    private List<HabitoId> generadosPara(UserId persona) {
        return jdbc.queryForList("SELECT habito_id FROM renaser.registros_habito WHERE participante_id = ? AND fecha_ejecucion = ?",
                UUID.class, persona.value(), HOY).stream().map(HabitoId::of).toList();
    }

    private String categoria(Formacion formacion, String nombre) {
        CategoriaDelCatalogo creada = categorias.crear(new CrearCategoriaCommand(admin, formacion, nombre, null));
        categoriasCreadas.add(creada.categoria().clave());
        return creada.categoria().clave();
    }

    private UserId usuario(String rol, String formacion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Fixture " + rol, rol);
        com.renaser.os.FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        if (formacion != null) {
            jdbc.update("""
                    INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone, formacion)
                    VALUES (?, 15, ?, ?, 'America/Lima', ?)
                    """, id, INICIO, Timestamp.from(Instant.parse("2026-09-26T15:00:00Z")), formacion);
        }
        usuarios.add(id);
        return UserId.of(id);
    }

    private HabitoId habitoSql(String titulo, String categoria, String formacion, String claveSistema) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.habitos (id, titulo, categoria_clave, formacion, clave_sistema) VALUES (?, ?, ?, ?, ?)
                """, id, titulo, categoria, formacion, claveSistema);
        jdbc.update("""
                INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, tipo_dia, hora_disparo, hora_limite)
                VALUES (?, 1, 'TODOS', '06:00', '22:00')
                """, id);
        habitos.add(id);
        return HabitoId.of(id);
    }
}
