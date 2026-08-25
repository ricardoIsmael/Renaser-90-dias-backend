package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.categoria.ActualizarCategoriaMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.ConsultarCategoriasMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.CrearCategoriaMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.EliminarCategoriaMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.ReordenarCategoriasMuroUseCase;
import com.renaser.os.community.application.ports.out.categoria.EliminarCategoriaMuroPort;
import com.renaser.os.community.application.ports.out.categoria.LoadCategoriaMuroPort;
import com.renaser.os.community.application.ports.out.categoria.SaveCategoriaMuroPort;
import com.renaser.os.community.domain.model.categoria.CategoriaMuro;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class CategoriaMuroService implements CrearCategoriaMuroUseCase, ActualizarCategoriaMuroUseCase,
        EliminarCategoriaMuroUseCase, ReordenarCategoriasMuroUseCase, ConsultarCategoriasMuroUseCase {

    private final LoadCategoriaMuroPort loadPort;
    private final SaveCategoriaMuroPort savePort;
    private final EliminarCategoriaMuroPort eliminarPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    public CategoriaMuroService(LoadCategoriaMuroPort loadPort, SaveCategoriaMuroPort savePort,
                                 EliminarCategoriaMuroPort eliminarPort, UserSummaryFinder userSummaryFinder,
                                 Clock clock) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.eliminarPort = eliminarPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CategoriaFilaAdmin crear(CrearCategoriaMuroCommand command) {
        requireAdmin(command.actorId());
        if (loadPort.porClave(command.clave()).isPresent()) {
            throw new IllegalStateException("Ya existe una categoria con la clave \"" + command.clave() + "\"");
        }
        int siguienteOrden = loadPort.listarTodas().size() + 1;
        CategoriaMuro categoria = CategoriaMuro.crear(command.clave(), command.etiqueta(), command.emoji(),
                siguienteOrden, clock.now());
        return new CategoriaFilaAdmin(savePort.save(categoria), 0);
    }

    @Override
    @Transactional
    public CategoriaFilaAdmin actualizar(ActualizarCategoriaMuroCommand command) {
        requireAdmin(command.actorId());
        CategoriaMuro categoria = requireCategoria(command.clave());
        categoria.actualizar(command.etiqueta(), command.emoji(), command.activa(), clock.now());
        CategoriaMuro guardada = savePort.save(categoria);
        return new CategoriaFilaAdmin(guardada, loadPort.contarPublicaciones(guardada.clave()));
    }

    @Override
    @Transactional
    public void eliminar(EliminarCategoriaMuroCommand command) {
        requireAdmin(command.actorId());
        CategoriaMuro categoria = requireCategoria(command.clave());
        categoria.requireEliminable();
        int publicaciones = loadPort.contarPublicaciones(command.clave());
        if (publicaciones > 0) {
            throw new IllegalStateException("\"" + categoria.etiqueta() + "\" tiene " + publicaciones
                    + " publicacion(es). Retirala en vez de eliminarla.");
        }
        eliminarPort.eliminar(command.clave());
    }

    @Override
    @Transactional
    public void reordenar(ReordenarCategoriasMuroCommand command) {
        requireAdmin(command.actorId());
        Set<String> existentes = loadPort.listarClaves();
        List<String> desconocidas = command.claves().stream().filter(k -> !existentes.contains(k)).toList();
        if (!desconocidas.isEmpty()) {
            throw new IllegalArgumentException("Categorias desconocidas: " + String.join(", ", desconocidas));
        }
        savePort.reordenar(command.claves());
    }

    @Override
    public List<CategoriaMuro> listarPublicas() {
        return loadPort.listarActivas();
    }

    @Override
    public List<CategoriaFilaAdmin> listarParaPanel(UserId actorId) {
        requireAdmin(actorId);
        return loadPort.listarTodas().stream()
                .map(c -> new CategoriaFilaAdmin(c, loadPort.contarPublicaciones(c.clave())))
                .toList();
    }

    @Override
    public Set<String> clavesExistentes() {
        return loadPort.listarClaves();
    }

    private CategoriaMuro requireCategoria(String clave) {
        return loadPort.porClave(clave)
                .orElseThrow(() -> new NoSuchElementException("Categoria no encontrada: " + clave));
    }

    private void requireAdmin(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran categorias del Muro");
        }
    }
}
