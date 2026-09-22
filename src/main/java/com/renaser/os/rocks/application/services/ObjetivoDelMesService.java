package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarMedicionDelMapaPort;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarMedicionDelMapaPort.MedicionDelParticipante;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.LoadRocaMensualPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamensual.DatosDelEje;
import com.renaser.os.rocks.domain.model.rocamensual.MesPrograma;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma el plan mensual de los tres ejes: para cada mes, lo que el sistema calcula y lo que la
 * persona haya guardado encima.
 *
 * <p>Toda la aritmetica vive en el dominio ({@code CalculadoraObjetivoMensual}); aca solo se juntan
 * las tres piezas que la cuenta necesita y que viven en lugares distintos: la Roca Maestra (este
 * modulo), que se mide (el Mapa, via {@code onboarding.api}) y el dia de programa (el padron).
 */
@Service
public class ObjetivoDelMesService implements ConsultarObjetivoDelMesUseCase {

    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final LoadRocaMensualPort loadRocaMensualPort;
    private final ConsultarMedicionDelMapaPort medicionPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;

    public ObjetivoDelMesService(LoadRocaMaestraPort loadRocaMaestraPort, LoadRocaMensualPort loadRocaMensualPort,
                                  ConsultarMedicionDelMapaPort medicionPort,
                                  ConsultarProgresoParticipanteRocksPort progresoPort) {
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.loadRocaMensualPort = loadRocaMensualPort;
        this.medicionPort = medicionPort;
        this.progresoPort = progresoPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanMensualDelEje> misObjetivosMensuales(UserId actorId) {
        ProgresoParticipanteRocks progreso = requireProgreso(actorId);
        int mesActual = MesPrograma.deDia(progreso.diaPrograma());
        MedicionDelParticipante medicion = medicionPort.deParticipante(actorId);
        Map<String, RocaMensual> editadas = editadasPorMaestraYMes(actorId);

        List<PlanMensualDelEje> planes = new ArrayList<>();
        for (RocaMaestra maestra : loadRocaMaestraPort.deParticipante(actorId)) {
            planes.add(planDelEje(maestra, mesActual, medicion, editadas));
        }
        return List.copyOf(planes);
    }

    private PlanMensualDelEje planDelEje(RocaMaestra maestra, int mesActual, MedicionDelParticipante medicion,
                                          Map<String, RocaMensual> editadas) {
        DatosDelEje datos = datosDe(maestra, medicion);
        List<MesDelPlan> meses = new ArrayList<>();
        for (int mes = 1; mes <= MesPrograma.MESES; mes++) {
            meses.add(new MesDelPlan(mes, MesPrograma.ultimoDiaDe(mes), mes == mesActual,
                    datos.calcular(mes, mesActual), editadas.get(claveDe(maestra, mes))));
        }
        return new PlanMensualDelEje(maestra.eje(), mesActual, datos.unidad(),
                maestra.eje() == EjeObjetivo.TRABAJO, List.copyOf(meses));
    }

    /**
     * De donde saca cada eje sus numeros. Este {@code switch} es el unico lugar del sistema donde
     * se decide que un objetivo de Cuerpo puede tener tope fisiologico y que los de Relaciones
     * viven enteros en el Mapa.
     */
    private static DatosDelEje datosDe(RocaMaestra maestra, MedicionDelParticipante medicion) {
        return switch (maestra.eje()) {
            case CUERPO -> DatosDelEje.deSalud(maestra.meta(), medicion.saludTipo(), medicion.saludUnidad());
            case TRABAJO -> DatosDelEje.deNegocio(maestra.meta(), medicion.negocioTipo(), medicion.negocioPeriodo());
            case RELACIONES -> DatosDelEje.deRelaciones(medicion.relacionesBase(), medicion.relacionesMeta());
        };
    }

    /**
     * Las mensuales guardadas, indexadas por (maestra, mes). Se leen TODAS de una: son como mucho
     * nueve filas y pedirlas de a una seria una consulta por eje y por mes.
     */
    private Map<String, RocaMensual> editadasPorMaestraYMes(UserId actorId) {
        return loadRocaMensualPort.deParticipante(actorId).stream()
                .collect(Collectors.toMap(
                        r -> r.rocaMaestraId().value() + "#" + r.numeroMes(), Function.identity(),
                        (a, b) -> b));
    }

    private static String claveDe(RocaMaestra maestra, int mes) {
        return maestra.id().value() + "#" + mes;
    }

    /**
     * Mismo guard que {@code RocaMensualService}: sin fila de participante -> 404, SUSPENDIDO -> 403,
     * y solo el propio aprendiz opera sus rocas (E-169: la puerta es tener el programa andando, no
     * el rol, porque el staff que activa su seguimiento personal tambien cursa los 90 dias).
     */
    private ProgresoParticipanteRocks requireProgreso(UserId actorId) {
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
            throw new NotAuthorizedException("Solo un aprendiz opera sus propias rocas");
        }
        return progreso;
    }
}
