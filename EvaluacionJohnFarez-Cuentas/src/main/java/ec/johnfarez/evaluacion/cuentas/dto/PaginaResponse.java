package ec.johnfarez.evaluacion.cuentas.dto;

import java.util.List;

/**
 * Respuesta paginada opcional. Se usa solo cuando el cliente envia page/size;
 * sin esos parametros los listados devuelven el arreglo plano de siempre.
 */
public record PaginaResponse<T>(
    List<T> contenido,
    int pagina,
    int tamano,
    long totalElementos,
    int totalPaginas) {

    public static <T> PaginaResponse<T> de(List<T> contenido, int pagina, int tamano, long totalElementos) {
        int totalPaginas = tamano <= 0 ? 0 : (int) Math.ceil((double) totalElementos / tamano);
        return new PaginaResponse<>(contenido, pagina, tamano, totalElementos, totalPaginas);
    }
}
