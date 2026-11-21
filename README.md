# Slovní fotbal

Česká slovní hra pro Android: ty proti počítači, slovo navazuje na poslední písmeno předchozího slova.
Aplikace je **plně offline** – slovník je součástí APK, nepotřebuje internet, účet ani registraci
a nežádá žádná oprávnění (ani `INTERNET`).

| | |
|---|---|
| Balíček (applicationId) | `cz.slovnifotbal` |
| Minimální Android | 8.0 (API 26) |
| Cílové SDK / kompilační SDK | 36 / 37 |
| Jazyk a UI | Kotlin 2.4.20, Jetpack Compose (BOM 2026.09.00), Material 3 |
| Architektura | MVVM (ViewModel + StateFlow + Coroutines), DataStore Preferences |
| Sestavení | Gradle 9.6.0, Android Gradle Plugin 9.5.0 (built-in Kotlin), JDK 17 |
| Slovník | **805 232 slov a tvarů** (hunspell cs_CZ, GNU GPL) |

---

## Funkce

**Hra**
- hráč začíná libovolným slovem, počítač odpovídá slovem na poslední písmeno, hráči se střídají
- každé slovo lze v jedné hře použít jen jednou, slova se kontrolují proti zabudovanému slovníku
- neplatný tah neznamená prohru – hráč dostane srozumitelnou českou hlášku a zkusí jiné slovo
- prohra, když hráč nemá platné slovo („Nemáš už žádné platné slovo. Prohráváš!“), výhra, když slovo nenajde počítač
- tlačítka ODESLAT, NOVÁ HRA, Vzdát se a nápověda (3× za hru – ukáže jedno platné slovo)
- výsledková obrazovka: „VYHRÁL JSI!“ + HRÁT ZNOVU, nebo „TENTOKRÁT VYHRÁL POČÍTAČ.“ + ODVETA,
  s přehledem průběhu hry a počtem tahů

**Pravidla zadávání**
- ořezání mezer, převod na malá písmena, sjednocení Unicode (NFC), plná česká diakritika
- zakázáno: prázdné slovo, čísla, interpunkce, speciální znaky, víc slov, cizí písmena, víc než 40 znaků
- velikost písmen se nerozlišuje, **diakritika se rozlišuje vždy** (čaj ≠ caj, les / léto / lék jsou tři slova)

**Nastavení** (ukládá se v DataStore, platí od nové hry)
- obtížnost Lehká / Střední / Těžká
- vlastní jména (výchozí vypnuto) – Otakar, Radek, Petr, Martin, Jana, Lenka a přes 100 000 dalších jmen osob, měst a zemí
- skloňované a časované tvary (výchozí vypnuto = jen základní tvary)
- minimální délka slova 2–8 (výchozí 2)
- pravidlo CH: dvě písmena C + H (výchozí) / samostatné písmeno CH (chata začíná i prach končí na CH)
- povolené slovní druhy: podstatná jména, přídavná jména, slovesa, příslovce, ostatní
- navazování na písmena s diakritikou: volně (á = a, ě = e, ů = u …; výchozí) / přesně
- zvukové efekty, světlý / tmavý / systémový vzhled

**Statistiky** (trvalé, v zařízení): odehrané hry, výhry, prohry, úspěšnost v %, nejdelší řetězec slov,
nejvíc tvých slov v jedné hře, počet použitých slov, série výher, hry a výhry podle obtížnosti.

**Odolnost**: rychlé opakované odeslání i dvojklik se ignorují, výpočet počítače běží mimo hlavní vlákno
a lze ho přerušit (nová hra během tahu počítače), rozehraná hra se ukládá po každém tahu a obnoví se po
otočení displeje, návratu z pozadí i po úplném ukončení aplikace. Chybějící, prázdný nebo poškozený slovník
zobrazí chybovou obrazovku s tlačítkem „Zkusit znovu“, aplikace nespadne.

