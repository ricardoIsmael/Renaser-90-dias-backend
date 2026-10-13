package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.shared.domain.UserId;

import java.time.Duration;

/** Cuenta las contraseñas mal escritas en la consola (plan.md D6: cinco en 15 min la bloquean 15 min). */
public interface LimitarDesbloqueosPort {

    boolean bloqueado(UserId actor, int maximo);

    /** Suma un fallo; al llegar a {@code maximo}, el bloqueo dura {@code ventana} desde ese fallo. */
    void registrarFallo(UserId actor, Duration ventana, int maximo);
}
