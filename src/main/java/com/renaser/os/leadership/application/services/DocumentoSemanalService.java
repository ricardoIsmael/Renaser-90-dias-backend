package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarDocumentoSemanalPort;
import com.renaser.os.leadership.application.ports.out.LeerDocumentoSemanalPort;
import com.renaser.os.leadership.application.ports.out.LeerDocumentoSemanalPort.Buscado;
import com.renaser.os.leadership.application.ports.out.RenderizarDocumentoPort;
import com.renaser.os.leadership.domain.model.documento.DatosDelDocumento;
import com.renaser.os.leadership.domain.model.documento.DescargaDeDocumento;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal.Contenido;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Generar, guardar y entregar el documento de una semana (spec 056, FR-039 y FR-040): reúne los datos ({@link FuenteDeDocumentos}, que ya
 * autorizó), saca su huella, y si ese mismo documento ya existe lo REUTILIZA; si no, lo dibuja, lo sube al almacenamiento privado, lo registra
 * y firma un enlace de lectura que vence a los 10 minutos. Cada pedido —generado o reutilizado— deja su fila en el registro de descargas.
 *
 * <p>Sin {@code @Transactional}: subir el archivo no retiene una conexión, y cada escritura a la base es una sola sentencia. El archivo se sube
 * ANTES de registrarlo, así una fila nunca apunta a un archivo que no está; si la subida falla no queda ni el documento ni la descarga. Dos
 * pedidos a la vez suben el mismo archivo a la misma ruta y comparten la fila ({@link GuardarDocumentoSemanalPort#insertar}).
 */
@Service
public class DocumentoSemanalService implements GenerarDocumentoSemanalUseCase {

    /** El enlace al documento dura lo que dice la spec (FR-039). */
    static final Duration VIGENCIA_DEL_ENLACE = Duration.ofMinutes(10);
    static final String TIPO_DE_CONTENIDO = "text/html; charset=utf-8";

    private final FuenteDeDocumentos fuente;
    private final LeerDocumentoSemanalPort leer;
    private final GuardarDocumentoSemanalPort guardar;
    private final RenderizarDocumentoPort plantilla;
    private final AlmacenamientoPort almacenamiento;
    private final IdGenerator ids;
    private final Clock clock;

    DocumentoSemanalService(FuenteDeDocumentos fuente, LeerDocumentoSemanalPort leer, GuardarDocumentoSemanalPort guardar,
                            RenderizarDocumentoPort plantilla, AlmacenamientoPort almacenamiento, IdGenerator ids, Clock clock) {
        this.fuente = fuente;
        this.leer = leer;
        this.guardar = guardar;
        this.plantilla = plantilla;
        this.almacenamiento = almacenamiento;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public DocumentoEntregado delAprendiz(PedidoDeDocumento pedido) {
        return entregar(pedido.actorId(), fuente.delAprendiz(pedido.actorId(), pedido.personaId(), pedido.semanaHasta()));
    }

    @Override
    public DocumentoEntregado delMentor(PedidoDeDocumento pedido) {
        return entregar(pedido.actorId(), fuente.delMentor(pedido.actorId(), pedido.personaId(), pedido.semanaHasta()));
    }

    @Override
    public DocumentoEntregado delMes(PedidoDeDocumentoDelMes pedido) {
        return entregar(pedido.actorId(), fuente.delMes(pedido.actorId(), pedido.mes()));
    }

    private DocumentoEntregado entregar(UserId actor, DocumentoArmado armado) {
        DatosDelDocumento datos = armado.datos();
        Contenido contenido = new Contenido(armado.personaId(), datos.tipo(), datos.desde(), datos.hasta(), DocumentoSemanal.huellaDe(datos), actor);
        Conseguido conseguido = conseguir(contenido, datos);
        URI url = almacenamiento.firmarLectura(conseguido.documento().ruta(), VIGENCIA_DEL_ENLACE);
        Instant ahora = clock.now();
        guardar.registrarDescarga(new DescargaDeDocumento(ids.newId(), conseguido.documento().id(), actor, conseguido.reutilizado(), ahora));
        return new DocumentoEntregado(url, ahora.plus(VIGENCIA_DEL_ENLACE), conseguido.reutilizado(), datos.desde(), datos.hasta());
    }

    /** El documento de esos datos: el que ya estaba, o uno nuevo. */
    private Conseguido conseguir(Contenido contenido, DatosDelDocumento datos) {
        Optional<DocumentoSemanal> yaGenerado = leer.existente(new Buscado(contenido.personaId(), contenido.tipo(), contenido.periodoInicio(),
                contenido.huella()));
        return yaGenerado.map(existente -> new Conseguido(existente, true)).orElseGet(() -> generar(contenido, datos));
    }

    /**
     * Dibuja, sube y registra. Si otro pedido registró primero el mismo documento, se queda con el de él: mismos datos, misma ruta, y este pedido
     * cuenta como una reutilización.
     */
    private Conseguido generar(Contenido contenido, DatosDelDocumento datos) {
        DocumentoSemanal nuevo = DocumentoSemanal.generar(ids.newId(), contenido, clock);
        String pagina = plantilla.renderizar(datos, nuevo.generadoEn());
        almacenamiento.subir(nuevo.ruta(), pagina.getBytes(StandardCharsets.UTF_8), TIPO_DE_CONTENIDO);
        DocumentoSemanal guardado = guardar.insertar(nuevo);
        return new Conseguido(guardado, !guardado.id().equals(nuevo.id()));
    }

    private record Conseguido(DocumentoSemanal documento, boolean reutilizado) {
    }
}
