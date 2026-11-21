#!/usr/bin/env python3
"""Sestaví herní slovník pro aplikaci Slovní fotbal.

Vstupy (lokální soubory):
  cs_CZ.aff, cs_CZ.dic   – český slovník pro Hunspell (LibreOffice, GNU GPL)
  en-cs.txt              – Svobodné slovníky (GNU FDL) – jen pro určení slovního druhu neohebných slov
  cs_full.txt            – frekvenční seznam (hermitdave/FrequencyWords, OpenSubtitles 2018, CC BY-SA 4.0)

Výstupy:
  cs_words.txt  – UTF-8, jedno slovo na řádek, malá písmena, seřazeno podle kódových bodů, bez duplicit
  cs_words.meta – binární metadata ke každému slovu (stejné pořadí), viz popis níže
  stats.json    – statistiky slovníku

Formát cs_words.meta (little-endian):
  0..3   magic "SFM1"
  4..7   u32 počet slov
  8..11  u32 CRC32 souboru cs_words.txt
  12..   u16 pro každé slovo:
           bity 0–4  slovní druhy základního tvaru (1=podst., 2=příd., 4=slov., 8=přísl., 16=ostatní)
           bity 5–9  slovní druhy, ke kterým je slovo skloňovaným/časovaným tvarem
           bit 10    vlastní jméno – základní tvar
           bit 11    vlastní jméno – skloňovaný tvar
           bity 12–15 frekvenční třída 0–15 (0 = v korpusu nenalezeno)
"""
from __future__ import annotations

import json
import re
import math
import os
import struct
import sys
import zlib
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hunspell_expand import (ADJ, ADV, NOUN, OTHER, VERB, generate, is_czech_word,  # noqa: E402
                             load_encs, parse_aff, parse_dic)

POS_BITS = {NOUN: 1, ADJ: 2, VERB: 4, ADV: 8, OTHER: 16}
MAX_LEN = 40
UNKNOWN_KIND = os.environ.get('SF_UNKNOWN_KIND', 'FORM')

# Uzavřená třída zájmenných a dalších nepravidelných tvarů, které Hunspell vede jako samostatná hesla.
# Nejde o nová slova – jen o jejich zařazení mezi tvary (ne mezi základní slovníkové tvary).
PRONOUN_FORMS = set("""
mě mne mi mně mnou tě tebe ti tobě tebou sebe si sobě sebou ho jeho něho mu jemu němu jím ním
ji ní jí nás nám námi vás vám vámi je jich nich jim nim jimi nimi něj ně nějž jejž jíž
toho tomu tom tím té tu tou těch těm těmi tomhle tohohle tomuhle tímhle téhle tuhle touhle
těchhle těmhle této tuto tohoto tomto tímto těchto těmto těmito toto
mého mému mém mým mou moji mých mými mojí tvého tvému tvém tvým tvou tvoji tvých tvými tvojí
svého svému svém svým svou svoji svých svými svojí našeho našemu našem naším naši našich našim
našimi vašeho vašemu vašem vaším vaši vašich vašim vašimi
koho komu kom kým čeho čemu čem čím
""".split())

NOUN_ENDINGS = sorted(set("""
ami emi ech ích ách ům ám ou em ovi ové mi ů a u e ě i y í ím ata aty at ete eti etem atům atech
""".split()), key=len, reverse=True)
ADJ_ENDINGS = sorted(set("ého ému ém ým ých ými á é ou ího ímu ím ích ími".split()), key=len, reverse=True)
NOUN_LEMMA_ENDINGS = ["", "a", "o", "e", "ě", "í", "um", "us", "os", "ost"]

ALTERNATIONS = [
    ("o", "ů"), ("ou", "ů"), ("n", "ň"), ("d", "ď"), ("t", "ť"), ("c", "k"), ("č", "k"),
    ("z", "h"), ("ž", "h"), ("š", "ch"), ("ř", "r"), ("c", "ec"), ("ň", "eň"), ("ě", "í"),
    ("e", "é"), ("a", "á"), ("i", "í"), ("u", "ú"),
]


def _one_step(stem: str):
    out = set()
    # vkladné e (ps -> pes, dn -> den, lv -> lev)
    if len(stem) >= 2:
        out.add(stem[:-1] + "e" + stem[-1])
        out.add(stem[:-1] + "ě" + stem[-1])
    for a, b in ALTERNATIONS:
        idx = stem.rfind(a)
        if idx >= 0 and idx >= len(stem) - 3:
            out.add(stem[:idx] + b + stem[idx + len(a):])
    return out


def stem_variants(stem: str):
    first = _one_step(stem)
    second = set()
    for s in first:
        second |= _one_step(s)
    return {stem} | first | second


