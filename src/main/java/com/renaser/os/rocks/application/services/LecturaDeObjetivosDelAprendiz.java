package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.RocasDelAprendizFinder.CierreDeLaSemanaAnterior;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.CierreDelEje;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDeNoventaDias;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Las dos lecturas de "hacia donde va" del acompanante (D-177): el objetivo de los 90 dias por eje y
 * lo que la persona escribio al cerrar la semana anterior. Componen los casos de uso de la app
 * ({@link ConsultarRocasMaestrasUseCase}, {@link ConsultarRocasSemanalesUseCase}), que ya corren la
 * guarda de cuenta activa y programa andando; aca no hay reglas nuevas.
 */
@Component
class LecturaDeObjetivosDelAprendiz {

    private final ConsultarRocasMaestrasUseCase rocasMaestras;
    private final ConsultarRocasSemanalesUseCase rocasSemanales;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;

    LecturaDeObjetivosDelAprendiz(ConsultarRocasMaestrasUseCase rocasMaestras,
                                  ConsultarRocasSemanalesUseCase rocasSemanales,
                                  ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock) {
        this.rocasMaestras = rocasMaestras;
        this.rocasSemanales = rocasSemanales;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    List<ObjetivoDeNoventaDias> deNoventaDias(UserId aprendizId) {
        return rocasMaestras.misRocasMaestras(aprendizId).stream()
                .sorted(Comparator.comparing(RocaMaestra::eje))
                .map(LecturaDeObjetivosDelAprendiz::aObjetivo)
                .toList();
    }

    Optional<CierreDeLaSemanaAnterior> cierreDeLaSemanaAnterior(UserId aprendizId) {
        List<RocaMaestra> maestras = rocasMaestras.misRocasMaestras(aprendizId);
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(aprendizId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + aprendizId));
        LocalDate hoy = clock.now().atZone(progreso.zona()).toLocalDate();
        SemanaPrograma semanas = progreso.semanas(hoy);
        int semanaDeHoy = semanas.numeroSemanaParaFecha(hoy);
        if (hoy.isBefore(semanas.primerDia()) || semanaDeHoy <= 1) {
            return Optional.empty();
        }
        int anterior = semanaDeHoy - 1;
        Map<RocaMaestraId, EjeObjetivo> ejePorMaestra = maestras.stream()
                .collect(Collectors.toMap(RocaMaestra::id, RocaMaestra::eje, (a, b) -> a));
        List<CierreDelEje> ejes = rocasSemanales.misRocasSemanales(aprendizId, anterior).stream()
                .filter(roca -> ejePorMaestra.containsKey(roca.rocaMaestraId()))
                .sorted(Comparator.comparing(roca -> ejePorMaestra.get(roca.rocaMaestraId())))
                .map(roca -> aCierre(ejePorMaestra.get(roca.rocaMaestraId()), roca))
                .toList();
        return Optional.of(new CierreDeLaSemanaAnterior(anterior, ejes));
    }

    /**
     * {@code unidadAdelante}: el mismo criterio que {@code ObjetivoDelMesService} ({@code eje == TRABAJO},
     * la moneda va delante). Si alguna vez cambia, cambia alla y esta linea lo tiene que seguir.
     */
    private static ObjetivoDeNoventaDias aObjetivo(RocaMaestra maestra) {
        MetaCuantitativa meta = maestra.meta();
        boolean adelante = maestra.eje() == EjeObjetivo.TRABAJO;
        if (meta == null) {
            return new ObjetivoDeNoventaDias(maestra.eje().name(), maestra.objetivo(), null, null, null, adelante,
                    null, null);
        }
        return new ObjetivoDeNoventaDias(maestra.eje().name(), maestra.objetivo(), meta.objetivo(), meta.avance(),
                meta.unidad(), adelante, meta.lineaBase(), meta.porcentaje());
    }

    private static CierreDelEje aCierre(EjeObjetivo eje, RocaSemanal roca) {
        return new CierreDelEje(eje.name(), roca.titulo(), roca.autoevaluacionInicio(), roca.autoevaluacionFin(),
                roca.bloqueoPrincipal(), roca.correccion());
    }
}
