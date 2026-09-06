package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.RegistrosConEvidenciaFinder;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio propio y no un metodo mas de {@link EvidenciaService}, por dos razones:
 *
 * <ul>
 *   <li>{@code EvidenciaService} ya expone siete metodos publicos — el objetivo de la regla 01
 *       (CLAUDE.MD §5.4.8). Sumarle el octavo por comodidad es como se llega al techo de diez.</li>
 *   <li>Es la unica pieza del modulo cuyo consumidor esta AFUERA y que no escribe nada. Tenerla
 *       aparte deja a la vista que la superficie publica de {@code evidence} hacia otros modulos
 *       son dos cosas chicas y sin estado compartido: registrar ({@code RegistrarEvidenciaPort}) y
 *       esta consulta. Mismo patron que {@code habits.AgendaDelDiaFinderService}.</li>
 * </ul>
 *
 * <p>Sin {@code @Transactional}: es una unica lectura, y la transaccion corta que Spring Data
 * aplica por metodo de repositorio ya alcanza (regla 01 / C-1 — nada de transacciones mas anchas
 * de lo necesario).
 */
@Service
class RegistrosConEvidenciaFinderService implements RegistrosConEvidenciaFinder {

    private final LoadEvidenciaPort loadEvidenciaPort;

    RegistrosConEvidenciaFinderService(LoadEvidenciaPort loadEvidenciaPort) {
        this.loadEvidenciaPort = loadEvidenciaPort;
    }

    @Override
    public Set<UUID> deEntre(Collection<UUID> registrosHabitoIds) {
        return loadEvidenciaPort.registrosHabitoConEvidencia(registrosHabitoIds);
    }
}
