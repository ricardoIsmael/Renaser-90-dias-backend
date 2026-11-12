package com.renaser.os.shared.domain;

/**
 * La página y el tamaño que pide quien llama a una lista, ya acotados (spec 050, tramo F; FR-034): el <b>único lugar</b>
 * donde se decide cuánto es demasiado. Un {@code size=100000} no puede llegar a una consulta ni a un {@code subList}: cada
 * lista declara su máximo y pasa su parámetro por aquí. {@code scripts/reglas-de-codigo.test.mjs} rompe el build si un
 * controller recibe un {@code size}, {@code limit} o {@code tamano} sin pasar por esta clase.
 *
 * <p>Dos maneras, y cada lista usa la que ya tenía para no romper a su cliente:
 * <ul>
 *   <li>{@link #estricta}: fuera de rango es un {@link IllegalArgumentException} (400 con el motivo, como
 *       {@code GET /admin/pagos/cuotas}). La que se usa en una lista nueva: el cliente se entera de que pidió de más.</li>
 *   <li>{@link #recortada}: fuera de rango se lleva al mínimo o al máximo, sin avisar. Solo para las listas que ya
 *       recortaban así antes de esta clase y cuyos clientes pueden depender de ello.</li>
 * </ul>
 *
 * @param pagina desde 0
 * @param tamano de 1 al máximo de esa lista
 */
public record PaginaAcotada(int pagina, int tamano) {

    public static final int TAMANO_MINIMO = 1;

    public PaginaAcotada {
        if (pagina < 0) {
            throw new IllegalArgumentException("page no puede ser negativa");
        }
        if (tamano < TAMANO_MINIMO) {
            throw new IllegalArgumentException("size debe ser al menos " + TAMANO_MINIMO);
        }
    }

    /** @throws IllegalArgumentException (400) si la página es negativa o el tamaño no está entre 1 y {@code maximo} */
    public static PaginaAcotada estricta(int pagina, int tamano, int maximo) {
        if (maximo < TAMANO_MINIMO) {
            throw new IllegalArgumentException("El máximo de una lista no puede ser menor que " + TAMANO_MINIMO);
        }
        if (pagina < 0) {
            throw new IllegalArgumentException("page no puede ser negativa");
        }
        if (tamano < TAMANO_MINIMO || tamano > maximo) {
            throw new IllegalArgumentException("size debe estar entre " + TAMANO_MINIMO + " y " + maximo);
        }
        return new PaginaAcotada(pagina, tamano);
    }

    /** Lleva la página a 0 si es negativa y el tamaño a {@code 1..maximo}: nunca falla por lo que pidió quien llama. */
    public static PaginaAcotada recortada(int pagina, int tamano, int maximo) {
        if (maximo < TAMANO_MINIMO) {
            throw new IllegalArgumentException("El máximo de una lista no puede ser menor que " + TAMANO_MINIMO);
        }
        return new PaginaAcotada(Math.max(0, pagina), Math.clamp(tamano, TAMANO_MINIMO, maximo));
    }

    /** Cuántos elementos hay que saltar para llegar a esta página (long: página × tamaño no desborda un int). */
    public long desde() {
        return (long) pagina * tamano;
    }
}
