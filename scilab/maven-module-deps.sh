#!/usr/bin/env bash
# ============================================================================
# maven-module-deps.sh — what does a Java module ACTUALLY depend on?
#
# Dependency discovery for the Ant→Maven migration (Stage 2). Answers, for one
# module: which other Scilab modules and which third-party packages must be on
# its compile classpath — so a module POM can be written from evidence rather
# than from a grep someone retyped from memory.
#
#   ./maven-module-deps.sh graphic_objects
#   ./maven-module-deps.sh gui --verbose      # also list the matching lines
#
# WHY THIS EXISTS. A plain `grep '^import'` is NOT sufficient, and this has now
# cost two stages:
#
#   * Stage 2-b (commons): three modules — history_manager, jvm, preferences —
#     appear only inside Class.forName()/loadClass() STRING literals. They are
#     deliberately NOT compile dependencies; adding them would create a reactor
#     CYCLE, which Maven cannot build at all. An import-only scan does not show
#     them, so nobody knows they were considered and ruled out.
#
#   * Stage 2-e (graphic_objects): CSSParser.java (JFlex-generated) carries
#     `@javax.annotation.Generated("JFlex")` — a fully-qualified ANNOTATION with
#     no import statement at all. javax.annotation left the JDK in Java 11, so
#     the module needs javax.annotation-api on the classpath (Ant already puts
#     it there, build.incl.xml:104). An import-only scan misses it entirely and
#     the failure surfaces as a confusing "package javax.annotation does not
#     exist" mid-build.
#
# So this reports THREE categories, and the third is the one that bites:
#   1. imports            — the obvious ones
#   2. reflection strings — must be ruled OUT of <dependencies>, deliberately
#   3. fully-qualified uses with no import — the silent class
#
# WHAT THIS SCRIPT CANNOT FIND, and no source scan can. A dependency may be
# required with ZERO occurrences of its name anywhere in the module's source.
# Stage 2-f Wave A hit it: helptools' FopConverter.java reads
# MimeConstants.MIME_POSTSCRIPT, and MimeConstants resolves fine — but the
# CONSTANT is inherited from a SUPERINTERFACE, org.apache.xmlgraphics.util.
# MimeConstants, which lives in a different jar (xmlgraphics-commons). javac
# needs that superinterface on the classpath to resolve the inherited member,
# so the module does not compile without a jar it never mentions.
#
# Only the compiler settles that class. Treat a clean run of this script as
# "no KNOWN-detectable dependency is missing", never as "the dependency set is
# complete" — and when javac reports a package you did not expect, check what
# build.incl.xml's compile.classpath already provides before adding anything.
# ============================================================================
set -uo pipefail

MOD="${1:-}"
VERBOSE=0
[ "${2:-}" = "--verbose" ] && VERBOSE=1

if [ -z "$MOD" ]; then
  echo "usage: $0 <module-name> [--verbose]" >&2
  echo "       e.g. $0 graphic_objects" >&2
  exit 2
fi

DEV="$(cd "$(dirname "$0")" && pwd)"
SRC="$DEV/modules/$MOD/src/java"

if [ ! -d "$SRC" ]; then
  echo "ERROR: no Java source at $SRC" >&2
  echo "       (module may be native-only, or the name is wrong)" >&2
  exit 1
fi

FILES=$(find "$SRC" -name '*.java' | wc -l | tr -d ' ')
echo "module: $MOD  ($FILES .java files)"
echo

# --- 1. import statements ---------------------------------------------------
echo "[1] imports (excluding the module's own packages)"
# NOTE: [[:space:]], not \s. BSD sed (macOS) treats \s as a LITERAL 's', so
# `sed -E 's/^\s*import\s+//'` silently fails to strip the import prefix and the
# self-package grep two lines down never fires — a module's own sub-packages then
# leak into its own "imports" list. Found in Stage 2-f Wave E. POSIX classes are
# portable across BSD and GNU; use them in every grep/sed here.
grep -rhE '^[[:space:]]*import[[:space:]]+(static[[:space:]]+)?[a-z]' "$SRC" --include='*.java' 2>/dev/null \
  | sed -E 's/^[[:space:]]*import[[:space:]]+(static[[:space:]]+)?//; s/;.*//' \
  | sed -E 's/\.[A-Z][A-Za-z0-9_]*$//; s/\.[*]$//' \
  | grep -vE "^org\.scilab\.(modules|forge)\.$MOD(\.|$)" \
  | sort -u | sed 's/^/    /'
echo

# --- 2. reflection: must be ruled OUT ---------------------------------------
# These resolve at RUNTIME. Putting them in <dependencies> can introduce a
# reactor cycle (Stage 2-b). Listed so the exclusion is deliberate and visible.
#
# TWO forms are detected, because Stage 2-f Wave D found the second reported a
# FALSE (none):
#   (a) inline    -- Class.forName("org.scilab.modules.x.Y")
#   (b) via const -- static final String PKG = "org.scilab.modules.x.Y";
#                    ... Class.forName(PKG);
# history_browser's EditInScinotesAction.java uses (b): the literal is on a
# `private static final String SCINOTES_PACKAGE = "..."` line, and the forName
# call names the CONSTANT. An inline-literal grep at the call site sees nothing.
echo "[2] reflection targets — do NOT add these to <dependencies>"
# (a) literals passed directly to forName/loadClass
REFL_INLINE=$(grep -rhoE '(Class\.forName|loadClass)\([[:space:]]*"[^"]+"' "$SRC" --include='*.java' 2>/dev/null \
  | grep -oE '"[^"]+"' | tr -d '"')
