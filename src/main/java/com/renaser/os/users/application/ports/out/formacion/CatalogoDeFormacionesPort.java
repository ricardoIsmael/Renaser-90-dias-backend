package com.renaser.os.users.application.ports.out.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;

import java.util.List;

/** Lee y guarda el catálogo de las tres formaciones (tabla {@code formaciones}, V125). */
public interface CatalogoDeFormacionesPort {

    List<FormacionConfigurada> todas();

    FormacionConfigurada de(Formacion formacion);

    /**
     * Guarda los cambios SOLO si la versión sigue siendo la que la persona vio (dos administradores no se pisan).
     *
     * @return {@code true} si guardó; {@code false} si la versión cambió
     */
    boolean guardar(FormacionConfigurada nueva, int versionEsperada, UserId actor);
}
