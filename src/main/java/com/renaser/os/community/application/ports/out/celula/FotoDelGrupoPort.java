package com.renaser.os.community.application.ports.out.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.FotoDelGrupo;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Qué foto propia tiene cada grupo (D-212, {@code celulas.foto_ruta} y {@code foto_cambiada_en}, V75). Solo
 * la referencia: los bytes viven en el almacenamiento.
 */
public interface FotoDelGrupoPort {

    Optional<FotoDelGrupo> deGrupo(CelulaId grupo);

    /** Los que tienen foto propia; los demás no figuran. Una consulta. */
    Map<CelulaId, FotoDelGrupo> deGrupos(Collection<CelulaId> grupos);

    /**
     * Pone la foto nueva y devuelve la ruta de la que reemplazó, en la misma sentencia: así se borra
     * exactamente el objeto que dejó de usarse aunque dos personas cambien la foto a la vez.
     */
    Optional<String> reemplazar(CelulaId grupo, FotoDelGrupo nueva);

    /** El grupo vuelve a la foto de Renaser; devuelve la ruta de la que tenía, si tenía. */
    Optional<String> quitar(CelulaId grupo);
}
