package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.MedicionDelTrack;
import com.renaser.os.habits.application.ports.out.medicion.MetaDeMedicionPort;
import com.renaser.os.habits.application.ports.out.medicion.SumarMedicionesPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.RegistroPoliticasHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MetaDeMedicionPort metaPort;

    @Autowired
    public MedicionesDelDia(List<PoliticaHabito> politicas, SumarMedicionesPort sumarMedicionesPort,
                            MetaDeMedicionPort metaPort) {
        this.politicas = new RegistroPoliticasHabito(politicas);
        this.sumarMedicionesPort = sumarMedicionesPort;
        this.metaPort = metaPort;
    }

    /** Sin metas fijadas por Administración (rige la de la política): la forma anterior a la spec 037. */
    public MedicionesDelDia(List<PoliticaHabito> politicas, SumarMedicionesPort sumarMedicionesPort) {
        this(politicas, sumarMedicionesPort, new SinMetasFijadas());
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
            unidadDe(habito).ifPresent(unidad -> resultado.put(registro, medicionDe(unidad, registro, habito,
                    totalDe(participante, habito, hasta))));
        }
        return resultado;
    }

    private MedicionDelTrack medicionDe(UnidadMedicion unidad, RegistroHabito registro, Habito habito, BigDecimal total) {
        MedicionDiaria dia = registro.medicion();
        return new MedicionDelTrack(unidad, dia != null ? dia.valor() : null, total, metaDe(habito).orElse(null),
                dia != null ? dia.origen() : null, dia != null && dia.lectura() != null ? dia.lectura().fuente() : null);
    }

    /** La que fijó Administración o, sin ella, la de la política del hábito. */
    private Optional<BigDecimal> metaDe(Habito habito) {
        return metaPort.metaDe(habito.id()).or(() -> politicas.para(habito).metaPorDefectoDeLaMedicion());
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

    /** El puerto sin ninguna meta fijada: solo para el constructor corto. */
    private static final class SinMetasFijadas implements MetaDeMedicionPort {

        @Override
        public Optional<BigDecimal> metaDe(HabitoId habitoId) {
            return Optional.empty();
        }

        @Override
        public void fijar(HabitoId habitoId, BigDecimal meta, java.time.Instant ahora) {
            throw new UnsupportedOperationException("Sin metas fijadas");
        }
    }
}
