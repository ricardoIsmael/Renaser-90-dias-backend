package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.domain.model.publicacion.Comentario;

public interface SaveComentarioPort {

    Comentario save(Comentario comentario);
}