# (b) String constants whose NAME is later handed to forName/loadClass. Collect
# every `... String IDENT = "value"` declaration, then keep those whose IDENT
# appears as a forName/loadClass argument anywhere in the module.
REFL_CONST=$(python3 - "$SRC" <<'PY'
import os, re, sys
src = sys.argv[1]
decl = re.compile(r'\bString\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"]+)"')
blob = []
for root, _d, files in os.walk(src):
    for fn in files:
        if fn.endswith(".java"):
            blob.append(open(os.path.join(root, fn), encoding="utf-8", errors="replace").read())
text = "\n".join(blob)
consts = dict(decl.findall(text))                      # IDENT -> "value"
used = set(re.findall(r'(?:Class\.forName|loadClass)\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*[\),]', text))
for ident in sorted(used):
    if ident in consts and "." in consts[ident]:       # looks like a class/pkg name
        print(consts[ident])
PY
)
REFL=$(printf '%s\n%s\n' "$REFL_INLINE" "$REFL_CONST" | grep -v '^$' | sort -u)
if [ -n "$REFL" ]; then
  echo "$REFL" | sed 's/^/    /'
  echo "    ^ resolved at runtime. If any names a Scilab module, a compile-time"
  echo "      dependency on it would likely CYCLE — that is why Ant uses reflection."
else
  echo "    (none — but a forName() whose argument is BUILT at runtime, e.g. string"
  echo "      concatenation, still cannot be seen here; grep Class.forName yourself"
  echo "      on a module that touches an unmigrated sibling.)"
fi
echo

# --- 3. THE SILENT CLASS: fully-qualified uses with no import ---------------
# Annotations and inline type references written out in full. Invisible to an
# import scan; they still need the package on the compile classpath.
echo "[3] fully-qualified uses with NO import — the class that bites"
IMPORTED=$(grep -rhE '^[[:space:]]*import[[:space:]]' "$SRC" --include='*.java' 2>/dev/null \
  | sed -E 's/^[[:space:]]*import[[:space:]]+(static[[:space:]]+)?//; s/;.*//' | sort -u)
FQ=$(grep -rhoE '(@|[^A-Za-z0-9_."])((javax|jakarta|java|com|org|jdk|sun)(\.[a-z][A-Za-z0-9_]*)+\.[A-Z][A-Za-z0-9_]*)' \
      "$SRC" --include='*.java' 2>/dev/null \
    | grep -oE '((javax|jakarta|java|com|org|jdk|sun)(\.[a-z][A-Za-z0-9_]*)+\.[A-Z][A-Za-z0-9_]*)' \
    | sort -u)
FOUND=0
JDK_CORE=0
while IFS= read -r fq; do
  [ -z "$fq" ] && continue
  pkg="${fq%.*}"
  # skip if it (or its package) was imported, and skip the module's own packages
  echo "$IMPORTED" | grep -qxF "$fq" && continue
  echo "$IMPORTED" | grep -qxF "$pkg.*" && continue
  case "$pkg" in org.scilab.modules."$MOD"*|org.scilab.forge."$MOD"*) continue;; esac
  # Packages the JDK still ships need nothing on the classpath -- counted, not
  # listed, so the entries that DO need a jar are not buried in java.awt noise.
  #
  # The distinction that matters: some javax.* packages are STILL in the JDK
  # (javax.swing -> java.desktop, javax.xml.parsers -> java.xml, and org.w3c.dom
  # / org.xml.sax likewise), while others were REMOVED at Java 11 and now need a
  # jar -- javax.annotation (JSR-250), javax.xml.bind (JAXB), javax.activation,
  # javax.jws, javax.xml.ws, javax.transaction. Suppressing all of javax.* would
  # hide exactly the case this script exists to catch, so the split is explicit.
  case "$fq" in
    java.*) JDK_CORE=$((JDK_CORE + 1)); continue;;
    # removed from the JDK -- always report these
    javax.annotation.*|javax.xml.bind.*|javax.activation.*|javax.jws.*|javax.xml.ws.*|javax.transaction.*) ;;
    # still shipped by the JDK
    javax.*|org.w3c.dom.*|org.xml.sax.*|org.ietf.jgss.*)
        JDK_CORE=$((JDK_CORE + 1)); continue;;
  esac
  echo "    $fq   <-- needs a jar on the compile classpath"
  [ "$VERBOSE" = "1" ] && grep -rn --include='*.java' -F "$fq" "$SRC" | head -2 | sed 's|^|        |'
  FOUND=1
done <<< "$FQ"
[ "$FOUND" = "0" ] && echo "    (none outside java.*)"
[ "$JDK_CORE" -gt 0 ] && echo "    ($JDK_CORE further java.* references suppressed — always in the JDK)"
echo
echo "NOTE: java.* and the javax.*/org.w3c.dom/org.xml.sax packages the JDK still"
echo "      ships are counted above, not listed. The javax.* packages REMOVED at"
echo "      Java 11 (annotation, xml.bind, activation, jws, xml.ws, transaction)"
echo "      are always reported — that is the case this script exists to catch."
echo
echo "      [3] OVER-REPORTS, deliberately. It scans source TEXT, so it also"
echo "      matches names inside comments, javadoc and string literals. Two real"
echo "      examples: graphic_objects appears to reference org.scilab.modules.gui,"
echo "      which is 12th in topo order to its 7th and would CYCLE — it is javadoc;"
echo "      and Stage 2-b found org.scilab.modules.core.Scilab the same way."
echo "      Over-reporting is the safe direction: you are told to look, and the"
echo "      compiler settles it. Never add a dependency from this list without"
echo "      opening the file. Check build.incl.xml's compile.classpath for what"
echo "      Ant already provides, and reproduce THAT — do not invent a set Ant"
echo "      does not have."
