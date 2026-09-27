package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoConAprendices;
import com.renaser.os.mentoring.application.ports.in.ConsultarAtencionDelSemaforoUseCase;
import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.mentoring.domain.model.semaforo.OrdenDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.PeriodoDelSemaforo;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * La lista «¿A quién atiendo hoy?» de Administración (S-4): ver
 * {@link ConsultarAtencionDelSemaforoUseCase}.
 *
 * <p>Lecturas en lote, nunca una por persona: el padrón de aprendices activos (una consulta), los
 * grupos que están corriendo con sus aprendices, UNA lectura del semáforo para todos
 * ({@link MedicionDeGrupos#ventanasDe}, la misma de las tablas) y una de nombres de mentores. El color
 * sale de {@link MedicionDelAprendiz}, igual que en la tabla del grupo: la persona se ve con el mismo
 * color y porcentaje en las dos vistas.
 */
@Service
public class AtencionDelSemaforoService implements ConsultarAtencionDelSemaforoUseCase {

    /** Referencia del encabezado: todo el padrón vive en Lima (regla 02). */
    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");
    private static final Set<ColorSemaforo> NECESITAN_ATENCION = EnumSet.of(ColorSemaforo.ROJO, ColorSemaforo.AMARILLO);

    private final AccesoAVistasDelSemaforo acceso;
    private final MedicionDeGrupos medicion;
    private final AcompanamientoFinder acompanamientoFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    AtencionDelSemaforoService(AccesoAVistasDelSemaforo acceso, MedicionDeGrupos medicion,
                               AcompanamientoFinder acompanamientoFinder, UserSummaryFinder userSummaryFinder,
                               Clock clock) {
        this.acceso = acceso;
        this.medicion = medicion;
        this.acompanamientoFinder = acompanamientoFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AtencionDelSemaforo atencionDe(ConsultaAtencion consulta) {
        acceso.requireAdminActivo(consulta.actorId());
        Instant ahora = clock.now();
        Map<UserId, UserSummary> padron = padronActivo();
        Map<UserId, VentanaDelSemaforo> ventanas = medicion.ventanasDe(padron.keySet(), null);
        List<AprendizQueNecesitaAtencion> aprendices = queNecesitanAtencion(padron, ventanas, ahora);
        PeriodoDelSemaforo periodo = PeriodoDelSemaforo.de(ventanas.values(),
                PeriodoDelSemaforo.esperado(ahora.atZone(ZONA_POR_DEFECTO).toLocalDate(), null));
        return new AtencionDelSemaforo(periodo, cuantos(aprendices, ColorSemaforo.ROJO),
                cuantos(aprendices, ColorSemaforo.AMARILLO), aprendices);
    }

    /** Aprendices con la cuenta ACTIVA, el mismo criterio que las tablas y el barrido. */
    private Map<UserId, UserSummary> padronActivo() {
        Map<UserId, UserSummary> padron = new LinkedHashMap<>();
        userSummaryFinder.aprendicesActivos().forEach(aprendiz -> padron.put(aprendiz.id(), aprendiz));
        return padron;
    }

    private List<AprendizQueNecesitaAtencion> queNecesitanAtencion(Map<UserId, UserSummary> padron,
                                                                   Map<UserId, VentanaDelSemaforo> ventanas,
                                                                   Instant ahora) {
        List<AprendizQueNecesitaAtencion> filas = new ArrayList<>();
        Map<UserId, List<GrupoDelAprendiz>> grupos = null;
        for (Map.Entry<UserId, VentanaDelSemaforo> ventana : ventanas.entrySet()) {
            MedicionDelAprendiz suya = MedicionDelAprendiz.de(ventana.getValue());
            if (!NECESITAN_ATENCION.contains(suya.color())) {
                continue;
            }
            // Los grupos se piden solo si alguien necesita atención, y una sola vez.
            grupos = grupos == null ? gruposPorAprendiz(ahora) : grupos;
            UserSummary perfil = padron.get(ventana.getKey());
            filas.add(new AprendizQueNecesitaAtencion(ventana.getKey().value(), perfil.fullName(), perfil.avatarUrl(),
                    suya, grupos.getOrDefault(ventana.getKey(), List.of())));
        }
        filas.sort(orden());
        return List.copyOf(filas);
    }

    /** Todos los grupos que corren hoy, recepción y sin mentor incluidos; un aprendiz puede estar en dos (D-139). */
    private Map<UserId, List<GrupoDelAprendiz>> gruposPorAprendiz(Instant ahora) {
        List<GrupoConAprendices> operativos = acompanamientoFinder.gruposOperativos(ahora);
        Map<UserId, UserSummary> mentores = userSummaryFinder.findByIds(operativos.stream()
                .map(GrupoConAprendices::mentorId).filter(Objects::nonNull).distinct().toList());
        Map<UserId, List<GrupoDelAprendiz>> porAprendiz = new LinkedHashMap<>();
        for (GrupoConAprendices grupo : operativos) {
            UserSummary mentor = grupo.mentorId() == null ? null : mentores.get(grupo.mentorId());
            GrupoDelAprendiz suyo = new GrupoDelAprendiz(grupo.grupoId(), grupo.nombre(), grupo.recepcion(),
                    mentor == null ? null : mentor.fullName());
            grupo.aprendices().forEach(aprendiz -> porAprendiz.computeIfAbsent(aprendiz, a -> new ArrayList<>()).add(suyo));
        }
        return porAprendiz;
    }

    /** Rojo, amarillo; por nombre; ante homónimos, por id para que sea estable. */
    private static Comparator<AprendizQueNecesitaAtencion> orden() {
        return OrdenDelSemaforo.porMedicionYNombre(AprendizQueNecesitaAtencion::medicion,
                        AprendizQueNecesitaAtencion::nombre)
                .thenComparing(AprendizQueNecesitaAtencion::aprendizId);
    }

    private static int cuantos(List<AprendizQueNecesitaAtencion> aprendices, ColorSemaforo color) {
        return (int) aprendices.stream().filter(a -> a.medicion().color() == color).count();
    }
}
