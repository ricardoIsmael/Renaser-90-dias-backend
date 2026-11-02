package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.medicion.MetaDeMedicionAdminUseCases;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.medicion.MetaDeMedicionPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.RegistroPoliticasHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Spec 037 — la meta diaria de «Kilómetros diarios» en el panel de Administración (solo ADMIN y ALQUIMISTA, por
 * {@link HabitoAdminGuard}, además del permiso del endpoint). Qué hábito mide y cuál es su meta por defecto lo dice su
 * política: un solo lugar. Rige desde ya para los registros automáticos; la captura con número a mano no la mira.
 */
@Service
public class MetaDeMedicionAdminService implements MetaDeMedicionAdminUseCases {

    private final LoadHabitoPort loadHabitoPort;
    private final MetaDeMedicionPort metaPort;
    private final RegistroPoliticasHabito politicas;
    private final HabitoAdminGuard guard;
    private final Clock clock;

    public MetaDeMedicionAdminService(LoadHabitoPort loadHabitoPort, MetaDeMedicionPort metaPort,
                                      List<PoliticaHabito> politicas, HabitoAdminGuard guard, Clock clock) {
        this.loadHabitoPort = loadHabitoPort;
        this.metaPort = metaPort;
        this.politicas = new RegistroPoliticasHabito(politicas);
        this.guard = guard;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MetaDeMedicion consultar(UserId actorId, HabitoId habitoId) {
        guard.requireAdmin(actorId);
        return vistaDe(habitoMedible(habitoId));
    }

    @Override
    @Transactional
    public MetaDeMedicion fijar(FijarMetaCommand command) {
        guard.requireAdmin(command.actorId());
        Habito habito = habitoMedible(command.habitoId());
        BigDecimal meta = command.meta() == null ? null : command.meta().setScale(2, RoundingMode.HALF_UP);
        if (meta != null && !PoliticaKilometros.metaValida(meta)) {
            throw new IllegalArgumentException("La meta tiene que estar entre 0,5 y 50 km.");
        }
        metaPort.fijar(habito.id(), meta, clock.now());
        return vistaDe(habito);
    }

    private MetaDeMedicion vistaDe(Habito habito) {
        BigDecimal porDefecto = politicas.para(habito).metaPorDefectoDeLaMedicion().orElseThrow();
        Optional<BigDecimal> fijada = metaPort.metaDe(habito.id());
        return new MetaDeMedicion(UnidadMedicion.KILOMETROS, fijada.orElse(porDefecto), porDefecto,
                PoliticaKilometros.META_MINIMA_KM, PoliticaKilometros.META_MAXIMA_KM, fijada.isEmpty());
    }

    /** Solo un hábito del catálogo con meta: los demás, y uno que no existe, son el mismo 404. */
    private Habito habitoMedible(HabitoId id) {
        Habito habito = loadHabitoPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Hábito no encontrado: " + id));
        if (habito.claveSistema() == null || politicas.para(habito).metaPorDefectoDeLaMedicion().isEmpty()) {
            throw new NoSuchElementException("Este hábito no tiene meta: " + id);
        }
        return habito;
    }
}
