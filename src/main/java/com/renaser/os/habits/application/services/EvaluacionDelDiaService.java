package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort;
import com.renaser.os.habits.application.ports.out.diario.LoadEntradaDiarioPort;
import com.renaser.os.habits.application.ports.out.diario.SaveEntradaDiarioPort;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.EntradaDiarioId;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Guarda y lee la marca de «día cerrado» (spec 017) en {@code entradas_diario}, tipo {@code EVALUACION_DIA}. Una fila por persona y
 * fecha ({@code UNIQUE} de {@code V1}): cerrar otra vez reemplaza la nota y conserva {@code creado_en}, que es la hora del primer
 * cierre.
 */
@Service
public class EvaluacionDelDiaService implements EvaluacionDelDiaPort {

    private static final TipoEntradaDiario TIPO = TipoEntradaDiario.EVALUACION_DIA;

    private final LoadEntradaDiarioPort cargar;
    private final SaveEntradaDiarioPort guardar;
    private final Clock clock;
    private final IdGenerator idGenerator;

    EvaluacionDelDiaService(LoadEntradaDiarioPort cargar, SaveEntradaDiarioPort guardar, Clock clock,
                            IdGenerator idGenerator) {
        this.cargar = cargar;
        this.guardar = guardar;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public MarcaDeCierre dejarCerrado(UserId participanteId, LocalDate fecha, String nota) {
        Instant ahora = clock.now();
        EntradaDiario entrada = cargar.porParticipanteFechaYTipo(participanteId, fecha, TIPO)
                .map(existente -> conLaNota(existente, nota, ahora))
                .orElseGet(() -> EntradaDiario.escribir(EntradaDiarioId.of(idGenerator.newId()), participanteId, fecha,
                        TIPO, nota, ahora));
        return aMarca(guardar.save(entrada));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarcaDeCierre> marcasEntre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        return cargar.entreFechasDeTipo(participanteId, TIPO, desde, hasta).stream().map(EvaluacionDelDiaService::aMarca).toList();
    }

    private static EntradaDiario conLaNota(EntradaDiario existente, String nota, Instant ahora) {
        existente.actualizarTexto(nota, ahora);
        return existente;
    }

    private static MarcaDeCierre aMarca(EntradaDiario entrada) {
        return new MarcaDeCierre(entrada.fecha(), entrada.contenidoTexto(), entrada.creadoEn());
    }
}
