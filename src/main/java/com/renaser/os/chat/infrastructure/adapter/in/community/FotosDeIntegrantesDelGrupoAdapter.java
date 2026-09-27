package com.renaser.os.chat.infrastructure.adapter.in.community;

import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase.TarjetasDelGrupo;
import com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion.FotosDelChatController;
import com.renaser.os.community.api.FotosDeIntegrantesDelGrupo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Le contesta a {@code community} dónde está la tarjeta con nombre de cada integrante de un grupo
 * (D-206): en el chat del grupo, {@code /api/v1/chat/conversations/{chat}/miembros/{usuario}/foto}.
 *
 * <p>Es un adaptador de ENTRADA: otro módulo le pregunta a chat. Solo arma las rutas de los que el caso de
 * uso dice que llevan tarjeta (según el modo del servidor); quién puede ver cada foto lo decide el
 * endpoint cuando se la pide, con la regla del chat. Un grupo sin chat no tiene dónde servirlas y vuelve
 * vacío (la app muestra la foto subida o las iniciales).
 */
@Component
class FotosDeIntegrantesDelGrupoAdapter implements FotosDeIntegrantesDelGrupo {

    private final VerFotosDelChatUseCase fotos;

    FotosDeIntegrantesDelGrupoAdapter(VerFotosDelChatUseCase fotos) {
        this.fotos = fotos;
    }

    @Override
    public Map<UserId, String> rutasDeLasFotos(UUID grupoId, Collection<UserId> integrantes) {
        if (integrantes.isEmpty()) {
            return Map.of();
        }
        return fotos.tarjetasDelGrupo(grupoId, integrantes)
                .map(FotosDeIntegrantesDelGrupoAdapter::rutas)
                .orElse(Map.of());
    }

    private static Map<UserId, String> rutas(TarjetasDelGrupo tarjetas) {
        Map<UserId, String> rutas = new LinkedHashMap<>();
        tarjetas.conTarjeta().forEach(id ->
                rutas.put(id, FotosDelChatController.rutaDeLaFotoDeIntegrante(tarjetas.chat(), id)));
        return rutas;
    }
}
