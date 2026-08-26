package com.renaser.os.rocks.application.ports.in.dashboard;

import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase.RocaDiariaVista;
import com.renaser.os.rocks.domain.model.dashboard.DiaGrillaSemanal;
import com.renaser.os.rocks.domain.model.dashboard.EstadoRitmoRocas;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Hueco #15: un solo llamado para la pantalla principal de Rocas — compone
 * lecturas ya existentes de {@code rocamaestra}/{@code rocasemanal}/
 * {@code rocadiaria} en vez de duplicar sus reglas. Ver
 * {@code docs/MODULO_ROCKS.md} §9 para qué campos del {@code getDashboard}
 * del repo viejo entran acá y cuáles quedan fuera (y por qué).
 */
public interface ConsultarDashboardRocasUseCase {

    DashboardRocas dashboard(UserId actorId);

    /**
     * {@code coherenceScore} del repo viejo NO viaja acá — vive conceptualmente
     * en {@code points} (docs/MODULO_POINTS.md Q-2/Q-3), sin puerto público
     * todavía; no se inventó una integración que ese módulo no expone.
     *
     * @param diaPrograma               día de programa del actor (0 si el programa no arrancó)
     * @param numeroSemana              semana de programa a la que pertenece hoy
     * @param inicioSemana              lunes de esa semana (o {@code fechaInicio} si es la semana 1 corta)
     * @param finSemana                 domingo de esa semana, recortado al fin del programa (día 90)
     * @param rocasMaestras             las (0-3) Rocas Maestras del actor
     * @param rocasDesbloqueadas        {@code rocasMaestras.size() == 3} (onboarding completo)
     * @param tieneRocaSemanal          hay una Roca Semanal por cada eje para {@code numeroSemana}
     * @param rocasSemanales            las Rocas Semanales de {@code numeroSemana}, con su compuerta de edición
     * @param grillaSemanal             un día por cada fecha entre {@code inicioSemana} y {@code finSemana}
     * @param ritmo                     semáforo de los últimos 7 días (ver {@link EstadoRitmoRocas})
     * @param diasCompletadosUltimos7   cuántos de esos 7 días tuvieron al menos una Roca completada
     * @param progresoSemanalPct        % completado/planificado de los días YA transcurridos de la semana
     * @param planificacionBloqueada    Ley II — a partir de qué punto planificar deja de ser opcional
     * @param puedeCrearPlanDiario      compuerta real de {@code CrearPlanDiarioUseCase} (ventana + roca semanal de mañana)
     * @param puedeCrearPlanSemanal     compuerta real de {@code CrearPlanSemanalUseCase} (onboarding completo)
     * @param planificacionSemanalTardia si se planificara AHORA, ¿contaría a destiempo? (para avisar antes de entrar)
     * @param rocasDeHoy                las Rocas Diarias de hoy, con bloqueo Ley IV ya resuelto
     * @param fechaInicioPrograma       para que el cliente sepa si el programa todavía no arrancó
     */
    record DashboardRocas(int diaPrograma, int numeroSemana, LocalDate inicioSemana, LocalDate finSemana,
                           List<RocaMaestra> rocasMaestras, boolean rocasDesbloqueadas, boolean tieneRocaSemanal,
                           List<RocaSemanalVista> rocasSemanales, List<DiaGrillaSemanal> grillaSemanal,
                           EstadoRitmoRocas ritmo, int diasCompletadosUltimos7, int progresoSemanalPct,
                           boolean planificacionBloqueada, boolean puedeCrearPlanDiario, boolean puedeCrearPlanSemanal,
                           boolean planificacionSemanalTardia, List<RocaDiariaVista> rocasDeHoy,
                           LocalDate fechaInicioPrograma) {
    }

    /** {@code editable}: la ventana real de W-03 (RK-5), no una franja fija de 48h. */
    record RocaSemanalVista(RocaSemanal roca, boolean editable) {
    }
}
