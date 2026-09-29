package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.MedicionDelTrack;
import com.renaser.os.habits.application.ports.out.medicion.SumarMedicionesPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.RegistroPoliticasHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * D-226 — la medición de cada hábito medible de un día, para la agenda ({@code GET /habit-tracks/today}):
 * su unidad, lo registrado ese día y el total acumulado del programa. La app lo usa para pedir «¿Cuántos
 * km recorriste hoy?» y mostrar el total sin otra llamada.
 *
 * <p>Qué hábito mide y en qué unidad lo dice su política ({@link PoliticaHabito#unidadDeMedicion}), la
 * misma que valida el número al completar: un solo lugar. Solo se consulta la base si el día tiene
 * algún hábito medible, y es una consulta por hábito medible (hoy, uno).
 */
@Component
public class MedicionesDelDia {

    private final RegistroPoliticasHabito politicas;
    private final SumarMedicionesPort sumarMedicionesPort;

    public MedicionesDelDia(List<PoliticaHabito> politicas, SumarMedicionesPort sumarMedicionesPort) {
        this.politicas = new RegistroPoliticasHabito(politicas);
        this.sumarMedicionesPort = sumarMedicionesPort;
    }

    /**
     * @param habitos los hábitos del día ya cargados por la proyección (sin consulta extra)
     * @return por registro de un hábito medible, su medición; los demás registros no aparecen
     */
    public Map<RegistroHabito, MedicionDelTrack> de(UserId participante, List<RegistroHabito> registros,
                                                   Map<HabitoId, Habito> habitos, LocalDate hasta) {
        Map<RegistroHabito, MedicionDelTrack> resultado = new HashMap<>();
        for (RegistroHabito registro : registros) {
            Habito habito = habitos.get(registro.habitoId());
            unidadDe(habito).ifPresent(unidad -> resultado.put(registro, new MedicionDelTrack(unidad,
                    registro.medicion() != null ? registro.medicion().valor() : null,
                    totalDe(participante, habito, hasta))));
        }
        return resultado;
    }

    /** Solo un hábito del catálogo mide: uno personal no tiene clave, así que no tiene política propia. */
    private Optional<UnidadMedicion> unidadDe(Habito habito) {
        if (habito == null || habito.claveSistema() == null) {
            return Optional.empty();
        }
        return politicas.para(habito).unidadDeMedicion();
    }

    private BigDecimal totalDe(UserId participante, Habito habito, LocalDate hasta) {
        Collection<UserId> uno = List.of(participante);
        return sumarMedicionesPort.sumaPorParticipante(uno, habito.claveSistema(), hasta)
                .getOrDefault(participante, BigDecimal.ZERO.setScale(2));
    }
}
