package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.user.PurgeExpiredAccountsUseCase;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.PlazoDeGracia;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Borra para siempre las cuentas cerradas cuya gracia vencio (D-243). Cumple la regla 02:
 * <ul>
 *   <li><b>Derivado, no incrementado:</b> la condicion es una fecha ({@code baja_solicitada_en + gracia
 *       <= ahora}); una corrida que no ocurre se pone al dia en la siguiente.</li>
 *   <li><b>Pagina</b> de a {@link #TAMANO_LOTE} por id, sin cargar el padron.</li>
 *   <li><b>Una cuenta a la vez</b>, cada una en su propia transaccion ({@link BorradoDefinitivoService})
 *       y con su try/catch: una que falla no frena a las demas y se reintenta la hora siguiente.</li>
 * </ul>
 * La gracia se mide en instantes (30 x 24 h desde el cierre), no en dias calendario de nadie: no
 * depende de la zona de la persona.
 */
@Service
class BarridoDeCuentasCerradasService implements PurgeExpiredAccountsUseCase {

    private static final Logger log = LoggerFactory.getLogger(BarridoDeCuentasCerradasService.class);

    static final int TAMANO_LOTE = 50;

    private final LoadUserPort loadUserPort;
    private final BorradoDefinitivoService borrado;
    private final Clock clock;
    private final PlazoDeGracia plazo;

    BarridoDeCuentasCerradasService(LoadUserPort loadUserPort, BorradoDefinitivoService borrado, Clock clock,
                                    PlazoDeGracia plazo) {
        this.loadUserPort = loadUserPort;
        this.borrado = borrado;
        this.clock = clock;
        this.plazo = plazo;
    }

    @Override
    public ResultadoPurga purgeExpired() {
        Instant corte = plazo.corteParaBorrar(clock.now());
        int purgadas = 0;
        int fallidas = 0;
        UserId ultimo = null;
        List<UserId> lote = loadUserPort.cerradasVencidas(corte, null, TAMANO_LOTE);
        while (!lote.isEmpty()) {
            for (UserId cuenta : lote) {
                if (borrarSinFrenar(cuenta)) {
                    purgadas++;
                } else {
                    fallidas++;
                }
                ultimo = cuenta;
            }
            lote = loadUserPort.cerradasVencidas(corte, ultimo, TAMANO_LOTE);
        }
        return new ResultadoPurga(purgadas, fallidas);
    }

    private boolean borrarSinFrenar(UserId cuenta) {
        try {
            borrado.borrar(cuenta, RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null);
            return true;
        } catch (RuntimeException e) {
            log.error("[users.BarridoDeCuentasCerradas] fallo borrando la cuenta {}", cuenta, e);
            return false;
        }
    }
}
