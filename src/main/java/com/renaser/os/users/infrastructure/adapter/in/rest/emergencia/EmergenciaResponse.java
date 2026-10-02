package com.renaser.os.users.infrastructure.adapter.in.rest.emergencia;

import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;

/** Un pedido de emergencia tal como lo ve quien lo pidió. Mapeado a mano: nada del dominio se filtra solo. */
public record EmergenciaResponse(String id, String queOcurrio, Integer diaPedido, int diaAlPedir, String estado,
                                 String creadaEn, String resueltaEn, Integer diaAplicado) {

    static EmergenciaResponse from(SolicitudDeEmergencia s) {
        return new EmergenciaResponse(s.id().toString(), s.queOcurrio(), s.diaPedido(), s.diaAlPedir(),
                s.estado().name(), s.creadaEn().toString(), s.resueltaEn() == null ? null : s.resueltaEn().toString(),
                s.diaAplicado());
    }
}
