package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.UserId;

import java.util.Map;
import java.util.Optional;

/**
 * Las funcionalidades de hábitos que dependen del hábito y no de la ruta (spec 002, catálogo): completar el de
 * kilómetros es {@code habitos.km} y el de gimnasio, {@code habitos.gimnasio}. El {@code @Funcionalidad("habitos")}
 * del endpoint ya cubre los hábitos enteros; esto cubre esas dos hijas.
 */
final class FuncionalidadesDeHabitos {

    private static final Map<String, String> POR_CLAVE_SISTEMA = Map.of(
            PoliticaKilometros.CLAVE_SISTEMA, "habitos.km",
            KilometrosAcumuladosService.CLAVE_GIMNASIO, "habitos.gimnasio");

    private FuncionalidadesDeHabitos() {
    }

    /** Lanza {@code FuncionalidadApagadaException} si la funcionalidad propia de ese hábito está apagada para quien. */
    static void exigirParaCompletar(Interruptores interruptores, Habito habito, UserId actor) {
        clavePropia(habito).ifPresent(clave -> interruptores.exigirPrendidaPara(clave, actor));
    }

    static Optional<String> clavePropia(Habito habito) {
        return Optional.ofNullable(habito.claveSistema()).map(POR_CLAVE_SISTEMA::get);
    }
}
