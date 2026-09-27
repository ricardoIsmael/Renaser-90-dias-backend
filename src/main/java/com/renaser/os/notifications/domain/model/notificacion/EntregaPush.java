package com.renaser.os.notifications.domain.model.notificacion;

import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;

import java.util.List;

/**
 * A que dispositivos se empuja una notificacion ADEMAS de guardarla en la bandeja (D-184). La
 * bandeja es siempre el contrato; esto decide solo el empujon al telefono o al navegador.
 *
 * <p>Existe porque "bandeja y push" dejo de ser la unica forma correcta de avisar:
 * <ul>
 *   <li>{@link #NINGUNO}: logros de todos los dias (un habito completado) y avisos que la persona
 *   apago. Quedan en la bandeja; el telefono no suena.</li>
 *   <li>{@link #SOLO_NAVEGADOR}: el telefono ya tiene su propia alarma local para esto (la app
 *   programa el recordatorio de un habito en el dispositivo). El push a Android/iOS seria el mismo
 *   aviso dos veces; el navegador no tiene alarma local, asi que para el sigue siendo el unico
 *   aviso.</li>
 *   <li>{@link #TODOS}: el comportamiento de siempre.</li>
 * </ul>
 */
public enum EntregaPush {

    TODOS,
    SOLO_NAVEGADOR,
    NINGUNO;

    /** Los tokens a los que corresponde empujar, de entre todos los de la persona. */
    public List<TokenPush> filtrar(List<TokenPush> tokens) {
        return switch (this) {
            case TODOS -> tokens;
            case SOLO_NAVEGADOR -> tokens.stream().filter(t -> t.plataforma() == PlataformaPush.WEB).toList();
            case NINGUNO -> List.of();
        };
    }
}
