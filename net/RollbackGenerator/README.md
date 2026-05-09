# RollbackGenerator

Narzędzie do automatycznego generowania rollbacków dla skryptów migracji SQL.

## Opis

`RollbackGenerator` to osobny projekt konsolowy, który generuje pliki `.rollback.sql` dla wszystkich plików `.sql` w określonym katalogu. Używany jest przez MSBuild target w projekcie `DbUp` do automatycznego generowania rollbacków podczas builda.

## Użycie

### Jako narzędzie konsolowe

```bash
RollbackGenerator <ConnectionString> <ScriptsDirectory> [ProjectDirectory]
```

Przykład:
```bash
dotnet run -- "Server=localhost;Database=TestDb;Integrated Security=true" "Scripts"
```

### Przez MSBuild Target

RollbackGenerator jest automatycznie wywoływany przez MSBuild target `GenerateRollbacks` w projekcie `DbUp` po buildzie (tylko w konfiguracji Release, chyba że `GenerateRollbacksInDebug=true`).

## Konfiguracja

- **ConnectionString**: Connection string do bazy danych używanej do generowania rollbacków
- **ScriptsDirectory**: Katalog zawierający pliki SQL migracji
- **ProjectDirectory**: (opcjonalny) Katalog projektu

## Jak to działa

1. Skanuje `ScriptsDirectory` w poszukiwaniu wszystkich plików `.sql`
2. Dla każdego pliku `.sql`, który nie ma odpowiedniego `.rollback.sql`, generuje rollback
3. Używa `DacFxRollbackOrchestrator` do generowania rollbacków
4. Zapisuje wygenerowane pliki `.rollback.sql` obok plików migracji

## Wymagania

- .NET 10.0
- SQL Server lub SQL Server LocalDB
- Microsoft.SqlServer.DacFx