### Proč „volné“ navazování na diakritiku
Na Ě, Ů a Ý v češtině nezačíná žádné slovo a na Í téměř žádné (náměstí, nádraží, psaní …).
Při přesném navazování by chytrý počítač vyhrál hned prvním tahem. Výchozí režim proto navazuje na písmeno
bez čárky/kroužku (á→A, é/ě→E, í→I, ó→O, ú/ů→U, ý→Y, ď→D, ť→T, ň→N). Č, Ř, Š, Ž a CH zůstávají vždy
samostatnými písmeny. Přesný režim lze zapnout v Nastavení. Při kontrole, zda slovo existuje, se diakritika
rozlišuje vždy.

---

## Počítačový soupeř

Hra je graf nad písmeny: každé slovo je hrana z prvního do posledního písmene a každou hranu lze použít jednou.
Slova se stejnou dvojicí písmen jsou pro průběh hry zaměnitelná, takže soupeř místo statisíců slov počítá
s maticí 43 × 43 počtů zbývajících slov. Díky tomu je i hluboký výpočet rychlý na každém telefonu.

| Obtížnost | Chování |
|---|---|
| **Lehká** | náhodné platné nepoužité slovo (bez strategie), preferuje běžná slova, odpověď 0,45–0,75 s |
| **Střední** | hodnotí koncová písmena podle počtu slov, která zbudou hráči, vyhýbá se tahům, po kterých by sám neměl odpověď, mezi dobrými tahy volí s prvkem náhody |
| **Těžká** | negamax s alfa-beta ořezáváním a iterativním prohlubováním, časový limit 1,2 s, hledá vynucené výhry (zahnání do písmene bez slov) |

Ověřeno simulací 24 her pro každou dvojici (počítač proti počítači na skutečném slovníku, limit 150 ms na tah):
Lehká : Střední = 3 : 21, Střední : Těžká = 0 : 24. Těžká obtížnost dosahuje na desktopu hloubky 8–10 tahů
za 0,3–1,2 s.

Počítač nikdy nepoužije slovo mimo slovník, již použité slovo, slovo se špatným počátečním písmenem
ani slovo, které nastavení nepovoluje (ověřeno testem s 27 simulovanými hrami na skutečném slovníku).

---

## Slovník

| | |
|---|---|
| Soubor | `app/src/main/assets/dictionary/cs_words.txt` (UTF-8, 1 slovo na řádek, malá písmena, seřazeno, bez duplicit) |
| Metadata | `app/src/main/assets/dictionary/cs_words.meta` (slovní druhy, tvar/základní tvar, vlastní jméno, frekvence) |
| **Počet slov** | **805 232** |
| – základní tvary obecných slov | 233 854 (podst. jm. 85 811, příd. jm. 69 153, příslovce 53 808, slovesa 22 543, ostatní 4 026) |
| – pouze skloňované/časované tvary | 427 661 |
| – pouze vlastní jména | 143 717 (z toho 102 818 základních tvarů) |
| Zdroj | český slovník pro Hunspell z projektu LibreOffice (`LibreOffice/dictionaries`, `cs_CZ.dic` + `cs_CZ.aff`), autoři Petr Kolář a přispěvatelé, aktualizace 2021 Miroslav Pošta |
| Licence | **GNU GPL** (text v `app/src/main/assets/licenses/`) |

Jak slovník vznikl (skripty v `tools/dictionary/`):
1. 261 166 hesel slovníku Hunspell bylo rozgenerováno podle pravidel `.aff` (skloňování, časování,
   předpony ne-/nej-) – celkem 4,27 milionu tvarů.
2. Zařazeny jsou **všechny základní tvary** a **běžné tvary** – ty, které se aspoň jednou vyskytují
   v korpusu titulků OpenSubtitles (FrequencyWords, CC BY-SA 4.0). Vzácné tvary (např. „nejnerychlejšími“)
   zařazeny nejsou.
3. Slovní druhy se určily podle skloňovacích vzorů Hunspellu, u ~2 000 neohebných slov podle
   Svobodného slovníku (GNU FDL).
