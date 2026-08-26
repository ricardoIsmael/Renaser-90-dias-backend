package com.renaser.os.habits.application.ports.in.diario;

import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/** {@code GET /api/v1/journal/today} (repo viejo, R-05). */
public interface ConsultarBitacoraNocturnaUseCase {

    EstadoBitacoraHoy consultarHoy(UserId actorId);

    /**
     * {@code fecha} es "hoy" en la zona horaria DEL PARTICIPANTE, no la del servidor — resuelta
     * una sola vez aca para que el caller (el controller) no tenga que recalcularla con
     * {@code LocalDate.now()} del huso horario equivocado cuando {@code entrada} no existe
     * todavia. {@code entrada} nullable y no {@code Optional}: un {@code Optional} como campo
     * de un record es exactamente lo que §5.4.7 prohibe (es un tipo de retorno, no un modelo
     * de datos) — la ausencia se consulta con {@link #existe()}.
     */
    record EstadoBitacoraHoy(LocalDate fecha, EntradaDiario entrada) {

        public boolean existe() {
            return entrada != null;
        }
    }
}
