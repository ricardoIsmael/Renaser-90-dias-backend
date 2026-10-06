package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarZonasDeParticipantesPort;
import com.renaser.os.habits.application.ports.out.registro.ConsultarPendientesVencidosPort;
import com.renaser.os.habits.application.ports.out.registro.ConsultarPendientesVencidosPort.PendientesDeParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.registro.CorteDeExpiracion;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * E-534 — el barrido que pasa a {@code EXPIRADO} lo {@code PENDIENTE} de los dias que ya terminaron, segun el dia local
 * de CADA participante ({@link CorteDeExpiracion}). Antes vivia en {@code RegistroService} y vencia todo con la fecha
 * UTC de las 05:00 UTC, que solo es la medianoche de Lima.
 *
 * <p><b>Regla 02 §4, punto por punto.</b> Corre cada hora y el dominio decide con quien hay algo que hacer: una hora en
 * que a nadie se le termino el dia no escribe nada. Pagina el padron de a {@value #TAMANO_LOTE} participantes (keyset
 * por id, una consulta agrupada por pagina, sin cargar las filas de nadie hasta saber que algo le vencio). Sin
 * {@code @Transactional} sobre el barrido: cada fila se guarda en SU transaccion (C-6), y un {@code try/catch} por
 * participante y por fila hace que un fallo quede para la proxima hora sin frenar a los demas.
 *
 * <p><b>Que NO cambia.</b> Lo que vence (solo {@code PENDIENTE}), como vence ({@link RegistroHabito#expirar}: 0 puntos,
 * sin penalizacion, sin evento) y, para Lima, cuando: su dia termina a las 05:00 UTC, la misma hora del barrido de antes.
 */
@Service
public class ExpiracionDeRegistrosService implements ExpirarRegistrosVencidosUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpiracionDeRegistrosService.class);

    /** Participantes por pagina. Cada uno trae una fecha: la pagina entera pesa menos que un dia de una persona. */
    static final int TAMANO_LOTE = 200;
    /**
     * El default de {@code participantes_programa.timezone}. Solo para quien no tiene fila de programa, que no deberia
     * existir: {@code registros_habito} la exige por FK.
     */
    static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final ConsultarPendientesVencidosPort pendientesPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final SaveRegistroHabitoPort saveRegistroPort;
    private final ConsultarZonasDeParticipantesPort zonasPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final Clock clock;
    /**
     * REQUIRES_NEW: cada fila en su transaccion, aislada de las demas (C-6). Se puede porque el barrido no tiene
     * ninguna transaccion abierta ni filas bloqueadas; en {@code RegistroService.completar} no (ahi el registro ya esta
     * bajo bloqueo pesimista de la transaccion en curso, y abrir otra sobre la misma fila es un auto-interbloqueo
     * entre dos conexiones del pool). Venia de {@code RegistroService} con esa misma advertencia.
     */
    private final TransactionTemplate transaccionPropia;

    public ExpiracionDeRegistrosService(ConsultarPendientesVencidosPort pendientesPort,
                                        LoadRegistroHabitoPort loadRegistroPort, SaveRegistroHabitoPort saveRegistroPort,
                                        ConsultarZonasDeParticipantesPort zonasPort,
                                        ConsultarProgresoParticipanteHabitsPort progresoPort, Clock clock,
                                        PlatformTransactionManager transactionManager) {
        this.pendientesPort = pendientesPort;
        this.loadRegistroPort = loadRegistroPort;
        this.saveRegistroPort = saveRegistroPort;
        this.zonasPort = zonasPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    public ResultadoDelBarrido expirarDiasTerminados() {
        Instant ahora = clock.now();
        LocalDate tope = CorteDeExpiracion.fechaMasTardiaPosible(ahora);
        ResultadoDelBarrido total = ResultadoDelBarrido.VACIO;
        UserId despuesDe = null;
        List<PendientesDeParticipante> pagina;
        do {
            pagina = pendientesPort.pagina(tope, despuesDe, TAMANO_LOTE);
            total = total.mas(procesar(pagina, ahora));
            despuesDe = pagina.isEmpty() ? despuesDe : pagina.getLast().participanteId();
        } while (pagina.size() == TAMANO_LOTE);
        return total;
    }

    private ResultadoDelBarrido procesar(List<PendientesDeParticipante> pagina, Instant ahora) {
        Map<UserId, ZoneId> zonas = zonasEnLote(pagina);
        ResultadoDelBarrido resultado = ResultadoDelBarrido.VACIO;
        for (PendientesDeParticipante pendientes : pagina) {
            resultado = resultado.mas(expirarDe(pendientes, zonas.get(pendientes.participanteId()), ahora));
        }
        return resultado;
    }

    /**
     * UNA consulta por pagina. Si falla —una zona corrupta revienta el lote entero al mapearse—, se pregunta de a uno:
     * asi falla solo quien la tiene rota, no la pagina.
     */
    private Map<UserId, ZoneId> zonasEnLote(List<PendientesDeParticipante> pagina) {
        if (pagina.isEmpty()) {
            return Map.of();
        }
        try {
            return zonasPort.deProgramasActivados(pagina.stream().map(PendientesDeParticipante::participanteId).toList());
        } catch (RuntimeException ex) {
            log.warn("[habits] no se pudieron leer en lote las zonas de {} participante(s); se leen de a uno: {}",
                    pagina.size(), ex.toString());
            return Map.of();
        }
    }

    /** Un participante que falla (zona rota, una lectura que revienta) queda para la proxima hora; los demas siguen. */
    private ResultadoDelBarrido expirarDe(PendientesDeParticipante pendientes, ZoneId zonaDelLote, Instant ahora) {
        UserId participante = pendientes.participanteId();
        try {
            ZoneId zona = zonaDelLote != null ? zonaDelLote : zonaDe(participante);
            CorteDeExpiracion corte = CorteDeExpiracion.para(zona, ahora);
            if (!corte.yaTermino(pendientes.masVieja())) {
                return new ResultadoDelBarrido(1, 0, 0);
            }
            return expirarLoQueVencio(participante, corte, ahora);
        } catch (RuntimeException ex) {
            log.warn("[habits] no se pudo expirar lo vencido de {} (se reintenta la proxima hora): {}", participante,
                    ex.toString());
            return new ResultadoDelBarrido(1, 0, 1);
        }
    }

    /** Quien no activo su programa no viene en el lote: su zona se lee sola (es la excepcion, no la regla). */
    private ZoneId zonaDe(UserId participante) {
        return progresoPort.deParticipante(participante)
                .map(progreso -> ZoneId.of(progreso.timezone()))
                .orElse(ZONA_POR_DEFECTO);
    }

    /** C-6: cada fila en SU transaccion; la que falla queda {@code PENDIENTE} para la proxima corrida. */
    private ResultadoDelBarrido expirarLoQueVencio(UserId participante, CorteDeExpiracion corte, Instant ahora) {
        int expirados = 0;
        int fallidos = 0;
        for (RegistroHabito registro : loadRegistroPort.pendientesDeParticipanteAnterioresA(participante,
                corte.hoyEnSuZona())) {
            if (!corte.vence(registro)) {
                continue;
            }
            try {
                expirarEnTransaccionPropia(registro, ahora);
                expirados++;
            } catch (RuntimeException ex) {
                fallidos++;
                log.warn("[habits] no se pudo expirar el registro {} (del {}): {}", registro.id(),
                        registro.fechaEjecucion(), ex.toString());
            }
        }
        return new ResultadoDelBarrido(1, expirados, fallidos);
    }

    private void expirarEnTransaccionPropia(RegistroHabito registro, Instant ahora) {
        transaccionPropia.executeWithoutResult(status -> {
            registro.expirar(ahora);
            saveRegistroPort.save(registro);
        });
    }
}
