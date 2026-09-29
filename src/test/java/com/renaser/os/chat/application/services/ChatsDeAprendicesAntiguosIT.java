package com.renaser.os.chat.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.conversacion.CompletarChatsDeAprendicesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El relleno de los chats de los aprendices que entraron antes de D-136/D-173 (D-224), contra Postgres
 * de verdad: la semilla es SQL a propósito — es exactamente como quedaron los aprendices viejos, con su
 * cuenta, su fila en el programa y su grupo, pero sin ningún evento que les haya creado los chats.
 *
 * <p>Lo que un doble no puede probar: que el UNIQUE de {@code clave_directa} y las lecturas de
 * {@code participantes_programa} / {@code asignaciones_celula} dejan pasar a quien corresponde y a
 * nadie más, que no se escribe ni un mensaje, y que el administrador lo ve en SU lista.
 *
 * <p>El barrido recorre toda la base, así que las aserciones miran solo a las personas de esta prueba,
 * y la limpieza borra toda conversación que no existía antes (también las de datos ajenos a esta
 * prueba que el barrido haya completado).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ChatsDeAprendicesAntiguosIT {

    @Autowired
    private CompletarChatsDeAprendicesUseCase completarChats;
    @Autowired
    private ListarConversacionesUseCase listarConversaciones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID cohorteId;
    private final List<UUID> celulas = new ArrayList<>();
    private final List<UUID> usuarios = new ArrayList<>();
    private Set<UUID> conversacionesPrevias;

    @BeforeEach
    void seed() {
        celulas.clear();
        usuarios.clear();
        conversacionesPrevias = new HashSet<>(jdbcTemplate.queryForList("SELECT id FROM renaser.conversaciones", UUID.class));
        cohorteId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte antigua', CURRENT_DATE - 30)",
                cohorteId);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.queryForList("SELECT id FROM renaser.conversaciones", UUID.class).stream()
                .filter(id -> !conversacionesPrevias.contains(id))
                .forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    @Test
    @DisplayName("un aprendiz de antes queda con su soporte (él + el staff) y su chat de dos con el mentor, sin un solo mensaje, y el admin lo ve en su lista")
    void aprendizViejoQuedaConSusChats() {
        UUID admin = usuario("ADMIN", "Rosa Admin", "ACTIVO");
        UUID grupo = grupo("REGULAR");
        UUID mentor = usuario("MENTOR", "Carlos Ramírez", "ACTIVO");
        UUID ana = aprendizEn(grupo, "Ana María Pérez", "ACTIVO");
        asignar(grupo, mentor, "MENTOR");

        completarChats.completarTodos();

        UUID soporte = soporteDe(ana);
        assertThat(soporte).as("soporte de Ana").isNotNull();
        assertThat(participantes(soporte)).contains(ana, admin).doesNotContain(mentor);
        UUID deDos = chatDeDos(ana, mentor);
        assertThat(deDos).as("chat de dos Ana-Carlos").isNotNull();
        assertThat(participantes(deDos)).containsExactlyInAnyOrder(ana, mentor);
        assertThat(mensajesEn(soporte) + mensajesEn(deDos)).as("sin bienvenida retroactiva").isZero();
        assertThat(listarConversaciones.listar(UserId.of(admin)))
                .as("el administrador lo ve en su lista")
                .anySatisfy(resumen -> assertThat(resumen.conversacion().id().value()).isEqualTo(soporte));
    }

    @Test
    @DisplayName("correrlo dos veces no duplica nada: la segunda no crea ni soporte ni chat de dos para estas personas")
    void idempotente() {
        UUID grupo = grupo("REGULAR");
        UUID mentor = usuario("MENTOR", "Carlos Ramírez", "ACTIVO");
        UUID ana = aprendizEn(grupo, "Ana Pérez", "ACTIVO");
        asignar(grupo, mentor, "MENTOR");

        completarChats.completarTodos();
        int despuesDeLaPrimera = conversacionesDe(ana);
        var segunda = completarChats.completarTodos();

        assertThat(despuesDeLaPrimera).isEqualTo(2);
        assertThat(conversacionesDe(ana)).isEqualTo(2);
        assertThat(segunda.soportesCreados()).isZero();
        assertThat(segunda.chatsDeDosAbiertos()).isZero();
    }

    @Test
    @DisplayName("suspendidos, pendientes de aprobar (INACTIVO), aprendices sin programa y el staff no reciben soporte; con un suspendido no se abre chat de dos")
    void soloAprendicesActivosEnElPrograma() {
        UUID grupo = grupo("REGULAR");
        UUID mentor = usuario("MENTOR", "Carlos Ramírez", "ACTIVO");
        asignar(grupo, mentor, "MENTOR");
        UUID suspendido = aprendizEn(grupo, "Luis Soto", "SUSPENDIDO");
        UUID inactivo = aprendizEn(grupo, "Eva Ríos", "INACTIVO");
        UUID sinPrograma = usuario("APRENDIZ", "Juan Invitado", "ACTIVO");
        UUID admin = usuario("ADMIN", "Rosa Admin", "ACTIVO");
        programa(admin, null);

        completarChats.completarTodos();

        assertThat(soporteDe(suspendido)).isNull();
        assertThat(soporteDe(inactivo)).isNull();
        assertThat(soporteDe(sinPrograma)).as("D-136: el soporte nace al entrar al programa").isNull();
        assertThat(soporteDe(admin)).as("el staff no tiene soporte propio").isNull();
        assertThat(soporteDe(mentor)).isNull();
        assertThat(chatDeDos(suspendido, mentor)).isNull();
        assertThat(chatDeDos(inactivo, mentor)).isNull();
    }

    @Test
    @DisplayName("un grupo fuera de su período no abre chats de dos; la recepción sí, con cada guía")
    void soloGruposOperativos() {
        UUID vencido = grupoConPeriodo("REGULAR", "CURRENT_DATE - 60", "CURRENT_DATE - 30");
        UUID mentor = usuario("MENTOR", "Carlos Ramírez", "ACTIVO");
        asignar(vencido, mentor, "MENTOR");
        UUID ana = aprendizEn(vencido, "Ana Pérez", "ACTIVO");
        UUID recepcion = grupo("RECEPCION");
        UUID guia = usuario("MENTOR", "Guía Uno", "ACTIVO");
        asignar(recepcion, guia, "GUIA");
        UUID luis = aprendizEn(recepcion, "Luis Soto", "ACTIVO");

        completarChats.completarTodos();

        assertThat(chatDeDos(ana, mentor)).isNull();
        assertThat(soporteDe(ana)).as("el soporte no depende del grupo").isNotNull();
        assertThat(chatDeDos(luis, guia)).isNotNull();
    }

    @Test
    @DisplayName("al reactivarse, la cuenta recibe en el momento su soporte y su chat de dos, sin mensajes")
    void reactivacion() {
        UUID grupo = grupo("REGULAR");
        UUID mentor = usuario("MENTOR", "Carlos Ramírez", "ACTIVO");
        asignar(grupo, mentor, "MENTOR");
        UUID ana = aprendizEn(grupo, "Ana Pérez", "SUSPENDIDO");
        completarChats.completarTodos();
        assertThat(soporteDe(ana)).isNull();

        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'ACTIVO' WHERE id = ?", ana);
        completarChats.completarDe(UserId.of(ana));

        assertThat(soporteDe(ana)).isNotNull();
        assertThat(chatDeDos(ana, mentor)).isNotNull();
        assertThat(mensajesEn(soporteDe(ana))).isZero();
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private UUID grupo(String tipo) {
        return grupoConPeriodo(tipo, "CURRENT_DATE - 5", "CURRENT_DATE + 20");
    }

    private UUID grupoConPeriodo(String tipo, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin) "
                + "VALUES (?, 'Fenix', ?, CAST(? AS renaser.tipo_celula), " + inicio + ", " + fin + ")", id, cohorteId, tipo);
        celulas.add(id);
        return id;
    }

    private UUID usuario(String rol, String nombre, String estado) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
        usuarios.add(id);
        return id;
    }

    /** Un aprendiz aprobado hace días: cuenta, fila en el programa (Día 10, coherente con su inicio) y grupo. */
    private UUID aprendizEn(UUID grupo, String nombre, String estado) {
        UUID id = usuario("APRENDIZ", nombre, estado);
        programa(id, grupo);
        asignar(grupo, id, "APRENDIZ");
        return id;
    }

    private void programa(UUID usuario, UUID grupo) {
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, timezone, celula_id)
                VALUES (?, 10, CURRENT_DATE - 9, 'America/Lima', ?)
                """, usuario, grupo);
    }

    private void asignar(UUID celula, UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '3 days',
                        'ADMINISTRATIVO', ?)
                """, id, celula, usuario, funcion, "prueba|" + id);
    }

    private UUID soporteDe(UUID aprendiz) {
        return jdbcTemplate.queryForList("SELECT id FROM renaser.conversaciones WHERE tipo = 'SOPORTE' AND clave_directa = ?",
                UUID.class, "soporte:" + aprendiz).stream().findFirst().orElse(null);
    }

    private UUID chatDeDos(UUID uno, UUID otro) {
        String a = uno.toString();
        String b = otro.toString();
        String clave = a.compareTo(b) <= 0 ? a + "_" + b : b + "_" + a;
        return jdbcTemplate.queryForList("SELECT id FROM renaser.conversaciones WHERE tipo = 'DIRECTA' AND clave_directa = ?",
                UUID.class, clave).stream().findFirst().orElse(null);
    }

    private List<UUID> participantes(UUID conversacion) {
        return jdbcTemplate.queryForList("SELECT usuario_id FROM renaser.participantes_conversacion WHERE conversacion_id = ?",
                UUID.class, conversacion);
    }

    private int conversacionesDe(UUID usuario) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM renaser.participantes_conversacion pc
                JOIN renaser.conversaciones c ON c.id = pc.conversacion_id
                WHERE pc.usuario_id = ? AND c.tipo IN ('SOPORTE', 'DIRECTA')
                """, Integer.class, usuario);
        return n == null ? 0 : n;
    }

    private int mensajesEn(UUID conversacion) {
        Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE conversacion_id = ?",
                Integer.class, conversacion);
        return n == null ? 0 : n;
    }
}
