package com.renaser.os.notifications.domain.model.notificacion;

import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;

import java.time.Instant;
import java.util.List;

/**
 * A que dispositivos se empuja una notificacion ADEMAS de guardarla en la bandeja (D-184). La
 * bandeja es siempre el contrato; esto decide solo el empujon al telefono o al navegador.
 *
 * <p>Existe porque "bandeja y push" dejo de ser la unica forma correcta de avisar:
 * <ul>
 *   <li>{@link #NINGUNO}: logros de todos los dias (un habito completado) y avisos que la persona
 *   apago. Quedan en la bandeja; el telefono no suena.</li>
 *   <li>{@link #RESPALDO_DE_ALARMA_LOCAL} (D-217): el telefono deberia tener su propia alarma local
 *   para esto. Va al navegador siempre, y a cada telefono que NO confirmo en las ultimas 26 h que sus
 *   alarmas siguen vivas ({@link TokenPush#tieneAlarmasLocalesVigentes}).</li>
 *   <li>{@link #SOLO_NAVEGADOR}: navegador y nunca telefonos, sin mirar confirmaciones. Desde D-217
 *   ninguna regla de produccion lo elige; se conserva como opcion explicita.</li>
 *   <li>{@link #TODOS}: el comportamiento de siempre.</li>
 * </ul>
 *
 * <p><b>Cambiado 2026-09-28 (D-217).</b> Antes el aviso de inicio de un habito con recordatorio usaba
 * {@link #SOLO_NAVEGADOR}, y este javadoc decia: «el telefono ya tiene su propia alarma local para
 * esto. El push a Android/iOS seria el mismo aviso dos veces». No siempre la tiene: Android borra las
 * alarmas de una app detenida a la fuerza (cerrarla desde recientes en Xiaomi/Samsung/Huawei, ahorro
 * de bateria) hasta que se vuelve a abrir, y con {@code SOLO_NAVEGADOR} ese dia no sonaba nada.
 */
public enum EntregaPush {

    TODOS,
    RESPALDO_DE_ALARMA_LOCAL,
    SOLO_NAVEGADOR,
    NINGUNO;

    /**
     * Los tokens a los que corresponde empujar, de entre todos los de la persona.
     *
     * @param ahora el instante del envio: decide si la confirmacion de alarmas de cada telefono sigue
     *              vigente (solo lo usa {@link #RESPALDO_DE_ALARMA_LOCAL})
     */
    public List<TokenPush> filtrar(List<TokenPush> tokens, Instant ahora) {
        return switch (this) {
            case TODOS -> tokens;
            case RESPALDO_DE_ALARMA_LOCAL -> tokens.stream()
                    .filter(t -> esNavegador(t) || !t.tieneAlarmasLocalesVigentes(ahora))
                    .toList();
            case SOLO_NAVEGADOR -> tokens.stream().filter(EntregaPush::esNavegador).toList();
            case NINGUNO -> List.of();
        };
    }

    private static boolean esNavegador(TokenPush token) {
        return token.plataforma() == PlataformaPush.WEB;
    }
}
