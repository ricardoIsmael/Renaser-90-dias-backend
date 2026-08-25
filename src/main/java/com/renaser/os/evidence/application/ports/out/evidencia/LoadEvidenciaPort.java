package com.renaser.os.evidence.application.ports.out.evidencia;

import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadEvidenciaPort {

    Optional<Evidencia> byId(EvidenciaId id);

    /**
     * Lote de evidencias {@code PENDIENTE} con {@code subida_en <= hasta}, ordenadas por
     * {@code subida_en} ascendente, con {@code FOR UPDATE SKIP LOCKED} sobre
     * {@code evidencias_cola_ia_idx} — seguro con múltiples instancias del scheduler
     * corriendo a la vez (mismo patrón que {@code calendar.LoadRecordatorioPort}, que a
     * su vez cita el comentario original de este mismo índice en el baseline).
     */
    List<Evidencia> pendientesLote(Instant hasta, int limite);
}
