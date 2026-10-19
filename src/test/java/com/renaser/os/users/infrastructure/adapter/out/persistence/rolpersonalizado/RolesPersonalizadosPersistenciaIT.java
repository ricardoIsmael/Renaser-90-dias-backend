package com.renaser.os.users.infrastructure.adapter.out.persistence.rolpersonalizado;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.BorrarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.CrearRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.EditarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.ReemplazarRolPersonalizadoCommand;
import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.RolPersonalizadoResumen;
import com.renaser.os.users.api.RolesPersonalizadosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los roles personalizados contra Postgres con V105 (D-272): el caso de uso de gestión con el adaptador real, la lectura
 * para otros módulos, el nombre que la cuenta lee en su mismo SELECT ({@code @Formula}) y lo que la base defiende sola
 * (FK de la cuenta, CHECK del rol base).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RolesPersonalizadosPersistenciaIT {

    @Autowired
    private GestionarRolesPersonalizadosUseCase gestion;
    @Autowired
    private RolesPersonalizadosFinder finder;
    @Autowired
    private UserSummaryFinder usuarios;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> cuentas = new ArrayList<>();
    private final Instant inicio = Instant.now();

    @AfterEach
    void limpiar() throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite && jdbc.queryForObject("SELECT count(*) FROM event_publication "
                + "WHERE completion_date IS NULL AND publication_date >= ?", Integer.class,
                java.sql.Timestamp.from(inicio)) > 0) {
            Thread.sleep(100);
        }
        jdbc.update("UPDATE renaser.usuarios SET rol_personalizado_id = NULL WHERE rol_personalizado_id IS NOT NULL");
        jdbc.update("DELETE FROM renaser.roles_personalizados");
        cuentas.forEach(id -> {
            jdbc.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
        });
    }

    private UUID cuenta(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, ?, "
                + "CAST(? AS renaser.rol_usuario), 'ACTIVO')", id, id + "@renaser.test", "Fixture " + rol, rol);
        cuentas.add(id);
        return id;
    }

    private void asignar(UUID cuenta, UUID rol) {
        jdbc.update("UPDATE renaser.usuarios SET rol_personalizado_id = ? WHERE id = ?", rol, cuenta);
    }

    @Test
    @DisplayName("crear, leer con sus claves, asignar: la cuenta muestra el nombre y figura en las asignaciones vigentes")
    void crearYAsignar() {
        UserId soporte = UserId.of(cuenta("SOPORTE_TI"));
        RolPersonalizadoResumen creado = gestion.crear(new CrearRolPersonalizadoCommand(soporte, "Mentor junior",
                "Sin eventos", UserRole.MENTOR, Set.of("muro", "chat", "chat.grupos"))).despues();
        UUID mentor = cuenta("MENTOR");
        UUID aprendiz = cuenta("APRENDIZ");
        asignar(mentor, creado.id());
        asignar(aprendiz, creado.id());

        RolPersonalizadoResumen leido = finder.porId(creado.id()).orElseThrow();
        assertThat(leido.funcionalidades()).containsExactlyInAnyOrder("muro", "chat", "chat.grupos");
        assertThat(leido.clave()).isEqualTo("rp:" + creado.id());
        assertThat(leido.personas()).as("cuenta todas las referencias").isEqualTo(2);
        assertThat(leido.version()).isEqualTo(1);
        assertThat(finder.todos()).extracting(RolPersonalizadoResumen::id).containsExactly(creado.id());

        assertThat(finder.asignaciones()).as("el aprendiz tiene un rol que hereda de otra base: no vale")
                .containsOnlyKeys(UserId.of(mentor));
        assertThat(finder.delUsuario(UserId.of(mentor))).map(RolPersonalizadoResumen::id).contains(creado.id());
        assertThat(finder.delUsuario(UserId.of(aprendiz))).isEmpty();
        assertThat(usuarios.findById(UserId.of(mentor)).orElseThrow().rolPersonalizado())
                .isEqualTo(new RolPersonalizadoAsignado(creado.id(), "Mentor junior"));
        assertThat(usuarios.findById(UserId.of(aprendiz)).orElseThrow().rolPersonalizado()).isNull();
    }

    @Test
    @DisplayName("desactivado: conserva las referencias pero no restringe ni se muestra; editar con versión vieja es 409")
    void desactivar() {
        UserId soporte = UserId.of(cuenta("SOPORTE_TI"));
        UUID rol = gestion.crear(new CrearRolPersonalizadoCommand(soporte, "Mentor junior", null, UserRole.MENTOR,
                Set.of("muro"))).despues().id();
        UUID mentor = cuenta("MENTOR");
        asignar(mentor, rol);

        RolPersonalizadoResumen apagado = gestion.editar(new EditarRolPersonalizadoCommand(soporte, rol,
                "Mentor junior", null, null, Set.of("muro", "eventos"), false, 1)).despues();

        assertThat(apagado.version()).isEqualTo(2);
        assertThat(finder.porId(rol).orElseThrow().funcionalidades()).containsExactlyInAnyOrder("muro", "eventos");
        assertThat(finder.asignaciones()).isEmpty();
        assertThat(usuarios.findById(UserId.of(mentor)).orElseThrow().rolPersonalizado()).isNull();
        assertThat(jdbc.queryForObject("SELECT rol_personalizado_id FROM renaser.usuarios WHERE id = ?", UUID.class,
                mentor)).isEqualTo(rol);
        assertThatThrownBy(() -> gestion.editar(new EditarRolPersonalizadoCommand(soporte, rol, "Mentor junior",
                null, null, Set.of(), true, 1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("con personas no se borra (409, y la FK lo defiende igual); reemplazar a ninguno las libera y se borra")
    void borrarYReemplazar() {
        UserId soporte = UserId.of(cuenta("SOPORTE_TI"));
        UUID rol = gestion.crear(new CrearRolPersonalizadoCommand(soporte, "Mentor junior", null, UserRole.MENTOR,
                Set.of())).despues().id();
        asignar(cuenta("MENTOR"), rol);
        asignar(cuenta("MENTOR"), rol);

        assertThatThrownBy(() -> gestion.borrar(new BorrarRolPersonalizadoCommand(soporte, rol)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM renaser.roles_personalizados WHERE id = ?", rol))
                .isInstanceOf(DataIntegrityViolationException.class);

        int movidas = gestion.reemplazarEnBloque(new ReemplazarRolPersonalizadoCommand(soporte, rol, null))
                .personasMovidas();
        gestion.borrar(new BorrarRolPersonalizadoCommand(soporte, rol));

        assertThat(movidas).isEqualTo(2);
        assertThat(finder.porId(rol)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.roles_personalizados_funcionalidades "
                + "WHERE rol_id = ?", Integer.class, rol)).isZero();
    }

    /** Una cuenta de prueba de «ver como» (V107, D-273), como la deja {@code CuentasDePruebaService}. */
    private UUID cuentaDePrueba(UUID rolPersonalizado) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, es_cuenta_de_prueba, "
                + "rol_personalizado_id) VALUES (?, ?, 'Prueba · Mentor junior', 'MENTOR', 'ACTIVO', true, ?)", id,
                "ver-como+rp-" + rolPersonalizado + "@renaser.invalid", rolPersonalizado);
        cuentas.add(id);
        return id;
    }

    @Test
    @DisplayName("«ver como» (D-273): la cuenta de prueba no cuenta entre las personas, no se mueve en un reemplazo y "
            + "no impide borrar el rol (se suelta sola); sí la restringe la regla (asignaciones vigentes)")
    void cuentaDePruebaDeUnRol() {
        UserId soporte = UserId.of(cuenta("SOPORTE_TI"));
        UUID rol = gestion.crear(new CrearRolPersonalizadoCommand(soporte, "Mentor junior", null, UserRole.MENTOR,
                Set.of("muro"))).despues().id();
        UUID otro = gestion.crear(new CrearRolPersonalizadoCommand(soporte, "Mentor senior", null, UserRole.MENTOR,
                Set.of("muro"))).despues().id();
        UUID dePrueba = cuentaDePrueba(rol);
        UUID real = cuenta("MENTOR");
        asignar(real, rol);

        assertThat(finder.porId(rol).orElseThrow().personas()).as("solo la persona real").isEqualTo(1);
        assertThat(finder.todos()).filteredOn(r -> r.id().equals(rol)).extracting(RolPersonalizadoResumen::personas)
                .containsExactly(1L);
        assertThat(finder.asignaciones()).as("la regla la restringe igual").containsKey(UserId.of(dePrueba));

        assertThat(gestion.reemplazarEnBloque(new ReemplazarRolPersonalizadoCommand(soporte, rol, otro))
                .personasMovidas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rol_personalizado_id FROM renaser.usuarios WHERE id = ?", UUID.class,
                dePrueba)).as("la cuenta de prueba sigue siendo la de su rol").isEqualTo(rol);

        gestion.borrar(new BorrarRolPersonalizadoCommand(soporte, rol));

        assertThat(finder.porId(rol)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT rol_personalizado_id FROM renaser.usuarios WHERE id = ?", UUID.class,
                dePrueba)).as("se soltó para poder borrar").isNull();
    }

    @Test
    @DisplayName("V107: una cuenta de prueba nunca es Soporte TI, siempre usa el correo reservado y no guarda contraseña")
    void laBaseDefiendeLasCuentasDePrueba() {
        String insertar = "INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, es_cuenta_de_prueba, "
                + "hash_contrasena) VALUES (?, ?, 'Prueba', CAST(? AS renaser.rol_usuario), 'ACTIVO', true, ?)";

        assertThatThrownBy(() -> jdbc.update(insertar, UUID.randomUUID(), "ver-como+it-support@renaser.invalid",
                "SOPORTE_TI", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, UUID.randomUUID(), UUID.randomUUID() + "@renaser.test",
                "MENTOR", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, UUID.randomUUID(), "ver-como+mentor-x@renaser.invalid",
                "MENTOR", "{bcrypt}$2a$10$abc")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la base rechaza un rol personalizado con base Soporte TI o con una clave que no es su id")
    void laBaseDefiende() {
        UUID id = UUID.randomUUID();
        String insertar = "INSERT INTO renaser.roles_personalizados (id, clave, nombre, rol_base, activo, version, "
                + "creado_por, creado_en, actualizado_por, actualizado_en) VALUES (?, ?, ?, "
                + "CAST(? AS renaser.rol_usuario), true, 1, ?, now(), ?, now())";

        assertThatThrownBy(() -> jdbc.update(insertar, id, "rp:" + id, "Técnico", "SOPORTE_TI", id, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "mentor_junior", "Mentor junior", "MENTOR", id, id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
