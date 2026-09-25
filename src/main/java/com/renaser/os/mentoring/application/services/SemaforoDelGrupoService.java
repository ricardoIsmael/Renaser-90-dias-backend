package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoBasico;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoAdministrativoUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase;
import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.mentoring.domain.model.semaforo.OrdenDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.PeriodoDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.ResumenDelGrupo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * La tabla del semáforo de un grupo: la del mentor que lo acompaña y la del administrador (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.3).
 *
 * <p>Dos puertas y un solo armado, igual que {@link SeguimientoService}: la autorización es otra
 * —relación vigente para el mentor, rol para el administrador— y por eso vive en dos métodos; la
 * tabla es la misma, porque si fueran dos cálculos verían números distintos del mismo día.
 *
 * <p>Tres lecturas en lote, nunca una por aprendiz: el padrón del grupo, los nombres
 * ({@code findByIds}) y el semáforo de todos juntos. El semáforo solo se lee: lo calculó el barrido
 * horario de {@code points}.
 */
@Service
public class SemaforoDelGrupoService implements ConsultarSemaforoDelGrupoUseCase,
        ConsultarSemaforoDelGrupoAdministrativoUseCase {

    private final AccesoAVistasDelSemaforo acceso;
    private final MedicionDeGrupos medicion;
    private final AcompanamientoFinder acompanamientoFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    SemaforoDelGrupoService(AccesoAVistasDelSemaforo acceso, MedicionDeGrupos medicion,
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
    public TablaDelSemaforo tablaDe(ConsultaTablaDelGrupo consulta) {
        Instant ahora = clock.now();
        // Primero autorizar y despues leer: el camino que termina en 403 no carga el grupo.
        acceso.requireAcompananteVigente(consulta.actorId(), consulta.grupoId(), ahora);
        return armarTabla(consulta.grupoId(), consulta.semanaHasta(), ahora);
    }

    @Override
    @Transactional(readOnly = true)
    public TablaDelSemaforo tablaDe(ConsultaTablaAdministrativa consulta) {
        acceso.requireAdminActivo(consulta.actorId());
        return armarTabla(consulta.grupoId(), consulta.semanaHasta(), clock.now());
    }

    /** El armado compartido. Que sea UNO solo es el requisito, no una comodidad (V22). */
    private TablaDelSemaforo armarTabla(UUID grupoId, LocalDate semanaHasta, Instant ahora) {
        GrupoBasico grupo = acompanamientoFinder.grupo(grupoId)
                .orElseThrow(() -> new NoSuchElementException("Grupo no encontrado"));
        List<UserId> aprendices = medicion.aprendicesDe(grupoId, ahora);
        Map<UserId, VentanaDelSemaforo> ventanas = medicion.ventanasDe(aprendices, semanaHasta);

        List<FilaDelSemaforo> filas = filas(aprendices, ventanas);
        ResumenDelGrupo resumen = ResumenDelGrupo.de(filas.stream().map(FilaDelSemaforo::medicion).toList());
        return new TablaDelSemaforo(grupo.grupoId(), grupo.nombre(),
                periodo(aprendices, ventanas, esperado(grupo, semanaHasta, ahora)), resumen.conteo(), filas);
    }

    private List<FilaDelSemaforo> filas(List<UserId> aprendices, Map<UserId, VentanaDelSemaforo> ventanas) {
        Map<UserId, UserSummary> perfiles = userSummaryFinder.findByIds(aprendices);
        return aprendices.stream()
                .map(aprendiz -> fila(aprendiz, perfiles.get(aprendiz), ventanas.get(aprendiz)))
                .sorted(orden())
                .toList();
    }

    private static FilaDelSemaforo fila(UserId aprendiz, UserSummary perfil, VentanaDelSemaforo ventana) {
        return new FilaDelSemaforo(aprendiz.value(), perfil == null ? null : perfil.fullName(),
                perfil == null ? null : perfil.avatarUrl(), MedicionDelAprendiz.de(ventana));
    }

    /** Rojo, amarillo, sin datos, verde; por nombre; y ante homónimos, por id para que sea estable. */
    private static Comparator<FilaDelSemaforo> orden() {
        return OrdenDelSemaforo.<FilaDelSemaforo>porColorYNombre(fila -> fila.medicion().color(), FilaDelSemaforo::nombre)
                .thenComparing(FilaDelSemaforo::aprendizId);
    }

    private static PeriodoDelSemaforo periodo(List<UserId> aprendices, Map<UserId, VentanaDelSemaforo> ventanas,
                                              PeriodoDelSemaforo esperado) {
        return PeriodoDelSemaforo.de(aprendices.stream().map(ventanas::get).filter(Objects::nonNull).toList(),
                esperado);
    }

    /** Hoy en la zona del GRUPO, no la del servidor (regla 02): es la referencia del encabezado. */
    private static PeriodoDelSemaforo esperado(GrupoBasico grupo, LocalDate semanaHasta, Instant ahora) {
        LocalDate hoyDelGrupo = ahora.atZone(ZoneId.of(grupo.zonaHoraria())).toLocalDate();
        return PeriodoDelSemaforo.esperado(hoyDelGrupo, semanaHasta);
    }
}
