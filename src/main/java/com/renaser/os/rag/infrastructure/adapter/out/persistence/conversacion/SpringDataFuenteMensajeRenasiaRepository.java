package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataFuenteMensajeRenasiaRepository
        extends JpaRepository<FuenteMensajeRenasiaJpaEntity, FuenteMensajeRenasiaId> {

    /** EN LOTE para una pagina de mensajes (nunca N+1 — CLAUDE.MD del encargo). */
    List<FuenteMensajeRenasiaJpaEntity> findByMensajeIdIn(List<UUID> mensajeIds);
}
