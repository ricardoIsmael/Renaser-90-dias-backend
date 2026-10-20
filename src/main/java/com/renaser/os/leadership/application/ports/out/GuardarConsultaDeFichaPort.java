package com.renaser.os.leadership.application.ports.out;

import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;

/** Inserta una consulta. Solo INSERT: {@code consultas_de_ficha} es append-only y la base lo hace cumplir (V109). */
public interface GuardarConsultaDeFichaPort {

    ConsultaDeFicha insertar(ConsultaDeFicha consulta);
}
