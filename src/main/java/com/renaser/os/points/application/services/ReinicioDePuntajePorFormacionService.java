package com.renaser.os.points.application.services;

import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.application.ports.in.puntaje.ReiniciarPuntajeAlCambiarDeFormacionUseCase;
import com.renaser.os.points.application.ports.out.ajuste.SaveAjustePort;
import com.renaser.os.points.application.ports.out.puntaje.LoadPuntajePort;
import com.renaser.os.points.application.ports.out.puntaje.SavePuntajePort;
import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.points.domain.model.ajuste.ResultadoAjuste;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reinicia el puntaje de quien pasa a otra formación (spec 022, Q8-A) SIN borrar nada: el saldo vuelve al inicial con un ajuste del ledger
 * ({@code MANUAL_ADJUSTMENT}, con la nota de dónde venía), la coherencia a 100 y la racha a 0. Quien nunca tuvo puntaje no escribe nada.
 */
@Service
class ReinicioDePuntajePorFormacionService implements ReiniciarPuntajeAlCambiarDeFormacionUseCase {

    private final LoadPuntajePort cargar;
    private final SavePuntajePort guardar;
    private final SaveAjustePort guardarAjuste;
    private final Clock clock;

    ReinicioDePuntajePorFormacionService(LoadPuntajePort cargar, SavePuntajePort guardar, SaveAjustePort guardarAjuste, Clock clock) {
        this.cargar = cargar;
        this.guardar = guardar;
        this.guardarAjuste = guardarAjuste;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void reiniciar(UserId aprendiz, Formacion desde, Formacion hacia) {
        cargar.byParticipanteIdParaEscritura(aprendiz).ifPresent(puntaje -> reiniciar(puntaje, desde, hacia));
    }

    private void reiniciar(PuntajeParticipante puntaje, Formacion desde, Formacion hacia) {
        int delta = puntaje.reiniciarParaOtraFormacion(clock);
        ResultadoAjuste resultado = puntaje.registrarAjuste(delta, clock);
        guardar.save(puntaje);
        if (delta != 0) {
            guardarAjuste.save(AjustePuntos.registrar(puntaje.participanteId(), MotivoPuntos.MANUAL_ADJUSTMENT, resultado,
                    "Reinicio por cambio de formación: " + desde.codigo() + " → " + hacia.codigo(), clock));
        }
    }
}
