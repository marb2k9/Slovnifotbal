# Sestavení slovníku

Skripty v této složce vytvoří soubory `app/src/main/assets/dictionary/cs_words.txt` a `cs_words.meta`.
Běží offline nad lokálními soubory, potřebují jen Python 3.10+ (bez dalších balíčků).

## Zdroje
| Soubor | Odkud | Licence |
|---|---|---|
| `cs_CZ.aff`, `cs_CZ.dic` | https://github.com/LibreOffice/dictionaries – složka `cs_CZ` | GNU GPL |
| `en-cs.txt` | tamtéž, `cs_CZ/thesaurus/en-cs.txt` (Svobodné slovníky) | GNU FDL 1.1+ |
| `cs_full.txt` | https://github.com/hermitdave/FrequencyWords – `content/2018/cs/cs_full.txt` | CC BY-SA 4.0 |

## Spuštění
```bash
python3 build_dictionary.py cs_CZ.aff cs_CZ.dic en-cs.txt cs_full.txt ../../app/src/main/assets/dictionary blocklist.txt
```
Skript vypíše statistiky (stejné jako v `stats.json`). Po přegenerování uprav v testu
`DictionaryTest.EXPECTED_WORD_COUNT` očekávaný počet slov.

## Co skript dělá
1. `hunspell_expand.py` načte pravidla `.aff` (předpony, přípony, podmínky, pokračovací třídy,
   křížové kombinace) a rozgeneruje každé heslo `.dic` na všechny tvary.
2. Slovní druh se určí podle skloňovacích/časovacích příznaků (např. Z = vzor žena, Y = přídavná jména,
   J/A/B/O = slovesa, R u přídavných jmen = příslovce na -e/-ě). Neohebná hesla bez příznaků se zařadí
   podle Svobodného slovníku; jejich nepravidelné tvary (koně, psa, tebou …) se rozpoznají jednoduchým
   lematizátorem a označí jako tvary.
3. Do slovníku jdou všechny základní tvary a tvary doložené v korpusu titulků (běžné tvary).
4. Vyřadí se zkratky, slova s neplatnými znaky, vulgarismy (NOSUGGEST a `blocklist.txt`).
5. `cs_words.txt` se zapíše seřazený podle Unicode, `cs_words.meta` obsahuje ke každému slovu 16 bitů:
   - bity 0–4 slovní druhy základního tvaru (1 podst., 2 příd., 4 slov., 8 přísl., 16 ostatní)
   - bity 5–9 slovní druhy, k nimž je slovo tvarem
   - bit 10/11 vlastní jméno (základní tvar / tvar)
   - bity 12–15 frekvenční třída 0–15
   Hlavička: `SFM1`, počet slov (u32 LE), CRC32 souboru `cs_words.txt` (u32 LE).
