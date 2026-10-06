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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Clock clock;

    public RecordatorioService(LoadEventoPort loadEventoPort, LoadExcepcionPort loadExcepcionPort,
                                SaveRecordatorioPort saveRecordatorioPort, AudienciaDelEventoService audiencia,
                                Clock clock) {
        this.loadEventoPort = loadEventoPort;
        this.loadExcepcionPort = loadExcepcionPort;
        this.saveRecordatorioPort = saveRecordatorioPort;
        this.audiencia = audiencia;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int generar(Instant ahora) {
        Instant hastaMax = ahora.plusSeconds((ReglaRecordatorio.MAX_DIAS_ANTES + 1) * 86_400L);
        Instant desdeAnuncio = ahora.minusSeconds(ANUNCIO_VALIDEZ_HORAS * 3600);
        List<Evento> eventos = loadEventoPort.candidatosParaRecordatorios(ahora, hastaMax, desdeAnuncio);

        int creados = 0;
        for (Evento evento : eventos) {
            creados += generarParaEvento(evento, ahora);
        }
        return creados;
    }

    private int generarParaEvento(Evento evento, Instant ahora) {
        List<ReglaRecordatorio> reglas = evento.reglasRecordatorioEfectivas();
        List<Excepcion> excepciones = loadExcepcionPort.porEvento(evento.id());
        int diasVentana = CalculadoraRecordatorios.diasDeVentana(reglas, VENTANA_DIAS_DEFECTO);
        Instant hasta = ahora.plusSeconds(diasVentana * 86_400L);
        List<Ocurrencia> ocurrencias = reglas.isEmpty() ? List.of()
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
            List<InstanteRecordatorio> instantes = CalculadoraRecordatorios.instantesPara(occ.inicioOcurrencia(),
                    reglas, evento.timezone(), ahora);
            if (instantes.isEmpty()) {
                continue;
            }
            List<RecordatorioEvento> filas = new ArrayList<>();
            for (UserId u : usuarios) {
                for (InstanteRecordatorio instante : instantes) {
                    filas.add(RecordatorioEvento.programar(evento.id(), instante.inicioOcurrencia(), u,
                            instante.enviarEn(), clock));
                }
            }
            creados += saveRecordatorioPort.encolarSiFalta(filas);
        }
        return creados;
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
