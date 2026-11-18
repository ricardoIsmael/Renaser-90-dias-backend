package com.renaser.os.leadership;

import com.renaser.os.EventosPendientes;
import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase.RegistrarObservacionCommand;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que el mentor recibe cuando el líder le deja una observación (spec 056, FR-025 y SC-005), contra Postgres de verdad: el texto en su chat con el líder,
 * el aviso en su bandeja con la ruta de ese chat, y todo UNA sola vez por {@code operationKey} — también con dos pedidos a la vez, que es lo que un doble
 * no prueba: el índice único de {@code observaciones_mentor} es lo que deshace el mensaje y el aviso del que pierde la carrera.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ObservacionesAlMentorIT {

    @Autowired private RegistrarObservacionUseCase registrar;
    @Autowired private JdbcTemplate jdbc;

    private final List<UUID> usuarios = new ArrayList<>();
    private UUID lider;
    private UUID mentor;

    @BeforeEach
    void sembrar() {
        usuarios.clear();
        lider = usuario("LIDER_MENTORES", "Lucía Líder");
        mentor = usuario("MENTOR", "Mateo Mentor");
    }

    @AfterEach
    void limpiar() {
        EventosPendientes.esperarQueTerminen(jdbc);
        for (UUID id : usuarios) {
            jdbc.update("DELETE FROM renaser.observaciones_mentor WHERE autor_id = ? OR mentor_id = ?", id, id);
        }
        for (UUID id : usuarios) {
            jdbc.update("DELETE FROM renaser.conversaciones WHERE id IN (SELECT conversacion_id FROM renaser.participantes_conversacion WHERE usuario_id = ?)", id);
            jdbc.update("DELETE FROM renaser.formaciones_personal WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
        }
    }

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        usuarios.add(id);
        return id;
    }

    /** Un mentor que trabaja SOLO en el Diplomado: no comparte ninguna formación con el líder de la Escuela, así que el chat de dos no se puede abrir. */
    private UUID mentorDelDiplomado() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Dora Diplomado', 'MENTOR', 'ACTIVO')
                """, id, id + "@renaser.test");
        jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, 'DIPLOMADO', true)", id);
        usuarios.add(id);
        return id;
    }

    private RegistrarObservacionCommand sugerencia(String clave) {
        return new RegistrarObservacionCommand(UserId.of(lider), UserId.of(mentor), "SUGERENCIA", "Las consultas del fin de semana", false, null, clave);
    }

    private int cuenta(String sql, Object... parametros) {
        Integer n = jdbc.queryForObject(sql, Integer.class, parametros);
        return n == null ? 0 : n;
    }

    private int mensajesDelLiderAlMentor() {
        return cuenta("""
                SELECT count(*) FROM renaser.mensajes m
                JOIN renaser.conversaciones c ON c.id = m.conversacion_id AND c.tipo = 'DIRECTA'
                WHERE m.emisor_id = ? AND m.texto = 'Las consultas del fin de semana'
                  AND EXISTS (SELECT 1 FROM renaser.participantes_conversacion p WHERE p.conversacion_id = c.id AND p.usuario_id = ?)
                """, lider, mentor);
    }

    private List<String> avisosDelMentor() {
        return jdbc.queryForList("SELECT titulo || '|' || ruta_app FROM renaser.notificaciones WHERE usuario_id = ? AND tipo = 'REVISION_DEL_LIDER'",
                String.class, mentor);
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025 SC-005: la observación llega al mentor como mensaje en su chat con el líder y como aviso con la ruta de ese chat, una sola vez por clave")
    void llegaUnaSolaVez() {
        ObservacionDeMentor primera = registrar.registrar(sugerencia("k-it-1"));
        ObservacionDeMentor repetida = registrar.registrar(sugerencia("k-it-1"));
        EventosPendientes.esperarQueTerminen(jdbc);

        assertThat(repetida.id()).isEqualTo(primera.id());
        assertThat(primera.enviadaPorChat()).isTrue();
        assertThat(mensajesDelLiderAlMentor()).as("un solo mensaje en el chat").isEqualTo(1);
        UUID conversacion = jdbc.queryForObject("SELECT conversacion_id FROM renaser.mensajes WHERE id = ?", UUID.class, primera.mensajeId());
        assertThat(avisosDelMentor()).as("un solo aviso, con la ruta del chat donde está el texto")
                .containsExactly("Tu líder te dejó una sugerencia|/chat/" + conversacion);
        assertThat(cuenta("SELECT count(*) FROM renaser.observaciones_mentor WHERE autor_id = ?", lider)).isEqualTo(1);
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025 SC-005: dos pedidos a la vez con la misma clave dejan una observación, un mensaje y un aviso")
    void dosPedidosALaVezNoDuplican() throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Callable<ObservacionDeMentor> pedido = () -> {
                salida.await();
                return registrar.registrar(sugerencia("k-it-2"));
            };
            Future<ObservacionDeMentor> uno = hilos.submit(pedido);
            Future<ObservacionDeMentor> otro = hilos.submit(pedido);
            salida.countDown();

            int exitos = 0;
            for (Future<ObservacionDeMentor> resultado : List.of(uno, otro)) {
                try {
                    resultado.get();
                    exitos++;
                } catch (ExecutionException perdio) {
                    assertThat(perdio.getCause()).as("el que pierde la carrera recibe el 409 del índice único, no un 500").isInstanceOf(IllegalStateException.class);
                }
            }
            assertThat(exitos).isGreaterThanOrEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
        EventosPendientes.esperarQueTerminen(jdbc);

        assertThat(cuenta("SELECT count(*) FROM renaser.observaciones_mentor WHERE autor_id = ?", lider)).isEqualTo(1);
        assertThat(mensajesDelLiderAlMentor()).as("el mensaje del que perdió se deshizo con su transacción").isEqualTo(1);
        assertThat(avisosDelMentor()).hasSize(1);
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025 SC-005: si el chat de dos no se puede abrir (el mentor es de otra formación), el motivo sube y no queda ni la observación, ni el mensaje, ni el aviso")
    void siElChatNoSePuedeAbrirNoQuedaNada() {
        UUID deOtraFormacion = mentorDelDiplomado();

        assertThatThrownBy(() -> registrar.registrar(new RegistrarObservacionCommand(UserId.of(lider), UserId.of(deOtraFormacion), "ALERTA",
                "Revisa el cierre de la semana", false, null, "k-it-3"))).isInstanceOf(NoSuchElementException.class);
        EventosPendientes.esperarQueTerminen(jdbc);

        assertThat(cuenta("SELECT count(*) FROM renaser.observaciones_mentor WHERE autor_id = ?", lider)).isZero();
        assertThat(cuenta("SELECT count(*) FROM renaser.mensajes WHERE emisor_id = ?", lider)).isZero();
        assertThat(cuenta("SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ? AND tipo = 'REVISION_DEL_LIDER'", deOtraFormacion)).isZero();
    }
}
