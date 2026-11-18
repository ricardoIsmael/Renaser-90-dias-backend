package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase.DocumentoEntregado;
import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase.PedidoDeDocumento;
import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase.PedidoDeDocumentoDelMes;
import com.renaser.os.leadership.application.ports.out.GuardarDocumentoSemanalPort;
import com.renaser.os.leadership.application.ports.out.LeerDocumentoSemanalPort;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz;
import com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento;
import com.renaser.os.leadership.domain.model.documento.DescargaDeDocumento;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal;
import com.renaser.os.leadership.domain.model.documento.TipoDeDocumento;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El servicio que genera, guarda y entrega un documento (spec 056, FR-039 y FR-040) sobre dobles en memoria: no sabe nada de la ficha ni de
 * Postgres, solo qué hacer con los datos que le da la fuente.
 */
class DocumentoSemanalServiceTest {

    private static final UserId LIDER = UserId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final UserId MENTORA = UserId.of(UUID.fromString("33333333-3333-3333-3333-333333333333"));
    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    /** 03:00 UTC del 18 de noviembre: en Lima todavía es el 17 (regla 02). */
    private static final Instant AHORA = Instant.parse("2026-11-18T03:00:00Z");

    private final List<String> eventos = new ArrayList<>();
    private final Map<String, byte[]> objetos = new HashMap<>();
    private final Map<String, String> tiposSubidos = new HashMap<>();
    private final List<DocumentoSemanal> documentos = new ArrayList<>();
    private final List<DescargaDeDocumento> descargas = new ArrayList<>();
    private final List<Instant> generadosEn = new ArrayList<>();
    private final List<Duration> vigencias = new ArrayList<>();
    private DocumentoArmado aEntregar = new DocumentoArmado(ANA, DatosDePruebaDelDocumento.aprendiz());
    private RuntimeException fallaLaFuente;
    private RuntimeException fallaLaSubida;
    private Instant ahora = AHORA;
    private int siguienteId;
    private DocumentoSemanalService servicio;

    @BeforeEach
    void preparar() {
        FuenteDeDocumentos fuente = new FuenteDeDocumentos() {
            @Override
            public DocumentoArmado delAprendiz(UserId actorId, UUID personaId, LocalDate semanaHasta) {
                return armar();
            }

            @Override
            public DocumentoArmado delMentor(UserId actorId, UUID mentorId, LocalDate semanaHasta) {
                return armar();
            }

            @Override
            public DocumentoArmado delMes(UserId actorId, String mes) {
                return armar();
            }

            private DocumentoArmado armar() {
                if (fallaLaFuente != null) {
                    throw fallaLaFuente;
                }
                return aEntregar;
            }
        };
        LeerDocumentoSemanalPort leer = buscado -> documentos.stream().filter(d -> d.personaId().equals(buscado.personaId())
                && d.tipo() == buscado.tipo() && d.periodoInicio().equals(buscado.periodoInicio()) && d.huella().equals(buscado.huella()))
                .findFirst();
        GuardarDocumentoSemanalPort guardar = new GuardarDocumentoSemanalPort() {
            @Override
            public DocumentoSemanal insertar(DocumentoSemanal documento) {
                documentos.add(documento);
                eventos.add("registrar documento");
                return documento;
            }

            @Override
            public void registrarDescarga(DescargaDeDocumento descarga) {
                descargas.add(descarga);
            }
        };
        AlmacenamientoPort almacenamiento = new AlmacenamientoDeLaPrueba();
        Clock reloj = new Clock() {
            @Override
            public Instant now() {
                return ahora;
            }

            @Override
            public LocalDate today() {
                return LocalDate.of(2026, 11, 18);
            }
        };
        IdGenerator ids = () -> UUID.fromString(String.format("00000000-0000-0000-0000-%012d", ++siguienteId));
        servicio = new DocumentoSemanalService(fuente, leer, guardar, (datos, generadoEn) -> {
            generadosEn.add(generadoEn);
            return "<html>" + datos + "</html>";
        }, almacenamiento, ids, reloj);
    }

    private PedidoDeDocumento pedido(UserId actor) {
        return new PedidoDeDocumento(actor, ANA.value(), null);
    }

