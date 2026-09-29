package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.out.medicion.SumarMedicionesPort;
import com.renaser.os.points.api.MedicionAcumuladaFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * D-226 — implementa {@link MedicionAcumuladaFinder} para el ranking de km. La suma sale de las
 * FECHAS (cada registro del día con su número), no de un contador que se incrementa: si una noche no
 * corre el corte del ranking, la siguiente da el total correcto igual (regla 02 §2).
 */
@Service
public class KilometrosAcumuladosService implements MedicionAcumuladaFinder {

    private final SumarMedicionesPort sumarMedicionesPort;

    public KilometrosAcumuladosService(SumarMedicionesPort sumarMedicionesPort) {
        this.sumarMedicionesPort = sumarMedicionesPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserId, BigDecimal> kilometrosAcumulados(Collection<UserId> participantes, LocalDate hasta) {
        return sumarMedicionesPort.sumaPorParticipante(participantes, PoliticaKilometros.CLAVE_SISTEMA, hasta);
    }
}
