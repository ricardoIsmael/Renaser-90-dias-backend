package com.renaser.os.calendar.infrastructure.adapter.in.rest.meet;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.in.meet.TranscripcionesDeMeetUseCase;
import com.renaser.os.calendar.application.ports.out.meet.ReunionesDeMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.SaveCuentaGooglePort;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.transcripcion.LecturaDeReunion;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Spec 047 de punta a punta, con la base real, el cifrado real, el servidor HTTP real y la Meet REST API simulada (el puerto que la lee; el
 * adaptador HTTP se prueba aparte con las respuestas de Google). Nada de producción: Testcontainers y una clave de prueba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "renaser.calendar.meet.cifrado.clave=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "renaser.calendar.meet.cifrado.version=1",
        "renaser.scheduling.transcripciones-de-meet.cron=-",
        "renaser.scheduling.transcripciones-de-meet-retencion.cron=-"})
@Import(TestcontainersConfiguration.class)
class TranscripcionDeLaSesionIT {

    private static final String ENLACE = "https://meet.google.com/abc-defg-hij";
    private static final String TEXTO = "Ana Ruiz: Buenas noches, hoy cerramos la semana del programa.\nLuis: A mí me costó la constancia.";
    private static final String GRABACION = "https://drive.google.com/file/d/grabacion-de-prueba/view";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CopiaDeEstados copiaDeEstados;
    @Autowired
    private SaveCuentaGooglePort cuentas;
    @Autowired
    private TranscripcionesDeMeetUseCase useCase;
    @MockitoBean
    private ReunionesDeMeetPort reuniones;

