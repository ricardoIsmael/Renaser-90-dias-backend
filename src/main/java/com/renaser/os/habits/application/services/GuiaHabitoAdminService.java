package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.guiaadmin.CrearAdjuntoGuiaEnlaceUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.ConfirmarAdjuntoGuiaArchivoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.ConsultarGuiasDeHabitoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarAdjuntoGuiaUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarGuiaHabitoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.GuiaConAdjuntos;
import com.renaser.os.habits.application.ports.in.guiaadmin.SolicitarUrlAdjuntoGuiaUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.UpsertGuiaHabitoUseCase;
import com.renaser.os.habits.application.ports.out.adjunto.LoadAdjuntoGuiaPort;
import com.renaser.os.habits.application.ports.out.adjunto.SaveAdjuntoGuiaPort;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.guia.SaveGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Panel admin de guias y sus adjuntos (hueco #11). */
@Service
public class GuiaHabitoAdminService implements ConsultarGuiasDeHabitoUseCase, UpsertGuiaHabitoUseCase,
        EliminarGuiaHabitoUseCase, CrearAdjuntoGuiaEnlaceUseCase, EliminarAdjuntoGuiaUseCase,
        SolicitarUrlAdjuntoGuiaUseCase, ConfirmarAdjuntoGuiaArchivoUseCase {

    /** Mismo bucket compartido que `rocks`/`habits`/`calendar`/`users` (D-34). */
    static final String BUCKET_ADJUNTOS_GUIA = "renaser-files";
    private static final String PREFIJO_RUTA_ADJUNTO = "guias-habito";
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);

    private final LoadHabitoPort loadHabitoPort;
    private final LoadGuiaHabitoPort loadGuiaPort;
    private final SaveGuiaHabitoPort saveGuiaPort;
    private final LoadAdjuntoGuiaPort loadAdjuntoPort;
    private final SaveAdjuntoGuiaPort saveAdjuntoPort;
    private final HabitoAdminGuard guard;
    private final AlmacenamientoPort almacenamientoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public GuiaHabitoAdminService(LoadHabitoPort loadHabitoPort, LoadGuiaHabitoPort loadGuiaPort,
                                   SaveGuiaHabitoPort saveGuiaPort, LoadAdjuntoGuiaPort loadAdjuntoPort,
                                   SaveAdjuntoGuiaPort saveAdjuntoPort, HabitoAdminGuard guard,
                                   AlmacenamientoPort almacenamientoPort, Clock clock, IdGenerator idGenerator) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadGuiaPort = loadGuiaPort;
        this.saveGuiaPort = saveGuiaPort;
        this.loadAdjuntoPort = loadAdjuntoPort;
        this.saveAdjuntoPort = saveAdjuntoPort;
        this.guard = guard;
        this.almacenamientoPort = almacenamientoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
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

        // Upsert: solo el camino de alta pide identidad nueva, la guia existente conserva la suya.
        GuiaHabito guia = porHabitoYDia(command.habitoId(), command.diaInicio())
                .orElseGet(() -> GuiaHabito.crear(GuiaHabitoId.of(idGenerator.newId()), command.habitoId(),
                        command.diaInicio(), ahora));
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
        GuiaHabito guia = guiaParaAdjunto(command.habitoId(), command.diaInicio(), ahora);
        AdjuntoGuia adjunto = AdjuntoGuia.deEnlace(AdjuntoGuiaId.of(idGenerator.newId()), guia.id(),
                command.seccion(), command.url(), command.titulo(), siguienteOrden(guia.id()), ahora);
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

    @Override
    public UrlAdjuntoGuia solicitarUrl(SolicitarUrlAdjuntoGuiaCommand command) {
        guard.requireAdmin(command.actorId());
        requireHabito(command.habitoId());
        String ruta = PREFIJO_RUTA_ADJUNTO + "/" + command.habitoId() + "/" + UUID.randomUUID();
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlAdjuntoGuia(url, BUCKET_ADJUNTOS_GUIA, ruta);
    }

    @Override
    @Transactional
    public AdjuntoGuia confirmar(ConfirmarAdjuntoGuiaArchivoCommand command) {
        guard.requireAdmin(command.actorId());
        requireHabito(command.habitoId());
        Instant ahora = clock.now();
        GuiaHabito guia = guiaParaAdjunto(command.habitoId(), command.diaInicio(), ahora);
        AdjuntoGuia adjunto = AdjuntoGuia.deArchivo(AdjuntoGuiaId.of(idGenerator.newId()), guia.id(),
                command.seccion(), command.tipoMedio(), command.ruta(), command.mime(), command.tamanoBytes(),
                command.nombreOriginal(), command.titulo(), siguienteOrden(guia.id()), ahora);
        return saveAdjuntoPort.save(adjunto);
    }

    /** Guia del tramo pedido, o una nueva con textos vacios si todavia no existe — misma
     * regla para adjuntos ENLACE y de archivo. */
    private GuiaHabito guiaParaAdjunto(HabitoId habitoId, int diaInicio, Instant ahora) {
        return porHabitoYDia(habitoId, diaInicio)
                .orElseGet(() -> saveGuiaPort.save(GuiaHabito.crear(GuiaHabitoId.of(idGenerator.newId()), habitoId,
                        diaInicio, ahora)));
    }

    private int siguienteOrden(GuiaHabitoId guiaId) {
        return loadAdjuntoPort.porGuias(List.of(guiaId)).size();
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
