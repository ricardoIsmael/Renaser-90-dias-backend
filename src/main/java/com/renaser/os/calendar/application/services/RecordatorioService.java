package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.recordatorio.GenerarRecordatoriosUseCase;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.recordatorio.CalculadoraRecordatorios;
import com.renaser.os.calendar.domain.model.recordatorio.InstanteRecordatorio;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Puerto directo de {@code generar()} de {@code reminderService.ts} (repo viejo): deja en la cola los
 * avisos que faltan. {@code despachar()} vive en {@link DespachoDeRecordatoriosService}.
 *
 * <p><b>Corregido 2026-09-27 (E-360).</b> Esta clase implementaba tambien {@code despachar()}, en una
 * sola transaccion de hasta 500 filas. Paso a su propia clase al partirlo en lotes: con los dos casos de
 * uso juntos la clase pasaba el techo de 300 lineas.
 *
 * <p>La audiencia (a quien le llegan los avisos) vive desde el 2026-10-06 en {@link AudienciaDelEventoService}:
 * la comparte con la hoja «Quien respondio» (D-256).
 *
 * <p><b>Cada evento en su transaccion (issue #86, 2026-11-05).</b> {@code generar()} era un {@code @Transactional} unico: un evento
 * que fallaba al encolar revertia el barrido entero y no quedaba ningun aviso de ningun evento. Ahora cada evento va en la suya y un
 * fallo se anota y no detiene a los demas (regla 02 §4).
 */
@Service
public class RecordatorioService implements GenerarRecordatoriosUseCase {

    /** VENTANA_DIAS del repo viejo: suelo de la ventana de generacion. */
    private static final int VENTANA_DIAS_DEFECTO = 3;
    /** ANUNCIO_VALIDEZ_MS del repo viejo: 24h desde creado el evento. */
    private static final long ANUNCIO_VALIDEZ_HORAS = 24;

    private final LoadEventoPort loadEventoPort;
    private final LoadExcepcionPort loadExcepcionPort;
    private final SaveRecordatorioPort saveRecordatorioPort;
    private final AudienciaDelEventoService audiencia;
    /** Spec 041: los avisos de mensaje (chat y WhatsApp) viajan por la misma cola, junto a los del telefono. */
    private final PlanDeMensajesDelEvento mensajes;
    private final Clock clock;
    private final TransactionOperations transaccionDelEvento;

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);

    @Autowired
    public RecordatorioService(LoadEventoPort loadEventoPort, LoadExcepcionPort loadExcepcionPort,
                                SaveRecordatorioPort saveRecordatorioPort, AudienciaDelEventoService audiencia,
                                PlanDeMensajesDelEvento mensajes, Clock clock, PlatformTransactionManager transactionManager) {
        this(loadEventoPort, loadExcepcionPort, saveRecordatorioPort, audiencia, mensajes, clock,
                new TransactionTemplate(transactionManager));
    }

    /** Para las pruebas sin Spring: {@code TransactionOperations.withoutTransaction()}. */
    RecordatorioService(LoadEventoPort loadEventoPort, LoadExcepcionPort loadExcepcionPort,
                        SaveRecordatorioPort saveRecordatorioPort, AudienciaDelEventoService audiencia,
                        PlanDeMensajesDelEvento mensajes, Clock clock, TransactionOperations transaccionDelEvento) {
        this.transaccionDelEvento = transaccionDelEvento;
        this.mensajes = mensajes;
        this.loadEventoPort = loadEventoPort;
        this.loadExcepcionPort = loadExcepcionPort;
        this.saveRecordatorioPort = saveRecordatorioPort;
        this.audiencia = audiencia;
        this.clock = clock;
    }

    @Override
    public int generar(Instant ahora) {
        Instant hastaMax = ahora.plusSeconds((ReglaRecordatorio.MAX_DIAS_ANTES + 1) * 86_400L);
        Instant desdeAnuncio = ahora.minusSeconds(ANUNCIO_VALIDEZ_HORAS * 3600);
        List<Evento> eventos = loadEventoPort.candidatosParaRecordatorios(ahora, hastaMax, desdeAnuncio);

        int creados = 0;
        for (Evento evento : eventos) {
            try {
                Integer delEvento = transaccionDelEvento.execute(status -> generarParaEvento(evento, ahora));
                creados += delEvento == null ? 0 : delEvento;
            } catch (RuntimeException e) {
                log.error("[RecordatorioService] no se pudieron encolar los avisos del evento {}; sigo con los demas", evento.id().value(), e);
            }
        }
        return creados;
    }

    private int generarParaEvento(Evento evento, Instant ahora) {
        List<ReglaRecordatorio> reglas = evento.reglasRecordatorioEfectivas();
        List<Excepcion> excepciones = loadExcepcionPort.porEvento(evento.id());
        int diasVentana = mensajes.diasDeVentana(CalculadoraRecordatorios.diasDeVentana(reglas, VENTANA_DIAS_DEFECTO));
        Instant hasta = ahora.plusSeconds(diasVentana * 86_400L);
        // Los avisos de mensaje (spec 041) no dependen de las reglas del telefono: aunque el evento no avise al telefono, sigue habiendo ocurrencias.
        List<Ocurrencia> ocurrencias = reglas.isEmpty() && !mensajes.hayAvisos() ? List.of()
                : ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(), evento.timezone(),
                        evento.recurrencia(), ahora, hasta, excepciones);

        if (ocurrencias.isEmpty() && !evento.notificarAlCrear()) {
            return 0;
        }
        List<UserId> usuarios = audiencia.destinatarios(evento);
        if (usuarios.isEmpty()) {
            return 0;
        }

        int creados = anunciar(evento, usuarios, ahora);
        if (ocurrencias.isEmpty()) {
            return creados;
        }

        // D-189: ya NO se deja afuera a quien dijo "Voy". Si su alarma local lo cubre lo decide
        // `notifications` al entregar (solo si tiene un telefono registrado); quien respondio desde
        // la web no tiene alarma local y necesita estos avisos.
        for (Ocurrencia occ : ocurrencias) {
            creados += saveRecordatorioPort.encolarSiFalta(filasDeLaOcurrencia(evento, occ, reglas, usuarios, ahora));
        }
        return creados;
    }

    /** Primero los avisos del telefono y despues los de mensaje: si caen en el mismo instante, la cola los junta en UNA fila con las dos marcas (issue #86). */
    private List<RecordatorioEvento> filasDeLaOcurrencia(Evento evento, Ocurrencia occ, List<ReglaRecordatorio> reglas,
                                                         List<UserId> usuarios, Instant ahora) {
        List<InstanteRecordatorio> instantes = CalculadoraRecordatorios.instantesPara(occ.inicioOcurrencia(), reglas,
                evento.timezone(), ahora);
        List<RecordatorioEvento> filas = new ArrayList<>();
        for (UserId u : usuarios) {
            for (InstanteRecordatorio instante : instantes) {
                filas.add(RecordatorioEvento.programar(evento.id(), instante.inicioOcurrencia(), u, instante.enviarEn(),
                        clock));
            }
        }
        Instant ultimoCambio = evento.actualizadoEn() != null ? evento.actualizadoEn() : evento.creadoEn();
        filas.addAll(mensajes.filas(evento, mensajes.instantes(occ.inicioOcurrencia(), ahora, ultimoCambio), usuarios));
        return filas;
    }

    /** anunciar() del repo viejo: clave FIJA (enviarEn = inicioOcurrencia = creadoEn del
     * evento) — dos pasadas del cron no duplican el anuncio. */
    private int anunciar(Evento evento, List<UserId> usuarios, Instant ahora) {
        if (!evento.notificarAlCrear()) {
            return 0;
        }
        if (ahora.toEpochMilli() - evento.creadoEn().toEpochMilli() > ANUNCIO_VALIDEZ_HORAS * 3_600_000L) {
            return 0;
        }
        if (evento.recurrencia() == null && !evento.iniciaEn().isAfter(ahora)) {
            return 0;
        }
        List<RecordatorioEvento> filas = usuarios.stream()
                .map(u -> RecordatorioEvento.programar(evento.id(), evento.creadoEn(), u, evento.creadoEn(), clock))
                .toList();
        return saveRecordatorioPort.encolarSiFalta(filas);
    }
}
