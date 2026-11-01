package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.FormacionDeLaSesion;

/** El personal con varias formaciones elige en cuál trabaja (spec 022, FR-013). La elección la guarda el servidor y rige en su siguiente pedido. */
public interface ElegirFormacionActivaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException (403) si no es personal o no tiene asignada esa formación
     */
    FormacionDeLaSesion elegir(UserId persona, Formacion formacion);
}
