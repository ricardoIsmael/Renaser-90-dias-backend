package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.CerrarHuecosDeLaSemanaUseCase;
import com.renaser.os.mentoring.application.ports.out.FotosDeHuecosPort;
import com.renaser.os.mentoring.domain.model.huecos.FotoDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.SemanaPedida;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El barrido que guarda la foto de la semana que acaba de terminar (spec 015, FR-018). Cada vez que corre, mira a cada
 * persona con programa activado y cuenta activa en SU zona: si su domingo ya terminó y la foto de esa semana no existe,
 * la calcula y la guarda. Una semana más vieja que la última cerrada no se rellena: el horario que regía se
 * sobrescribió, y calcularla ahora con el de hoy mentiría.
 *
 * <p><b>Sin {@code @Transactional} sobre el barrido</b> (regla 02 §4): el cálculo de cada persona es de solo lectura y su
 * foto se guarda con un INSERT propio. Una persona que falla no frena a las demás y se reintenta sola en la próxima
 * corrida. <b>Idempotente</b>: lo que ya tiene foto no se calcula, y si dos instancias coinciden, el
 * {@code ON CONFLICT DO NOTHING} deja la primera.
 */
@Service
public class CierreDeHuecosService implements CerrarHuecosDeLaSemanaUseCase {

    private static final Logger log = LoggerFactory.getLogger(CierreDeHuecosService.class);

    /** Mismo tamaño de página que el semáforo y el reloj del programa. */
    static final int TAMANO_LOTE = 500;

    private final ProgramasActivadosFinder programas;
    private final UserSummaryFinder usuarios;
    private final ParticipacionProgramaFinder participaciones;
    private final CalculoDeLosHuecos calculo;
    private final FotosDeHuecosPort fotos;
    private final Clock clock;

    CierreDeHuecosService(ProgramasActivadosFinder programas, UserSummaryFinder usuarios,
                          ParticipacionProgramaFinder participaciones, CalculoDeLosHuecos calculo,
                          FotosDeHuecosPort fotos, Clock clock) {
        this.programas = programas;
        this.usuarios = usuarios;
        this.participaciones = participaciones;
        this.calculo = calculo;
        this.fotos = fotos;
        this.clock = clock;
    }

    @Override
    public ResultadoDelCierre cerrarPendientes() {
        Instant ahora = clock.now();
        ResultadoDelCierre total = ResultadoDelCierre.VACIO;
        int offset = 0;
        List<ProgramaActivado> pagina;
        do {
            pagina = programas.pagina(offset, TAMANO_LOTE);
            total = total.mas(procesar(pagina, ahora));
            offset += TAMANO_LOTE;
        } while (pagina.size() == TAMANO_LOTE);
        return total;
    }

    private ResultadoDelCierre procesar(List<ProgramaActivado> pagina, Instant ahora) {
        List<PorCerrar> medibles = medibles(pagina, ahora);
        if (medibles.isEmpty()) {
            return ResultadoDelCierre.VACIO;
        }
        int guardadas = 0;
        int fallidas = 0;
        for (PorCerrar persona : sinFoto(medibles)) {
            try {
                guardadas += guardar(persona, ahora) ? 1 : 0;
            } catch (RuntimeException e) {
                // Una persona que falla no frena el barrido (regla 02 §4); se reintenta sola.
                fallidas++;
                log.error("[mentoring.CierreDeHuecos] fallo el participante {}; sigue el barrido",
                        persona.programa().participanteId(), e);
            }
        }
        return new ResultadoDelCierre(medibles.size(), guardadas, fallidas);
    }

    /** Las personas de cuenta activa y no de prueba cuya semana recién terminada toca su programa. */
    private List<PorCerrar> medibles(List<ProgramaActivado> pagina, Instant ahora) {
        List<ProgramaActivado> conFechas = pagina.stream()
                .filter(programa -> programa.primeraFecha() != null && programa.ultimaFecha() != null).toList();
        if (conFechas.isEmpty()) {
            return List.of();
        }
        Map<UserId, UserSummary> cuentas = usuarios.findByIds(
                conFechas.stream().map(ProgramaActivado::participanteId).toList());
        List<PorCerrar> medibles = new ArrayList<>();
        for (ProgramaActivado programa : conFechas) {
            UserSummary cuenta = cuentas.get(programa.participanteId());
            if (cuenta != null && cuenta.status() == UserStatus.ACTIVE && !cuenta.esCuentaDePrueba()) {
                semanaQueTermino(programa, ahora).ifPresent(lunes -> medibles.add(new PorCerrar(programa, lunes)));
            }
        }
        return medibles;
    }

    /**
     * La última semana ya terminada en la zona de la persona (el lunes de hoy menos siete días), si toca su programa:
     * no empieza después de su último día ni termina antes de su primero.
     */
    private static Optional<LocalDate> semanaQueTermino(ProgramaActivado programa, Instant ahora) {
        LocalDate hoy = ahora.atZone(programa.zona()).toLocalDate();
        LocalDate lunes = SemanaPedida.lunesDe(hoy).minusWeeks(1);
        boolean tocaSuPrograma = !lunes.isAfter(programa.ultimaFecha()) && !lunes.plusDays(6).isBefore(programa.primeraFecha());
        return tocaSuPrograma ? Optional.of(lunes) : Optional.empty();
    }

    /** Descarta a quien ya tiene la foto de su semana: una consulta por lunes distinto, no una por persona. */
    private List<PorCerrar> sinFoto(List<PorCerrar> medibles) {
        Map<LocalDate, List<PorCerrar>> porLunes = medibles.stream().collect(Collectors.groupingBy(PorCerrar::lunes));
        List<PorCerrar> pendientes = new ArrayList<>();
        porLunes.forEach((lunes, personas) -> {
            Set<UserId> conFoto = fotos.conFotoDe(personas.stream().map(p -> p.programa().participanteId()).toList(),
                    lunes);
            personas.stream().filter(p -> !conFoto.contains(p.programa().participanteId())).forEach(pendientes::add);
        });
        return pendientes;
    }

    private boolean guardar(PorCerrar persona, Instant ahora) {
        UserId id = persona.programa().participanteId();
        ParticipacionPrograma participacion = participaciones.deParticipante(id)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + id));
        LocalDate hoy = ahora.atZone(participacion.zona()).toLocalDate();
        SemanaDeHuecos semana = calculo.calcular(participacion, persona.lunes(), hoy);
        return fotos.registrar(new FotoDeHuecos(id, participacion.zona(), semana, ahora));
    }

    /** Una persona y el lunes de la semana que acaba de terminar. */
    private record PorCerrar(ProgramaActivado programa, LocalDate lunes) {
    }
}
