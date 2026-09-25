package com.renaser.os.rag.infrastructure.adapter.in.rest.memoria;

import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase.MemoriaEnElPerfil;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;

import java.util.List;
import java.util.UUID;

/**
 * Lo que ve la persona en su perfil. Mapeo a mano (regla 04): un campo nuevo del dominio no se
 * filtra solo al cliente.
 *
 * @param activa  si la memoria esta encendida; apagada, la app oculta la seccion salvo que haya algo
 *                guardado de antes (se muestra para poder borrarlo)
 * @param resumen lo que venian conversando, o {@code null} si no hay (todavia no se compacto, o la
 *                persona borro algo)
 */
public record MemoriaRenasiaResponse(boolean activa, List<RecuerdoResponse> recuerdos, String resumen) {

    /** @param categoria el nombre estable, para agrupar; {@code titulo}, lo que se muestra */
    public record RecuerdoResponse(UUID id, String categoria, String titulo, String texto) {
    }

    static MemoriaRenasiaResponse from(MemoriaEnElPerfil enElPerfil) {
        MemoriaDeRenasia memoria = enElPerfil.memoria();
        return new MemoriaRenasiaResponse(enElPerfil.activa(), memoria.recuerdos().stream()
                .map(r -> new RecuerdoResponse(r.id(), r.categoria().name(), r.categoria().paraLaPersona(), r.texto()))
                .toList(), memoria.resumen().orElse(null));
    }
}
