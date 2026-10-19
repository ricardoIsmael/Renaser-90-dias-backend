package com.renaser.os.points.infrastructure.adapter.out.azar;

import com.renaser.os.points.application.ports.out.semaforo.mensaje.AzarPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** El azar del mensaje del día con {@link SecureRandom}: uniforme, y nadie puede adivinar cuál le toca mañana. */
@Component
class AzarSeguroAdapter implements AzarPort {

    private final SecureRandom azar = new SecureRandom();

    @Override
    public int indice(int n) {
        return azar.nextInt(n);
    }
}
