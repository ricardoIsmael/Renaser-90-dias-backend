package com.renaser.os.habits.application.ports.out.adjunto;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;

public interface SaveAdjuntoGuiaPort {

    AdjuntoGuia save(AdjuntoGuia adjunto);

    void eliminar(AdjuntoGuiaId id);
}
