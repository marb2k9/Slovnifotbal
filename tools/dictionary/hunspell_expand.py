#!/usr/bin/env python3
"""Rozgeneruje český hunspell slovník (cs_CZ.aff + cs_CZ.dic) na jednotlivé tvary.

Výstupem je iterátor záznamů (tvar, pos, kind), kde:
  pos  = množina slovních druhů (NOUN, ADJ, VERB, ADV, OTHER)
  kind = 'BASE' (základní/slovníkový tvar) nebo 'FORM' (odvozený, skloňovaný, časovaný tvar)
Vlastní jména (velké počáteční písmeno v .dic) jsou označena proper=True.

Skript je čistě offline – čte jen lokální soubory, které dostane jako argumenty.
"""
from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field

CZECH_LOWER = "aábcčdďeéěfghiíjklmnňoópqrřsštťuúůvwxyýzž"
CZECH_SET = set(CZECH_LOWER)

NOUN, ADJ, VERB, ADV, OTHER = "NOUN", "ADJ", "VERB", "ADV", "OTHER"

NOUN_FLAGS = set("PVUDHLSQZM")
VERB_FLAGS = set("AJBOTX")
ADJ_FLAGS = set("Yé")


@dataclass
class Rule:
    strip: str
    add: str
    cont: str
    cond: list  # list of (negated, set_of_chars) or None for any char


@dataclass
class AffixClass:
    kind: str  # 'PFX' / 'SFX'
    flag: str
    cross: bool
    rules: list = field(default_factory=list)


def parse_condition(cond: str):
    if cond == ".":
        return []
    out = []
    i = 0
    while i < len(cond):
        ch = cond[i]
        if ch == "[":
            j = cond.index("]", i)
            body = cond[i + 1 : j]
            neg = body.startswith("^")
            if neg:
                body = body[1:]
            out.append((neg, set(body)))
            i = j + 1
        elif ch == ".":
            out.append(None)
            i += 1
        else:
            out.append((False, {ch}))
            i += 1
    return out


def cond_match_end(word: str, cond) -> bool:
    if len(cond) > len(word):
        return False
    tail = word[len(word) - len(cond):]
    for c, spec in zip(tail, cond):
        if spec is None:
            continue
        neg, chars = spec
        if (c in chars) == neg:
            return False
    return True


def cond_match_start(word: str, cond) -> bool:
    if len(cond) > len(word):
        return False
    for c, spec in zip(word, cond):
        if spec is None:
            continue
        neg, chars = spec
        if (c in chars) == neg:
            return False
    return True


