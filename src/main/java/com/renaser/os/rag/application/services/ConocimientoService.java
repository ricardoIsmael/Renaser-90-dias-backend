package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conocimiento.IndexarConocimientoUseCase;
import com.renaser.os.rag.application.ports.out.conocimiento.SaveChunkConocimientoPort;
import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimientoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/** Único caso de uso del agregado {@code conocimiento}: la ingesta administrada (D-46). */
@Service
public class ConocimientoService implements IndexarConocimientoUseCase {

    private final SaveChunkConocimientoPort saveChunkConocimientoPort;
    private final EmbeddingPort embeddingPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ConocimientoService(SaveChunkConocimientoPort saveChunkConocimientoPort, EmbeddingPort embeddingPort,
                                UserSummaryFinder userSummaryFinder, Clock clock, IdGenerator idGenerator) {
        this.saveChunkConocimientoPort = saveChunkConocimientoPort;
        this.embeddingPort = embeddingPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * <b>C-1 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):</b> este
     * método YA NO es {@code @Transactional}. Antes envolvía en una sola transacción la
     * llamada a {@link EmbeddingPort} (IA) y el guardado, reteniendo una conexión de Hikari
     * mientras esperaba a la IA. {@code saveChunkConocimientoPort.save} ya abre su propia
     * transacción corta ({@code PgVectorNativoAdapter.save}, {@code @Transactional} propio);
     * la llamada a la IA queda afuera de cualquier transacción.
     */
    @Override
    public ChunkIndexado indexar(IndexarConocimientoCommand command) {
        requireAdmin(command.actorId());
        var embedding = embeddingPort.generar(command.contenido());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        var chunk = ChunkConocimiento.indexar(ChunkConocimientoId.of(idGenerator.newId()), command.tipoFuente(),
                command.clase(), command.documentoId(), command.leccionId(), command.contenido(), embedding,
                command.metadatos(), clock);
        var guardado = saveChunkConocimientoPort.save(chunk);
        return new ChunkIndexado(guardado.id().value());
    }

    private void requireAdmin(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST indexan conocimiento");
        }
    }
}