def find_lemma(word: str, noun_bases: set, adj_bases: set):
    """Vrátí (lemma, druh) pokud slovo vypadá jako tvar jiného hesla, jinak None."""
    if word in PRONOUN_FORMS:
        return ("", OTHER)
    for e in NOUN_ENDINGS:
        if word.endswith(e) and len(word) - len(e) >= 2:
            stem = word[: -len(e)]
            for s in stem_variants(stem):
                for le in NOUN_LEMMA_ENDINGS:
                    cand = s + le
                    if cand != word and cand in noun_bases:
                        return (cand, NOUN)
    for e in ADJ_ENDINGS:
        if word.endswith(e) and len(word) - len(e) >= 2:
            stem = word[: -len(e)]
            for le in ("ý", "í"):
                cand = stem + le
                if cand != word and cand in adj_bases:
                    return (cand, ADJ)
    return None


# Vysoce specifické vulgární kořeny – odstraní se i všechny tvary a odvozeniny, které je obsahují na začátku.
VULGAR_ROOTS = re.compile(r"^(z|vy|o|na|po|u|pro|za|do|roz|s)?(kurv|kurev)|^píč|^čurák|^zmrd")


def load_blocklist(path: str) -> set:
    out = set()
    if path and os.path.isfile(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#"):
                    out.add(line.lower())
    return out


def freq_tier(count: int) -> int:
    if count <= 0:
        return 0
    return min(15, int(math.ceil(math.log2(count + 1))))


def main():
    aff, dic, encs, freqp, outdir = sys.argv[1:6]
    blocklist = load_blocklist(sys.argv[6] if len(sys.argv) > 6 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "blocklist.txt"))
    os.makedirs(outdir, exist_ok=True)

    classes, forbidden_flag, nosuggest_flag = parse_aff(aff)
    entries = parse_dic(dic)
    pos_map, encs_lemmas = load_encs(encs)

    freq: dict[str, int] = {}
    with open(freqp, encoding="utf-8") as f:
        for line in f:
            p = line.split()
            if len(p) == 2 and p[1].isdigit():
                freq[p[0]] = int(p[1])

    skipped = Counter()
    forbidden_words = set()
    usable = []
    for w, fl in entries:
        if not w:
            skipped["prázdné"] += 1
            continue
        if forbidden_flag and forbidden_flag in fl:
            forbidden_words.add(w.lower())
            skipped["zakázané (FORBIDDENWORD)"] += 1
            continue
        if nosuggest_flag and nosuggest_flag in fl:
            skipped["vulgarismy (NOSUGGEST)"] += 1
            continue
        if not w[0].isupper() and w.lower() in blocklist:
            skipped["vulgární a urážlivá slova (blocklist.txt)"] += 1
            continue
        if w[0].isupper() and len(w) > 1 and w.isupper():
            skipped["zkratky"] += 1
            continue
        if not is_czech_word(w.lower()):
            skipped["neplatné znaky (tečky, pomlčky, cizí písmena)"] += 1
            continue
        if any(c.isupper() for c in w[1:]):
            skipped["smíšená velká písmena"] += 1
            continue
        usable.append((w, fl))

    # Základní tvary s příznaky – pro určení, zda je heslo bez příznaků jen nepravidelným tvarem.
    noun_bases, adj_bases, all_bases = set(), set(), set()
    flagless = []
    agg: dict[str, list] = {}

    vowels = set("aáeéěiíoóuúůyý")

    def junk(form: str) -> bool:
        # zkratky a citoslovce bez samohlásky (pc, lg, hm, pst) – kromě slabikotvorného r/l (vlk, krk)
        if not any(c in vowels for c in form) and not any(c in "rl" for c in form[1:-1]):
            return True
        # trojí opakování písmene (hmmm, pššš)
        return any(form[i] == form[i + 1] == form[i + 2] for i in range(len(form) - 2))

    junk_count = Counter()

    def add(form, pos, kind, proper):
        if not is_czech_word(form) or len(form) > MAX_LEN or form in forbidden_words:
            return
        if junk(form):
            junk_count["zkratky/citoslovce bez samohlásky"] += 1
            return
        if VULGAR_ROOTS.search(form):
            junk_count["vulgární kořeny"] += 1
            return
        a = agg.get(form)
        if a is None:
            a = [0, 0, False, False]
            agg[form] = a
        bits = 0
        for p in pos:
            bits |= POS_BITS[p]
        if proper:
            if kind == "BASE":
                a[2] = True
            else:
                a[3] = True
        elif kind == "BASE":
            a[0] |= bits
        else:
            a[1] |= bits

    for w, fl in usable:
        has_flags = any(f in classes for f in fl)
        if not has_flags and not w[0].isupper():
            flagless.append(w)
            continue
        res = generate(w, fl, classes, pos_map, encs_lemmas)
        for form, pos, kind, proper in res:
            add(form, pos, kind, proper)
            if kind == "BASE" and not proper:
                all_bases.add(form)
                if NOUN in pos:
                    noun_bases.add(form)
                if ADJ in pos:
                    adj_bases.add(form)

    flagless_stats = Counter()
    # Neohebná/nepravidelná hesla bez příznaků: kandidáti na základní tvar jsou slova z en-cs.
    flagless_lower = {w.lower() for w in flagless}
    VOWELS = set("aáeéěiíoóuúůyý")

    def abbreviation_like(w: str) -> bool:
        if any(c in VOWELS for c in w):
            return False
        # slabikotvorné r/l (vlk, krk, prst, smrt, čtvrt) – uvnitř slova
        return not any(c in "rl" for c in w[1:-1])

    frequent_flagless = {w for w in flagless_lower
                         if freq.get(w, 0) >= 5 and not abbreviation_like(w)}
    flagless_lemma_candidates = {w for w in flagless_lower if w in encs_lemmas} | frequent_flagless
    noun_targets = noun_bases | flagless_lemma_candidates
    adj_targets = adj_bases
    decided = {}
    noun_evidence = set()
    for wl in sorted(flagless_lower):
        hit = find_lemma(wl, noun_targets - {wl}, adj_targets)
        if hit is not None:
            decided[wl] = "FORM"
            if hit[1] == NOUN and hit[0]:
                noun_evidence.add(hit[0])
        elif wl in encs_lemmas and not abbreviation_like(wl):
            decided[wl] = "BASE"
        elif wl in frequent_flagless:
            decided[wl] = "BASE"
        else:
            decided[wl] = "UNKNOWN"
    unknown_samples = []
    for wl in sorted(flagless_lower):
        kind = decided[wl]
        pos = set(pos_map.get(wl) or ())
        if wl in noun_evidence:
            pos.add(NOUN)
        if not pos:
            pos = {OTHER}
        if kind == "FORM":
            add(wl, pos, "FORM", False)
            flagless_stats["tvar"] += 1
        elif kind == "BASE":
            add(wl, pos, "BASE", False)
            flagless_stats["základní tvar"] += 1
        else:
            add(wl, pos, UNKNOWN_KIND, False)
            flagless_stats["bez doložení (" + UNKNOWN_KIND + ")"] += 1
            unknown_samples.append(wl)
    with open(os.path.join(outdir, "flagless_unknown.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(unknown_samples))
    with open(os.path.join(outdir, "flagless_decisions.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(f"{w}\t{decided[w]}" for w in sorted(decided)))

    # Výběr: všechny základní tvary + tvary doložené v korpusu (běžné tvary)
    selected = {}
    dropped_forms = 0
    for form, (bpos, fpos, pbase, pform) in agg.items():
        has_base = bool(bpos) or pbase
        if not has_base and freq.get(form, 0) < 1:
            dropped_forms += 1
            continue
        # u slova se základním tvarem ponecháme i informaci o tvaru jen tehdy, pokud je běžný
        cnt = freq.get(form, 0)
        if not bpos and not pbase:
            pass
        selected[form] = (bpos, fpos, pbase, pform, cnt)

    words = sorted(selected.keys())  # řazení podle kódových bodů Unicode
    txt_bytes = ("\n".join(words) + "\n").encode("utf-8")
    with open(os.path.join(outdir, "cs_words.txt"), "wb") as f:
        f.write(txt_bytes)
    crc = zlib.crc32(txt_bytes) & 0xFFFFFFFF
    with open(os.path.join(outdir, "cs_words.meta"), "wb") as f:
        f.write(b"SFM1")
        f.write(struct.pack("<II", len(words), crc))
        buf = bytearray()
        for wd in words:
            bpos, fpos, pbase, pform, cnt = selected[wd]
            v = (bpos & 0x1F) | ((fpos & 0x1F) << 5)
            if pbase:
                v |= 1 << 10
            if pform:
                v |= 1 << 11
            v |= freq_tier(cnt) << 12
            buf += struct.pack("<H", v)
        f.write(buf)

    st = Counter()
    for wd in words:
        bpos, fpos, pbase, pform, cnt = selected[wd]
        common = bool(bpos or fpos)
        if bpos:
            st["základní tvary (obecná slova)"] += 1
        if fpos and not bpos:
            st["pouze tvary (obecná slova)"] += 1
        if (pbase or pform) and not common:
            st["pouze vlastní jména"] += 1
        if pbase:
            st["vlastní jména – základní tvar"] += 1
        for p, b in POS_BITS.items():
            if bpos & b:
                st[f"základní tvar: {p}"] += 1
    stats = {
        "total_words": len(words),
        "categories": dict(st),
        "skipped_entries": dict(skipped),
        "skipped_forms": dict(junk_count),
        "flagless_entries": dict(flagless_stats),
        "dropped_rare_forms": dropped_forms,
        "source_entries": len(entries),
        "crc32": f"{crc:08x}",
        "bytes": len(txt_bytes),
    }
    with open(os.path.join(outdir, "stats.json"), "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)
    print(json.dumps(stats, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
