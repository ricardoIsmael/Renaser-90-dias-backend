package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * El chat de soporte de un aprendiz EN LA FORMACIÓN EN QUE ESTÁ AHORA (spec 024, FR-028). Quien pasa de formación tiene un soporte por cada una
 * ({@link Conversacion#claveSoporteDe(UserId, Formacion)}): los avisos del programa (tarjeta del semáforo, Caja, emergencia) van siempre al de la
 * formación vigente, nunca al de la anterior.
 */
@Component
class SoporteVigenteDelAprendiz {

    private final LoadConversacionPort conversaciones;
    private final AlcanceDeFormacionFinder formaciones;

    SoporteVigenteDelAprendiz(LoadConversacionPort conversaciones, AlcanceDeFormacionFinder formaciones) {
        this.conversaciones = conversaciones;
        this.formaciones = formaciones;
    }

    /** Su soporte de hoy; vacío si todavía no lo tiene. Un aprendiz sin formación conocida es de la Escuela. */
    Optional<Conversacion> de(UserId aprendiz) {
        Formacion formacion = formaciones.formacionDe(aprendiz).orElse(Formacion.POR_DEFECTO);
        return conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(aprendiz, formacion));
    }
}
