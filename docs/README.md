# Dokumentacja platform-core

Ten katalog zawiera dwa rodzaje dokumentacji:

1. **Dokumentację projektową**, która opisuje pierwotne założenia:
   - [SaaS Platform Core HLD](SaaS_Platform_Core_HLD.md)
   - [SaaS Platform Core LLD](SaaS_Platform_Core_LLD_reviewed.md)
   - [Decyzje architektoniczne](platform-core-architecture-decisions.md)
2. **Dokumentację powdrożeniową**, która opisuje kod istniejący obecnie:
   - [Przewodnik po aktualnej implementacji](platform-core-implementation-guide.md)
   - [Katalog techniczny i odchylenia](platform-core-technical-catalog.md)

## Od czego zacząć

Osoba poznająca projekt powinna czytać dokumenty w następującej kolejności:

1. `platform-core-implementation-guide.md` - wyjaśnia cel aplikacji i główne
   przepływy bez wymagania szczegółowej znajomości Springa.
2. `platform-core-technical-catalog.md` - zawiera endpointy, migracje,
   konfigurację, model CI/CD oraz różnice względem specyfikacji.
3. HLD i LLD - pokazują pierwotny kierunek architektoniczny i funkcje planowane
   na dalsze etapy.
4. ADR - wyjaśniają decyzje podjęte podczas implementacji.

Instrukcje i skrypty wykonywane ręcznie podczas wdrożenia znajdują się w
podkatalogu `deployment`. Skrypt `deployment/supabase-profile-trigger.sql`
instaluje trigger tworzący profil po rejestracji użytkownika w Supabase Auth.

> HLD i LLD nie są opisem stanu produkcyjnego. W przypadku rozbieżności źródłem
> prawdy dla aktualnego zachowania są kod, migracje Flyway oraz dokumentacja
> powdrożeniowa.
