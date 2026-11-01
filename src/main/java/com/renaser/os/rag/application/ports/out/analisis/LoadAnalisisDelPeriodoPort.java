package com.renaser.os.rag.application.ports.out.analisis;

import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;
import com.renaser.os.rag.domain.model.analisis.EstadoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Lectura de los análisis 80/20 ya decididos (spec 031). Cada lectura es una transacción corta propia: nunca rodea a la IA (C-1). */
public interface LoadAnalisisDelPeriodoPort {

    Optional<AnalisisDelPeriodo> deLaPersona(UserId participante, TipoDePeriodo tipo, LocalDate inicio);

    /**
     * Cómo está cada periodo que ya tiene fila, para varias personas en UNA consulta (el barrido horario no pide una por persona).
     * Solo los que empiezan desde {@code inicioMinimo}.
     */
    List<EstadoDeUnPeriodo> estadosDe(Collection<UserId> participantes, LocalDate inicioMinimo);

    /** El más reciente que está listo, de esa formación: lo que SER lee y explica. */
    Optional<AnalisisDelPeriodo> ultimoListo(UserId participante, Formacion formacion, TipoDePeriodo tipo);

    /** Cuántos intentos de generación se hicieron desde {@code desde}: el tope diario global cuenta con esto, sin una tabla de contadores. */
    int intentosDesde(Instant desde);

    /** Lo mínimo para decidir si un periodo toca: sin texto ni cifras. */
    record EstadoDeUnPeriodo(UserId participante, TipoDePeriodo tipo, LocalDate inicio, EstadoDelAnalisis estado,
                             Instant ultimoIntentoEn) {
    }
}
