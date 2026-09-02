package com.renaser.os.evidence.application.ports.out.evidencia;

import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.TipoDestino;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadEvidenciaPort {

    Optional<Evidencia> byId(EvidenciaId id);

    /**
     * Versión con bloqueo para el camino de escritura (anular veredicto): evita que dos
     * admins concurrentes (o un doble clic) lean la misma evidencia con
     * {@code penalizacionAplicada=true} antes de que ninguno escriba y ambos le pidan a
     * {@code points} que revierta la penalización dos veces — mismo patrón que
     * {@code rocks.LoadRocaDiariaPort.byIdParaEscritura} (C-2/C-13,
     * docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html).
     */
    Optional<Evidencia> byIdParaEscritura(EvidenciaId id);

    /**
     * Lote de evidencias {@code PENDIENTE} con {@code subida_en <= hasta}, ordenadas por
     * {@code subida_en} ascendente, con {@code FOR UPDATE SKIP LOCKED} sobre
     * {@code evidencias_cola_ia_idx} — seguro con múltiples instancias del scheduler
     * corriendo a la vez (mismo patrón que {@code calendar.LoadRecordatorioPort}, que a
     * su vez cita el comentario original de este mismo índice en el baseline).
     */
    List<Evidencia> pendientesLote(Instant hasta, int limite);

    /**
     * Listado paginado por keyset, más nueva primero ({@code creadoEn} descendente) —
     * mismo patrón que {@code community.LoadPublicacionPort.feed}: {@code cursor} es el
     * {@code creadoEn} de la última evidencia ya cargada ({@code null} para la primera
     * página), y {@code limite} es el tamaño de página deseado; el adaptador trae
     * {@code limite + 1} filas para que el llamador sepa si hay más sin un COUNT aparte.
     * Todos los campos de {@link FiltroEvidencia} son opcionales — filtro vacío = todas
     * las evidencias visibles para quien haya resuelto el filtro (la autorización, quién
     * puede pedir qué {@code participanteId}, vive en {@code EvidenciaService}, no acá).
     */
    List<Evidencia> buscar(FiltroEvidencia filtro, Instant cursor, int limite);

    /**
     * Filtro de {@link #buscar}. {@code null} en cualquier campo = sin restringir por
     * ese campo. {@code desde}/{@code hasta} filtran por {@code creadoEn} (mismo campo
     * que ordena el keyset, para que "evidencia subida en tal rango" y "página siguiente"
     * hablen del mismo reloj).
     */
    record FiltroEvidencia(UserId participanteId, EstadoValidacion estado, TipoDestino tipoDestino, Instant desde,
                            Instant hasta) {
    }
}
