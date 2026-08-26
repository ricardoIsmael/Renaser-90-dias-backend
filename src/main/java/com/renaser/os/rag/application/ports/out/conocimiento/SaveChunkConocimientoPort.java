package com.renaser.os.rag.application.ports.out.conocimiento;

import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;

/** Persiste un chunk ya indexado (con su embedding ya calculado) — INSERT nativo, ver {@link VectorStorePort}. */
public interface SaveChunkConocimientoPort {

    ChunkConocimiento save(ChunkConocimiento chunk);
}
