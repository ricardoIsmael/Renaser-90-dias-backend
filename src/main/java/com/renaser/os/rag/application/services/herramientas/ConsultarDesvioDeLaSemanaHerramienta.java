package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarHabitosVencidosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarHabitosVencidosPort.HabitoVencido;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ProgresoDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort.SemaforoDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code consultar_desvio_de_la_semana} (R0, solo lectura, D-177): los HECHOS de la semana en curso que
 * dicen si la persona se esta alejando de lo que se propuso. Sin juicio: como decirlo lo decide el
 * prompt ("Tus objetivos: el plan de la semana y las acciones del dia").
 *
 * <p><b>Que junta, y de donde.</b> De {@code rocks}: el avance de la semana, lo planificado y
 * completado por eje en los dias ya terminados, y los objetivos semanales con su estado. De
 * {@code habits}: los habitos que vencieron sin cumplirse en los dias ya terminados y los que estan en
 * pausa hoy. De {@code points}: el semaforo que ya guardo el barrido. Cada fuente por su puerto (D-41).
 *
 * <p><b>Hoy no cuenta como desvio.</b> Lo de hoy todavia se puede hacer: se informa aparte, nunca como
 * incumplido.
 *
 * <p><b>Los cambios de horario NO son un desvio</b> y no se cuentan: no hay tope de cambios (D-170), y
 * mover un habito para cumplirlo es lo contrario de alejarse.
 *
 * <p><b>Si una fuente falla, sale el resto</b> y el texto dice que falto. Solo es un fallo si no se pudo
 * leer ninguna.
 */
@Component
public class ConsultarDesvioDeLaSemanaHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_desvio_de_la_semana";

    private static final Logger log = LoggerFactory.getLogger(ConsultarDesvioDeLaSemanaHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Devuelve los hechos de la semana en curso que muestran si la persona va hacia sus objetivos o se "
                    + "esta alejando: por eje, cuantas rocas planifico y completo en los dias ya terminados; como "
                    + "va hoy; sus objetivos de la semana; los habitos que vencieron sin cumplirse; los habitos en "
                    + "pausa; y su semaforo si lo tiene. Son hechos, no un juicio. Usala cuando pregunte como va su "
                    + "semana, si va a llegar, o antes de decirle que se esta alejando de su objetivo.",
            List.of());

    private final ConsultarRocasDelAprendizPort rocasPort;
    private final ConsultarHabitosVencidosPort vencidosPort;
    private final GestionarPlanDeHabitosPort planPort;
    private final ConsultarSemaforoDelAprendizPort semaforoPort;

    public ConsultarDesvioDeLaSemanaHerramienta(ConsultarRocasDelAprendizPort rocasPort,
                                                ConsultarHabitosVencidosPort vencidosPort,
                                                GestionarPlanDeHabitosPort planPort,
                                                ConsultarSemaforoDelAprendizPort semaforoPort) {
        this.rocasPort = rocasPort;
        this.vencidosPort = vencidosPort;
        this.planPort = planPort;
        this.semaforoPort = semaforoPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        List<String> faltantes = new ArrayList<>();
        ProgresoDeLaSemana progreso = leer("el avance de sus rocas", faltantes, () -> rocasPort.progresoDeLaSemana(actorId));
        RocasDeLaSemana objetivos = leer("sus objetivos de la semana", faltantes, () -> rocasPort.deLaSemana(actorId));
        PlanDelAprendiz plan = leer("sus habitos en pausa", faltantes, () -> planPort.planDe(actorId));
        LocalDate hoy = progreso != null ? progreso.hoy() : plan != null ? plan.hoy() : null;
        if (hoy == null) {
            return ResultadoHerramienta.fallo("No pude leer como va su semana en este momento.");
        }
        LocalDate desde = progreso != null ? progreso.inicio() : hoy.with(DayOfWeek.MONDAY);
        List<HabitoVencido> vencidos = !hoy.isAfter(desde) ? List.of() : leer("sus habitos vencidos", faltantes,
                () -> vencidosPort.vencidosEntre(actorId, desde, hoy.minusDays(1)));
        Optional<SemaforoDelAprendiz> semaforo = leer("su semaforo", faltantes, () -> semaforoPort.de(actorId));
        List<HabitoDelPlan> pausados = plan == null ? null
                : plan.habitos().stream().filter(HabitoDelPlan::pausadoHoy).toList();
        return ResultadoHerramienta.exito(TextoDelDesvio.componer(new DesvioDeLaSemana(desde, hoy, progreso,
                objetivos, vencidos, pausados, semaforo == null ? null : semaforo.orElse(null),
                semaforo != null && semaforo.isEmpty(), List.copyOf(faltantes))));
    }

    /** {@code null} si la fuente fallo; en ese caso anota que falto. Nunca propaga. */
    private static <T> T leer(String que, List<String> faltantes, Supplier<T> lectura) {
        try {
            return lectura.get();
        } catch (NoSuchElementException | NotAuthorizedException sinAcceso) {
            faltantes.add(que);
            return null;
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer {}", NOMBRE, que, falla);
            faltantes.add(que);
            return null;
        }
    }
}
