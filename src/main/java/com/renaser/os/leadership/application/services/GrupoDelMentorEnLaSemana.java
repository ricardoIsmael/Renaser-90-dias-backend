package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.Fuente;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizEnAlerta;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.ConteoPorColor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.TitularDelGrupo;
import com.renaser.os.leadership.domain.model.documento.OrdenPorRiesgo;
import com.renaser.os.leadership.domain.model.documento.ReglasDelMentorEnLaSemana;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.GrupoMedido;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cómo le fue a los aprendices de un mentor en una semana, para su documento (spec 056, FR-036): el color y el porcentaje de cada uno en esa
 * semana de sábado a viernes y en la anterior —de ahí sale la tendencia—, el conteo por color, el titular de su grupo, los aprendices con 3 o
 * más días sin actividad y a quiénes en rojo hay que escribirles. Todo sale del semáforo de {@code points}, el mismo de las pantallas del líder,
 * leído una vez para todos los aprendices, nunca uno por uno.
 *
 * <p>Una cuenta de prueba no aparece. Si el semáforo no responde, nadie queda con un color inventado: todos salen «sin datos» y sin comparación.
 */
@Component
class GrupoDelMentorEnLaSemana {

    private static final int UN_DECIMAL = 1;

    private final SemaforoFinder semaforo;
    private final UserSummaryFinder usuarios;
    private final CuentasDePruebaFinder pruebas;

    GrupoDelMentorEnLaSemana(SemaforoFinder semaforo, UserSummaryFinder usuarios, CuentasDePruebaFinder pruebas) {
        this.semaforo = semaforo;
        this.usuarios = usuarios;
        this.pruebas = pruebas;
    }

    /** @param viernes el viernes que cierra la semana */
    Resultado leer(List<GrupoMedido> grupos, LocalDate viernes) {
        Map<UserId, String> grupoDe = new LinkedHashMap<>();
        grupos.forEach(grupo -> grupo.aprendices().forEach(aprendiz -> grupoDe.putIfAbsent(aprendiz, grupo.nombre())));
        pruebas.entre(grupoDe.keySet()).forEach(grupoDe::remove);
        List<UserId> ids = List.copyOf(grupoDe.keySet());
        Map<UserId, UserSummary> perfiles = ids.isEmpty() ? Map.of() : usuarios.findByIds(ids);
        Fuente<Map<UserId, VentanaDelSemaforo>> esta = leerSemana(ids, viernes);
        Fuente<Map<UserId, VentanaDelSemaforo>> anterior = leerSemana(ids, viernes.minusDays(7));
        List<Fila> filas = new ArrayList<>();
        for (UserId id : ids) {
            filas.add(fila(perfiles.get(id), grupoDe.get(id), ventanaDe(esta, id), ventanaDe(anterior, id)));
        }
        filas.sort(Comparator.comparing(Fila::aprendiz, OrdenPorRiesgo.orden()));
        return resultado(grupos, filas);
    }

    private Fuente<Map<UserId, VentanaDelSemaforo>> leerSemana(List<UserId> ids, LocalDate viernes) {
        if (ids.isEmpty()) {
            return Fuente.de(Map.of());
        }
        return IndicadoresDeMentores.leer("el semáforo de la semana", () -> semaforo.semanaDe(ids, viernes));
    }

    private static VentanaDelSemaforo ventanaDe(Fuente<Map<UserId, VentanaDelSemaforo>> leida, UserId id) {
        return leida.disponible() ? leida.valor().get(id) : null;
    }

    private static Fila fila(UserSummary perfil, String grupo, VentanaDelSemaforo esta, VentanaDelSemaforo anterior) {
        ColorDeLaFila estado = esta == null ? ColorDeLaFila.SIN_DATOS : ColorDeLaFila.de(esta.color());
        BigDecimal porcentaje = esta == null ? null : esta.porcentaje();
        String tendencia = ReglasDelMentorEnLaSemana.tendencia(porcentaje, anterior == null ? null : anterior.porcentaje());
        int sinActividad = esta == null ? 0 : ReglasDelMentorEnLaSemana.diasSinActividad(esta.dias());
        String nombre = perfil == null ? null : perfil.fullName();
        return new Fila(new AprendizDelMentor(nombre, grupo, estado, porcentaje, tendencia), sinActividad);
    }

    private static Resultado resultado(List<GrupoMedido> grupos, List<Fila> filas) {
        List<AprendizDelMentor> aprendices = filas.stream().map(Fila::aprendiz).toList();
        List<AprendizEnAlerta> alertas = filas.stream()
                .filter(fila -> fila.diasSinActividad() >= DatosDelMentor.DIAS_SIN_ACTIVIDAD_PARA_ALERTA)
                .map(fila -> new AprendizEnAlerta(fila.aprendiz().nombre(), fila.diasSinActividad()))
                .sorted(Comparator.comparingInt(AprendizEnAlerta::diasSinActividad).reversed()
                        .thenComparing(AprendizEnAlerta::nombre, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<String> enRojo = aprendices.stream().filter(a -> a.estado() == ColorDeLaFila.ROJO).map(AprendizDelMentor::nombre)
                .filter(Objects::nonNull).toList();
        return new Resultado(titular(grupos, aprendices), conteo(aprendices), aprendices, alertas, enRojo);
    }

    private static ConteoPorColor conteo(List<AprendizDelMentor> aprendices) {
        return new ConteoPorColor(cuantos(aprendices, ColorDeLaFila.ROJO), cuantos(aprendices, ColorDeLaFila.AMARILLO),
                cuantos(aprendices, ColorDeLaFila.VERDE), cuantos(aprendices, ColorDeLaFila.SIN_DATOS));
    }

    private static int cuantos(List<AprendizDelMentor> aprendices, ColorDeLaFila estado) {
        return (int) aprendices.stream().filter(aprendiz -> aprendiz.estado() == estado).count();
    }

    /** El estado de su grupo es el del promedio de quienes tuvieron datos, con los mismos umbrales del semáforo; sin ninguno, «sin datos». */
    private static TitularDelGrupo titular(List<GrupoMedido> grupos, List<AprendizDelMentor> aprendices) {
        List<BigDecimal> medidos = aprendices.stream().map(AprendizDelMentor::porcentaje).filter(Objects::nonNull).toList();
        BigDecimal promedio = medidos.isEmpty() ? null : medidos.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(medidos.size()), UN_DECIMAL, RoundingMode.HALF_UP);
        ColorDeLaFila estado = promedio == null ? ColorDeLaFila.SIN_DATOS : ColorDeLaFila.de(ColorSemaforo.delPorcentaje(promedio));
        return new TitularDelGrupo(estado, promedio, aprendices.size(), grupos.stream().map(GrupoMedido::nombre).toList());
    }

    private record Fila(AprendizDelMentor aprendiz, int diasSinActividad) {
    }

    /** Todo lo del grupo que el documento del mentor necesita. */
    record Resultado(TitularDelGrupo titular, ConteoPorColor conteo, List<AprendizDelMentor> aprendices, List<AprendizEnAlerta> alertas,
                     List<String> enRojoPorEscribir) {
    }
}