4. Vyřazeno: zkratky, slova s tečkou/pomlčkou/cizími písmeny, vulgarismy (příznak NOSUGGEST
   a `tools/dictionary/blocklist.txt`) a urážlivé výrazy – hra je vhodná i pro děti.

Slovník není kompletní: chybí některá řídká, odborná, slangová a nová slova a vzácné tvary.
Vlastní slova lze přidat na konec `cs_words.txt` (aplikace soubor při načtení sama seřadí; protože pak
nesedí kontrolní součet metadat, budou se všechna slova chovat jako základní tvary všech slovních druhů).
Plnohodnotné přegenerování: viz `tools/dictionary/README.md`.

---

## Instalace APK do telefonu

1. Stáhni `SlovniFotbal.apk` do telefonu (např. ze zprávy, e-mailu nebo Google Disku).
2. Otevři soubor v aplikaci **Soubory** / **Stažené soubory**.
3. Android se zeptá na povolení instalace z tohoto zdroje → **Nastavení** → zapni **Povolit z tohoto zdroje** → zpět.
4. Klepni na **Instalovat**. Případné varování Play Protect („neznámý vývojář“) potvrď volbou **Přesto instalovat**.
5. Spusť **Slovní fotbal**. Načtení slovníku při startu trvá obvykle do 2 sekund.

Přes počítač (USB ladění zapnuté v telefonu): `adb install -r SlovniFotbal.apk`

---

## Sestavení projektu

### Android Studio
1. Rozbal `SlovniFotbal_Android_Project.zip` a otevři složku `SlovniFotbal` (File → Open).
2. Android Studio stáhne Gradle 9.6.0, AGP 9.5.0 a knihovny (poprvé je potřeba internet).
   Potřebná je aktuální verze Android Studia s podporou AGP 9.5 a JDK 17 nebo novější.
3. Vyber konfiguraci **app** a spusť ▶ na telefonu/emulátoru, nebo **Build → Generate App Bundles or APKs → Build APK(s)**.

### Příkazová řádka
```bash
./gradlew testDebugUnitTest          # jednotkové testy
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk
./gradlew connectedDebugAndroidTest  # instrumentační testy na připojeném zařízení
```

### GitHub Actions
Workflow `.github/workflows/android.yml` po každém pushi do `main` spustí jednotkové testy, sestaví
debug i release APK (artefakt **SlovniFotbal-apk**) a spustí instrumentační testy na emulátorech
Android 8.0 (API 26) a Android 14 (API 34).

---

## Podepsaná release verze

Bez vlastního klíče se `assembleRelease` podepíše ladicím klíčem (APK jde nainstalovat, ale nehodí se
pro Google Play). Vlastní klíč:

1. Vytvoř klíč (jednou, a **pečlivě ho zálohuj** – bez něj nepůjde vydat aktualizace):
   ```bash
   keytool -genkeypair -v -keystore slovnifotbal-release.jks -keyalg RSA -keysize 4096 \
           -validity 10000 -alias slovnifotbal
   ```
2. Do kořene projektu vedle `settings.gradle.kts` vytvoř `keystore.properties` (je v `.gitignore`):
   ```properties
   storeFile=slovnifotbal-release.jks
   storePassword=TVOJE_HESLO
   keyAlias=slovnifotbal
   keyPassword=TVOJE_HESLO
   ```
   (alternativně proměnné prostředí `SF_KEYSTORE_FILE`, `SF_KEYSTORE_PASSWORD`, `SF_KEY_ALIAS`, `SF_KEY_PASSWORD`)
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (zmenšené R8, podepsané tvým klíčem).
   V Android Studiu: **Build → Generate Signed App Bundle or APK → APK**.
4. Kontrola podpisu: `apksigner verify --print-certs app-release.apk`.

Pozor: aplikaci podepsanou jiným klíčem nelze nainstalovat přes verzi podepsanou ladicím klíčem –
je potřeba ji nejdřív odinstalovat (statistiky se tím smažou).

