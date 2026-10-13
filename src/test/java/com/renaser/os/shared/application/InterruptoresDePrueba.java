package com.renaser.os.shared.application;

import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.UserId;

import java.util.HashSet;
import java.util.Set;

/** {@link Interruptores} para pruebas sin Spring: apaga (para todos) las claves que la prueba nombre. */
public final class InterruptoresDePrueba implements Interruptores {

    private final Set<String> apagadas = new HashSet<>();

    public static InterruptoresDePrueba conApagadas(String... claves) {
        InterruptoresDePrueba interruptores = new InterruptoresDePrueba();
        interruptores.apagadas.addAll(Set.of(claves));
        return interruptores;
    }

    @Override
    public boolean prendida(String clave) {
        return apagadas.stream().noneMatch(apagada -> clave.equals(apagada) || clave.startsWith(apagada + "."));
    }

    @Override
    public boolean prendidaPara(String clave, UserId actor) {
        return prendida(clave);
    }

    @Override
    public void exigirPrendidaPara(String clave, UserId actor) {
        if (!prendida(clave)) {
            throw new FuncionalidadApagadaException(clave, "Apagada en la prueba");
        }
    }
}
