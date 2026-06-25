/**
 * Moduł rejestrowania ważnych zdarzeń biznesowych.
 *
 * <p>Zapis działa asynchronicznie i w osobnej transakcji, aby problem z
 * audytem nie wycofał głównej operacji użytkownika.</p>
 */
@org.springframework.modulith.ApplicationModule
package pl.zydron.platform.platformcore.audit;