def parse_aff(path: str):
    classes: dict[str, AffixClass] = {}
    forbidden_flag = None
    nosuggest_flag = None
    with open(path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    i = 0
    while i < len(lines):
        raw = lines[i]
        line = raw.split("#", 1)[0].strip() if not raw.startswith("#") else ""
        i += 1
        if not line:
            continue
        parts = line.split()
        if parts[0] == "FORBIDDENWORD":
            forbidden_flag = parts[1]
        elif parts[0] == "NOSUGGEST":
            nosuggest_flag = parts[1]
        elif parts[0] in ("PFX", "SFX") and len(parts) >= 4 and parts[2] in ("Y", "N"):
            kind, flag, cross, count = parts[0], parts[1], parts[2] == "Y", int(parts[3])
            ac = AffixClass(kind, flag, cross)
            read = 0
            while read < count and i < len(lines):
                r = lines[i].split("#", 1)[0].strip()
                i += 1
                if not r:
                    continue
                rp = r.split()
                if rp[0] != kind or rp[1] != flag:
                    raise ValueError(f"Neočekávaný řádek v {flag}: {r}")
                strip = "" if rp[2] == "0" else rp[2]
                add = rp[3]
                cont = ""
                if "/" in add:
                    add, cont = add.split("/", 1)
                if add == "0":
                    add = ""
                cond = parse_condition(rp[4] if len(rp) > 4 else ".")
                ac.rules.append(Rule(strip, add, cont, cond))
                read += 1
            classes[flag] = ac
    return classes, forbidden_flag, nosuggest_flag


def parse_dic(path: str):
    entries = []
    with open(path, encoding="utf-8") as f:
        first = True
        for line in f:
            line = line.rstrip("\n").rstrip("\r")
            if first:
                first = False
                if line.strip().isdigit():
                    continue
            if not line.strip():
                continue
            # hunspell: lomítko odděluje příznaky (escapované \/ v cs slovníku nejsou)
            if "/" in line:
                w, fl = line.split("/", 1)
            else:
                w, fl = line, ""
            fl = fl.split()[0] if fl.strip() else ""
            entries.append((w.strip(), fl))
    return entries


def apply_sfx(word: str, ac: AffixClass):
    for r in ac.rules:
        if r.strip and not word.endswith(r.strip):
            continue
        if not cond_match_end(word, r.cond):
            continue
        base = word[: len(word) - len(r.strip)] if r.strip else word
        yield base + r.add, r


def apply_pfx(word: str, ac: AffixClass):
    for r in ac.rules:
        if r.strip and not word.startswith(r.strip):
            continue
        if not cond_match_start(word, r.cond):
            continue
        rest = word[len(r.strip):] if r.strip else word
        yield r.add + rest, r


def is_czech_word(w: str) -> bool:
    return bool(w) and all(c in CZECH_SET for c in w)


def stem_pos(word_lower: str, flags: str, encs_pos: dict) -> set:
    fs = set(flags)
    pos = set()
    if "F" in fs:
        # číslovky typu dvacet, třicet (předpony jedna-, dva- …)
        return {OTHER}
    if fs & VERB_FLAGS:
        pos.add(VERB)
    if fs & ADJ_FLAGS:
        pos.add(ADJ)
    if fs & NOUN_FLAGS:
        pos.add(NOUN)
    # K: střední rod (kuře, kotě) – u přídavných jmen jen odvození na -ost
    if "K" in fs and ADJ not in pos:
        pos.add(NOUN)
    # C: dveře, brýle, dříví – u sloves jen příčestí
    if "C" in fs and VERB not in pos:
        pos.add(NOUN)
    # I: 1. pád mn. č. životných podst. jmen, u sloves na -t archaický infinitiv na -ti
    if "I" in fs and not pos:
        if word_lower.endswith("t") or word_lower.endswith("ct"):
            pos.add(VERB)
        else:
            pos.add(NOUN)
    if "I" in fs and "N" in fs and (word_lower.endswith("t")) and NOUN in pos:
        # např. "stát" = podstatné jméno i sloveso
        pos.add(VERB)
    if not pos:
        p = encs_pos.get(word_lower)
        if p:
            pos |= p
        elif "N" in fs and (word_lower.endswith("t") or word_lower.endswith("ct")):
            pos.add(VERB)
        elif "R" in fs:
            pos.add(NOUN)
        else:
            pos.add(OTHER)
    return pos


def generate(entry_word: str, flags: str, classes, encs_pos, encs_lemmas):
    """Vrátí seznam (tvar_lower, frozenset(pos), kind, proper)."""
    proper = entry_word[:1].isupper()
    w = entry_word
    wl = w.lower()
    out = []
    fs = [f for f in flags if f in classes]
    if not fs:
        if proper:
            out.append((wl, frozenset({NOUN}), "BASE", True))
        else:
            if wl in encs_lemmas:
                out.append((wl, frozenset(encs_pos.get(wl) or {OTHER}), "BASE", False))
            else:
                # nepravidelné tvary (koně, psa, tebou …) – vedeno jako tvar
                out.append((wl, frozenset(encs_pos.get(wl) or {OTHER}), "FORM", False))
        return out

    spos = frozenset(stem_pos(wl, flags, encs_pos))
    out.append((wl, spos, "BASE", proper))

    pfx_root = [classes[f] for f in fs if classes[f].kind == "PFX"]
    sfx_root = [classes[f] for f in fs if classes[f].kind == "SFX"]

    def classify_sfx(flag: str, form: str, rule: Rule, pos: frozenset):
        """Určí slovní druh a druh tvaru pro tvar vzniklý příponou."""
        if ADJ in pos and flag == "R":
            return frozenset({ADV}), "BASE"  # rychlý -> rychle
        if ADJ in pos and flag == "K":
            if form.endswith("ost"):
                return frozenset({NOUN}), "BASE"  # rychlý -> rychlost
            return frozenset({NOUN}), "FORM"
        if flag == "y":
            if form.endswith("eji") or form.endswith("ěji"):
                return frozenset({ADV}), "FORM"
            return frozenset({ADJ}), "FORM"
        return pos, "FORM"

    def classify_cont(form: str, pos: frozenset):
        # druhá úroveň: přivlastňovací přídavná jména (-ův/-ova), přechodníky/příčestí (-ící/-oucí)
        if VERB in pos:
            return frozenset({VERB}), "FORM"
        return frozenset({ADJ}), "FORM"

    level1 = []  # (form, pos, kind, cross_ok, cont)
    for ac in sfx_root:
        for form, rule in apply_sfx(w, ac):
            pos, kind = classify_sfx(ac.flag, form, rule, spos)
            level1.append((form, pos, kind, ac.cross, rule.cont))
            out.append((form.lower(), pos, kind, proper))

    level2 = []
    for form, pos, kind, cross, cont in level1:
        if not cont:
            continue
        cpos, ckind = classify_cont(form, pos)
        for cf in cont:
            if cf not in classes:
                continue
            ac = classes[cf]
            if ac.kind == "SFX":
                for form2, rule2 in apply_sfx(form, ac):
                    p2 = pos if ac.flag == "Y" and VERB not in pos and ADJ in pos else cpos
                    if ac.flag == "Y" and ADJ in pos:
                        p2 = pos
                    level2.append((form2, p2, "FORM", cross and ac.cross))
                    out.append((form2.lower(), p2, "FORM", proper))
            else:  # PFX v pokračovací třídě (např. nej- u 2. stupně)
                for form2, rule2 in apply_pfx(form, ac):
                    out.append((form2.lower(), pos, "FORM", proper))

    # předpony z kořene (ne-, nej-, nejne-, jedna-/dva- …)
    for pac in pfx_root:
        numeral = pac.flag == "F"
        for pform, prule in apply_pfx(w, pac):
            if numeral:
                out.append((pform.lower(), frozenset({OTHER}), "BASE", proper))
            else:
                out.append((pform.lower(), spos, "FORM", proper))
        if not pac.cross:
            continue
        for form, pos, kind, cross, cont in level1:
            if not cross:
                continue
            for pform, prule in apply_pfx(form, pac):
                out.append((pform.lower(), pos if not numeral else frozenset({OTHER}),
                            "FORM", proper))
        for form2, p2, k2, cross2 in level2:
            if not cross2:
                continue
            for pform, prule in apply_pfx(form2, pac):
                out.append((pform.lower(), p2, "FORM", proper))
    return out


def load_encs(path: str):
    """Načte česko-anglický slovník Svobodné slovníky a vrátí (pos_map, lemma_set)."""
    pos_map: dict[str, set] = {}
    lemmas = set()
    tagmap = {"n:": NOUN, "adj:": ADJ, "v:": VERB, "adv:": ADV,
              "num:": OTHER, "prep:": OTHER, "conj:": OTHER, "interj:": OTHER,
              "pron:": OTHER, "part:": OTHER}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 2:
                continue
            cz = cols[1].strip()
            if not cz or " " in cz:
                continue
            czl = cz.lower()
            if not is_czech_word(czl):
                continue
            if cz[:1].isupper():
                continue
            lemmas.add(czl)
            special = cols[2] if len(cols) > 2 else ""
            for tok in special.split():
                if tok in tagmap:
                    pos_map.setdefault(czl, set()).add(tagmap[tok])
    return pos_map, lemmas


if __name__ == "__main__":
    aff, dic, encs = sys.argv[1:4]
    classes, forb, nosug = parse_aff(aff)
    print("affix classes:", len(classes), "forbidden:", forb, "nosuggest:", nosug)
    entries = parse_dic(dic)
    print("entries:", len(entries))
    pos_map, lemmas = load_encs(encs)
    print("en-cs lemmas:", len(lemmas), "with pos:", len(pos_map))
    for word in sys.argv[4:]:
        for e in entries:
            if e[0] == word:
                res = generate(e[0], e[1], classes, pos_map, lemmas)
                print(word, e[1], len(res))
                seen = {}
                for form, pos, kind, proper in res:
                    seen.setdefault((form, kind), set()).update(pos)
                print("  ", sorted(f"{f}:{k[0]}:{'/'.join(sorted(p))}" for (f, k), p in seen.items()))
