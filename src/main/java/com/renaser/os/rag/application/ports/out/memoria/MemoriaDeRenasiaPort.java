package com.renaser.os.rag.application.ports.out.memoria;

import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Donde vive lo que el acompanante recuerda de cada persona (D-167, V67).
 *
 * <p>Las tres escrituras se excluyen entre si por persona: lo que la persona borra desde su perfil
 * no puede volver porque una compactacion lo guardo al mismo tiempo.
 */
public interface MemoriaDeRenasiaPort {

    /** {@link MemoriaDeRenasia#vacia()} si todavia no se compacto nada de esa persona. */
    MemoriaDeRenasia de(UserId participanteId);

    /**
     * Reemplaza la memoria entera, en una sola transaccion, solo si sigue siendo {@code antes}.
     *
     * @return {@code false} si cambio mientras se compactaba (la persona borro algo): no se pisa, y la
     *         compactacion se reintenta en otro turno con lo que quedo
     */
    boolean reemplazar(UserId participanteId, MemoriaDeRenasia antes, MemoriaDeRenasia nueva);

    /**
     * Borra ese recuerdo y tambien el resumen, que podia nombrarlo.
     *
     * @return {@code false} si no existe o no es de esa persona: nadie borra lo ajeno
     */
    boolean olvidarRecuerdo(UserId participanteId, UUID recuerdoId);

    /** Borra recuerdos y resumen; lo conversado hasta {@code hasta} no vuelve a compactarse. */
    void olvidarTodo(UserId participanteId, Instant hasta);
}
