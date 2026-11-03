package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;

/** Lee el interruptor «Código Renaser en las fichas» (spec 034). Solo Administración, Alquimista y Soporte TI. */
public interface ConsultarVisibilidadDelCodigoRenaserUseCase {

    VisibilidadDelCodigoRenaser consultar(UserId actor);
}
