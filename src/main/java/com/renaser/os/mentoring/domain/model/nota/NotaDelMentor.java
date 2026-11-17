package com.renaser.os.mentoring.domain.model.nota;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Una nota privada de quien acompaña sobre un aprendiz (spec 055, FR-023 a FR-026).
 *
 * <ul>
 *   <li>Su texto tiene entre 1 y {@value #MAXIMO} caracteres sin contar los espacios de los extremos; se guarda recortado.</li>
 *   <li>La nota sigue al aprendiz, no al grupo ni al mentor: la lee quien lo acompañe hoy, aunque la haya escrito otro.</li>
 *   <li>La corrige SOLO su autor y SOLO el día en que la escribió, contado en la zona del autor (regla 02); cada corrección
 *       entrega el texto que reemplaza para guardarlo en el historial (constitución IV).</li>
 *   <li>No se borra: no hay ninguna operación que lo haga.</li>
 * </ul>
 */
public final class NotaDelMentor {

    public static final int MAXIMO = 2000;
    public static final String NOTA_INVALIDA = "NOTA_INVALIDA";
    public static final String NO_ES_EL_AUTOR = "NO_ES_EL_AUTOR";
    public static final String YA_NO_SE_PUEDE_CORREGIR = "YA_NO_SE_PUEDE_CORREGIR";

    private final UUID id;
    private final UserId aprendizId;
    private final UserId autorId;
    private String texto;
    private final LocalDate diaDelAutor;
    private final Instant creadoEn;
    private Instant corregidoEn;

    private NotaDelMentor(UUID id, UserId aprendizId, UserId autorId, String texto, LocalDate diaDelAutor,
                          Instant creadoEn, Instant corregidoEn) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.aprendizId = Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
        this.autorId = Objects.requireNonNull(autorId, "autorId es obligatorio");
        this.texto = texto;
        this.diaDelAutor = Objects.requireNonNull(diaDelAutor, "diaDelAutor es obligatorio");
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        this.corregidoEn = corregidoEn;
    }

    /** Una nota nueva: su día es el de HOY en la zona de su autor. */
    public static NotaDelMentor escribir(UUID id, UserId aprendizId, UserId autorId, String texto, ZoneId zonaDelAutor,
                                         Clock clock) {
        Instant ahora = clock.now();
        return new NotaDelMentor(id, aprendizId, autorId, validar(texto), ahora.atZone(zonaDelAutor).toLocalDate(), ahora,
                null);
    }

    /** Una nota ya guardada, tal cual se leyó. */
    public static NotaDelMentor rehidratar(UUID id, UserId aprendizId, UserId autorId, String texto,
                                           LocalDate diaDelAutor, Instant creadoEn, Instant corregidoEn) {
        return new NotaDelMentor(id, aprendizId, autorId, texto, diaDelAutor, creadoEn, corregidoEn);
    }

    /**
     * Cambia el texto y entrega lo que reemplazó.
     *
     * @throws NotAuthorizedException    {@link #NO_ES_EL_AUTOR} si quien corrige no la escribió
     * @throws RechazoConCodigoException {@link #YA_NO_SE_PUEDE_CORREGIR} (409) si ya no es el día en que se escribió, o
     *                                   {@link #NOTA_INVALIDA} (400) si el texto nuevo no sirve
     */
    public VersionDeLaNota corregir(UserId quien, String nuevoTexto, ZoneId zonaDelAutor, Clock clock) {
        if (!autorId.equals(quien)) {
            throw new NotAuthorizedException("Solo quien escribió la nota puede corregirla.", NO_ES_EL_AUTOR);
        }
        Instant ahora = clock.now();
        if (!diaDelAutor.equals(ahora.atZone(zonaDelAutor).toLocalDate())) {
            throw RechazoConCodigoException.conflicto(YA_NO_SE_PUEDE_CORREGIR,
                    "Esta nota ya no se puede corregir: solo se corrige el mismo día en que la escribiste.");
        }
        String recortado = validar(nuevoTexto);
        VersionDeLaNota anterior = new VersionDeLaNota(id, texto, ahora);
        this.texto = recortado;
        this.corregidoEn = ahora;
        return anterior;
    }

    /** Si {@code quien} puede corregirla ahora: es el autor y es el mismo día. */
    public boolean puedeCorregirla(UserId quien, ZoneId zonaDelAutor, Clock clock) {
        return autorId.equals(quien) && diaDelAutor.equals(clock.now().atZone(zonaDelAutor).toLocalDate());
    }

    /** El texto recortado; falla si queda vacío o pasa del máximo. */
    static String validar(String texto) {
        String recortado = texto == null ? "" : texto.strip();
        if (recortado.isEmpty() || recortado.length() > MAXIMO) {
            throw RechazoConCodigoException.invalido(NOTA_INVALIDA,
                    "La nota no puede estar vacía ni pasar de " + MAXIMO + " caracteres.");
        }
        return recortado;
    }

    public UUID id() {
        return id;
    }

    public UserId aprendizId() {
        return aprendizId;
    }

    public UserId autorId() {
        return autorId;
    }

    public String texto() {
        return texto;
    }

    public LocalDate diaDelAutor() {
        return diaDelAutor;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    /** Null hasta la primera corrección. */
    public Instant corregidoEn() {
        return corregidoEn;
    }
}
