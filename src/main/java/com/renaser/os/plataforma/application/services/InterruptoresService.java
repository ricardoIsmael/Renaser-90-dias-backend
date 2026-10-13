package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.domain.model.estado.ResultadoDeEvaluacion;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * La implementación de {@link Interruptores} (contrato §1) que usan los procesos, los WebSockets y el interceptor
 * web. {@code @Primary} por si el respaldo {@code TodoPrendido} de {@code shared} llegara a registrarse igual.
 */
@Service
@Primary
class InterruptoresService implements Interruptores {

    private final EstadoVigente estado;
    private final RolDelActor rolDelActor;

    InterruptoresService(EstadoVigente estado, RolDelActor rolDelActor) {
        this.estado = estado;
        this.rolDelActor = rolDelActor;
    }

    @Override
    public boolean prendida(String clave) {
        return estado.evaluar(clave, null).prendida();
    }

    @Override
    public boolean prendidaPara(String clave, UserId actor) {
        return evaluarPara(clave, actor).prendida();
    }

    @Override
    public void exigirPrendidaPara(String clave, UserId actor) {
        ResultadoDeEvaluacion resultado = actor == null ? estado.evaluar(clave, null) : evaluarPara(clave, actor);
        if (!resultado.prendida()) {
            throw new FuncionalidadApagadaException(clave, resultado.aviso());
        }
    }

    private ResultadoDeEvaluacion evaluarPara(String clave, UserId actor) {
        return estado.evaluar(clave, rolDelActor.de(actor));
    }
}
