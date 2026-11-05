package com.renaser.os.calendar.infrastructure.adapter.out.persistence.meet;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.out.meet.ConexionesEnCursoPort.ConexionEnCurso;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.ArtefactosDeLaSala;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.EstadoDeLaCuenta;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las tablas de V169 contra Postgres de verdad (spec 040): una sala por evento y por fecha, la cuenta única con el permiso permanente cifrado,
 * el {@code state} de un solo uso y la auditoría sin secretos. La clave de cifrado es de mentira (32 bytes de relleno en base64) y solo vive en
 * esta prueba.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "renaser.calendar.meet.cifrado.clave=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "renaser.calendar.meet.cifrado.version=1"})
@Transactional
class SalasYCuentaDeGooglePersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");
    private static final String TOKEN_SECRETO = "1//permiso-permanente-de-prueba";

    @Autowired
    private SalaMeetJdbcAdapter salas;
    @Autowired
    private CuentaGoogleJdbcAdapter cuenta;
    @Autowired
    private ConexionesGoogleJdbcAdapter conexiones;
    @Autowired
    private AuditoriaGoogleJdbcAdapter auditoria;
    @Autowired
    private JdbcTemplate jdbc;

    private SalaMeet sala(EventoId evento, LocalDate fecha, String idGoogle) {
        return SalaMeet.nueva(UUID.randomUUID(), evento, fecha, idGoogle, AccesoSalaMeet.ABIERTA, AHORA);
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: una sala por evento y por fecha: se guarda, vuelve igual y la misma fecha no se repite")
    void unaSalaPorFecha() {
        EventoId evento = EventoId.of(UUID.randomUUID());
        salas.guardar(sala(evento, LocalDate.of(2026, 11, 10), "a".repeat(32)));

        assertThat(salas.porEvento(evento)).singleElement().satisfies(s -> {
            assertThat(s.fechaOcurrencia()).isEqualTo(LocalDate.of(2026, 11, 10));
            assertThat(s.estado()).isEqualTo(EstadoSalaMeet.PENDIENTE);
            assertThat(s.accesoElegido()).isEqualTo(AccesoSalaMeet.ABIERTA);
        });
        assertThatThrownBy(() -> {
            salas.guardar(sala(evento, LocalDate.of(2026, 11, 10), "b".repeat(32)));
            jdbc.execute("SELECT 1");
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // spec 040 FR-036, FR-037
    @Test
    @DisplayName("FR-036: lo que Google confirmó de la grabación, la transcripción y las notas se guarda por sala con sus tres estados (V171)")
    void laGrabacionConfirmadaSeGuarda() {
        EventoId evento = EventoId.of(UUID.randomUUID());
        SalaMeet creada = sala(evento, LocalDate.of(2026, 11, 12), "g".repeat(32));
        creada.completarCon("https://meet.google.com/abc-defg-hij", AHORA);
        creada.registrarArtefactos(new ArtefactosDeLaSala(true, false, null), AHORA);
        salas.guardar(creada);

        assertThat(salas.porEvento(evento)).singleElement().extracting(SalaMeet::artefactos)
                .isEqualTo(new ArtefactosDeLaSala(true, false, null));
        auditoria.registrar(new RegistroDeAuditoriaGoogle(UUID.randomUUID(), AHORA, AccionDeAuditoriaGoogle.AJUSTAR_GRABACION, null,
                evento.value(), false, 403, "corr-grabacion"));
        assertThat(auditoria.ultimos(50)).extracting(RegistroDeAuditoriaGoogle::correlacion).contains("corr-grabacion");
    }

    // spec 040 FR-007
    @Test
    @DisplayName("FR-007: el id de Google es único en toda la tabla: dos salas nunca comparten el mismo evento de Google")
    void elIdDeGoogleEsUnico() {
        salas.guardar(sala(EventoId.of(UUID.randomUUID()), LocalDate.of(2026, 11, 10), "c".repeat(32)));

        assertThatThrownBy(() -> {
            salas.guardar(sala(EventoId.of(UUID.randomUUID()), LocalDate.of(2026, 11, 11), "c".repeat(32)));
            jdbc.execute("SELECT 1");
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // spec 040 FR-003
    @Test
    @DisplayName("FR-003: una sala CREADO guarda su enlace y su acceso confirmado; la base impide un CREADO sin enlace")
    void unaSalaCreadaTieneEnlace() {
        EventoId evento = EventoId.of(UUID.randomUUID());
        SalaMeet creada = sala(evento, LocalDate.of(2026, 11, 10), "d".repeat(32));
        creada.completarCon("https://meet.google.com/abc-defg-hij", AHORA);
        creada.registrarAccesoConfirmado(AccesoSalaMeet.ABIERTA, AHORA);
        salas.guardar(creada);

        assertThat(salas.porEvento(evento)).singleElement().satisfies(s -> {
            assertThat(s.estado()).isEqualTo(EstadoSalaMeet.CREADO);
            assertThat(s.enlace()).isEqualTo("https://meet.google.com/abc-defg-hij");
            assertThat(s.accesoConfirmado()).isEqualTo(AccesoSalaMeet.ABIERTA);
        });
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO renaser.eventos_meet (id, evento_id, fecha_ocurrencia, id_google, estado, acceso_elegido, creada_en, actualizada_en)
                VALUES (?, ?, DATE '2026-11-17', ?, 'CREADO', 'ABIERTA', now(), now())
                """, UUID.randomUUID(), evento.value(), "e".repeat(32))).isInstanceOf(DataIntegrityViolationException.class);
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: lo que toca reintentar se busca por el próximo turno; lo que ya está bien o espera su hora no aparece")
    void loPendienteSeBuscaPorElProximoTurno() {
        EventoId fallida = EventoId.of(UUID.randomUUID());
        EventoId buena = EventoId.of(UUID.randomUUID());
        EventoId futura = EventoId.of(UUID.randomUUID());
        SalaMeet a = sala(fallida, LocalDate.of(2026, 11, 10), "1".repeat(32));
        a.fallarAlCrear("Google no respondió.", AHORA, AHORA.minusSeconds(5));
        SalaMeet b = sala(buena, LocalDate.of(2026, 11, 10), "2".repeat(32));
        b.completarCon("https://meet.google.com/aaa-bbbb-ccc", AHORA);
        SalaMeet c = sala(futura, LocalDate.of(2026, 11, 10), "3".repeat(32));
        c.fallarAlCrear("Google no respondió.", AHORA, AHORA.plus(Duration.ofMinutes(10)));
        salas.guardar(a);
        salas.guardar(b);
        salas.guardar(c);

        List<EventoId> pendientes = salas.eventosConTrabajoPendiente(AHORA, 500);

        assertThat(pendientes).contains(fallida).doesNotContain(buena, futura);
    }

    // spec 040 FR-005
    @Test
    @DisplayName("FR-005: purgar quita solo las lápidas viejas (sala ya borrada en Google), no las salas vivas")
    void purgarLapidas() {
        EventoId evento = EventoId.of(UUID.randomUUID());
        SalaMeet viva = sala(evento, LocalDate.of(2026, 11, 10), "4".repeat(32));
        viva.completarCon("https://meet.google.com/aaa-bbbb-ccc", AHORA);
        SalaMeet lapida = sala(evento, LocalDate.of(2026, 11, 17), "5".repeat(32));
        lapida.completarCon("https://meet.google.com/ddd-eeee-fff", AHORA);
        lapida.marcarBorrada(AHORA.minus(Duration.ofDays(400)));
        salas.guardar(viva);
        salas.guardar(lapida);

        int purgadas = salas.purgarLapidasAnterioresA(AHORA.minus(Duration.ofDays(365)));

        assertThat(purgadas).isGreaterThanOrEqualTo(1);
        assertThat(salas.porEvento(evento)).extracting(SalaMeet::fechaOcurrencia).containsExactly(LocalDate.of(2026, 11, 10));
    }

    // spec 040 FR-011, SC-006
    @Test
    @DisplayName("FR-011: el permiso permanente se guarda CIFRADO: en la base no está en claro, y al cargar vuelve igual")
    void elPermisoPermanenteSeGuardaCifrado() {
        UserId admin = UserId.of(UUID.randomUUID());
        CuentaDeGoogle conectada = CuentaDeGoogle.conectar("renasercorporation@gmail.com", TOKEN_SECRETO,
                Set.of(CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO, CuentaDeGoogle.PERMISO_AJUSTES_DE_LA_SALA), admin, AHORA);
        conectada.fijarCalendario("calendario-renaser@group.calendar.google.com");

        cuenta.guardar(conectada);

        Map<String, Object> fila = jdbc.queryForMap("SELECT refresh_token_cifrado, version_de_clave FROM renaser.integracion_google WHERE id = 1");
        assertThat((String) fila.get("refresh_token_cifrado")).startsWith("v1:").doesNotContain(TOKEN_SECRETO).doesNotContain("permanente");
        assertThat(((Number) fila.get("version_de_clave")).intValue()).isEqualTo(1);
        CuentaDeGoogle cargada = cuenta.cargar().orElseThrow();
        assertThat(cargada.refreshToken()).isEqualTo(TOKEN_SECRETO);
        assertThat(cargada.correo()).isEqualTo("renasercorporation@gmail.com");
        assertThat(cargada.estado()).isEqualTo(EstadoDeLaCuenta.ACTIVA);
        assertThat(cargada.calendarioParaLasSalas()).isEqualTo("calendario-renaser@group.calendar.google.com");
        assertThat(cargada.tienePermiso(CuentaDeGoogle.PERMISO_AJUSTES_DE_LA_SALA)).isTrue();
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: hay UNA sola cuenta para toda la plataforma: conectar otra la reemplaza, y desconectar la borra")
    void unaSolaCuenta() {
        UserId admin = UserId.of(UUID.randomUUID());
        cuenta.guardar(CuentaDeGoogle.conectar("uno@gmail.com", "token-uno", Set.of(), admin, AHORA));
        cuenta.guardar(CuentaDeGoogle.conectar("dos@gmail.com", "token-dos", Set.of(), admin, AHORA));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.integracion_google", Integer.class)).isEqualTo(1);
        assertThat(cuenta.cargar().orElseThrow().correo()).isEqualTo("dos@gmail.com");

        cuenta.borrar();

        assertThat(cuenta.cargar()).isEmpty();
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: el state se guarda como hash, se usa una sola vez, y vencido o inventado no sirve; el verificador de PKCE va cifrado")
    void elStateEsDeUnSoloUso() {
        UserId admin = UserId.of(UUID.randomUUID());
        conexiones.guardar("hash-del-state", admin, "verificador-de-pkce-secreto", AHORA.plus(Duration.ofMinutes(15)));
        conexiones.guardar("hash-vencido", admin, "otro-verificador", AHORA.minusSeconds(1));

        String guardado = jdbc.queryForObject(
                "SELECT code_verifier_cifrado FROM renaser.integracion_google_conexiones WHERE hash_del_state = 'hash-del-state'", String.class);
        assertThat(guardado).doesNotContain("verificador-de-pkce-secreto");
        assertThat(conexiones.consumir("hash-del-state", AHORA)).contains(new ConexionEnCurso(admin, "verificador-de-pkce-secreto"));
        assertThat(conexiones.consumir("hash-del-state", AHORA)).as("la segunda vez ya no está").isEmpty();
        assertThat(conexiones.consumir("hash-vencido", AHORA)).isEmpty();
        assertThat(conexiones.consumir("hash-inventado", AHORA)).isEmpty();
    }

    // spec 040 FR-016, SC-006
    @Test
    @DisplayName("FR-016: la auditoría guarda quién, qué evento, resultado y código de Google; no tiene ninguna columna para secretos ni enlaces")
    void laAuditoriaNoGuardaSecretos() {
        UUID evento = UUID.randomUUID();
        UserId admin = UserId.of(UUID.randomUUID());
        auditoria.registrar(new RegistroDeAuditoriaGoogle(UUID.randomUUID(), AHORA, AccionDeAuditoriaGoogle.CREAR_SALA, admin, evento, false, 503, "corr-1"));
        auditoria.registrar(new RegistroDeAuditoriaGoogle(UUID.randomUUID(), AHORA.minusSeconds(60), AccionDeAuditoriaGoogle.CONECTAR, null, null, true, null, "corr-2"));

        List<RegistroDeAuditoriaGoogle> ultimos = auditoria.ultimos(10);

        assertThat(ultimos).extracting(RegistroDeAuditoriaGoogle::correlacion).containsSubsequence("corr-1", "corr-2");
        assertThat(ultimos.getFirst().codigoHttp()).isEqualTo(503);
        List<String> columnas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'renaser' AND table_name = 'integracion_google_auditoria'", String.class);
        assertThat(columnas).noneMatch(c -> c.contains("token") || c.contains("secret") || c.contains("enlace") || c.contains("url"));
    }

    // spec 040 FR-016
    @Test
    @DisplayName("FR-016: la auditoría se conserva 12 meses: lo más viejo se purga")
    void laAuditoriaSePurga() {
        auditoria.registrar(new RegistroDeAuditoriaGoogle(UUID.randomUUID(), AHORA.minus(Duration.ofDays(400)), AccionDeAuditoriaGoogle.CREAR_SALA, null, null, true, 200, "vieja"));
        auditoria.registrar(new RegistroDeAuditoriaGoogle(UUID.randomUUID(), AHORA, AccionDeAuditoriaGoogle.CREAR_SALA, null, null, true, 200, "nueva"));

        int purgadas = auditoria.purgarAnterioresA(AHORA.minus(Duration.ofDays(365)));

        assertThat(purgadas).isGreaterThanOrEqualTo(1);
        assertThat(auditoria.ultimos(50)).extracting(RegistroDeAuditoriaGoogle::correlacion).contains("nueva").doesNotContain("vieja");
    }
}
