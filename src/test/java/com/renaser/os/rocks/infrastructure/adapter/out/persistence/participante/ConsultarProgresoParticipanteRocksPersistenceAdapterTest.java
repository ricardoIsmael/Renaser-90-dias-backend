package com.renaser.os.rocks.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Copia propia (RK-1) del patron de `phasecontracts` — ver javadoc del puerto. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConsultarProgresoParticipanteRocksPersistenceAdapterTest {

    @Autowired
    private ConsultarProgresoParticipanteRocksPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId crearParticipante(String rolCrudo, String estadoCrudo, int diaPrograma, String timezone,
                                      LocalDate fechaInicio) {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario), CAST(:estado AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .setParameter("rol", rolCrudo)
                .setParameter("estado", estadoCrudo)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, fecha_inicio)
                        VALUES (:id, :dia, :tz, :fecha)
                        """)
                .setParameter("id", id.value())
                .setParameter("dia", diaPrograma)
                .setParameter("tz", timezone)
                .setParameter("fecha", fechaInicio)
                .executeUpdate();
        return id;
    }

    @Test
    void devuelveDiaProgramaZonaYFechaDeUnAprendizActivo() {
        UserId id = crearParticipante("APRENDIZ", "ACTIVO", 20, "America/Lima", LocalDate.of(2026, 8, 1));

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().diaPrograma()).isEqualTo(20);
        assertThat(progreso.get().rol()).isEqualTo(RolParticipante.TRAINEE);
        assertThat(progreso.get().suspendido()).isFalse();
        assertThat(progreso.get().zona()).isEqualTo(ZoneId.of("America/Lima"));
        // Sin programa_activado_en: la fecha_inicio es la provisional del alta y no viaja como Dia 1 (D-201, D-203).
        assertThat(progreso.get().diaUnoElegido()).isNull();
    }

    @Test
    void marcaSuspendidoCuandoElEstadoEsSuspendido() {
        UserId id = crearParticipante("APRENDIZ", "SUSPENDIDO", 10, "America/Lima", LocalDate.of(2026, 8, 1));

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().suspendido()).isTrue();
    }

    @Test
    void devuelveVacioSiElParticipanteNoExiste() {
        assertThat(adapter.deParticipante(UserId.of(UUID.randomUUID()))).isEmpty();
    }

    /* --------------------------------------------------------------------------------------------
     * D-203 (E-339): de qué fecha sale la semana de rocas, contra el users.api real. Desde el día 90
     * users da el día acotado, y el adaptador trae la fecha real del día 90; antes no la pide.
     * ------------------------------------------------------------------------------------------ */

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private UserId crearActivado(LocalDate fechaInicio, int diasAjuste) {
        UserId id = crearParticipante("APRENDIZ", "ACTIVO", 0, "America/Lima", fechaInicio);
        entityManager.createNativeQuery("""
                        UPDATE renaser.participantes_programa
                           SET programa_activado_en = now(), dias_ajuste_programa = :ajuste
                         WHERE usuario_id = :id
                        """)
                .setParameter("ajuste", diasAjuste)
                .setParameter("id", id.value())
                .executeUpdate();
        return id;
    }

    @Test
    void unGraduadoSinAjusteTraeSuDiaNoventaYSuSemanaSaleDeLaFechaDeInicio() {
        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate inicio = hoy.minusDays(100);
        UserId id = crearActivado(inicio, 0);

        var progreso = adapter.deParticipante(id).orElseThrow();

        assertThat(progreso.diaUnoElegido()).isEqualTo(inicio);
        assertThat(progreso.diaPrograma()).isEqualTo(90);
        assertThat(progreso.ultimaFechaDelPrograma()).isEqualTo(inicio.plusDays(89));
        assertThat(progreso.semanas(hoy).orElseThrow().primerDia()).isEqualTo(inicio);
    }

    @Test
    void unGraduadoConAjusteTraeElDiaNoventaCorridoYLaSemanaLoAcompana() {
        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate inicio = hoy.minusDays(110);
        UserId id = crearActivado(inicio, 5);

        var progreso = adapter.deParticipante(id).orElseThrow();

        assertThat(progreso.diaPrograma()).isEqualTo(90);
        assertThat(progreso.ultimaFechaDelPrograma()).isEqualTo(inicio.plusDays(94));
        assertThat(progreso.semanas(hoy).orElseThrow().primerDia()).isEqualTo(inicio.plusDays(5));
    }

    @Test
    void enCursoNoPideLaFechaDelDiaNoventaYElAnclaSaleDelDia() {
        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate inicio = hoy.minusDays(40);
        UserId id = crearActivado(inicio, -3);

        var progreso = adapter.deParticipante(id).orElseThrow();

        assertThat(progreso.diaPrograma()).isEqualTo(44);
        assertThat(progreso.ultimaFechaDelPrograma()).isNull();
        assertThat(progreso.semanas(hoy).orElseThrow().primerDia()).isEqualTo(inicio.minusDays(3));
    }

    /** D-201: la fecha provisional del alta (ya pasada: tardo en activar) no es un Dia 1 ni ancla ninguna semana. */
    @Test
    void sinActivarNoHayDiaUnoNiSemanasConFechas() {
        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate provisional = hoy.minusDays(12);
        UserId id = crearParticipante("APRENDIZ", "ACTIVO", 0, "America/Lima", provisional);

        var progreso = adapter.deParticipante(id).orElseThrow();

        assertThat(progreso.programaActivado()).isFalse();
        assertThat(progreso.diaUnoElegido()).isNull();
        assertThat(progreso.semanas(hoy)).isEmpty();
    }
}
