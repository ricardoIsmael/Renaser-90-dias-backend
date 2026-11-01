package com.renaser.os.onboarding.application.ports.in.diagnostico;

import com.renaser.os.onboarding.domain.model.estado.TipoDeFicha;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** Darle el diagnóstico (o devolverle la ficha estándar) a varias personas que ya existen, de una vez (spec 026, 026-9 B). */
public interface AsignarFichaUseCase {

    ResultadoDeAsignacion asignar(UserId actor, List<UserId> personas, TipoDeFicha ficha);

    record ResultadoDeAsignacion(int asignadas, List<Omitida> omitidas) {

        public ResultadoDeAsignacion {
            omitidas = List.copyOf(omitidas);
        }
    }

    record Omitida(UserId id, String nombre, String motivo) {
    }
}