    private PedidosDePrueba pedidos;
    private UUID admin;
    private UUID mentor;
    private UUID aprendiz;
    private UUID evento;
    private UUID sala;
    private LocalDate fecha;
    private Instant ahora;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        admin = pedidos.usuario("ADMIN");
        mentor = pedidos.usuario("MENTOR");
        aprendiz = pedidos.usuario("APRENDIZ");
        ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant inicio = ahora.minus(Duration.ofHours(2));
        fecha = inicio.atZone(LIMA).toLocalDate();
        evento = UUID.randomUUID();
        sala = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.eventos (id, titulo, inicia_en, duracion_minutos, timezone, tipo_ubicacion, valor_ubicacion, tipo_audiencia,
                                             tipo_evento, estado, acceso_sala_meet)
                VALUES (?, 'Sesión de prueba 047', ?, 60, 'America/Lima', CAST('MEET' AS renaser.tipo_ubicacion), ?,
                        CAST('TODOS' AS renaser.tipo_audiencia), CAST('ESPONTANEO' AS renaser.tipo_evento_calendario),
                        CAST('PUBLICADO' AS renaser.estado_evento), 'ABIERTA')
                """, evento, Timestamp.from(inicio), ENLACE);
        jdbc.update("""
                INSERT INTO renaser.eventos_meet (id, evento_id, fecha_ocurrencia, id_google, enlace, estado, acceso_elegido, acceso_confirmado,
                                                  creada_en, actualizada_en, grabacion_confirmada, transcripcion_confirmada, notas_confirmadas)
                VALUES (?, ?, ?, ?, ?, 'CREADO', 'ABIERTA', 'ABIERTA', now(), now(), true, true, null)
                """, sala, evento, fecha, UUID.randomUUID().toString().replace("-", ""), ENLACE);
        CuentaDeGoogle cuenta = CuentaDeGoogle.conectar("renasercorporation@gmail.com", "1//permiso-de-prueba",
                Set.of(CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO, CuentaDeGoogle.PERMISO_AJUSTES_DE_LA_SALA, CuentaDeGoogle.PERMISO_LEER_REUNIONES),
                UserId.of(admin), ahora.minus(Duration.ofDays(1)));
        cuenta.fijarCalendario("calendario-de-prueba@group.calendar.google.com");
        cuentas.guardar(cuenta);
        interruptor(false);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.eventos_meet_transcripciones_accesos WHERE evento_id = ?", evento);
        jdbc.update("DELETE FROM renaser.eventos_meet WHERE evento_id = ?", evento);
        jdbc.update("DELETE FROM renaser.eventos WHERE id = ?", evento);
        jdbc.update("DELETE FROM renaser.integracion_google_auditoria WHERE evento_id = ?", evento);
        cuentas.borrar();
        interruptor(false);
        pedidos.borrarCuentas();
    }

    private void interruptor(boolean prendido) {
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave IN ('eventos.meet', 'eventos.meet.transcripcion')");
        if (prendido) {
            for (String clave : List.of("eventos.meet", "eventos.meet.transcripcion")) {
                jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                        + "VALUES (?, true, nextval('renaser.funcionalidades_version_seq'), ?, now())", clave, admin);
            }
        }
        copiaDeEstados.recargar();
    }

    private String ruta() {
        return "/api/v1/calendar/events/" + evento + "/meet/transcripciones";
    }

    private void guardarLaTranscripcion() {
        when(reuniones.leer(any(), any(), any())).thenReturn(LecturaDeReunion.SIN_REUNION);
        when(reuniones.leer(eq(ENLACE), any(), any())).thenReturn(new LecturaDeReunion(1, 2, TEXTO, GRABACION, false));
        useCase.buscarPendientes(Instant.now());
    }

    // spec 047 FR-002, FR-006, FR-010, SC-004
    @Test
    @DisplayName("FR-006 y FR-010: terminada la sesión, la transcripción y la grabación quedan guardadas CIFRADAS: en la base no hay texto en claro")
    void seGuardaCifrada() {
        guardarLaTranscripcion();

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM renaser.eventos_meet_transcripciones WHERE evento_meet_id = ?", sala);
        assertThat(fila.get("estado")).isEqualTo("LISTA");
        assertThat((String) fila.get("texto_cifrado")).startsWith("v1:").doesNotContain("Buenas noches").doesNotContain("Ana Ruiz");
        assertThat((String) fila.get("grabacion_cifrada")).startsWith("v1:").doesNotContain("grabacion-de-prueba");
        assertThat(fila.get("entradas")).isEqualTo(2);
        Integer auditados = jdbc.queryForObject("SELECT count(*) FROM renaser.integracion_google_auditoria WHERE evento_id = ? "
                + "AND accion = 'TRANSCRIPCION_GUARDADA'", Integer.class, evento);
        assertThat(auditados).isEqualTo(1);
        String auditoria = jdbc.queryForList("SELECT row_to_json(a)::text FROM renaser.integracion_google_auditoria a WHERE evento_id = ?",
                String.class, evento).toString();
        assertThat(auditoria).doesNotContain("Buenas noches");
    }

    // spec 047 FR-011, FR-012, FR-013, FR-016, SC-003
    @Test
    @DisplayName("SC-003: el texto no sale por ningún lado para nadie, salvo ADMIN con motivo; queda la constancia de quién lo sacó")
    void quienPuedeSacarla() throws Exception {
        guardarLaTranscripcion();
        interruptor(true);
        String deAdmin = pedidos.sesionDe(admin);
        String deMentor = pedidos.sesionDe(mentor);
        String deAprendiz = pedidos.sesionDe(aprendiz);

        HttpResponse<String> estados = pedidos.enviar("GET", ruta(), deAdmin, null);
        assertThat(estados.statusCode()).isEqualTo(200);
        JsonNode lista = pedidos.json(estados);
        assertThat(lista.get(0).path("estado").asText()).isEqualTo("LISTA");
        assertThat(lista.get(0).path("grabacion").asBoolean()).isTrue();
        assertThat(estados.body()).doesNotContain("Buenas noches").doesNotContain("grabacion-de-prueba");

        assertThat(pedidos.enviar("GET", ruta(), deMentor, null).statusCode()).isEqualTo(403);
        assertThat(pedidos.enviar("GET", ruta(), deAprendiz, null).statusCode()).isEqualTo(403);
        String descarga = ruta() + "/" + fecha + "/descarga";
        assertThat(pedidos.enviar("POST", descarga, deMentor, Map.of("motivo", "Quiero leer lo que dijeron")).statusCode()).isEqualTo(403);
        assertThat(pedidos.enviar("POST", descarga, deAprendiz, Map.of("motivo", "Quiero leer lo que dijeron")).statusCode()).isEqualTo(403);
        assertThat(pedidos.enviar("POST", descarga, deAdmin, Map.of("motivo", "corto")).statusCode()).isEqualTo(400);

        HttpResponse<String> evento = pedidos.enviar("GET", "/api/v1/calendar/events/" + this.evento, deAdmin, null);
        assertThat(evento.statusCode()).isEqualTo(200);
        assertThat(evento.body()).doesNotContain("Buenas noches").doesNotContain("grabacion-de-prueba");
        assertThat(pedidos.json(evento).path("recordingNotice").asBoolean()).as("P4-B: aviso de grabación").isTrue();

        HttpResponse<String> texto = pedidos.enviar("POST", descarga, deAdmin, Map.of("motivo", "Reclamo de una alumna sobre la sesión"));
        assertThat(texto.statusCode()).isEqualTo(200);
        assertThat(texto.body()).contains("Buenas noches").contains(GRABACION);
        assertThat(texto.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(jdbc.queryForObject("SELECT accion || ': ' || motivo FROM renaser.eventos_meet_transcripciones_accesos WHERE evento_id = ?",
                String.class, this.evento)).isEqualTo("LECTURA: Reclamo de una alumna sobre la sesión");

        // spec 047 FR-016: borrar a pedido
        String borrar = ruta() + "/" + fecha + "/borrar";
        assertThat(pedidos.enviar("POST", borrar, deMentor, Map.of("motivo", "Lo pidió la alumna por la Ley 29733")).statusCode()).isEqualTo(403);
        assertThat(pedidos.enviar("POST", borrar, deAdmin, Map.of("motivo", "Lo pidió la alumna por la Ley 29733")).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT estado FROM renaser.eventos_meet_transcripciones WHERE evento_meet_id = ?", String.class, sala))
                .isEqualTo("BORRADA");
        assertThat(pedidos.enviar("POST", descarga, deAdmin, Map.of("motivo", "Reclamo de una alumna sobre la sesión")).statusCode()).isEqualTo(404);
    }

    // spec 047 FR-001, SC-005
    @Test
    @DisplayName("FR-001: con el interruptor apagado (de fábrica) los endpoints se esconden")
    void apagadoDeFabrica() throws Exception {
        assertThat(pedidos.enviar("GET", ruta(), pedidos.sesionDe(admin), null).statusCode()).isEqualTo(404);
    }

    // spec 047 FR-014, FR-015, SC-006
    @Test
    @DisplayName("FR-014 y FR-015: vencida (o con la sala borrada) se vacía lo cifrado y queda la constancia BORRADA")
    void retencion() {
        guardarLaTranscripcion();
        jdbc.update("UPDATE renaser.eventos_meet_transcripciones SET borrar_despues_de = now() - interval '1 minute' WHERE evento_meet_id = ?", sala);

        assertThat(useCase.aplicarRetencion(Instant.now())).isGreaterThanOrEqualTo(1);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM renaser.eventos_meet_transcripciones WHERE evento_meet_id = ?", sala);
        assertThat(fila.get("estado")).isEqualTo("BORRADA");
        assertThat(fila.get("texto_cifrado")).isNull();
        assertThat(fila.get("grabacion_cifrada")).isNull();
        assertThat(fila.get("borrada_en")).isNotNull();
    }

    // spec 047 FR-002, FR-008
    @Test
    @DisplayName("FR-002: una sesión que todavía no termina no se busca; terminada, sí")
    void soloLasTerminadas() {
        jdbc.update("UPDATE renaser.eventos SET inicia_en = ? WHERE id = ?", Timestamp.from(Instant.now().minus(Duration.ofMinutes(30))), evento);
        jdbc.update("UPDATE renaser.eventos_meet SET fecha_ocurrencia = ? WHERE id = ?",
                Instant.now().minus(Duration.ofMinutes(30)).atZone(LIMA).toLocalDate(), sala);
        when(reuniones.leer(any(), any(), any())).thenReturn(new LecturaDeReunion(1, 2, TEXTO, null, false));
        useCase.buscarPendientes(Instant.now());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.eventos_meet_transcripciones WHERE evento_meet_id = ?", Integer.class, sala))
                .isZero();
    }
}
