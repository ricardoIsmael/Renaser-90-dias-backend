package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitoadmin.CopiarHabitoAOtraFormacionUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.HabitoDelCatalogo;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.Accion;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.CambioDelCatalogo;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.Entidad;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.guia.SaveGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * «Copiar a otra formación» (spec 023, historia 3). Una sola transacción: el hábito, sus horarios, su guía (los textos; los archivos adjuntos no se
 * copian: son de S3 y compartirlos haría que borrar uno rompa el otro) y su audiencia por rol (spec 001). La copia no guarda ningún vínculo con el
 * original (FR-023). Conserva la {@code clave_sistema}, para que un hábito de flujo propio funcione igual en la formación nueva (FR-005): la clave es
 * única por formación (V134), así que si la destino ya tiene uno con esa clave se rechaza con un motivo claro.
 */
@Service
public class CopiarHabitoService implements CopiarHabitoAOtraFormacionUseCase {

    private final LoadHabitoPort loadPort;
    private final SaveHabitoPort savePort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final SaveHorarioHabitoPort saveHorarioPort;
    private final LoadGuiaHabitoPort loadGuiaPort;
    private final SaveGuiaHabitoPort saveGuiaPort;
    private final LoadCategoriaHabitoPort categoriasPort;
    private final RegistrarCambioDelCatalogoPort auditoria;
    private final AudienciasDelCatalogo audiencias;
    private final HabitoAdminGuard guard;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public CopiarHabitoService(LoadHabitoPort loadPort, SaveHabitoPort savePort, LoadHorarioHabitoPort loadHorarioPort,
                               SaveHorarioHabitoPort saveHorarioPort, LoadGuiaHabitoPort loadGuiaPort,
                               SaveGuiaHabitoPort saveGuiaPort, LoadCategoriaHabitoPort categoriasPort,
                               RegistrarCambioDelCatalogoPort auditoria, AudienciasDelCatalogo audiencias,
                               HabitoAdminGuard guard, Clock clock, IdGenerator idGenerator) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.loadHorarioPort = loadHorarioPort;
        this.saveHorarioPort = saveHorarioPort;
        this.loadGuiaPort = loadGuiaPort;
        this.saveGuiaPort = saveGuiaPort;
        this.categoriasPort = categoriasPort;
        this.auditoria = auditoria;
        this.audiencias = audiencias;
        this.guard = guard;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public HabitoDelCatalogo copiar(CopiarHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        Habito origen = loadPort.byId(command.habitoId()).filter(Habito::esDeSistema)
                .orElseThrow(() -> new NoSuchElementException("Hábito no encontrado: " + command.habitoId()));
        if (origen.esDeFormacion(command.destino())) {
            throw new IllegalArgumentException("Elige otra formación: el hábito ya es de esa.");
        }
        CategoriaHabito categoria = categoriaDestino(command);
        exigirSinChoques(origen, command);

        Instant ahora = clock.now();
        Habito copia = savePort.save(origen.copiarA(HabitoId.of(idGenerator.newId()), command.destino(), categoria.clave(), ahora));
        copiarHorarios(origen, copia, ahora);
        copiarGuias(origen, copia, ahora);
        HabitoDelCatalogo delOrigen = audiencias.presentar(origen);
        audiencias.guardarAlCrear(copia.id(), delOrigen.audienciaGuardada(), command.actorId());
        auditoria.registrar(new CambioDelCatalogo(command.destino(), Entidad.HABITO, copia.id().value().toString(), Accion.COPIADO,
                command.actorId(), Map.of("titulo", copia.titulo(), "desde", origen.formacion().codigo(),
                "original", origen.id().value().toString(), "categoria", categoria.clave()), ahora));
        return audiencias.presentar(copia);
    }

    private CategoriaHabito categoriaDestino(CopiarHabitoCommand command) {
        CategoriaHabito categoria = categoriasPort.byClave(command.categoriaDestino())
                .orElseThrow(() -> new IllegalArgumentException("Esa categoría no existe."));
        if (categoria.formacion() != command.destino()) {
            throw new IllegalArgumentException("Esa categoría no es de la formación elegida.");
        }
        if (!categoria.activa()) {
            throw new IllegalArgumentException("Esa categoría está desactivada. Actívala o elige otra.");
        }
        return categoria;
    }

    /** El título y la clave funcional son únicos dentro de la formación destino (V134): se avisa antes de llegar a la base. */
    private void exigirSinChoques(Habito origen, CopiarHabitoCommand command) {
        for (Habito existente : loadPort.catalogoCompleto()) {
            if (!existente.esDeFormacion(command.destino())) {
                continue;
            }
            if (CategoriaHabito.nombreNormalizado(existente.titulo()).equals(CategoriaHabito.nombreNormalizado(origen.titulo()))) {
                throw new IllegalStateException("Ya hay un hábito con ese título en la formación elegida.");
            }
            if (origen.claveSistema() != null && origen.claveSistema().equals(existente.claveSistema())) {
                throw new IllegalStateException("La formación elegida ya tiene un hábito con la misma función («"
                        + existente.titulo() + "»).");
            }
        }
    }

    private void copiarHorarios(Habito origen, Habito copia, Instant ahora) {
        loadHorarioPort.porHabito(origen.id()).forEach(horario -> saveHorarioPort.save(
                horario.copiarPara(HorarioHabitoId.of(idGenerator.newId()), copia.id(), ahora)));
    }

    private void copiarGuias(Habito origen, Habito copia, Instant ahora) {
        loadGuiaPort.porHabito(origen.id()).forEach(guia -> saveGuiaPort.save(
                guia.copiarPara(GuiaHabitoId.of(idGenerator.newId()), copia.id(), ahora)));
    }
}
