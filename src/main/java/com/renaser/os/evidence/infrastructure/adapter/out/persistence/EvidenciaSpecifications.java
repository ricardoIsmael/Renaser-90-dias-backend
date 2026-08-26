package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.TipoDestino;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort.FiltroEvidencia;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/**
 * Traduce {@link FiltroEvidencia} + cursor de keyset a un {@code Specification} — un
 * predicado por filtro presente, compuestos con {@code and}. Ninguno usa el patrón
 * {@code (:param IS NULL OR ...)} que causó E-31 (docs/BITACORA_ERRORES.md): un filtro
 * ausente simplemente no agrega predicado, en vez de agregar uno que Postgres no puede
 * tipar.
 */
final class EvidenciaSpecifications {

    private EvidenciaSpecifications() {
    }

    static Specification<EvidenciaJpaEntity> filtro(FiltroEvidencia filtro, Instant cursor) {
        // unrestricted() y no where(null): Spring Data JPA 4 agrego una segunda sobrecarga de
        // where (PredicateSpecification), asi que pasarle null ya no compila — es ambiguo.
        Specification<EvidenciaJpaEntity> spec = Specification.unrestricted();
        if (filtro.participanteId() != null) {
            UUID participanteId = filtro.participanteId().value();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("participanteId"), participanteId));
        }
        if (filtro.estado() != null) {
            EstadoValidacionJpa estado = EstadoValidacionJpa.valueOf(filtro.estado().name());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estadoValidacion"), estado));
        }
        if (filtro.tipoDestino() != null) {
            String columna = columnaDeDestino(filtro.tipoDestino());
            spec = spec.and((root, query, cb) -> cb.isNotNull(root.get(columna)));
        }
        if (filtro.desde() != null) {
            Instant desde = filtro.desde();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("creadoEn"), desde));
        }
        if (filtro.hasta() != null) {
            Instant hasta = filtro.hasta();
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("creadoEn"), hasta));
        }
        if (cursor != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("creadoEn"), cursor));
        }
        return spec;
    }

    private static String columnaDeDestino(TipoDestino tipoDestino) {
        return switch (tipoDestino) {
            case REGISTRO_HABITO -> "registroHabitoId";
            case ROCA_DIARIA -> "rocaDiariaId";
            case REGISTRO_ESPIRITU -> "registroEspirituId";
        };
    }
}
