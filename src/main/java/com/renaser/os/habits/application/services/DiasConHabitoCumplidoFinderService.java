package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.application.ports.out.registro.ConsultarObligacionesHistoricasPort;
import com.renaser.os.points.api.DiasConHabitoCumplidoFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Implementa el contrato que {@code points} declara para poder derivar la racha.
 *
 * <p><b>La dirección de la dependencia es deliberada</b> y sigue el patrón ya establecido por
 * {@code HabitosDelDiaFinderService}: la interfaz vive en {@code points.api} y la implementación
 * acá, porque {@code habits} ya depende de {@code points} y el camino inverso sería un ciclo que
 * Spring Modulith rechaza.
 *
 * <p><b>Reutiliza la consulta que ya existe</b> ({@link ConsultarObligacionesHistoricasPort}),
 * apoyada en {@code registros_dia_idx (participante_id, fecha_ejecucion)}. No se escribió SQL
 * nuevo a propósito: para una persona y una ventana de 90 días son unos cientos de filas, y una
 * consulta más contra la misma tabla sería un índice más que mantener a cambio de nada medible. Si
 * algún día esto se pide para un padrón entero y no para una persona, ahí sí conviene un
 * {@code SELECT DISTINCT fecha_ejecucion} propio — y el contrato de {@code points} no cambia.
 *
 * <p>Sin {@code @Transactional}: es una sola lectura.
 */
@Service
class DiasConHabitoCumplidoFinderService implements DiasConHabitoCumplidoFinder {

    private final ConsultarObligacionesHistoricasPort consultarPort;

    DiasConHabitoCumplidoFinderService(ConsultarObligacionesHistoricasPort consultarPort) {
        this.consultarPort = consultarPort;
    }

    @Override
    public List<LocalDate> entre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango va al reves: " + desde + " → " + hasta);
        }
        return consultarPort.entre(Set.of(participanteId), desde, hasta).stream()
                .filter(obligacion -> obligacion.estado().cumplido())
                .map(ObligacionHabito::fecha)
                .distinct()   // acá vive el "por día, no por hábito" del contrato
                .sorted()
                .toList();
    }
}
