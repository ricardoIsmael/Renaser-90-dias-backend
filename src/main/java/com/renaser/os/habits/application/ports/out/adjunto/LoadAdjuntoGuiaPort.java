package com.renaser.os.habits.application.ports.out.adjunto;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadAdjuntoGuiaPort {

    /** UNA sola consulta para N guias — para anidar adjuntos en el GET de guias (panel admin), nunca N+1. */
    List<AdjuntoGuia> porGuias(Collection<GuiaHabitoId> guiaIds);

    Optional<AdjuntoGuia> byId(AdjuntoGuiaId id);
}
