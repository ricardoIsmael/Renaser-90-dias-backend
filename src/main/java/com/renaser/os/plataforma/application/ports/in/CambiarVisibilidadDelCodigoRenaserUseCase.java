package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;

/**
 * Enciende o apaga «Código Renaser en las fichas» para el mentor, la guía y el líder juntos (spec 034). Sin motivo
 * escrito. Rige en el acto, queda auditado y no avisa a ninguna persona de las fichas. Pedir lo que ya rige no escribe nada.
 */
public interface CambiarVisibilidadDelCodigoRenaserUseCase {

    VisibilidadDelCodigoRenaser cambiar(UserId actor, boolean visible);
}
