package com.renaser.os.evidence.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.application.ports.in.evidencia.ProcesarColaValidacionUseCase;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.ia.ResultadoValidacionIA;
import com.renaser.os.evidence.application.ports.out.ia.ValidacionIAPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Prueba de la corrección de C-4 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html)
 * contra Postgres real (Testcontainers) — a propósito SIN {@code @Transactional} de clase,
 * mismo motivo que {@code ProcesarValidacionV90ServiceTransaccionIT} (que probó C-1): esa
 * anotación abriría una transacción ambiente ANTES de invocar el caso de uso bajo prueba,
 * y la aserción "la IA corre sin transacción activa" pasaría igual con el código viejo
 * (el que causaba C-4) y con el nuevo — la trampa exacta que este test evita.
 *
 * <p>Se autowirea el caso de uso por su interfaz pública ({@link ProcesarColaValidacionUseCase},
 * bean real con el proxy de Spring), nunca instanciando {@code EvidenciaService} con
 * {@code new}: sin el proxy, la ausencia de {@code @Transactional} no significaría nada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EvidenciaProcesarLoteTransaccionIT {

    private static final Instant SUBIDA_1 = Instant.parse("2026-08-25T09:00:00Z");
    private static final Instant SUBIDA_2 = Instant.parse("2026-08-25T09:05:00Z");

    @Autowired
    private ProcesarColaValidacionUseCase procesarColaUseCase;
    @Autowired
    private SaveEvidenciaPort saveEvidenciaPort;
    @Autowired
    private LoadEvidenciaPort loadEvidenciaPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private ValidacionIAPort validacionIAPort;

    private UserId participanteId;
    private UUID rocaDiariaId;

    /** Misma fixture que {@code EvidenciaPersistenceAdapterTest}/{@code RocaDiariaConcurrenciaTest},
     * pero con {@code JdbcTemplate} (auto-commit por sentencia) en vez de {@code EntityManager}
     * bajo un {@code @Transactional} de test: acá no hay transacción ambiente que la sostenga. */
    @BeforeEach
    void seedParticipanteYRoca() {
        participanteId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """,
                participanteId.value(), participanteId + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 20)",
                participanteId.value());
        rocaDiariaId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO renaser.rocas_diarias
                            (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje)
                        VALUES (?, ?, CURRENT_DATE, 1, 'titulo', CAST('VERDE' AS renaser.color_pareto), 5,
                                CAST('CUERPO' AS renaser.eje_objetivo))
                        """,
                rocaDiariaId, participanteId.value());
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / rocas_diarias / evidencias.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
    }

    private Evidencia crearPendiente(Instant subidaEn) {
        Evidencia evidencia = Evidencia.registrar(EvidenciaId.of(UUID.randomUUID()), participanteId,
                new DestinoEvidencia.RocaDiaria(rocaDiariaId), TipoEvidencia.TEXTO, null, null, "contenido", null,
                null, null, false, subidaEn, FixedClock.at(subidaEn));
        return saveEvidenciaPort.save(evidencia);
    }

    @Test
    @DisplayName("C-4: procesarLote() llama a la IA SIN ninguna transaccion Spring activa")
    void laLlamadaALaIaOcurreFueraDeLaTransaccion() {
        crearPendiente(SUBIDA_1);
        AtomicBoolean transaccionActivaDuranteLaIa = new AtomicBoolean(true);
        when(validacionIAPort.validar(any())).thenAnswer(inv -> {
            transaccionActivaDuranteLaIa.set(TransactionSynchronizationManager.isActualTransactionActive());
            return ResultadoValidacionIA.NO_DISPONIBLE;
        });

        procesarColaUseCase.procesarLote();

        assertThat(transaccionActivaDuranteLaIa).isFalse();
    }

    @Test
    @DisplayName("C-4 (poison pill): una evidencia que lanza en la IA no bloquea al resto del lote, contra "
            + "Postgres real")
    void unFalloDeIaEnUnaEvidenciaNoBloqueaAlRestoDelLote() {
        Evidencia fallaSiempre = crearPendiente(SUBIDA_1);
        Evidencia procesaBien = crearPendiente(SUBIDA_2);
        when(validacionIAPort.validar(any())).thenAnswer(inv -> {
            Evidencia recibida = inv.getArgument(0);
            if (recibida.id().equals(fallaSiempre.id())) {
                throw new RuntimeException("timeout simulado de la IA");
            }
            return ResultadoValidacionIA.APROBADA;
        });

        int procesadas = procesarColaUseCase.procesarLote();

        assertThat(procesadas).isEqualTo(2);
        Evidencia fallaRecargada = loadEvidenciaPort.byId(fallaSiempre.id()).orElseThrow();
        Evidencia procesaBienRecargada = loadEvidenciaPort.byId(procesaBien.id()).orElseThrow();
        // Antes (C-4): la excepcion en fallaSiempre revertia TODA la transaccion, incluida
        // la aprobacion ya hecha de procesaBien -> las dos quedaban PENDIENTE para siempre.
        assertThat(fallaRecargada.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        assertThat(fallaRecargada.intentosIa()).isEqualTo(1);
        assertThat(procesaBienRecargada.estadoValidacion()).isEqualTo(EstadoValidacion.VALIDA);
    }
}
