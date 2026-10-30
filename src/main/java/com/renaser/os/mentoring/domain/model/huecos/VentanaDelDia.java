package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;

import java.util.List;
import java.util.Optional;

/**
 * El tramo del día que cuenta para los huecos: de la hora de Despertar a la de Dormir. Si falta alguna de las dos, o
 * Dormir no es posterior a Despertar (una persona que trabaja de noche), es el día entero de 00:00 a 24:00 (FR-007).
 */
public record VentanaDelDia(int desde, int hasta) {

    public VentanaDelDia {
        if (desde < 0 || hasta > TramoDelDia.FIN_DEL_DIA || desde >= hasta) {
            throw new IllegalArgumentException("Ventana invalida: " + desde + "-" + hasta);
        }
    }

    public static VentanaDelDia diaEntero() {
        return new VentanaDelDia(0, TramoDelDia.FIN_DEL_DIA);
    }

    /** La ventana de los hábitos que ese día ocupan tiempo: el día entero si no están los dos o no son coherentes. */
    public static VentanaDelDia de(List<HabitoConHora> habitos) {
        Optional<Integer> despertar = horaDe(habitos, HabitoConHora.DESPERTAR);
        Optional<Integer> dormir = horaDe(habitos, HabitoConHora.DORMIR);
        if (despertar.isPresent() && dormir.isPresent() && despertar.get() < dormir.get()) {
            return new VentanaDelDia(despertar.get(), dormir.get());
        }
        return diaEntero();
    }

    /**
     * Por qué el día cuenta entero; vacío si hay ventana (Despertar y Dormir coherentes). Es la misma regla de
     * {@link #de}: una sola fuente.
     */
    public static Optional<MotivoSinVentana> motivoDeDiaEntero(List<HabitoConHora> habitos) {
        Optional<Integer> despertar = horaDe(habitos, HabitoConHora.DESPERTAR);
        Optional<Integer> dormir = horaDe(habitos, HabitoConHora.DORMIR);
        if (despertar.isEmpty() && dormir.isEmpty()) {
            return Optional.of(MotivoSinVentana.SIN_DESPERTAR_NI_DORMIR);
        }
        if (despertar.isEmpty()) {
            return Optional.of(MotivoSinVentana.SIN_DESPERTAR);
        }
        if (dormir.isEmpty()) {
            return Optional.of(MotivoSinVentana.SIN_DORMIR);
        }
        return despertar.get() < dormir.get() ? Optional.empty() : Optional.of(MotivoSinVentana.DORMIR_ANTES_DE_DESPERTAR);
    }

    /** {@code false} cuando la ventana es el día entero: la app lo dice como «de 00:00 a 24:00». */
    public boolean vieneDeLaPersona() {
        return !equals(diaEntero());
    }

    public int minutos() {
        return hasta - desde;
    }

    private static Optional<Integer> horaDe(List<HabitoConHora> habitos, String clave) {
        return habitos.stream().filter(habito -> clave.equals(habito.claveSistema())).findFirst()
                .map(habito -> TramoDelDia.minutosDe(habito.horaDisparo()));
    }
}
