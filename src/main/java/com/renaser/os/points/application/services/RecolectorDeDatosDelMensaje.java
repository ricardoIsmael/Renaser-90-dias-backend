package com.renaser.os.points.application.services;

import com.renaser.os.points.api.CumplimientoPorHabitoFinder;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMensajeDelSemaforoUseCase.ConsultaDelMensaje;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase.ResumenParaHoy;
import com.renaser.os.points.application.services.DatosEstablesDelDia.Memoria;
import com.renaser.os.points.domain.model.semaforo.mensaje.DatosDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.HabitosDestacados;
import com.renaser.os.points.domain.model.semaforo.mensaje.HabitosQueFaltan;
import com.renaser.os.points.domain.model.semaforo.mensaje.Variable;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Reúne los datos de la persona para escribir su mensaje (FR-005, FR-006) sin pedirle nada nuevo a la base en las
 * lecturas repetidas del día (SC-004):
 * <ul>
 *   <li>El día y la fase salen de la participación que {@code /home} ya leyó.</li>
 *   <li>{@code {faltan}} sale de lo de hoy que {@code /home} ya leyó para sus tarjetas ({@code consulta.deHoy()}) y
 *       de los seis días cerrados de la ventana del semáforo; se recalcula en cada lectura.</li>
 *   <li>El nombre y los hábitos destacados no cambian en el día: se leen una vez por persona y día local y se guardan
 *       ({@link DatosEstablesDelDia}).</li>
 * </ul>
 * Solo se pide lo que las variables del mensaje necesitan.
 *
 * <p>Un dato que no aplica al grupo no se calcula: {@code {faltan}} solo existe donde hay un color mejor al que subir
 * y hoy es un día medido, y los hábitos destacados solo en los tres colores. Quedan en {@code null} y los mensajes
 * que los piden dejan de ser candidatos.
 */
@Component
class RecolectorDeDatosDelMensaje {

    private final UserSummaryFinder usuarios;
    private final CumplimientoPorHabitoFinder cumplimientoPorHabito;
    private final DatosEstablesDelDia estables;

    RecolectorDeDatosDelMensaje(UserSummaryFinder usuarios, CumplimientoPorHabitoFinder cumplimientoPorHabito,
                                DatosEstablesDelDia estables) {
        this.usuarios = usuarios;
        this.cumplimientoPorHabito = cumplimientoPorHabito;
        this.estables = estables;
    }

    /**
     * @param hoy      el día local de la persona
     * @param pedidas  las variables que se van a usar; las demás no se consultan
     */
    DatosDelMensaje reunir(ConsultaDelMensaje consulta, GrupoDelMensaje grupo, LocalDate hoy, Set<Variable> pedidas) {
        ParticipacionPrograma participacion = consulta.participacion();
        Memoria deHoy = estables.de(consulta.persona(), hoy, participacion.zona());
        boolean enElPrograma = participacion.diaPrograma() >= 1;
        HabitosDestacados destacados = destacadosDe(consulta, grupo, pedidas, deHoy);
        return new DatosDelMensaje(nombreDe(consulta, pedidas, deHoy), participacion.diaPrograma(),
                enElPrograma ? participacion.fase().nombreVisible() : null, destacados.flojo(), destacados.fuerte(),
                faltanDe(consulta, grupo, pedidas));
    }

    private String nombreDe(ConsultaDelMensaje consulta, Set<Variable> pedidas, Memoria deHoy) {
        if (!pedidas.contains(Variable.NOMBRE)) {
            return null;
        }
        return deHoy.nombre(() -> usuarios.findById(consulta.persona()).map(UserSummary::fullName)).orElse(null);
    }

    private HabitosDestacados destacadosDe(ConsultaDelMensaje consulta, GrupoDelMensaje grupo, Set<Variable> pedidas,
                                           Memoria deHoy) {
        boolean losPide = pedidas.contains(Variable.HABITO_FLOJO) || pedidas.contains(Variable.HABITO_FUERTE);
        List<DiaDelSemaforo> ventana = consulta.semaforo().dias();
        if (!losPide || !grupo.esDeColor() || ventana.isEmpty()) {
            return new HabitosDestacados(null, null);
        }
        return deHoy.destacados(() -> HabitosDestacados.de(cumplimientoPorHabito.enRango(consulta.persona(),
                ventana.getFirst().fecha(), ventana.getLast().fecha())));
    }

    /**
     * Sin consultar nada: lo de hoy y la ventana ya vienen en la consulta. Sin lo de hoy (la consulta del chat) no hay
     * «cuánto falta».
     */
    private Integer faltanDe(ConsultaDelMensaje consulta, GrupoDelMensaje grupo, Set<Variable> pedidas) {
        ResumenParaHoy semaforo = consulta.semaforo();
        if (!pedidas.contains(Variable.FALTAN) || !grupo.admiteCuantoFalta() || !semaforo.seMideHoy()) {
            return null;
        }
        return consulta.deHoy()
                .flatMap(deHoy -> HabitosQueFaltan.de(semaforo.color(), semaforo.dias(), deHoy.aDia()))
                .orElse(null);
    }
}
