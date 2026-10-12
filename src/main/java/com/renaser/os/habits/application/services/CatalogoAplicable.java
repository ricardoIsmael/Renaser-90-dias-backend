package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.audiencia.CargarAudienciasPort;
import com.renaser.os.habits.application.ports.out.audiencia.HistorialDeRolPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.HistorialDeAudiencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El ÚNICO lugar que decide qué hábitos globales le tocan a una persona en su día local (spec 001, research R-4). Lo
 * usan la generación de registros y las vistas que antes leían {@code catalogoActivo()} a secas; todo lo demás
 * (coherencia, semáforo, puntos, agenda, avisos, flujos propios) parte de los registros y hereda el filtro.
 *
 * <p>Vigencia estricta (clarify 2026-10-12): para el día {@code dia} rigen la audiencia y el rol guardados antes de
 * ese día en la zona de la persona. Dos consultas por llamada —las audiencias de todo el catálogo en lote y el
 * historial de rol de la persona—, nunca una por hábito.
 *
 * <p>El código de rol sale del espejo local {@code RolParticipante}: un rol nuevo pide sumarle una constante (el
 * {@code switch} del adaptador de progreso no compila sin ella), y nada más en habits.
 */
@Component
public class CatalogoAplicable {

    private final LoadHabitoPort loadHabitoPort;
    private final CargarAudienciasPort audienciasPort;
    private final HistorialDeRolPort historialDeRolPort;

    public CatalogoAplicable(LoadHabitoPort loadHabitoPort, CargarAudienciasPort audienciasPort,
                             HistorialDeRolPort historialDeRolPort) {
        this.loadHabitoPort = loadHabitoPort;
        this.audienciasPort = audienciasPort;
        this.historialDeRolPort = historialDeRolPort;
    }

    /** Los hábitos globales activos que aplican a la persona el día local {@code dia}, en el orden del catálogo. */
    public List<Habito> globalesPara(UserId persona, ProgresoParticipanteHabits progreso, LocalDate dia) {
        return separar(persona, progreso, dia).get(true);
    }

    /**
     * Los globales activos que NO le tocan ese día. Lo usa la vista de quien acompaña, que parte de su propia consulta
     * del catálogo y solo tiene que sacar estos (FR-013).
     */
    public Set<HabitoId> globalesQueNoLeTocan(UserId persona, ProgresoParticipanteHabits progreso, LocalDate dia) {
        return separar(persona, progreso, dia).get(false).stream().map(Habito::id).collect(Collectors.toSet());
    }

    private Map<Boolean, List<Habito>> separar(UserId persona, ProgresoParticipanteHabits progreso, LocalDate dia) {
        List<Habito> activos = loadHabitoPort.catalogoActivo();
        if (activos.isEmpty()) {
            return Map.of(true, List.of(), false, List.of());
        }
        ZoneId zona = ZoneId.of(progreso.timezone());
        CodigoDeRol rol = historialDeRolPort.de(persona)
                .vigenteEl(dia, zona, CodigoDeRol.of(progreso.rol().name()));
        Map<HabitoId, HistorialDeAudiencia> audiencias = audienciasPort.deHabitos(
                activos.stream().map(Habito::id).toList());
        return activos.stream().collect(Collectors.partitioningBy(h -> audiencias
                .getOrDefault(h.id(), HistorialDeAudiencia.vacio()).vigenteEl(dia, zona).aplicaA(rol)));
    }
}
