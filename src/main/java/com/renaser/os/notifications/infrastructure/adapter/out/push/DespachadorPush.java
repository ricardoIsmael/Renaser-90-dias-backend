package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.application.ports.out.push.ResultadoEnvioPush;
import com.renaser.os.notifications.application.ports.out.push.TransportePush;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reparte cada token al transporte de su plataforma.
 *
 * <p>Es el único {@link PushPort} del sistema. Antes esa responsabilidad vivía dentro del
 * adaptador web, que filtraba por {@code plataforma == WEB} y descartaba en silencio los tokens
 * de iOS y Android: un aprendiz con la app instalada aparecía como "notificado" sin que nada
 * hubiera salido. Ahora un token sin transporte devuelve {@code SIN_TRANSPORTE} y queda visible.
 *
 * <p>Un transporte que falla no afecta a los demás: cada token se intenta por separado y una
 * excepción inesperada se traduce a fallo temporal en vez de cortar el lote.
 */
@Component
class DespachadorPush implements PushPort {

    private static final Logger log = LoggerFactory.getLogger(DespachadorPush.class);

    private final Map<PlataformaPush, TransportePush> porPlataforma = new EnumMap<>(PlataformaPush.class);

    /**
     * El mapa se arma UNA vez, al arrancar, y ahí mismo se detecta la ambigüedad. Que dos
     * transportes se peleen la misma plataforma es un error de configuración: resolverlo en
     * caliente eligiendo "el primero" haría que el canal efectivo dependiera del orden en que
     * Spring construyó los beans, y eso cambia sin que nadie lo toque.
     */
    DespachadorPush(List<TransportePush> transportes) {
        for (PlataformaPush plataforma : PlataformaPush.values()) {
            List<TransportePush> candidatos = transportes.stream()
                    .filter(t -> t.atiende(plataforma))
                    .toList();
            if (candidatos.size() > 1) {
                throw new IllegalStateException("Dos transportes atienden " + plataforma + ": "
                        + candidatos.stream().map(TransportePush::nombre).toList());
            }
            if (candidatos.size() == 1) {
                porPlataforma.put(plataforma, candidatos.getFirst());
            }
        }
        log.info("[notifications.DespachadorPush] transportes activos: {}", porPlataforma.keySet());
    }

    @Override
    public List<ResultadoEnvioPush> enviar(List<TokenPush> tokens, String titulo, String cuerpo, String rutaApp) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<ResultadoEnvioPush> resultados = new ArrayList<>(tokens.size());
        for (TokenPush token : tokens) {
            TransportePush transporte = porPlataforma.get(token.plataforma());
            if (transporte == null) {
                resultados.add(ResultadoEnvioPush.sinTransporte(token.id(), token.plataforma().name()));
                continue;
            }
            try {
                resultados.add(transporte.entregar(token, titulo, cuerpo, rutaApp));
            } catch (RuntimeException e) {
                // El contrato dice que un transporte no lanza. Si igual lo hace, se trata como
                // fallo temporal: es preferible reintentar de mas que perder el aviso en silencio.
                log.warn("[notifications.DespachadorPush] el transporte {} lanzo: {}",
                        token.plataforma(), e.getMessage());
                resultados.add(ResultadoEnvioPush.temporal(token.id(), e.getMessage()));
            }
        }
        return resultados;
    }
}
