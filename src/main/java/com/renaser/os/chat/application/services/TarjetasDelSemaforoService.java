package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.semaforo.EnviarTarjetasDelSemaforoUseCase;
import com.renaser.os.chat.domain.model.semaforo.ColorDeTarjeta;
import com.renaser.os.chat.domain.model.semaforo.HoraDeLaTarjeta;
import com.renaser.os.chat.domain.model.semaforo.TarjetaDelSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoDelDiaFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El barrido de la tarjeta diaria del semáforo (D-223). Por página de programas activados: quién está en
 * su 23:50–23:59 local, cuáles son aprendices con la cuenta activa, y qué dio su día en el semáforo.
 *
 * <p><b>Quién recibe:</b> solo {@code APRENDIZ} con la cuenta {@code ACTIVA} y con el día MEDIDO por el
 * semáforo, que ya deja afuera el día sin nada programado, el que cae antes del Día 1 o después del 90, y
 * el que está en pausa o tuvo la cuenta suspendida (D-209). El staff con programa propio no recibe tarjeta.
 *
 * <p><b>Sin {@code @Transactional} sobre el barrido</b> (regla 02 §4): cada aprendiz en su propia
 * transacción ({@code enviarUnaVez}) y uno que falla no frena a los demás; lo reintenta la corrida
 * siguiente de su ventana.
 */
@Service
public class TarjetasDelSemaforoService implements EnviarTarjetasDelSemaforoUseCase {

    private static final Logger log = LoggerFactory.getLogger(TarjetasDelSemaforoService.class);

    /** Mismo tamaño de página que el barrido del semáforo ({@code CierreDelSemaforoService}). */
    static final int TAMANO_LOTE = 500;

    private final ProgramasActivadosFinder programasFinder;
    private final UserSummaryFinder usuarios;
    private final SemaforoDelDiaFinder semaforoDelDia;
    private final TarjetaEnSoporte tarjetaEnSoporte;
    private final Clock clock;

    TarjetasDelSemaforoService(ProgramasActivadosFinder programasFinder, UserSummaryFinder usuarios,
                               SemaforoDelDiaFinder semaforoDelDia, TarjetaEnSoporte tarjetaEnSoporte, Clock clock) {
        this.programasFinder = programasFinder;
        this.usuarios = usuarios;
        this.semaforoDelDia = semaforoDelDia;
        this.tarjetaEnSoporte = tarjetaEnSoporte;
        this.clock = clock;
    }

    @Override
    public ResultadoDeTarjetas enviarLasQueTocan() {
        Instant ahora = clock.now();
        ResultadoDeTarjetas total = ResultadoDeTarjetas.NADA;
        int offset = 0;
        List<ProgramaActivado> pagina;
        do {
            pagina = programasFinder.pagina(offset, TAMANO_LOTE);
            total = total.mas(procesar(pagina, ahora));
            offset += TAMANO_LOTE;
        } while (pagina.size() == TAMANO_LOTE);
        return total;
    }

    private ResultadoDeTarjetas procesar(List<ProgramaActivado> pagina, Instant ahora) {
        Map<UserId, LocalDate> enSuHora = aprendicesActivos(diaQueTocaDeCadaUno(pagina, ahora));
        if (enSuHora.isEmpty()) {
            return ResultadoDeTarjetas.NADA;
        }
        List<TarjetaDelSemaforo> tarjetas = tarjetasDe(enSuHora);
        int enviadas = 0;
        int fallidas = 0;
        for (TarjetaDelSemaforo tarjeta : tarjetas) {
            try {
                enviadas += tarjetaEnSoporte.entregar(tarjeta) ? 1 : 0;
            } catch (RuntimeException e) {
                // Uno que falla no frena el barrido (regla 02 §4); la corrida de las 23:55 lo reintenta.
                fallidas++;
                log.error("[chat.semaforo] falló la tarjeta de {} del {}; sigue el barrido",
                        tarjeta.aprendiz(), tarjeta.fecha(), e);
            }
        }
        return new ResultadoDeTarjetas(enSuHora.size(), enviadas, fallidas);
    }

    /** El día local de cada uno que está en su 23:50–23:59 en este instante (regla 02: nunca el del servidor). */
    private static Map<UserId, LocalDate> diaQueTocaDeCadaUno(List<ProgramaActivado> pagina, Instant ahora) {
        Map<UserId, LocalDate> dias = new HashMap<>();
        for (ProgramaActivado programa : pagina) {
            HoraDeLaTarjeta.diaQueToca(ahora, programa.zona())
                    .ifPresent(dia -> dias.put(programa.participanteId(), dia));
        }
        return dias;
    }

    private Map<UserId, LocalDate> aprendicesActivos(Map<UserId, LocalDate> dias) {
        if (dias.isEmpty()) {
            return dias;
        }
        Map<UserId, UserSummary> perfiles = usuarios.findByIds(dias.keySet());
        Map<UserId, LocalDate> activos = new HashMap<>();
        dias.forEach((id, dia) -> {
            UserSummary perfil = perfiles.get(id);
            if (perfil != null && perfil.role() == UserRole.TRAINEE && perfil.status() == UserStatus.ACTIVE) {
                activos.put(id, dia);
            }
        });
        return activos;
    }

    /** Una lectura del semáforo por fecha (en una misma corrida casi siempre es una sola). */
    private List<TarjetaDelSemaforo> tarjetasDe(Map<UserId, LocalDate> enSuHora) {
        Map<LocalDate, List<UserId>> porFecha = new HashMap<>();
        enSuHora.forEach((id, dia) -> porFecha.computeIfAbsent(dia, d -> new ArrayList<>()).add(id));
        List<TarjetaDelSemaforo> tarjetas = new ArrayList<>();
        porFecha.forEach((fecha, ids) -> semaforoDelDia.delDia(ids, fecha)
                .forEach((id, dia) -> tarjetaDe(id, dia).ifPresent(tarjetas::add)));
        return tarjetas;
    }

    /** Solo un día MEDIDO tiene tarjeta; el color es el que ya decidió el semáforo, no uno nuevo. */
    static Optional<TarjetaDelSemaforo> tarjetaDe(UserId aprendiz, DiaDelSemaforo dia) {
        if (dia.estado() != EstadoDiaSemaforo.MEDIDO) {
            return Optional.empty();
        }
        ColorDeTarjeta color = ColorDeTarjeta.valueOf(dia.color().name());
        return Optional.of(new TarjetaDelSemaforo(aprendiz, dia.fecha(), color, dia.porcentaje()));
    }
}
