package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Reemplazar el plan de un dia, contra Postgres de verdad.
 *
 * <h2>Por que esto NO se puede probar con dobles</h2>
 *
 * El bug que este test fija (E-209) no estaba en la logica: estaba en <b>cuando</b> se ejecuta el
 * borrado. El primer intento uso un derivado, {@code deleteByParticipanteIdAndFecha}, que Spring
 * Data resuelve cargando las filas y llamando {@code em.remove()} en cada una. Eso encola el
 * borrado hasta el flush, y Hibernate, al hacer flush, ejecuta los <b>INSERT antes que los
 * DELETE</b>: los objetivos nuevos chocaban contra los viejos en
 * {@code rocas_diarias_participante_id_fecha_eje_posicion_key}.
 *
 * <p>Un test con mocks habria pasado sin dudar — el servicio llama al puerto, el puerto llama al
 * repositorio, todo verde. El unique y el orden de flush solo existen contra una base real. Lo
 * encontro una prueba a mano en el emulador; esto es para que no haga falta la proxima vez.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Reemplazar el plan de un dia (E-209)")
class ReemplazarPlanDelDiaIT {

    @Autowired
    private SaveRocaDiariaPort savePort;

    @Autowired
    private LoadRocaDiariaPort loadPort;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * El borrado corre DENTRO de una transaccion, igual que en produccion: {@code crear} es
     * {@code @Transactional} y el {@code flushAutomatically} del repositorio lo exige. Llamar al
     * puerto suelto desde el test daria un {@code TransactionRequiredException} que no dice nada
     * del codigo — solo que el test no lo llamo como lo llama la aplicacion.
     */
    @Autowired
    private TransactionTemplate enTransaccion;

    private UserId participante;
    private LocalDate fecha;

    @BeforeEach
    void sembrarParticipante() {
        UUID id = UUID.randomUUID();
        participante = UserId.of(id);
        fecha = LocalDate.of(2026, 9, 24);
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Prueba', 'APRENDIZ', 'ACTIVO')", id, id + "@prueba.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio) VALUES (?, ?)",
                id, LocalDate.of(2026, 9, 8));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", participante.value());
    }

    private RocaDiaria objetivoDelDia(String titulo, int posicion) {
        return RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), participante, fecha, posicion, titulo,
                null, 5, false, EjeObjetivo.CUERPO, null, null, null, List.of(), clock);
    }

    @Test
    @DisplayName("el plan nuevo entra donde estaba el viejo, sin chocar contra el UNIQUE")
    void elPlanNuevoReemplazaAlViejo() {
        savePort.saveAll(List.of(objetivoDelDia("Caminar 40 minutos", 1)));

        enTransaccion.executeWithoutResult(x -> savePort.borrarDeParticipanteYFecha(participante, fecha));
        // La misma posicion y el mismo eje: es EXACTAMENTE el caso que rompia. Si el borrado
        // quedara encolado, este insert violaria el unique y volveria como 409.
        assertThatCode(() -> savePort.saveAll(List.of(objetivoDelDia("Caminar 20 minutos", 1))))
                .doesNotThrowAnyException();

        List<RocaDiaria> quedaron = loadPort.deParticipanteYFecha(participante, fecha);
        assertThat(quedaron).hasSize(1);
        assertThat(quedaron.get(0).titulo()).isEqualTo("Caminar 20 minutos");
    }

    @Test
    @DisplayName("borrar un dia sin plan no falla: reemplazar el de alguien que nunca planifico es normal")
    void borrarUnDiaVacioNoFalla() {
        assertThatCode(() -> enTransaccion.executeWithoutResult(
                x -> savePort.borrarDeParticipanteYFecha(participante, fecha))).doesNotThrowAnyException();
        assertThat(loadPort.deParticipanteYFecha(participante, fecha)).isEmpty();
    }

    @Test
    @DisplayName("solo borra la fecha pedida: el plan de otro dia queda intacto")
    void noSeLlevaPuestoOtroDia() {
        LocalDate otroDia = fecha.plusDays(1);
        savePort.saveAll(List.of(objetivoDelDia("Caminar 40 minutos", 1)));
        savePort.saveAll(List.of(RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), participante, otroDia,
                1, "Contactar clientes", null, 5, false, EjeObjetivo.TRABAJO, null, null, null, List.of(), clock)));

        enTransaccion.executeWithoutResult(x -> savePort.borrarDeParticipanteYFecha(participante, fecha));

        assertThat(loadPort.deParticipanteYFecha(participante, fecha)).isEmpty();
        assertThat(loadPort.deParticipanteYFecha(participante, otroDia)).hasSize(1);
    }
}
