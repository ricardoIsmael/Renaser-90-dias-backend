package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.AccionDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.DiaDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.MotivoRepetido;
import com.renaser.os.rocks.api.CierresDelDiaFinder.RespuestaDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.ResumenDeCierres;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.AvanceDeLaSemana;
import com.renaser.os.rocks.domain.model.cierre.CierreDeAccion;
import com.renaser.os.rocks.domain.model.cierre.EstadoDelCierre;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Arma lo que se ve del cierre de un día a partir de las rocas de ese día y de su marca (spec 017). Es una traducción pura del
 * dominio a la proyección pública: no lee nada ni decide permisos.
 *
 * <p><b>Una respuesta de cierre solo se muestra si la acción sigue pendiente.</b> Si se cumplió después de cerrar el día, su
 * respuesta vieja queda en la base pero no se lee (ya no hay nada que explicar).
 */
final class ArmadoDelCierre {

    private static final Comparator<RocaDiaria> POR_HORA_Y_POSICION = Comparator
            .comparing(RocaDiaria::horaInicio, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(RocaDiaria::posicion);

    private ArmadoDelCierre() {
    }

    static DiaDelCierre dia(LocalDate fecha, LocalDate hoy, List<RocaDiaria> rocas, MarcaDeCierre marca) {
        EstadoDelCierre estado = EstadoDelCierre.de(fecha, hoy, marca != null);
        return new DiaDelCierre(fecha, estado.name(), marca == null ? null : marca.cerradoEn(),
                marca == null ? null : marca.nota(), acciones(rocas));
    }

    static List<AccionDelCierre> acciones(List<RocaDiaria> rocas) {
        return rocas.stream().sorted(POR_HORA_Y_POSICION).map(ArmadoDelCierre::accion).toList();
    }

    private static AccionDelCierre accion(RocaDiaria roca) {
        return new AccionDelCierre(roca.id().value(), roca.titulo(), roca.horaInicio(), roca.completada(),
                roca.completadaEn(), roca.completada() ? null : respuesta(roca.cierre()));
    }

    private static RespuestaDelCierre respuesta(CierreDeAccion cierre) {
        if (cierre == null) {
            return null;
        }
        return new RespuestaDelCierre(cierre.respuesta().motivo().name(), cierre.respuesta().manana().name(),
                cierre.respuesta().nota());
    }

    /** Las acciones de la semana hasta hoy; {@code null} si no hay ninguna planificada (nunca «0 de 0»). */
    static AvanceDeLaSemana avance(List<RocaDiaria> rocasDeLaSemanaHastaHoy) {
        if (rocasDeLaSemanaHastaHoy.isEmpty()) {
            return null;
        }
        int cumplidas = (int) rocasDeLaSemanaHastaHoy.stream().filter(RocaDiaria::completada).count();
        return new AvanceDeLaSemana(cumplidas, rocasDeLaSemanaHastaHoy.size());
    }

    static ResumenDeCierres resumen(List<DiaDelCierre> dias) {
        int cerrados = (int) dias.stream().filter(d -> EstadoDelCierre.CERRADO.name().equals(d.estado())).count();
        int sinCerrar = (int) dias.stream().filter(d -> EstadoDelCierre.SIN_CERRAR.name().equals(d.estado())).count();
        return new ResumenDeCierres(cerrados, sinCerrar, motivos(dias));
    }

    private static List<MotivoRepetido> motivos(List<DiaDelCierre> dias) {
        Map<String, Integer> veces = new HashMap<>();
        dias.stream().flatMap(d -> d.acciones().stream()).map(AccionDelCierre::cierre).filter(Objects::nonNull)
                .forEach(c -> veces.merge(c.motivo(), 1, Integer::sum));
        return veces.entrySet().stream()
                .map(e -> new MotivoRepetido(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(MotivoRepetido::veces).reversed().thenComparing(MotivoRepetido::motivo))
                .toList();
    }
}
