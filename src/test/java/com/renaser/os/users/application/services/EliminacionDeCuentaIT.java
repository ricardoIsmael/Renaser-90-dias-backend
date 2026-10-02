package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase.ApproveAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.in.eliminacion.AdministrarEliminacionDeCuentasUseCase;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase.CerrarMiCuentaCommand;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.PlazoDeGracia;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-243 de punta a punta contra Postgres y Redis reales, por los casos de uso y sin dobles:
 * <ol>
 *   <li>la persona cierra su cuenta (con su contraseña) → queda sin acceso y oculta para los demas →
 *       el barrido, 30 dias despues, la borra → <b>ninguna fila con su id queda en ninguna tabla</b> →
 *       el mismo correo vuelve a darse de alta (decision 3 del dueño);</li>
 *   <li>un Admin elimina en el acto a otro Admin, y la historia de los demas (el dia que ese Admin le
 *       ajusto a un aprendiz) se conserva sin autor;</li>
 *   <li>una cuenta cerrada que un Admin recupera ya no la toca el barrido.</li>
 * </ol>
 *
 * <p><b>«Ninguna fila» se comprueba contra el esquema, no contra una lista escrita a mano</b>: se leen de
 * {@code information_schema} todas las columnas con FK a {@code usuarios(id)} o a
 * {@code participantes_programa(usuario_id)}, mas las columnas de persona que no tienen FK. Una tabla
 * nueva que guarde datos de una persona entra sola en la prueba.
 *
 * <p>Sin {@code @Transactional}: el borrado abre sus propias transacciones, como en produccion.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EliminacionDeCuentaIT {

    private static final String CONTRASENA = "Secreta123!clave";

    /** Columnas con el id de una persona que no tienen FK (no las encuentra la consulta al esquema). */
    private static final List<String> COLUMNAS_SIN_FK = List.of(
            "solicitudes_cuenta.usuario_id", "anomalias_acompanamiento.usuario_id");

    @Autowired
    private SubmitAccountRequestUseCase submitAccountRequest;
    @Autowired
    private ApproveAccountRequestUseCase approveAccountRequest;
    @Autowired
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;
    @Autowired
    private LoadUserPort loadUserPort;
    @Autowired
    private SaveUserPort saveUserPort;
    @Autowired
    private CerrarMiCuentaUseCase cerrarMiCuenta;
    @Autowired
    private AdministrarEliminacionDeCuentasUseCase administrar;
    @Autowired
    private CuentasCerradasFinder cuentasCerradas;
    @Autowired
    private BorradoDefinitivoService borrado;
    @Autowired
    private PlazoDeGracia plazo;
    @Autowired
    private Clock clock;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("cerrar → 30 dias → barrido: no queda ninguna fila suya y el correo vuelve a servir")
    void cerrarYBorrarAlVencerLaGracia() {
        UserId admin = adminActivo();
        String email = "se-va-" + UUID.randomUUID() + "@renaser.dev";
        UserId persona = altaRealAprobada(email, "Persona Que Se Va", admin);
        UserId otra = altaRealAprobada("se-queda-" + UUID.randomUUID() + "@renaser.dev", "Persona Que Queda", admin);
        sembrarDatosDe(persona, otra);
        assertThat(filasConId(persona)).isNotEmpty();

        var estado = cerrarMiCuenta.cerrar(new CerrarMiCuentaCommand(persona, CONTRASENA, null));

        User cerrada = loadUserPort.byId(persona).orElseThrow();
        assertThat(cerrada.hasAccess()).isFalse();
        assertThat(cerrada.status()).isEqualTo(UserStatus.SUSPENDED);
        // Postgres REDONDEA a microsegundos el instante del reloj (que trae nanosegundos), E-488.
        assertThat(estado.purgaEl()).isCloseTo(plazo.seBorraEl(cerrada.bajaSolicitadaEn()),
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(cuentasCerradas.cerradasEntre(List.of(persona, otra))).containsExactly(persona);

        // Un dia antes de cumplirse la gracia el barrido no la toca.
        barridoEn(estado.purgaEl().minus(Duration.ofDays(1))).purgeExpired();
        assertThat(loadUserPort.byId(persona)).isPresent();

        barridoEn(estado.purgaEl().plusSeconds(60)).purgeExpired();

        assertThat(loadUserPort.byId(persona)).isEmpty();
        assertThat(filasConId(persona)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.solicitudes_cuenta WHERE lower(email) = ?",
                Integer.class, email)).isZero();
        // Lo de la otra persona sigue en pie (su publicacion, sus mensajes de grupo, su cuenta).
        assertThat(loadUserPort.byId(otra)).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.publicaciones_muro WHERE autor_id = ?",
                Integer.class, otra.value())).isEqualTo(1);
        // La auditoria guarda el cierre y el borrado, sin el correo ni el nombre.
        assertThat(jdbc.queryForList("SELECT accion FROM renaser.auditoria_eliminacion_cuentas WHERE cuenta_id = ? "
                + "ORDER BY id", String.class, persona.value())).containsExactly("CERRADA", "ELIMINADA_AL_VENCER");

        // Decision 3: el mismo correo da de alta una cuenta nueva por el camino real.
        UserId nueva = altaRealAprobada(email, "Vuelve Con El Mismo Correo", admin);
        assertThat(loadUserPort.byEmail(new Email(email)).orElseThrow().id()).isEqualTo(nueva);

        limpiar(otra, nueva, admin);
    }

    @Test
    @DisplayName("un Admin elimina en el acto a otro Admin; el ajuste que ese Admin hizo queda sin autor")
    void adminEliminaEnElActo() {
        UserId admin = adminActivo();
        UserId otroAdmin = adminActivo();
        UserId aprendiz = altaRealAprobada("aprendiz-" + UUID.randomUUID() + "@renaser.dev", "Aprendiz", admin);
        jdbc.update("INSERT INTO renaser.ajustes_dia_programa (participante_id, dia_anterior, dia_nuevo, "
                + "dias_ajuste_anterior, dias_ajuste_nuevo, motivo, ajustado_por) VALUES (?, 5, 3, 0, 2, 'viajo', ?)",
                aprendiz.value(), otroAdmin.value());
        String correo = loadUserPort.byId(otroAdmin).orElseThrow().email().value();

        assertThatThrownBy(() -> administrar.eliminar(admin, otroAdmin, "otro@renaser.dev"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> administrar.eliminar(admin, admin, correo))
                .isInstanceOf(NotAuthorizedException.class);

        administrar.eliminar(admin, otroAdmin, correo.toUpperCase());

        assertThat(loadUserPort.byId(otroAdmin)).isEmpty();
        assertThat(filasConId(otroAdmin)).isEmpty();
        assertThat(jdbc.queryForList("SELECT ajustado_por FROM renaser.ajustes_dia_programa WHERE participante_id = ?",
                aprendiz.value())).singleElement().satisfies(fila -> assertThat(fila.get("ajustado_por")).isNull());
        assertThat(jdbc.queryForMap("SELECT accion, actor_id, rol FROM renaser.auditoria_eliminacion_cuentas "
                + "WHERE cuenta_id = ?", otroAdmin.value()))
                .containsEntry("accion", "ELIMINADA_POR_ADMIN").containsEntry("actor_id", admin.value())
                .containsEntry("rol", "ADMIN");

        limpiar(aprendiz, admin);
    }

    @Test
    @DisplayName("una cuenta cerrada que un Admin recupera vuelve a entrar y el barrido ya no la borra")
    void recuperarDentroDeLaGracia() {
        UserId admin = adminActivo();
        UserId persona = altaRealAprobada("arrepentida-" + UUID.randomUUID() + "@renaser.dev", "Arrepentida", admin);
        var estado = cerrarMiCuenta.cerrar(new CerrarMiCuentaCommand(persona, CONTRASENA, null));

        administrar.recuperar(admin, persona);

        User recuperada = loadUserPort.byId(persona).orElseThrow();
        assertThat(recuperada.hasAccess()).isTrue();
        assertThat(recuperada.bajaPendiente()).isFalse();
        barridoEn(estado.purgaEl().plus(Duration.ofDays(5))).purgeExpired();
        assertThat(loadUserPort.byId(persona)).isPresent();

        limpiar(persona, admin);
    }

    // ─── apoyo ───────────────────────────────────────────────────────────────────────────────

    private BarridoDeCuentasCerradasService barridoEn(Instant ahora) {
        return new BarridoDeCuentasCerradasService(loadUserPort, borrado, FixedClock.at(ahora), plazo);
    }

    /** Las pruebas no son transaccionales: lo que crean lo borran con el mismo borrado que prueban. */
    private void limpiar(UserId... cuentas) {
        for (UserId cuenta : cuentas) {
            borrado.borrar(cuenta, com.renaser.os.users.domain.model.user.RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN,
                    null);
        }
    }

    /** El alta de produccion: submit (usuarios + solicitudes_cuenta) y approve. */
    private UserId altaRealAprobada(String email, String nombre, UserId admin) {
        String token = tokenVerificacionEmailPort.generar(email, Duration.ofMinutes(30));
        String ip = "203.0.113." + ThreadLocalRandom.current().nextInt(1, 255);
        AccountRequestId solicitud = submitAccountRequest.submit(SubmitAccountRequestCommand.porFormulario(
                email, nombre, "+51 999 888 777", "Lima", token, CONTRASENA, ip));
        approveAccountRequest.approve(new ApproveAccountRequestCommand(solicitud, admin));
        return loadUserPort.byEmail(new Email(email)).orElseThrow().id();
    }

    private UserId adminActivo() {
        UserId id = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.rehydrate(id, new Email("admin-" + id + "@renaser.dev"), UserRole.ADMIN,
                UserStatus.ACTIVE, "Admin De Prueba", null, null, null, null));
        return id;
    }

    /**
     * Datos de la persona en varios modulos, y de otra persona que comparte algo con ella: una
     * conversacion directa, comentarios cruzados en el Muro.
     */
    private void sembrarDatosDe(UserId persona, UserId otra) {
        UUID p = persona.value();
        UUID o = otra.value();
        jdbc.update("INSERT INTO renaser.entradas_diario (participante_id, fecha, tipo, contenido_texto) "
                + "VALUES (?, current_date, 'ESCRITURA_LIBRE', 'mi diario')", p);
        UUID suPublicacion = UUID.randomUUID();
        UUID publicacionDeOtra = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.publicaciones_muro (id, autor_id, texto) VALUES (?, ?, 'mi post')",
                suPublicacion, p);
        jdbc.update("INSERT INTO renaser.publicaciones_muro (id, autor_id, texto) VALUES (?, ?, 'post de otra')",
                publicacionDeOtra, o);
        jdbc.update("INSERT INTO renaser.comentarios_muro (publicacion_id, autor_id, texto) VALUES (?, ?, 'hola')",
                publicacionDeOtra, p);
        jdbc.update("INSERT INTO renaser.comentarios_muro (publicacion_id, autor_id, texto) VALUES (?, ?, 'hola')",
                suPublicacion, o);
        UUID directa = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa) VALUES (?, 'DIRECTA', ?)",
                directa, "it-" + directa);
        for (UUID quien : new UUID[] {p, o}) {
            jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                    directa, quien);
            jdbc.update("INSERT INTO renaser.mensajes (conversacion_id, emisor_id, texto) VALUES (?, ?, 'mensaje')",
                    directa, quien);
        }
        jdbc.update("INSERT INTO renaser.notificaciones (usuario_id, tipo, titulo, cuerpo) "
                + "VALUES (?, 'ANUNCIO_SISTEMA', 't', 'c')", p);
        jdbc.update("INSERT INTO renaser.tokens_push (usuario_id, token) VALUES (?, ?)", p, "tok-" + p);
        jdbc.update("INSERT INTO renaser.tickets_soporte (usuario_id, categoria, asunto, mensaje) "
                + "VALUES (?, 'OTRO', 'a', 'm')", p);
        jdbc.update("INSERT INTO renaser.rocas_maestras (participante_id, eje, objetivo) VALUES (?, 'CUERPO', 'o')", p);
        jdbc.update("INSERT INTO renaser.memorias_renasia (participante_id, resumen, compactado_hasta, actualizado_en) "
                + "VALUES (?, 'r', now(), now())", p);
        jdbc.update("INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados, habitos_cumplidos, "
                + "objetivos_programados, objetivos_cumplidos, calculado_en) VALUES (?, current_date, 1, 1, 0, 0, now())", p);
        jdbc.update("INSERT INTO renaser.testimonios (usuario_id, nombre, texto, estrellas) VALUES (?, 'P', 't', 5)", p);
    }

    /** Toda tabla y columna donde aparece el id: vacio = no quedo nada de la persona. */
    private List<String> filasConId(UserId cuenta) {
        List<String> conFilas = new ArrayList<>();
        for (String columna : columnasDePersona()) {
            String[] partes = columna.split("\\.");
            Integer filas = jdbc.queryForObject("SELECT count(*) FROM renaser." + partes[0] + " WHERE "
                    + partes[1] + " = ?", Integer.class, cuenta.value());
            if (filas != null && filas > 0) {
                conFilas.add(columna + "=" + filas);
            }
        }
        return conFilas;
    }

    private Set<String> columnasDePersona() {
        List<Map<String, Object>> fks = jdbc.queryForList("""
                SELECT kcu.table_name, kcu.column_name
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.key_column_usage kcu
                    ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
                  JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
                 WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'renaser'
                   AND ((ccu.table_name = 'usuarios' AND ccu.column_name = 'id')
                     OR (ccu.table_name = 'participantes_programa' AND ccu.column_name = 'usuario_id'))""");
        Set<String> columnas = new java.util.TreeSet<>(COLUMNAS_SIN_FK);
        fks.forEach(fila -> columnas.add(fila.get("table_name") + "." + fila.get("column_name")));
        columnas.add("usuarios.id");
        return columnas;
    }
}
