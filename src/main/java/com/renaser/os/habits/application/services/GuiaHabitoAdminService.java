package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.guiaadmin.CrearAdjuntoGuiaEnlaceUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.ConsultarGuiasDeHabitoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarAdjuntoGuiaUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarGuiaHabitoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.GuiaConAdjuntos;
import com.renaser.os.habits.application.ports.in.guiaadmin.UpsertGuiaHabitoUseCase;
import com.renaser.os.habits.application.ports.out.adjunto.LoadAdjuntoGuiaPort;
import com.renaser.os.habits.application.ports.out.adjunto.SaveAdjuntoGuiaPort;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.guia.SaveGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/** Panel admin de guias y sus adjuntos (hueco #11). */
@Service
public class GuiaHabitoAdminService implements ConsultarGuiasDeHabitoUseCase, UpsertGuiaHabitoUseCase,
        EliminarGuiaHabitoUseCase, CrearAdjuntoGuiaEnlaceUseCase, EliminarAdjuntoGuiaUseCase {

    private final LoadHabitoPort loadHabitoPort;
    private final LoadGuiaHabitoPort loadGuiaPort;
    private final SaveGuiaHabitoPort saveGuiaPort;
    private final LoadAdjuntoGuiaPort loadAdjuntoPort;
    private final SaveAdjuntoGuiaPort saveAdjuntoPort;
    private final HabitoAdminGuard guard;
    private final Clock clock;

    public GuiaHabitoAdminService(LoadHabitoPort loadHabitoPort, LoadGuiaHabitoPort loadGuiaPort,
                                   SaveGuiaHabitoPort saveGuiaPort, LoadAdjuntoGuiaPort loadAdjuntoPort,
                                   SaveAdjuntoGuiaPort saveAdjuntoPort, HabitoAdminGuard guard, Clock clock) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadGuiaPort = loadGuiaPort;
        this.saveGuiaPort = saveGuiaPort;
        this.loadAdjuntoPort = loadAdjuntoPort;
        this.saveAdjuntoPort = saveAdjuntoPort;
        this.guard = guard;
        this.clock = clock;
    }

    @Override
    public List<GuiaConAdjuntos> listar(UserId actorId, HabitoId habitoId) {
        guard.requireAdmin(actorId);
        requireHabito(habitoId);
        List<GuiaHabito> guias = loadGuiaPort.porHabito(habitoId).stream()
                .sorted(Comparator.comparingInt(GuiaHabito::diaInicio)).toList();
        List<GuiaHabitoId> guiaIds = guias.stream().map(GuiaHabito::id).toList();
        Map<GuiaHabitoId, List<AdjuntoGuia>> adjuntosPorGuia = loadAdjuntoPort.porGuias(guiaIds).stream()
                .collect(Collectors.groupingBy(AdjuntoGuia::guiaId));
        return guias.stream()
                .map(g -> new GuiaConAdjuntos(g, adjuntosPorGuia.getOrDefault(g.id(), List.of()).stream()
                        .sorted(Comparator.comparingInt(AdjuntoGuia::orden)).toList()))
                .toList();
    }

    @Override
    @Transactional
    public GuiaConAdjuntos upsert(UpsertGuiaHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        requireHabito(command.habitoId());
        Instant ahora = clock.now();

        if (command.closePrevious()) {
            cerrarGuiaAnteriorAbierta(command.habitoId(), command.diaInicio(), ahora);
        }

        GuiaHabito guia = porHabitoYDia(command.habitoId(), command.diaInicio())
                .orElseGet(() -> GuiaHabito.crear(command.habitoId(), command.diaInicio(), ahora));
        guia.actualizarContenidoCompleto(command.contenido(), ahora);
        guia.establecerDiaFin(command.diaFin(), ahora);
        GuiaHabito guardada = saveGuiaPort.save(guia);
        List<AdjuntoGuia> adjuntos = loadAdjuntoPort.porGuias(List.of(guardada.id())).stream()
                .sorted(Comparator.comparingInt(AdjuntoGuia::orden)).toList();
        return new GuiaConAdjuntos(guardada, adjuntos);
    }

    @Override
    @Transactional
    public void eliminar(EliminarGuiaHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        requireGuia(command.guiaId());
        saveGuiaPort.eliminar(command.guiaId());
    }

    @Override
    @Transactional
    public AdjuntoGuia crear(CrearAdjuntoGuiaEnlaceCommand command) {
        guard.requireAdmin(command.actorId());
        requireHabito(command.habitoId());
        Instant ahora = clock.now();
        GuiaHabito guia = porHabitoYDia(command.habitoId(), command.diaInicio())
                .orElseGet(() -> saveGuiaPort.save(GuiaHabito.crear(command.habitoId(), command.diaInicio(), ahora)));
        int siguienteOrden = loadAdjuntoPort.porGuias(List.of(guia.id())).size();
        AdjuntoGuia adjunto = AdjuntoGuia.deEnlace(guia.id(), command.seccion(), command.url(), command.titulo(),
                siguienteOrden, ahora);
        return saveAdjuntoPort.save(adjunto);
    }

    @Override
    @Transactional
    public void eliminar(EliminarAdjuntoGuiaCommand command) {
        guard.requireAdmin(command.actorId());
        loadAdjuntoPort.byId(command.adjuntoId())
                .orElseThrow(() -> new NoSuchElementException("Adjunto no encontrado: " + command.adjuntoId()));
        saveAdjuntoPort.eliminar(command.adjuntoId());
    }

    /** Cierra, en dia-1, la guia abierta mas reciente que sea estrictamente anterior a la que se esta dando de alta. */
    private void cerrarGuiaAnteriorAbierta(HabitoId habitoId, int diaInicioNuevo, Instant ahora) {
        loadGuiaPort.masRecienteAbierta(habitoId)
                .filter(previa -> previa.diaInicio() < diaInicioNuevo)
                .ifPresent(previa -> {
                    previa.cerrarEn(diaInicioNuevo - 1, ahora);
                    saveGuiaPort.save(previa);
                });
    }

    private Optional<GuiaHabito> porHabitoYDia(HabitoId habitoId, int diaInicio) {
        return loadGuiaPort.porHabito(habitoId).stream().filter(g -> g.diaInicio() == diaInicio).findFirst();
    }

    private void requireHabito(HabitoId id) {
        loadHabitoPort.byId(id).orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + id));
    }

    private void requireGuia(GuiaHabitoId id) {
        loadGuiaPort.byId(id).orElseThrow(() -> new NoSuchElementException("Guia no encontrada: " + id));
    }
}
