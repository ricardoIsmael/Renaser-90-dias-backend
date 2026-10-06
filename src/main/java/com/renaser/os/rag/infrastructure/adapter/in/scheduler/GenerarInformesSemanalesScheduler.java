package com.renaser.os.rag.infrastructure.adapter.in.scheduler;

import com.renaser.os.rag.application.ports.in.espejosombra.GenerarInformesSemanalesUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido del Espejo Sombra. Corre CADA HORA y no un lunes a una hora UTC fija (E-560): el corte semanal es el
 * domingo 22:00 en la zona de cada participante, y esa hora cae en un instante UTC distinto según la zona (regla
 * 02 §1, la familia de E-91). El dominio ({@code SemanaDelInforme}) decide a quién le toca; para Lima el instante
 * es el mismo de antes (lunes 03:00 UTC = domingo 22:00 en Lima).
 *
 * <p>Cadencia semanal, no por aniversario (docs/MODULO_RAG.md §6, punto 4): la opción más simple. Si el negocio
 * pide aniversario, el cambio queda en esta regla.
 *
 * <p><b>C-5 ({@code docs/informes/auditoria-fixes/C-5.md}): {@code @SchedulerLock}, no opcional.</b>
 * {@code EspejoSombraService.generar} es un check-then-act sin lock protegido solo por la {@code UNIQUE
 * (participante_id, semana_inicio)}: sin cerrojo, dos instancias dispararían dos veces la llamada a IA por
 * participante. {@code lockAtMostFor} de 6 horas: peor caso estimado (NO medido) con un proveedor de IA real de
 * 45 s por llamada; ahora que el cron es horario, un lock tomado por un proceso muerto cuesta como mucho seis
 * corridas, y el margen de puesta al día de {@code SemanaDelInforme} es de un día.
 */
@Component
public class GenerarInformesSemanalesScheduler {

    private final GenerarInformesSemanalesUseCase generarInformes;

    public GenerarInformesSemanalesScheduler(GenerarInformesSemanalesUseCase generarInformes) {
        this.generarInformes = generarInformes;
    }

    @Scheduled(cron = "${renaser.scheduling.informes-semanales.cron:0 0 * * * *}", zone = "UTC")
    @SchedulerLock(name = "rag-generar-informes-semanales",
            lockAtMostFor = "${renaser.scheduling.shedlock.rag-generar-informes-semanales.lock-at-most-for:PT6H}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.rag-generar-informes-semanales.lock-at-least-for:PT1M}")
    public void generarInformesQueTocan() {
        generarInformes.generarLosQueTocan();
    }
}
