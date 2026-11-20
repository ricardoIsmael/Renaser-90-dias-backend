package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder;
import com.renaser.os.rocks.api.RocasDelAprendizFinder;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.DiaDeLaSemana;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDelMesDelEje;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.RocasDeLaSemana;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.shared.domain.CortesDeFase;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Implementa {@link ObjetivosDelAprendizFinder}: junta lo que ya da {@link RocasDelAprendizFinder} (rocas maestras, mes y semana, con
 * la zona y las compuertas resueltas ahí) y le suma el plazo y la fase de la persona. La única cuenta propia es sumar las acciones de la
 * semana. Antes vivía en {@code leadership} (spec 056, FR-027); se movió aquí para que el mentor (spec 057) lea lo mismo sin copiarlo.
 */
@Service
class ObjetivosDelAprendizService implements ObjetivosDelAprendizFinder {

    private final RocasDelAprendizFinder rocas;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;

    ObjetivosDelAprendizService(RocasDelAprendizFinder rocas, ConsultarProgresoParticipanteRocksPort progresoPort) {
        this.rocas = rocas;
        this.progresoPort = progresoPort;
    }

    @Override
    @Transactional(readOnly = true)
    public ObjetivosDelAprendiz objetivosDe(UserId aprendizId) {
        var noventaDias = rocas.objetivosDeNoventaDias(aprendizId);
        ObjetivosDelMes mes = delMes(aprendizId);
        ObjetivosDeLaSemana semana = deLaSemana(aprendizId);
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(aprendizId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + aprendizId));
        return new ObjetivosDelAprendiz(noventaDias, mes, semana, progreso.duracionDias(),
                faseDe(progreso.diaPrograma(), progreso.duracionDias()));
    }

    /** La fase del día de hoy, con sus cortes estirados al plazo de la persona; el día 0 cuenta como la fase 1. */
    static FaseDelPrograma faseDe(int dia, int duracionDias) {
        int numero = CortesDeFase.numeroDeFase(dia, duracionDias);
        return new FaseDelPrograma(numero, CortesDeFase.inicioDeFase(numero, duracionDias),
                CortesDeFase.finDeFase(numero, duracionDias));
    }

    private ObjetivosDelMes delMes(UserId persona) {
        List<ObjetivoDelMesDelEje> delMes = rocas.delMes(persona);
        return delMes.isEmpty() ? null
                : new ObjetivosDelMes(delMes.getFirst().numeroMes(), delMes.getFirst().diaDeCierre(), delMes);
    }

    private ObjetivosDeLaSemana deLaSemana(UserId persona) {
        RocasDeLaSemana semana = rocas.deLaSemana(persona);
        if (semana.rocas().isEmpty()) {
            return null;
        }
        List<DiaDeLaSemana> dias = rocas.progresoDeLaSemana(persona).dias();
        return new ObjetivosDeLaSemana(semana.numeroSemana(), semana.inicio(), semana.fin(),
                semana.rocas().stream().map(roca -> new ObjetivoDeLaSemana(roca.eje(), roca.titulo())).toList(),
                suma(dias, DiaDeLaSemana::total), suma(dias, DiaDeLaSemana::completadas));
    }

    /** Los días que no llegaron traen null: no suman. */
    private static int suma(List<DiaDeLaSemana> dias, Function<DiaDeLaSemana, Integer> valor) {
        return dias.stream().map(valor).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }
}
