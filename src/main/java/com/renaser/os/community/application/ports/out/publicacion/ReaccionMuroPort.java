package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Puerto unico para leer y escribir `reacciones_muro` — la fila es tan chica (PK
 * compuesta, sin identidad propia) que separar Load/Save no aporta claridad extra. */
public interface ReaccionMuroPort {

    Optional<TipoReaccion> deUsuario(PublicacionId publicacionId, UserId usuarioId);

    /**
     * Quien reacciono a UNA publicacion, mas reciente primero — a diferencia de
     * {@link #contarPorTipo}, que solo agrega. Consumido por {@code ConsultarReaccionesUseCase}
     * (modal "Reacciones del post"). No hace falta version en lote-de-publicaciones (a
     * diferencia de {@link #contarPorTipoDeVarias}): este listado se pide de a una publicacion
     * por vez, nunca para una pagina entera del feed.
     */
    List<ReaccionMuro> listarDe(PublicacionId publicacionId);

    /** Conteo agregado por tipo, listo para {@code Map.of(ME_GUSTA, n, NO_ME_GUSTA, m)}. */
    Map<TipoReaccion, Integer> contarPorTipo(PublicacionId publicacionId);

    /**
     * Version en lote de {@link #contarPorTipo} para una pagina entera del feed — misma razon
     * que {@code ConsultarPerfilUsuarioPort.porIds}: una consulta por publicacion multiplica el
     * costo del feed por el tamano de pagina (E-80).
     *
     * <p>Una publicacion sin ninguna reaccion <b>no aparece</b> en el mapa devuelto: quien
     * consume tiene que tratar la ausencia como cero, igual que con el mapa de una sola
     * publicacion, donde el tipo sin reacciones tampoco tiene entrada.
     */
    Map<PublicacionId, Map<TipoReaccion, Integer>> contarPorTipoDeVarias(Collection<PublicacionId> publicacionIds);

    /**
     * Version en lote de {@link #deUsuario} para una pagina entera, con un unico usuario: es
     * siempre "que reacciono <b>quien esta mirando</b>" sobre las N publicaciones que ve.
     * Sin entrada en el mapa = no reacciono a esa publicacion.
     */
    Map<PublicacionId, TipoReaccion> deUsuarioEnVarias(Collection<PublicacionId> publicacionIds, UserId usuarioId);

    void upsert(PublicacionId publicacionId, UserId usuarioId, TipoReaccion tipo);

    void eliminar(PublicacionId publicacionId, UserId usuarioId);
}