---

## Architektura

```
app/src/main/java/cz/slovnifotbal/
├── MainActivity.kt               – jediná aktivita, edge-to-edge, Compose
├── SlovniFotbalApplication.kt    – kontejner závislostí
├── domain/   GameSettings.kt (pravidla, obtížnost, CH, diakritika, slovní druhy, vzhled)
│             GameState.kt (stavy LOADING/READY/PLAYER_TURN/COMPUTER_THINKING/PLAYER_WON/PLAYER_LOST/ERROR,
│                           tahy, uložitelný stav hry) · GameStatistics.kt
├── engine/   WordFootballEngine.kt (herní logika) · ComputerPlayer.kt (soupeř) · WordDictionary.kt
│             (kompaktní slovník, binární vyhledávání) · WordPool.kt (index slov podle písmen)
│             WordFilter.kt (nastavení × metadata) · CzechAlphabet.kt (písmena, CH, diakritika, normalizace)
├── data/     DataStoreManager.kt · DictionaryRepository.kt · SoundPlayer.kt · AppContainer.kt
├── viewmodel/ GameViewModel.kt · SettingsViewModel.kt · StatisticsViewModel.kt · AppViewModelProvider.kt
└── ui/       WordFootballApp.kt (navigace, téma) · GameScreen.kt · ResultScreen.kt · SettingsScreen.kt
              StatisticsScreen.kt · components/ · theme/
```
Herní jádro (`domain` + `engine`) nemá žádnou závislost na Androidu a je celé pokryté jednotkovými testy.
Slovník se načítá jednou, asynchronně (Dispatchers.IO) s ukazatelem průběhu; v paměti zabírá ~15 MB
(1 bajt na písmeno). Každá hra si postaví index povolených slov podle dvojic písmen (~50 ms).

---

## Testy

| Sada | Testů | Obsah |
|---|---|---|
| `engine/DictionaryTest` | 11 | načtení slovníku, počet slov, striktní UTF-8, jedno slovo na řádek, řazení, bez duplicit, diakritika, vzorová slova a jména, tvary, poškozený / prázdný slovník, nesouhlasná metadata |
| `engine/NormalizationTest` | 5 | normalizace (mezery, velká písmena, NFC), chybové hlášky, počítání písmen s CH, třídy písmen |
| `engine/EngineRulesTest` | 17 | první tah, návaznost, CH v obou režimech, diakritika volně/přesně, neexistující a opakované slovo, min. délka, vlastní jména, tvary, slovní druhy, výhra, prohra, vzdání, restart, rychlé opakované odeslání, přerušený výpočet, nápověda |
| `engine/ComputerPlayerTest` | 7 | okamžitá výhra, vyhnutí se pasti, chování střední obtížnosti, žádné slovo, časový limit a přerušení na skutečném slovníku, 27 simulovaných her |
| `engine/GameSnapshotTest` | 4 | uložení a obnovení rozehrané hry, poškozený stav |
| `domain/GameStatisticsTest` | 2 | výpočet statistik |
| `data/DataStoreManagerTest` | 5 | ukládání nastavení, statistik a hry, přetrvání po „restartu“ |
| `viewmodel/GameViewModelTest` | 13 | celý tok hry ve ViewModelu, rychlé opakované odeslání, nová hra během tahu počítače, obnovení po ukončení aplikace, chybějící/prázdný slovník, statistiky |
| `androidTest/GameFlowTest` | 3 | instalace a spuštění na emulátoru, odehrání tahu, chybová hláška, obrazovky Nastavení a Statistiky |

---

## Licence a zdroje třetích stran
Viz `app/src/main/assets/licenses/NOTICE.txt` (dostupné i v aplikaci: Nastavení → O slovníku → Zdroje a licence).
Slovník je pod licencí GNU GPL; pokud bys aplikaci veřejně šířil, musí zůstat dostupný i zdroj slovníku
(soubor `cs_words.txt` a skripty v `tools/dictionary/` to splňují) a text licence.
