package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

/**
 * Un aprendiz pasó de una formación a otra (spec 024, historia 9; spec 022): deja la comunidad general y el soporte de la anterior y entra a los de
 * la nueva. Lo que escribió se queda donde lo escribió, atribuido a él. Los directos y el chat del grupo no se tocan aquí: los directos se esconden
 * solos (ya no comparten formación) y el del grupo lo reconcilia {@code community} al cerrar la asignación.
 */
public interface CambiarDeComunidadAlTrasladarUseCase {

    void alTrasladar(UserId aprendiz, Formacion desde, Formacion hacia);
}
