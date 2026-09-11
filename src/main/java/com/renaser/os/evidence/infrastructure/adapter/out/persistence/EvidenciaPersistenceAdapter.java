package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
class EvidenciaPersistenceAdapter implements LoadEvidenciaPort, SaveEvidenciaPort {

    private final SpringDataEvidenciaRepository repository;
    private final EvidenciaPersistenceMapper mapper;

    EvidenciaPersistenceAdapter(SpringDataEvidenciaRepository repository, EvidenciaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Evidencia> byId(EvidenciaId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Evidencia> byIdParaEscritura(EvidenciaId id) {
        return repository.findByIdParaEscritura(id.value()).map(mapper::toDomain);
    }

    /**
     * C-4 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): {@code @Transactional}
     * explícito y propio, a diferencia del resto de los métodos de lectura de esta clase.
     * {@code EvidenciaService.procesarLote} ya NO envuelve esta llamada en una transacción
     * externa (para no volver a atar el lock a las llamadas de IA que siguen después) —
     * sin esta anotación, un método de consulta sin anotación propia caería en el
     * {@code @Transactional(readOnly = true)} que Spring Data JPA aplica por defecto, y
     * Postgres no permite {@code SELECT ... FOR UPDATE} dentro de una transacción de solo
     * lectura. Sigue siendo una transacción corta: solo cubre el SELECT del lote, nunca
     * la IA.
     */
    @Override
    @Transactional
    public List<Evidencia> pendientesLote(Instant hasta, int limite) {
        return repository.pendientesLote(EstadoValidacionJpa.PENDIENTE, hasta, PageRequest.of(0, limite)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * Corta en seco con la coleccion vacia: un {@code IN ()} sin elementos no es SQL valido y
     * Hibernate lo traduce a un predicado siempre falso, pero igual va y vuelve a la base para
     * nada. Este metodo corre en cada carga de {@code GET /habit-tracks/today} — el dia que un
     * aprendiz no tenga ningun habito, que no cueste una consulta.
     */
    @Override
    public Set<UUID> registrosHabitoConEvidencia(Collection<UUID> registrosHabitoIds) {
        if (registrosHabitoIds == null || registrosHabitoIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.registrosHabitoConEvidencia(registrosHabitoIds));
    }

    @Override
    public Evidencia save(Evidencia evidencia) {
        var saved = repository.saveAndFlush(mapper.toEntity(evidencia));
        return mapper.toDomain(saved);
    }

    /**
     * {@code limite + 1} filas (mismo truco que {@code community.PublicacionPersistenceAdapter.paginaDe}):
     * el llamador sabe si hay página siguiente sin un COUNT aparte.
     */
    @Override
    public List<Evidencia> buscar(FiltroEvidencia filtro, Instant cursor, int limite) {
        Specification<EvidenciaJpaEntity> spec = EvidenciaSpecifications.filtro(filtro, cursor);
        Pageable pageable = PageRequest.of(0, limite + 1, Sort.by(Sort.Direction.DESC, "creadoEn"));
        return repository.findAll(spec, pageable).getContent().stream().map(mapper::toDomain).toList();
    }

    /**
     * Reduce la lectura ordenada a una entrega por obligacion: se queda con la PRIMERA subida y,
     * si alguna de las filas de ese registro esta aprobada, propaga esa aprobacion. Reenviar tres
     * veces el mismo archivo sigue siendo una entrega (plan.md §8).
     */
    @Override
    public Map<UUID, EntregaDeEvidencia> entregasDeRegistros(
            Collection<UUID> registrosHabitoIds) {
        if (registrosHabitoIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EntregaDeEvidencia> porRegistro =
                new LinkedHashMap<>();
        for (Object[] fila : repository.entregasDeRegistros(registrosHabitoIds)) {
            UUID registroId = (UUID) fila[0];
            UUID evidenciaId = (UUID) fila[1];
            Instant subidaEn = (Instant) fila[2];
            EstadoValidacion estado = traducirEstadoValidacion(fila[3]);

            EntregaDeEvidencia previa = porRegistro.get(registroId);
            if (previa == null) {
                porRegistro.put(registroId, new EntregaDeEvidencia(
                        registroId, evidenciaId, subidaEn, estado, 1));
                continue;
            }
            // La consulta viene ordenada por subidaEn, asi que `previa` ya es la mas temprana:
            // solo se acumula el conteo y, si esta llego aprobada, se conserva esa aprobacion.
            EstadoValidacion mejor =
                    previa.verificada() || estado == EstadoValidacion.VALIDA
                            ? EstadoValidacion.VALIDA
                            : previa.estadoRevision();
            porRegistro.put(registroId, new EntregaDeEvidencia(
                    registroId, previa.evidenciaId(), previa.primeraEntregaEn(), mejor, previa.archivos() + 1));
        }
        return porRegistro;
    }

    private static EstadoValidacion traducirEstadoValidacion(Object valor) {
        return EstadoValidacion.valueOf(String.valueOf(valor));
    }
}
