package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.onboarding.api.AvisoDeCaja;
import com.renaser.os.onboarding.api.AvisoDeCajaEvent;
import com.renaser.os.onboarding.application.ports.in.caja.BarrerCajasUseCase;
import com.renaser.os.onboarding.application.ports.out.caja.PasosDeCajaPort;
import com.renaser.os.onboarding.application.services.caja.CajasDelPadron.CajaConFicha;
import com.renaser.os.onboarding.application.services.caja.CajasDelPadron.PaginaDelPadron;
import com.renaser.os.onboarding.domain.model.caja.AvisosDeCaja;
import com.renaser.os.onboarding.domain.model.caja.CajaRenaser;
import com.renaser.os.onboarding.domain.model.caja.TipoPasoCaja;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * El barrido horario de la caja (D-219, spec §5, regla 02): detecta quién pasó sola a «en revisión» y quién
 * tiene la caja en camino hace 3 o 5 días sin confirmar, y avisa.
 *
 * <ul>
 *   <li><b>Derivado, no incrementado:</b> todo sale de las fechas y los pasos; correrlo tarde se pone al día
 *       solo.</li>
 *   <li><b>Idempotente:</b> cada aviso deja su marca con {@code INSERT … ON CONFLICT DO NOTHING}, y solo la
 *       corrida que la inserta publica el aviso — marca y aviso en la MISMA transacción (outbox). Además la
 *       notificación lleva un {@code origen_evento_id} fijo (C-7).</li>
 *   <li><b>Paginado</b> ({@link CajasDelPadron#TAMANO_LOTE}), cada persona en su propia transacción corta y con
 *       su propio {@code try/catch}: una que falla no frena a las demás.</li>
 * </ul>
 *
 * <p><b>El aviso de «en revisión» automático está apagado salvo {@code CAJA_AVISAR_POR_REVISAR=true}</b>
 * ({@code renaser.caja.avisar-por-revisar}). El padrón de hoy ya pasó el Día 8 y muchos recibieron la caja
 * antes de la app: prendido sin más, la primera corrida le diría «tu caja está en revisión» a quien ya la
 * tiene. Se prende después de que el Admin marque «Ya se envió antes» a esos. Los plazos de 3 y 5 días no
 * dependen de esto: solo aplican a cajas enviadas desde la app.
 */
@Service
public class BarridoDeCajasService implements BarrerCajasUseCase {

    private static final Logger log = LoggerFactory.getLogger(BarridoDeCajasService.class);

    private final CajasDelPadron padron;
    private final PasosDeCajaPort pasos;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccion;
    private final boolean avisarPorRevisar;

    BarridoDeCajasService(CajasDelPadron padron, PasosDeCajaPort pasos, ApplicationEventPublisher eventos,
                          PlatformTransactionManager transactionManager,
                          @Value("${renaser.caja.avisar-por-revisar:false}") boolean avisarPorRevisar) {
        this.padron = padron;
        this.pasos = pasos;
        this.eventos = eventos;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.avisarPorRevisar = avisarPorRevisar;
    }

    @Override
    public ResultadoDelBarrido barrer() {
        int evaluadas = 0;
        int avisos = 0;
        int fallidas = 0;
        for (int offset = 0; ; offset += CajasDelPadron.TAMANO_LOTE) {
            PaginaDelPadron pagina = padron.pagina(offset);
            for (CajaConFicha leida : pagina.cajas()) {
                evaluadas++;
                try {
                    avisos += avisar(leida.caja());
                } catch (RuntimeException e) {
                    fallidas++;
                    log.warn("[onboarding.caja] el barrido fallo con {}: {}", leida.caja().aprendizId(), e.toString());
                }
            }
            if (pagina.ultima()) {
                return new ResultadoDelBarrido(evaluadas, avisos, fallidas);
            }
        }
    }

    private int avisar(CajaRenaser caja) {
        int dados = 0;
        for (TipoPasoCaja marca : AvisosDeCaja.debidos(caja)) {
            if (marca == TipoPasoCaja.AVISO_EN_REVISION && !avisarPorRevisar) {
                continue;
            }
            Boolean dado = transaccion.execute(status -> marcarYAvisar(caja, marca));
            dados += Boolean.TRUE.equals(dado) ? 1 : 0;
        }
        return dados;
    }

    private boolean marcarYAvisar(CajaRenaser caja, TipoPasoCaja marca) {
        if (!pasos.registrarSiFalta(AvisosDeCaja.marca(caja, marca))) {
            return false;
        }
        eventos.publishEvent(AvisoDeCajaEvent.de(caja.aprendizId(), caja.envioActual(), avisoDe(marca)));
        return true;
    }

    private static AvisoDeCaja avisoDe(TipoPasoCaja marca) {
        return switch (marca) {
            case AVISO_EN_REVISION -> AvisoDeCaja.EN_REVISION;
            case AVISO_RECORDATORIO -> AvisoDeCaja.RECORDATORIO;
            case AVISO_SIN_CONFIRMAR -> AvisoDeCaja.SIN_CONFIRMAR;
            default -> throw new IllegalArgumentException("No es una marca de aviso: " + List.of(marca));
        };
    }
}
