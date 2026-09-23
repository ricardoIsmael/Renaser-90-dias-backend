package com.renaser.os.rag.infrastructure.adapter.out.persistence.propuesta;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.in.propuesta.ResolverPropuestaUseCase;
import com.renaser.os.rag.application.ports.out.propuesta.LoadPropuestaAccionPort;
import com.renaser.os.rag.application.ports.out.propuesta.PropuestaModificadaEnParaleloException;
import com.renaser.os.rag.application.ports.out.propuesta.SavePropuestaAccionPort;
import com.renaser.os.rag.application.services.herramientas.AccionConfirmable;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.propuesta.EstadoPropuesta;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccion;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccionId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code propuestas_acompanante} (V63) contra Postgres real: lo que un fake en memoria no puede
 * demostrar es que el {@code @Version} de la entidad, con el mapper a mano de por medio, de verdad
 * rechaza una escritura sobre una version vieja — que es TODO lo que impide que un doble toque
 * sobre "Confirmar" ejecute dos veces (D-153). Tambien que el {@code jsonb} y los CHECK aceptan lo
 * que escribe el dominio.
 *
 * <p>El ultimo caso va de punta a punta por {@link ResolverPropuestaUseCase}, con toques de verdad
 * simultaneos ({@link CyclicBarrier}, mismo patron que {@code AsignacionCelulaConcurrenciaIT}) y
 * una accion de prueba que cuenta cuantas veces se ejecuto.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PropuestaAccionPersistenceAdapterIT.AccionDePruebaConfig.class})
class PropuestaAccionPersistenceAdapterIT {

    private static final String HERRAMIENTA_DE_PRUEBA = "prueba_it_contador";
    private static final int TOQUES_SIMULTANEOS = 6;
    private static final AtomicInteger EJECUCIONES = new AtomicInteger();

    @Autowired
    private LoadPropuestaAccionPort loadPort;
    @Autowired
    private SavePropuestaAccionPort savePort;
    @Autowired
    private ProponerAccionUseCase proponerUseCase;
    @Autowired
    private ResolverPropuestaUseCase resolverUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId participante;

    @TestConfiguration
    static class AccionDePruebaConfig {

        /** Tarda un poco a proposito: ensancha la ventana en que los toques se solapan. */
        @Bean
        AccionConfirmable accionQueCuenta() {
            return new AccionConfirmable() {
                @Override
                public String herramienta() {
                    return HERRAMIENTA_DE_PRUEBA;
                }

                @Override
                public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
                    EJECUCIONES.incrementAndGet();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return ResultadoHerramienta.exito("Hecho.");
                }
            };
        }
    }

    @BeforeEach
    void seedParticipante() {
        EJECUCIONES.set(0);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture propuestas', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        participante = UserId.of(id);
    }

    @AfterEach
    void limpiar() {
        // propuestas_acompanante cae por ON DELETE CASCADE de usuarios.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participante.value());
    }

    /** Microsegundos: es la precision de timestamptz, asi la ida y vuelta compara exacto. */
    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private PropuestaAccion nueva(Instant creadaEn) {
        return PropuestaAccion.crear(PropuestaAccionId.of(UUID.randomUUID()), participante,
                new InvocacionHerramienta("marcar_habito_completado", Map.of("registro_id", "r-1", "nota", "a=b")),
                "Marcar Meditar como hecho", creadaEn, Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("ida y vuelta: argumentos jsonb, huella integra y version inicial")
    void idaYVuelta() {
        PropuestaAccion guardada = savePort.save(nueva(ahora()));

        PropuestaAccion leida = loadPort.porId(guardada.id()).orElseThrow();

        assertThat(leida.invocacion()).isEqualTo(guardada.invocacion());
        assertThat(leida.argumentosIntegros()).isTrue();
        assertThat(leida.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(leida.venceEn()).isEqualTo(guardada.venceEn());
        assertThat(leida.version()).isNotNull();
    }

    @Test
    @DisplayName("dos confirmaciones sobre la misma version: la segunda es rechazada por el bloqueo optimista")
    void dobleConfirmacionSobreLaMismaVersion() {
        PropuestaAccion guardada = savePort.save(nueva(ahora()));
        PropuestaAccion primerToque = loadPort.porId(guardada.id()).orElseThrow();
        PropuestaAccion segundoToque = loadPort.porId(guardada.id()).orElseThrow();
        primerToque.confirmar(ahora());
        segundoToque.confirmar(ahora());

        PropuestaAccion reclamada = savePort.save(primerToque);

        assertThatThrownBy(() -> savePort.save(segundoToque))
                .isInstanceOf(PropuestaModificadaEnParaleloException.class);
        assertThat(reclamada.version()).isGreaterThan(guardada.version());
        assertThat(loadPort.porId(guardada.id()).orElseThrow().estado()).isEqualTo(EstadoPropuesta.CONFIRMADA);
    }

    @Test
    @DisplayName("pendientes del turno: solo PENDIENTE, del participante, desde el instante pedido y en orden")
    void pendientesCreadasDesde() {
        Instant base = ahora();
        savePort.save(nueva(base.minusSeconds(60)));
        PropuestaAccion primera = savePort.save(nueva(base));
        PropuestaAccion segunda = savePort.save(nueva(base.plusSeconds(1)));
        PropuestaAccion cancelada = nueva(base.plusSeconds(2));
        cancelada.cancelar(base.plusSeconds(3));
        savePort.save(cancelada);

        assertThat(loadPort.pendientesCreadasDesde(participante, base))
                .extracting(PropuestaAccion::id)
                .containsExactly(primera.id(), segunda.id());
    }

    @Test
    @DisplayName("toques simultaneos sobre Confirmar: la accion se ejecuta exactamente una vez")
    void toquesSimultaneosEjecutanUnaVez() throws Exception {
        PropuestaCreada creada = proponerUseCase.proponer(participante,
                InvocacionHerramienta.sinArgumentos(HERRAMIENTA_DE_PRUEBA), "Accion de prueba");

        List<ResultadoHerramienta> resultados = enParalelo(() -> resolverUseCase.confirmar(participante, creada.id()));

        assertThat(EJECUCIONES).hasValue(1);
        assertThat(resultados).hasSize(TOQUES_SIMULTANEOS).allMatch(r -> r instanceof ResultadoHerramienta.Exito);
        assertThat(loadPort.porId(PropuestaAccionId.of(creada.id())).orElseThrow().resultadoRegistrado())
                .contains(ResultadoHerramienta.exito("Hecho."));
    }

    private List<ResultadoHerramienta> enParalelo(Callable<ResultadoHerramienta> toque) throws Exception {
        CyclicBarrier todosListos = new CyclicBarrier(TOQUES_SIMULTANEOS);
        ExecutorService pool = Executors.newFixedThreadPool(TOQUES_SIMULTANEOS);
        List<Future<ResultadoHerramienta>> futuros;
        try {
            List<Callable<ResultadoHerramienta>> sincronizados = new ArrayList<>();
            for (int i = 0; i < TOQUES_SIMULTANEOS; i++) {
                sincronizados.add(() -> {
                    todosListos.await(10, TimeUnit.SECONDS);
                    return toque.call();
                });
            }
            futuros = pool.invokeAll(sincronizados, 60, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }
        List<ResultadoHerramienta> resultados = new ArrayList<>();
        for (Future<ResultadoHerramienta> futuro : futuros) {
            resultados.add(futuro.get());
        }
        return resultados;
    }
}
