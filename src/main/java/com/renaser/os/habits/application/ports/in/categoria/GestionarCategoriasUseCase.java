package com.renaser.os.habits.application.ports.in.categoria;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Configurar las categorias de habito de una formacion (spec 023, historia 2): crear, renombrar, cambiar el icono, ordenar, activar o
 * desactivar y eliminar. Solo ADMIN/ALCHEMIST. Una categoria con habitos activos no se desactiva ni se elimina; con habitos de cualquier
 * estado no se elimina (se desactiva).
 */
public interface GestionarCategoriasUseCase {

    CategoriaDelCatalogo crear(CrearCategoriaCommand command);

    CategoriaDelCatalogo actualizar(ActualizarCategoriaCommand command);

    CategoriaDelCatalogo cambiarActiva(CambiarActivaCommand command);

    /** Deja las categorias de la formacion en el orden dado (todas las claves, una sola vez cada una). */
    List<CategoriaDelCatalogo> ordenar(OrdenarCategoriasCommand command);

    void eliminar(EliminarCategoriaCommand command);

    record CrearCategoriaCommand(@NotNull UserId actorId, @NotNull Formacion formacion, @NotNull String nombre, String icono) {
        public CrearCategoriaCommand {
            SelfValidating.validateConstructorArgs(CrearCategoriaCommand.class, actorId, formacion, nombre, icono);
        }
    }

    /** {@code nombre} e {@code icono} nulos = no se tocan; un icono vacio lo quita. */
    record ActualizarCategoriaCommand(@NotNull UserId actorId, @NotNull String clave, String nombre, String icono,
                                      boolean cambiaElIcono) {
        public ActualizarCategoriaCommand {
            SelfValidating.validateConstructorArgs(ActualizarCategoriaCommand.class, actorId, clave, nombre, icono, cambiaElIcono);
        }
    }

    record CambiarActivaCommand(@NotNull UserId actorId, @NotNull String clave, boolean activa) {
        public CambiarActivaCommand {
            SelfValidating.validateConstructorArgs(CambiarActivaCommand.class, actorId, clave, activa);
        }
    }

    record OrdenarCategoriasCommand(@NotNull UserId actorId, @NotNull Formacion formacion, @NotNull List<String> claves) {
        public OrdenarCategoriasCommand {
            SelfValidating.validateConstructorArgs(OrdenarCategoriasCommand.class, actorId, formacion, claves);
        }
    }

    record EliminarCategoriaCommand(@NotNull UserId actorId, @NotNull String clave) {
        public EliminarCategoriaCommand {
            SelfValidating.validateConstructorArgs(EliminarCategoriaCommand.class, actorId, clave);
        }
    }
}