    @Test
    @DisplayName("spec 056 FR-039: la primera vez genera el documento, lo sube a reportes/{persona}/{inicio}-{tipo}-{huella8}.html como text/html y entrega su enlace")
    void laPrimeraVezLoGenera() {
        DocumentoEntregado entregado = servicio.delAprendiz(pedido(LIDER));

        String huella = DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz());
        String ruta = "reportes/" + ANA + "/2026-11-14-APRENDIZ-" + huella.substring(0, 8) + ".html";
        assertThat(entregado.reutilizado()).isFalse();
        assertThat(objetos).containsOnlyKeys(ruta);
        assertThat(new String(objetos.get(ruta), StandardCharsets.UTF_8)).startsWith("<html>").contains("Ana Pérez");
        assertThat(tiposSubidos.get(ruta)).isEqualTo("text/html; charset=utf-8");
        assertThat(entregado.url()).isEqualTo(URI.create("https://almacen.test/" + ruta + "?lectura"));
        assertThat(entregado.desde()).isEqualTo(DatosDePruebaDelDocumento.SABADO);
        assertThat(entregado.hasta()).isEqualTo(DatosDePruebaDelDocumento.VIERNES);
        assertThat(documentos).singleElement().satisfies(d -> {
            assertThat(d.ruta()).isEqualTo(ruta);
            assertThat(d.generadoPor()).isEqualTo(LIDER);
            assertThat(d.huella()).isEqualTo(huella);
        });
    }

    @Test
    @DisplayName("spec 056 FR-039: el enlace dura 10 minutos y el documento dice cuándo se generó con el reloj del sistema")
    void elEnlaceDuraDiezMinutos() {
        DocumentoEntregado entregado = servicio.delAprendiz(pedido(LIDER));

        assertThat(vigencias).containsExactly(Duration.ofMinutes(10));
        assertThat(entregado.venceEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(10)));
        assertThat(generadosEn).containsExactly(AHORA);
    }

    @Test
    @DisplayName("spec 056 FR-039 SC-006: pedirlo otra vez sin cambios en los datos entrega el MISMO documento, sin dibujarlo ni subirlo de nuevo, con un enlace nuevo")
    void sinCambiosSeReutiliza() {
        DocumentoEntregado primero = servicio.delAprendiz(pedido(LIDER));
        ahora = AHORA.plus(Duration.ofMinutes(30));

        DocumentoEntregado segundo = servicio.delAprendiz(pedido(MENTORA));

        assertThat(segundo.reutilizado()).isTrue();
        assertThat(segundo.url()).isEqualTo(primero.url());
        assertThat(segundo.venceEn()).as("enlace nuevo: vence 10 minutos después de ESTE pedido").isEqualTo(ahora.plus(Duration.ofMinutes(10)));
        assertThat(documentos).hasSize(1);
        assertThat(objetos).hasSize(1);
        assertThat(generadosEn).as("no se volvió a dibujar").hasSize(1);
        assertThat(eventos).containsExactly("subir", "registrar documento");
    }

    @Test
    @DisplayName("spec 056 FR-039: si los datos cambian sale otro documento con otra ruta y el anterior se queda guardado: se guardan para siempre")
    void siCambianLosDatosSaleOtro() {
        DocumentoEntregado primero = servicio.delAprendiz(pedido(LIDER));
        aEntregar = new DocumentoArmado(ANA, DatosDePruebaDelDocumento.aprendizConPalabras("Escribió algo nuevo"));

        DocumentoEntregado segundo = servicio.delAprendiz(pedido(LIDER));

        assertThat(segundo.reutilizado()).isFalse();
        assertThat(segundo.url()).isNotEqualTo(primero.url());
        assertThat(documentos).hasSize(2);
        assertThat(objetos).as("el primero sigue guardado").hasSize(2).containsKey(documentos.get(0).ruta());
    }

    @Test
    @DisplayName("spec 056 FR-040: cada pedido deja su fila de descarga con quién lo pidió, cuándo y si se reutilizó, también los que no generan nada")
    void cadaPedidoQuedaRegistrado() {
        servicio.delAprendiz(pedido(LIDER));
        ahora = AHORA.plusSeconds(60);
        servicio.delAprendiz(pedido(MENTORA));
        servicio.delAprendiz(pedido(LIDER));

        assertThat(descargas).extracting(DescargaDeDocumento::actorId).containsExactly(LIDER, MENTORA, LIDER);
        assertThat(descargas).extracting(DescargaDeDocumento::reutilizado).containsExactly(false, true, true);
        assertThat(descargas).extracting(DescargaDeDocumento::pedidoEn).containsExactly(AHORA, AHORA.plusSeconds(60), AHORA.plusSeconds(60));
        assertThat(descargas).extracting(DescargaDeDocumento::documentoId).containsOnly(documentos.getFirst().id());
    }

    @Test
    @DisplayName("spec 056 FR-039: el documento de un mentor y el del mes se generan igual, y el del mes es de quien lo pidió")
    void elMentorYElMes() {
        aEntregar = new DocumentoArmado(MENTORA, DatosDePruebaDelDocumento.mentor());
        DocumentoEntregado delMentor = servicio.delMentor(new PedidoDeDocumento(LIDER, MENTORA.value(), null));
        aEntregar = new DocumentoArmado(LIDER, DatosDePruebaDelDocumento.mes());
        DocumentoEntregado delMes = servicio.delMes(new PedidoDeDocumentoDelMes(LIDER, "2026-11"));

        assertThat(delMentor.reutilizado()).isFalse();
        assertThat(delMes.desde()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(delMes.hasta()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(documentos).extracting(DocumentoSemanal::tipo).containsExactly(TipoDeDocumento.MENTOR, TipoDeDocumento.MES);
        assertThat(documentos).extracting(DocumentoSemanal::personaId).containsExactly(MENTORA, LIDER);
        assertThat(documentos.getLast().ruta()).startsWith("reportes/" + LIDER + "/2026-11-01-MES-");
    }

    @Test
    @DisplayName("spec 056 FR-041: si la fuente rechaza el pedido (403 o 404) no se lee, no se dibuja, no se sube y no queda ni documento ni descarga")
    void siLaFuenteRechazaNoPasaNada() {
        fallaLaFuente = new NotAuthorizedException("No acompañas ese grupo.", "NO_ACOMPANA");

        assertThatThrownBy(() -> servicio.delAprendiz(pedido(MENTORA))).isSameAs(fallaLaFuente);

        assertThat(objetos).isEmpty();
        assertThat(documentos).isEmpty();
        assertThat(descargas).isEmpty();
        assertThat(generadosEn).isEmpty();
    }

    @Test
    @DisplayName("spec 056 FR-039: si el almacenamiento falla al subir no queda un documento ni una descarga apuntando a un archivo que no está")
    void siLaSubidaFallaNoQuedaNada() {
        fallaLaSubida = new IllegalStateException("S3 caído");

        assertThatThrownBy(() -> servicio.delAprendiz(pedido(LIDER))).isSameAs(fallaLaSubida);

        assertThat(documentos).isEmpty();
        assertThat(descargas).isEmpty();
    }

    @Test
    @DisplayName("spec 056 FR-039: dos pedidos a la vez de lo mismo: el que pierde la carrera recibe el documento del otro y cuenta como reutilización")
    void dosPedidosALaVez() {
        DatosDelAprendiz datos = DatosDePruebaDelDocumento.aprendiz();
        String huella = DocumentoSemanal.huellaDe(datos);
        DocumentoSemanal delOtroPedido = DocumentoSemanal.generar(UUID.fromString("99999999-9999-9999-9999-999999999999"),
                new DocumentoSemanal.Contenido(ANA, TipoDeDocumento.APRENDIZ, datos.desde(), datos.hasta(), huella, MENTORA), reloj());
        GuardarDocumentoSemanalPort ganoElOtro = new GuardarDocumentoSemanalPort() {
            @Override
            public DocumentoSemanal insertar(DocumentoSemanal documento) {
                return delOtroPedido;
            }

            @Override
            public void registrarDescarga(DescargaDeDocumento descarga) {
                descargas.add(descarga);
            }
        };
        DocumentoSemanalService conCarrera = new DocumentoSemanalService(new FuenteDeDocumentos() {
            @Override
            public DocumentoArmado delAprendiz(UserId actorId, UUID personaId, LocalDate semanaHasta) {
                return aEntregar;
            }

            @Override
            public DocumentoArmado delMentor(UserId actorId, UUID mentorId, LocalDate semanaHasta) {
                return aEntregar;
            }

            @Override
            public DocumentoArmado delMes(UserId actorId, String mes) {
                return aEntregar;
            }
        }, buscado -> Optional.empty(), ganoElOtro, (d, g) -> "<html></html>", new AlmacenamientoDeLaPrueba(), () -> UUID.randomUUID(), reloj());

        DocumentoEntregado entregado = conCarrera.delAprendiz(pedido(LIDER));

        assertThat(entregado.reutilizado()).isTrue();
        assertThat(entregado.url().toString()).contains(delOtroPedido.ruta());
        assertThat(descargas).singleElement().satisfies(d -> {
            assertThat(d.documentoId()).isEqualTo(delOtroPedido.id());
            assertThat(d.actorId()).isEqualTo(LIDER);
            assertThat(d.reutilizado()).isTrue();
        });
    }

    private Clock reloj() {
        return new Clock() {
            @Override
            public Instant now() {
                return ahora;
            }

            @Override
            public LocalDate today() {
                return LocalDate.of(2026, 11, 18);
            }
        };
    }

    /** Un almacenamiento que anota lo que se le pide, en el orden en que se lo pide. */
    private final class AlmacenamientoDeLaPrueba implements AlmacenamientoPort {

        @Override
        public URI firmarSubida(String ruta, String tipoContenido, Duration validez) {
            throw new UnsupportedOperationException("el servidor sube el documento él mismo");
        }

        @Override
        public URI firmarLectura(String ruta, Duration validez) {
            vigencias.add(validez);
            return URI.create("https://almacen.test/" + ruta + "?lectura");
        }

        @Override
        public URI urlPublica(String ruta) {
            throw new UnsupportedOperationException("un documento privado nunca tiene enlace público");
        }

        @Override
        public void subir(String ruta, byte[] contenido, String tipoContenido) {
            if (fallaLaSubida != null) {
                throw fallaLaSubida;
            }
            eventos.add("subir");
            objetos.put(ruta, contenido);
            tiposSubidos.put(ruta, tipoContenido);
        }

        @Override
        public void borrar(String ruta) {
            throw new UnsupportedOperationException("los documentos se guardan para siempre");
        }

        @Override
        public Optional<byte[]> leer(String ruta, long pesoMaximo) {
            return Optional.ofNullable(objetos.get(ruta));
        }
    }
}
