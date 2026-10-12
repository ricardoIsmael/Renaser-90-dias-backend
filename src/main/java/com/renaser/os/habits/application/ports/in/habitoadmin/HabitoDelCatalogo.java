package com.renaser.os.habits.application.ports.in.habitoadmin;

import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia;
import com.renaser.os.habits.domain.model.habito.Habito;

import java.util.Optional;

/**
 * Un hábito del catálogo como lo ve el administrador: el hábito, la ÚLTIMA versión guardada de su audiencia (rija o no
 * todavía), el nombre de quien la guardó y si es un cambio que todavía no rige (spec 001, contrato
 * {@code admin-habits.md}). Sin versión, es para todos.
 */
public record HabitoDelCatalogo(Habito habito, Optional<VersionDeAudiencia> audiencia, String nombreDeQuienCambio,
                                boolean rigeDesdeManana) {

    public static HabitoDelCatalogo sinAudiencia(Habito habito) {
        return new HabitoDelCatalogo(habito, Optional.empty(), null, false);
    }

    public Audiencia audienciaGuardada() {
        return audiencia.map(VersionDeAudiencia::audiencia).orElse(Audiencia.PARA_TODOS);
    }
}
