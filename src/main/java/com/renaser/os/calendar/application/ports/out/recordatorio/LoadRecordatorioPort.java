package com.renaser.os.calendar.application.ports.out.recordatorio;

import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;

import java.time.Instant;
import java.util.List;

public interface LoadRecordatorioPort {

    /**
     * Vencidos y sin enviar, con {@code FOR UPDATE SKIP LOCKED} sobre
     * {@code recordatorios_cola_idx} — seguro con multiples instancias corriendo el mismo
     * cron a la vez (mismo patron que la cola de evidencias de IA, ver comentario del
     * baseline junto al indice).
     *
     * <p><b>Sin verificar contra Postgres real todavia</b> — el test de integracion
     * (Testcontainers, otro agente) debe confirmar que dos instancias del scheduler
     * corriendo en paralelo nunca despachan la misma fila dos veces.
     */
    List<RecordatorioEvento> vencidosPendientes(Instant hasta, int limite);
}
