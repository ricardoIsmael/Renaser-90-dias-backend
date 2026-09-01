package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.TipoDestino;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort.FiltroEvidencia;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `evidence` es la dueña REAL de `evidencias` (a diferencia del INSERT nativo que
 * `rocks` usaba antes — RK-2, cerrado). Cubre el round-trip JPA, el índice único de
 * `es_principal`, y los CHECK de arco exclusivo / media-o-texto de la tabla real —
 * estos dos últimos vía INSERT nativo porque el dominio (`DestinoEvidencia` sealed +
 * las validaciones de {@code Evidencia.registrar}) hace esos estados irrepresentables
 * en Java: la única forma de probar que la BASE los rechaza es evitando el dominio.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EvidenciaPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T12:00:00Z"));

    @Autowired
    private EvidenciaPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;
    private UUID rocaDiariaId;

    @BeforeEach
    void seedParticipanteYRoca() {
        participanteId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", participanteId.value())
                .setParameter("email", participanteId + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 20)
                        """)
                .setParameter("id", participanteId.value())
                .executeUpdate();
        rocaDiariaId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.rocas_diarias
                            (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje)
                        VALUES (:id, :pid, CURRENT_DATE, 1, 'titulo', CAST('VERDE' AS renaser.color_pareto), 5,
                                CAST('CUERPO' AS renaser.eje_objetivo))
                        """)
                .setParameter("id", rocaDiariaId)
                .setParameter("pid", participanteId.value())
                .executeUpdate();
    }

    /** Un id cualquiera, distinto por evidencia: lo que debe fallar en los tests de constraint es el
     * indice unico de la tabla, no una colision de clave primaria. */
    private static EvidenciaId nuevoId() {
        return EvidenciaId.of(UUID.randomUUID());
    }

    private Evidencia evidenciaRocaTexto(boolean esPrincipal) {
        return Evidencia.registrar(nuevoId(), participanteId, new DestinoEvidencia.RocaDiaria(rocaDiariaId),
                TipoEvidencia.TEXTO, null, null, "listo", null, -12.05, -77.03, esPrincipal, CLOCK.now(), CLOCK);
    }

    /** {@code creadoEn} distinto por evidencia (a diferencia de {@link #evidenciaRocaTexto}, que siempre usa
     * {@code CLOCK} fijo) — necesario para probar orden de keyset y filtros de rango de fechas. */
    private Evidencia evidenciaRocaTextoEn(Instant creadoEn, boolean esPrincipal) {
        return Evidencia.registrar(nuevoId(), participanteId, new DestinoEvidencia.RocaDiaria(rocaDiariaId),
                TipoEvidencia.TEXTO, null, null, "contenido", null, null, null, esPrincipal, creadoEn,
                FixedClock.at(creadoEn));
    }

    @Test
    void guardaYRecuperaUnaEvidenciaDeRocaPorTexto() {
        Evidencia guardada = adapter.save(evidenciaRocaTexto(true));

        Optional<Evidencia> recuperada = adapter.byId(guardada.id());

        assertThat(recuperada).isPresent();
        Evidencia e = recuperada.get();
        assertThat(e.participanteId()).isEqualTo(participanteId);
        assertThat(e.destino()).isInstanceOf(DestinoEvidencia.RocaDiaria.class);
        assertThat(((DestinoEvidencia.RocaDiaria) e.destino()).rocaDiariaId()).isEqualTo(rocaDiariaId);
        assertThat(e.tipo()).isEqualTo(TipoEvidencia.TEXTO);
        assertThat(e.contenidoTexto()).isEqualTo("listo");
        assertThat(e.gpsLat()).isEqualTo(-12.05);
        assertThat(e.esPrincipal()).isTrue();
        assertThat(e.estadoValidacion().name()).isEqualTo("PENDIENTE");
    }

    @Test
    void byIdDeIdInexistenteDevuelveVacio() {
        assertThat(adapter.byId(EvidenciaId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("indice unico evidencias_principal_uk: dos evidencias 'esPrincipal' para la misma roca -> conflicto")
    void indiceUnicoDeEsPrincipalRechazaDosPrincipalesParaLaMismaRoca() {
        adapter.save(evidenciaRocaTexto(true));

        assertThatThrownBy(() -> adapter.save(evidenciaRocaTexto(true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pendientesLoteDevuelveSoloLasPendientesOrdenadasPorSubidaEn() {
        Evidencia primera = adapter.save(Evidencia.registrar(nuevoId(), participanteId,
                new DestinoEvidencia.RocaDiaria(rocaDiariaId),
                TipoEvidencia.TEXTO, null, null, "una", null, null, null, false, Instant.parse("2026-08-25T09:00:00Z"),
                CLOCK));
        Evidencia segunda = adapter.save(Evidencia.registrar(nuevoId(), participanteId,
                new DestinoEvidencia.RocaDiaria(rocaDiariaId),
                TipoEvidencia.TEXTO, null, null, "otra", null, null, null, false, Instant.parse("2026-08-25T09:05:00Z"),
                CLOCK));
        Evidencia aprobada = adapter.save(evidenciaRocaTexto(false));
        aprobada.aprobarPorIa();
        adapter.save(aprobada);

        List<Evidencia> lote = adapter.pendientesLote(Instant.parse("2026-08-25T23:59:59Z"), 25);

        assertThat(lote).extracting(e -> e.id().value()).containsExactly(primera.id().value(), segunda.id().value());
    }

    // ---- buscar (hueco #19/#20): filtros + keyset ----

    @Test
    @DisplayName("buscar: ordena por creadoEn descendente (mas nueva primero) y respeta el cursor de keyset")
    void buscarOrdenaPorCreadoEnDescendenteYRespetaCursor() {
        Evidencia e1 = adapter.save(evidenciaRocaTextoEn(Instant.parse("2026-08-25T09:00:00Z"), false));
        Evidencia e2 = adapter.save(evidenciaRocaTextoEn(Instant.parse("2026-08-25T09:05:00Z"), false));
        Evidencia e3 = adapter.save(evidenciaRocaTextoEn(Instant.parse("2026-08-25T09:10:00Z"), false));

        List<Evidencia> primeraPagina = adapter.buscar(new FiltroEvidencia(participanteId, null, null, null, null),
                null, 2);
        assertThat(primeraPagina).extracting(e -> e.id().value())
                .containsExactly(e3.id().value(), e2.id().value(), e1.id().value());

        List<Evidencia> siguientePagina = adapter.buscar(new FiltroEvidencia(participanteId, null, null, null, null),
                e2.creadoEn(), 2);
        assertThat(siguientePagina).extracting(e -> e.id().value()).containsExactly(e1.id().value());
    }

    @Test
    void buscarFiltraPorEstado() {
        Evidencia pendiente = adapter.save(evidenciaRocaTextoEn(Instant.parse("2026-08-25T09:00:00Z"), false));
        Evidencia aprobada = evidenciaRocaTextoEn(Instant.parse("2026-08-25T09:05:00Z"), false);
        aprobada.aprobarPorIa();
        adapter.save(aprobada);

        List<Evidencia> pendientes = adapter.buscar(
                new FiltroEvidencia(null, EstadoValidacion.PENDIENTE, null, null, null), null, 10);

        assertThat(pendientes).extracting(e -> e.id().value()).containsExactly(pendiente.id().value());
    }

    @Test
    void buscarFiltraPorTipoDestino() {
        adapter.save(evidenciaRocaTextoEn(Instant.parse("2026-08-25T09:00:00Z"), false));

        List<Evidencia> deRoca = adapter.buscar(new FiltroEvidencia(null, null, TipoDestino.ROCA_DIARIA, null, null),
                null, 10);
        List<Evidencia> deHabito = adapter.buscar(
                new FiltroEvidencia(null, null, TipoDestino.REGISTRO_HABITO, null, null), null, 10);

        assertThat(deRoca).isNotEmpty();
        assertThat(deHabito).isEmpty();
    }

    @Test
    void buscarFiltraPorRangoDeFechas() {
        adapter.save(evidenciaRocaTextoEn(Instant.parse("2026-08-20T09:00:00Z"), false));
        Evidencia reciente = adapter.save(evidenciaRocaTextoEn(Instant.parse("2026-08-25T09:00:00Z"), false));

        List<Evidencia> resultado = adapter.buscar(
                new FiltroEvidencia(null, null, null, Instant.parse("2026-08-24T00:00:00Z"), null), null, 10);

        assertThat(resultado).extracting(e -> e.id().value()).containsExactly(reciente.id().value());
    }

    @Test
    @DisplayName("buscar: filtra por participanteId, no devuelve evidencia de otro participante")
    void buscarFiltraPorParticipanteId() {
        UserId otroParticipante = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture Otro', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", otroParticipante.value())
                .setParameter("email", otroParticipante + "-otro@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 5)
                        """)
                .setParameter("id", otroParticipante.value())
                .executeUpdate();
        UUID otraRoca = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.rocas_diarias
                            (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje)
                        VALUES (:id, :pid, CURRENT_DATE, 1, 'titulo2', CAST('VERDE' AS renaser.color_pareto), 5,
                                CAST('CUERPO' AS renaser.eje_objetivo))
                        """)
                .setParameter("id", otraRoca)
                .setParameter("pid", otroParticipante.value())
                .executeUpdate();
        Evidencia miEvidencia = adapter.save(evidenciaRocaTextoEn(Instant.parse("2026-08-25T09:00:00Z"), false));
        adapter.save(Evidencia.registrar(nuevoId(), otroParticipante, new DestinoEvidencia.RocaDiaria(otraRoca),
                TipoEvidencia.TEXTO, null, null, "otro", null, null, null, false,
                Instant.parse("2026-08-25T09:05:00Z"), FixedClock.at(Instant.parse("2026-08-25T09:05:00Z"))));

        List<Evidencia> resultado = adapter.buscar(new FiltroEvidencia(participanteId, null, null, null, null), null,
                10);

        assertThat(resultado).extracting(e -> e.id().value()).containsExactly(miEvidencia.id().value());
    }

    // ---- CHECKs de la tabla real, probados con INSERT nativo (el dominio no permite construirlos) ----

    @Test
    void checkArcoExclusivoRechazaFilaSinNingunDestino() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO renaser.evidencias (id, participante_id, tipo, contenido_texto)
                        VALUES (:id, :pid, CAST('TEXTO' AS renaser.tipo_evidencia), 'x')
                        """)
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("pid", participanteId.value())
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("CHECK evidencia_un_destino: dos destinos a la vez (roca_diaria_id + registro_habito_id) se rechaza")
    void checkArcoExclusivoRechazaFilaConDosDestinos() {
        // No hace falta que registro_habito_id referencie una fila real: la fila se rechaza de
        // todas formas (CHECK o FK, ambas son integridad de la tabla real) apenas hay dos
        // columnas de destino no-nulas a la vez — eso es exactamente lo que el arco exclusivo prohibe.
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO renaser.evidencias
                            (id, participante_id, roca_diaria_id, registro_habito_id, tipo, contenido_texto)
                        VALUES (:id, :pid, :roca, :registro, CAST('TEXTO' AS renaser.tipo_evidencia), 'x')
                        """)
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("pid", participanteId.value())
                        .setParameter("roca", rocaDiariaId)
                        .setParameter("registro", UUID.randomUUID())
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void checkMediaOTextoRechazaTextoSinContenido() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO renaser.evidencias (id, participante_id, roca_diaria_id, tipo)
                        VALUES (:id, :pid, :roca, CAST('TEXTO' AS renaser.tipo_evidencia))
                        """)
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("pid", participanteId.value())
                        .setParameter("roca", rocaDiariaId)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }
}
