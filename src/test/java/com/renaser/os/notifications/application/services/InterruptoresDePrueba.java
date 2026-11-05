package com.renaser.os.notifications.application.services;

import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.UserId;

import java.util.HashSet;
import java.util.Set;

/** Los interruptores de Soporte TI en memoria: todo prendido salvo lo que se apague. */
final class InterruptoresDePrueba implements Interruptores {

    final Set<String> apagadas = new HashSet<>();

    static InterruptoresDePrueba todoPrendido() {
        return new InterruptoresDePrueba();
    }

    InterruptoresDePrueba apagar(String clave) {
        apagadas.add(clave);
        return this;
    }

    @Override
    public boolean prendida(String clave) {
        return !apagadas.contains(clave);
    }

    @Override
    public boolean prendidaPara(String clave, UserId actor) {
        return prendida(clave);
    }

    @Override
    public void exigirPrendidaPara(String clave, UserId actor) {
        if (!prendida(clave)) {
            throw new FuncionalidadApagadaException(clave, null);
        }
    }
}
