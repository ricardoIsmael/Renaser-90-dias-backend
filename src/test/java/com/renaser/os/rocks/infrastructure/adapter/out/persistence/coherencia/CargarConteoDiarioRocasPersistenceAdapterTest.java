package com.renaser.os.rocks.infrastructure.adapter.out.persistence.coherencia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.application.ports.out.coherencia.CargarConteoDiarioRocasPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.coherencia.DiaRocas;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-43: el caso que importa acá es que, con varios participantes, UNA sola
 * llamada a {@link CargarConteoDiarioRocasPort} devuelva a todos agrupados
 * por día — nunca una consulta por participante (el incidente real que
 * motivó D-43, ver cabecera de
 * {@code prisma/migrations/general_ranking_scores_function.sql}).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CargarConteoDiarioRocasPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final LocalDate HASTA = LocalDate.of(2026, 8, 24);
    private static final LocalDate DESDE = HASTA.minusDays(6); // 2026-08-18

    @Autowired
    private CargarConteoDiarioRocasPort port;

    @Autowired
    private SaveRocaDiariaPort saveRocaDiariaPort;

    @Autowired
    private EntityManager entityManager;

    private UserId a;
    private UserId b;
    private UserId c;

    @BeforeEach
    void seedParticipantes() {
        a = seedParticipante();
        b = seedParticipante();
        c = seedParticipante(); // sin ninguna roca diaria — debe quedar ausente del resultado
    }

    private UserId seedParticipante() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 20)
                        """)
                .setParameter("id", id.value())
                .executeUpdate();
        return id;
    }

    private RocaDiaria roca(UserId participante, LocalDate fecha, EjeObjetivo eje, boolean completada) {
        RocaDiaria r = RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), participante, fecha, 1,
                "titulo", null, 5, false, eje, null, null, null, CLOCK);
        if (completada) {
            r.completar(CLOCK.now(), CLOCK);
        }
        return r;
    }

    @Test
    void unaSolaLlamadaDevuelveATodosLosParticipantesAgrupadosPorDia() {
        LocalDate dentroVentana1 = LocalDate.of(2026, 8, 20);
        LocalDate dentroVentana2 = LocalDate.of(2026, 8, 22);
        LocalDate fueraDeVentana = LocalDate.of(2026, 8, 10);

        // A: 20/08 -> 3 de 3 completas; 22/08 -> 1 de 2; 10/08 fuera de ventana (no debe contarse)
        saveRocaDiariaPort.saveAll(List.of(
                roca(a, dentroVentana1, EjeObjetivo.CUERPO, true),
                roca(a, dentroVentana1, EjeObjetivo.TRABAJO, true),
                roca(a, dentroVentana1, EjeObjetivo.RELACIONES, true),
                roca(a, dentroVentana2, EjeObjetivo.CUERPO, true),
                roca(a, dentroVentana2, EjeObjetivo.TRABAJO, false),
                roca(a, fueraDeVentana, EjeObjetivo.CUERPO, true)));

        // B: hoy (24/08) -> 1 de 1, sin completar
        saveRocaDiariaPort.saveAll(List.of(roca(b, HASTA, EjeObjetivo.CUERPO, false)));

        // C: ninguna roca diaria en absoluto

        Map<UserId, List<DiaRocas>> resultado = port.conteoDiarioPorParticipante(List.of(a, b, c), DESDE, HASTA);

        assertThat(resultado.get(a)).hasSize(2)
                .anySatisfy(dia -> {
                    assertThat(dia.fecha()).isEqualTo(dentroVentana1);
                    assertThat(dia.total()).isEqualTo(3);
                    assertThat(dia.completadas()).isEqualTo(3);
                })
                .anySatisfy(dia -> {
                    assertThat(dia.fecha()).isEqualTo(dentroVentana2);
                    assertThat(dia.total()).isEqualTo(2);
                    assertThat(dia.completadas()).isEqualTo(1);
                });

        assertThat(resultado.get(b)).hasSize(1);
        assertThat(resultado.get(b).get(0).fecha()).isEqualTo(HASTA);
        assertThat(resultado.get(b).get(0).total()).isEqualTo(1);
        assertThat(resultado.get(b).get(0).completadas()).isEqualTo(0);

        assertThat(resultado.getOrDefault(c, List.of())).isEmpty();
    }
}
