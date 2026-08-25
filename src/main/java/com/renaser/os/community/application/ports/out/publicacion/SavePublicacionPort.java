package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.domain.model.publicacion.Publicacion;

public interface SavePublicacionPort {

    Publicacion save(Publicacion publicacion);
}
