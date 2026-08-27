package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.shared.domain.UserId;

/**
 * Renombrar el chat GLOBAL (#28, ficha del grupo). Restringido a ADMIN/ALCHEMIST —
 * confirmado contra la app real (`GlobalChatInfoSheet.tsx`: "'admin' ya representa
 * ADMIN + ALCHEMIST juntos... es el mismo criterio que usa el backend (isAdminRole)
 * para aceptar el PATCH"), no una suposicion propia.
 */
public interface RenombrarConversacionGlobalUseCase {

    Conversacion renombrar(RenombrarConversacionGlobalCommand command);

    record RenombrarConversacionGlobalCommand(UserId actorId, String nuevoNombre) {
    }
}
