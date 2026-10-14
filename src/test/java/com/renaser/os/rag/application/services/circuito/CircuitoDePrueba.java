package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.application.ports.in.circuito.CircuitoDeLaIa;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import com.renaser.os.shared.application.InterruptoresDePrueba;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link CircuitoDeLaIa} para pruebas sin Redis: corta las partes que la prueba nombre y anota lo que le cuentan
 * («falla ia.chat», «exito ia.voz»), en orden.
 */
public final class CircuitoDePrueba implements CircuitoDeLaIa {

    private final Set<ParteDeLaIa> cortadas = EnumSet.noneOf(ParteDeLaIa.class);
    public final List<String> anotado = new CopyOnWriteArrayList<>();

    public static CircuitoDePrueba cerrado() {
        return new CircuitoDePrueba();
    }

    public static CircuitoDePrueba cortando(ParteDeLaIa... partes) {
        CircuitoDePrueba circuito = new CircuitoDePrueba();
        circuito.cortadas.addAll(List.of(partes));
        return circuito;
    }

    /** {@link InterruptoresDeSer} con todo prendido a mano y este circuito. */
    public InterruptoresDeSer interruptores() {
        return new InterruptoresDeSer(InterruptoresDePrueba.conApagadas(), this);
    }

    /** {@link InterruptoresDeSer} con esas claves apagadas a mano y este circuito. */
    public InterruptoresDeSer interruptoresConApagadas(String... claves) {
        return new InterruptoresDeSer(InterruptoresDePrueba.conApagadas(claves), this);
    }

    @Override
    public void exigirPaso(ParteDeLaIa parte) {
        if (cortadas.contains(parte)) {
            throw new FuncionalidadApagadaException(parte.clave(), EstadoAutomaticoDeLaIa.AVISO);
        }
    }

    @Override
    public boolean dejaPasar(ParteDeLaIa parte) {
        return !cortadas.contains(parte);
    }

    @Override
    public void anotarFalla(ParteDeLaIa parte) {
        anotado.add("falla " + parte.clave());
    }

    @Override
    public void anotarExito(ParteDeLaIa parte) {
        anotado.add("exito " + parte.clave());
    }

    @Override
    public boolean cortado(ParteDeLaIa parte) {
        return cortadas.contains(parte);
    }
}
