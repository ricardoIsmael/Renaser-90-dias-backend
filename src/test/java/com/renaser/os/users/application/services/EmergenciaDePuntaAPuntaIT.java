package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.emergencia.AvisarEmergenciaEnSoporteUseCase;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase.PedirAyudaCommand;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase.SetProgramDayCommand;
import com.renaser.os.users.application.ports.out.emergencia.SaveSolicitudDeEmergenciaPort;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El botón de emergencia (D-244) contra Postgres real: V90 acepta la fila, el índice único parcial deja UNA
 * abierta por persona aunque el servicio no lo revise, el mensaje llega al chat de soporte sin repetirse ante una
 * reentrega, y «Cambiar día del programa» deja el pedido resuelto en la misma transacción.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EmergenciaDePuntaAPuntaIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private PedirAyudaPorEmergenciaUseCase pedirAyuda;
    @Autowired
    private AvisarEmergenciaEnSoporteUseCase avisarEnSoporte;
    @Autowired
    private SetTraineeProgramDayUseCase cambiarDia;
    @Autowired
    private SaveSolicitudDeEmergenciaPort guardar;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    private UserId admin;
    private UserId aprendiz;
    private UUID soporte;

    @BeforeEach
    void seed() {
        admin = nuevoUsuario("ADMIN");
        aprendiz = nuevoUsuario("APRENDIZ");
        LocalDate hoyEnLima = clock.now().atZone(LIMA).toLocalDate();
        // Dia 20: empezo hace 19 dias en su zona. Coherente: la columna dice 20 y avanzo hoy.
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                        programa_activado_en, timezone, dia_programa_avanzado_el, dias_ajuste_programa)
                VALUES (?, 20, ?, now(), 'America/Lima', ?, 0)
                """, aprendiz.value(), hoyEnLima.minusDays(19), hoyEnLima);
        soporte = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre)
                VALUES (?, 'SOPORTE', ?, 'Ana – Formación Renaser')
                """, soporte, "soporte:" + aprendiz.value());
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", soporte);
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", aprendiz.value());
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", admin.value());
    }

    private UserId nuevoUsuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Ana Pérez', CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", rol);
        return UserId.of(id);
    }

    @Test
    @DisplayName("pedir, avisar dos veces, cambiar el día: una fila, un mensaje y el pedido resuelto con el día aplicado")
    void dePuntaAPunta() {
        SolicitudDeEmergencia pedida = pedirAyuda.pedir(new PedirAyudaCommand(aprendiz, "Me operaron de urgencia", 12));
        assertThat(pedida.diaAlPedir()).isEqualTo(20);
        assertThat(pedirAyuda.consultar(aprendiz).abierta()).isEqualTo(pedida);

        assertThatThrownBy(() -> pedirAyuda.pedir(new PedirAyudaCommand(aprendiz, "Otra", 10)))
                .isInstanceOf(IllegalStateException.class);

        EmergenciaPedidaEvent evento = new EmergenciaPedidaEvent(pedida.id(), aprendiz, pedida.queOcurrio(), 12, 20);
        avisarEnSoporte.avisar(evento);
        avisarEnSoporte.avisar(evento);
        List<Map<String, Object>> mensajes = jdbcTemplate.queryForList("""
                SELECT tipo::text AS tipo, emisor_id, texto FROM renaser.mensajes
                 WHERE conversacion_id = ? AND texto LIKE 'Emergencia:%'
                """, soporte);
        assertThat(mensajes).as("la reentrega no lo repite").singleElement().satisfies(m -> {
            assertThat(m.get("tipo")).isEqualTo("SISTEMA");
            assertThat(m.get("emisor_id")).isEqualTo(aprendiz.value());
            assertThat(m.get("texto")).isEqualTo(
                    "Emergencia: Me operaron de urgencia\nPide volver al día 12 (hoy está en el día 20).");
        });

        cambiarDia.fijarDia(new SetProgramDayCommand(admin, aprendiz, 12, "Emergencia: Me operaron de urgencia"));

        Map<String, Object> fila = jdbcTemplate.queryForMap("""
                SELECT estado, dia_aplicado, resuelta_por, resuelta_en FROM renaser.solicitudes_emergencia WHERE id = ?
                """, pedida.id());
        assertThat(fila.get("estado")).isEqualTo("RESUELTA");
        assertThat(((Number) fila.get("dia_aplicado")).intValue()).isEqualTo(12);
        assertThat(fila.get("resuelta_por")).isEqualTo(admin.value());
        assertThat(fila.get("resuelta_en")).isNotNull();
        assertThat(pedirAyuda.consultar(aprendiz).abierta()).isNull();
        assertThat(pedirAyuda.consultar(aprendiz).diaActual()).isEqualTo(12);
    }

    @Test
    @DisplayName("el índice único parcial cierra la carrera: dos abiertas de la misma persona no entran")
    void unaAbiertaPorPersonaLaImponeLaBase() {
        guardar.save(SolicitudDeEmergencia.pedir(UUID.randomUUID(), aprendiz, "Uno", 5, 20, clock));

        assertThatThrownBy(() -> guardar.save(SolicitudDeEmergencia.pedir(UUID.randomUUID(), aprendiz, "Dos", 6, 20, clock)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
