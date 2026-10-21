package com.renaser.os.leadership.application.ports.out;

import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Lee {@code consultas_de_ficha} (V109). Solo lee: la tabla es append-only. */
public interface LeerConsultasDeFichaPort {

    /**
     * Las consultas que hizo el LÍDER DE MENTORES (no Administración ni Alquimista) en {@code [desde, hasta)} sobre
     * personas de estos mentores, en el orden en que se hicieron. Una sola lectura para todo el lote.
     */
    List<ConsultaDeFicha> delLiderEntre(Collection<UserId> mentores, Instant desde, Instant hasta);
}
