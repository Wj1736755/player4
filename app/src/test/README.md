# Testy jednostkowe dla DatabaseExporter

## Opis

Ten katalog zawiera testy jednostkowe dla klasy `DatabaseExporter`, która odpowiada za eksport playlist z aplikacji Music Player do plików M3U.

## Struktura testów

### DatabaseExporterTest.kt

Zawiera testy dla następujących funkcjonalności:

#### `exportAllPlaylists`

1. **Eksport pojedynczej playlisty** - sprawdza, czy pojedyncza playlista z dwoma utworami jest poprawnie eksportowana do pliku M3U
2. **Eksport wielu playlist** - weryfikuje eksport wielu playlist do osobnych plików M3U
3. **Pomijanie pustych playlist** - sprawdza, czy puste playlisty są pomijane podczas eksportu
4. **Sanityzacja nazw plików** - weryfikuje, czy znaki specjalne w nazwach playlist są poprawnie zamieniane na bezpieczne znaki w nazwach plików
5. **Sortowanie utworów** - sprawdza, czy utwory w playliście są sortowane według `orderInPlaylist`
6. **Obsługa błędów** - weryfikuje, czy błędy podczas eksportu są poprawnie obsługiwane i zliczane
7. **Brak playlist** - sprawdza zachowanie gdy nie ma playlist do eksportu
8. **Formatowanie czasu trwania** - weryfikuje, czy czas trwania utworu jest poprawnie formatowany w pliku M3U

#### `exportAllPlaylistsFromOriginalApp`

1. **Błąd kopiowania bazy** - sprawdza, czy funkcja zwraca błąd, gdy nie można skopiować bazy danych z oryginalnej aplikacji

## Uruchamianie testów

### Z linii poleceń

```bash
# Uruchomienie wszystkich testów
./gradlew test

# Uruchomienie tylko testów dla DatabaseExporter
./gradlew test --tests "org.fossify.musicplayer.helpers.DatabaseExporterTest"

# Uruchomienie konkretnego testu
./gradlew test --tests "org.fossify.musicplayer.helpers.DatabaseExporterTest.exportAllPlaylists - exports single playlist successfully"
```

### Z Android Studio

1. Otwórz plik `DatabaseExporterTest.kt`
2. Kliknij prawym przyciskiem na klasę testową lub konkretny test
3. Wybierz "Run 'DatabaseExporterTest'" lub "Run 'test name'"

## Technologie

- **JUnit 4** - framework testowy
- **Robolectric** - symulacja środowiska Android w testach jednostkowych
- **MockK** - biblioteka do mockowania obiektów w Kotlin

## Zależności

Testy wymagają następujących zależności (zdefiniowanych w `build.gradle.kts`):

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.robolectric:robolectric:4.11.1")
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("androidx.test:core:1.5.0")
testImplementation("androidx.test.ext:junit:1.1.5")
```

## Uwagi

1. Testy używają Robolectric do symulacji środowiska Android, więc można je uruchamiać bez emulatora lub urządzenia fizycznego
2. MockK jest używany do mockowania `AudioHelper`, aby uniknąć zależności od bazy danych
3. Testy są asynchroniczne i używają `CountDownLatch` do synchronizacji z callbackami
4. Pliki testowe są automatycznie usuwane po każdym teście w metodzie `tearDown()`

## Rozszerzanie testów

Przy dodawaniu nowych testów:

1. Użyj konwencji nazewnictwa: `funkcja - opis scenariusza`
2. Zachowaj strukturę Given-When-Then
3. Używaj `CountDownLatch` do obsługi asynchronicznych callbacków
4. Mockuj zależności za pomocą MockK
5. Czyszczenie po teście w metodzie `tearDown()`


