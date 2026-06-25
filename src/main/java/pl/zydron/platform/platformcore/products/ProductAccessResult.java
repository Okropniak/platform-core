package pl.zydron.platform.platformcore.products;

/**
 * Wynik funkcji SQL sprawdzającej dostęp do produktu.
 *
 * @param allowed czy użytkownik ma aktywny dostęp
 * @param role rola w produkcie; może być pusta przy braku dostępu
 */
public record ProductAccessResult(boolean allowed, String role) {
}
