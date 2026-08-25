package com.renaser.os.calendar.application.ports.in.recordatorio;

import java.time.Instant;

public interface DespacharRecordatoriosUseCase {

    /** despachar() del repo viejo, adaptado: en vez de mandar push directo, PUBLICA
     * {@code RecordatorioEventoDebidoEvent} por cada aviso vencido — `notifications`
     * decide el canal (fuera de este modulo). Devuelve cuantos se despacharon. */
    int despachar(Instant ahora);
}
