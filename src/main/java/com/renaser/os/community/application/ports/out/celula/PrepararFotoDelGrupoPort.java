package com.renaser.os.community.application.ports.out.celula;

import com.renaser.os.community.domain.model.celula.FotoSubidaDelGrupo;

/**
 * Deja la foto que alguien subió lista para guardar (D-212): un JPEG cuadrado de tamaño fijo, recortado al
 * centro. Leerla de verdad es lo que prueba que es una imagen; reescribirla deja afuera los metadatos
 * (el EXIF puede traer la ubicación del teléfono).
 */
public interface PrepararFotoDelGrupoPort {

    /**
     * @throws IllegalArgumentException si no se puede leer como imagen o sus medidas son desmedidas
     */
    byte[] comoJpegCuadrado(FotoSubidaDelGrupo foto);
}
