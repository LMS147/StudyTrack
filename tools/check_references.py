#!/usr/bin/env python3
"""
Static consistency checker for the StudyTrack Android app.

The dev sandbox cannot run Gradle (no JDK/Android SDK, blocked package hosts),
so this script catches the most common Android build failures before pushing:

  1. XML well-formedness for every resource + manifest
  2. Every resource reference in Kotlin (R.string.foo, R.id.bar, ...) resolves
  3. Every @string/@color/@drawable/... reference inside XML resolves
  4. ViewBinding usage: for each Fragment, every `binding.foo` property must
     exist as an id in the layout file that matches the binding class name
  5. Bottom-nav menu ids are navigation destinations in nav_graph.xml
  6. Kotlin `package` declarations match the file's directory
  7. Project-internal imports (com.studytrack.app.*) resolve to declared packages

Exit code 1 on any failure.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SRC = ROOT / "app/src/main/java/com/studytrack/app"

failures = []
def fail(msg):
    failures.append(msg)

# ---------------------------------------------------------------- gather resources
def collect(res_type):
    d = RES / res_type
    if not d.exists():
        return set()
    return {p.stem for p in d.glob("*.xml")}

resources = {
    "string": set(), "color": set(), "dimen": set(), "style": set(),
    "drawable": collect("drawable") | collect("mipmap-anydpi-v26"),
    "mipmap": collect("mipmap-anydpi-v26"),
    "layout": collect("layout"),
    "menu": collect("menu"),
    "navigation": collect("navigation"),
    "id": set(),
}

# color state lists / selectors live in res/color/, not res/values/
resources["color"] |= collect("color")

# Resources generated at build time by the google-services plugin (from
# google-services.json) — not present in res/, so treat them as defined.
resources["string"] |= {
    "default_web_client_id", "google_app_id", "gcm_defaultSenderId",
    "google_api_key", "project_id", "firebase_database_url", "ga_trackingId",
}

seen_definitions = {}

# parse values files for string/color/dimen/style — every values* variant
# (values/, values-night/, values-v27/, …) contributes definitions
for vf in [f for d in sorted(RES.glob("values*")) for f in d.glob("*.xml")]:
    try:
        tree = ET.parse(vf)
    except ET.ParseError as e:
        fail(f"XML parse error in {vf}: {e}")
        continue
    for el in tree.getroot():
        if el.tag in ("string", "color", "dimen", "style"):
            name = el.get("name")
            if name:
                # A duplicate name in the same file is a build error in AAPT2
                # ("Found item String/x more than one time") — catch it here.
                # Scoped per folder: values-night/ is *expected* to redefine
                # the names from values/, but a repeat inside one folder is an
                # AAPT2 error.
                scope = (vf.parent.name, el.tag)
                if name in seen_definitions.get(scope, set()):
                    fail(f"{vf}: duplicate <{el.tag}> '{name}'")
                seen_definitions.setdefault(scope, set()).add(name)
                resources[el.tag].add(name)

# every @+id declared in layouts & menus
for lf in (RES / "layout").glob("*.xml"):
    try:
        content = lf.read_text()
    except OSError as e:
        fail(f"Cannot read {lf}: {e}")
        continue
    try:
        ET.fromstring(content)
    except ET.ParseError as e:
        fail(f"XML parse error in {lf}: {e}")
    resources["id"] |= set(re.findall(r"@_\+id/(\w+)", content)) or set(re.findall(r"@\\+id/(\w+)", content)) or set(re.findall(r"\+id/(\w+)", content))

for mf in (RES / "menu").glob("*.xml") if (RES / "menu").exists() else []:
    content = mf.read_text()
    try:
        ET.fromstring(content)
    except ET.ParseError as e:
        fail(f"XML parse error in {mf}: {e}")
    resources["id"] |= set(re.findall(r"\+id/(\w+)", content))

# navigation destination + action ids (they generate R.id entries too)
nav_dest_ids = set()
if (RES / "navigation").exists():
    for nf in (RES / "navigation").glob("*.xml"):
        try:
            tree = ET.parse(nf)
        except ET.ParseError as e:
            fail(f"XML parse error in {nf}: {e}")
            continue
        for el in tree.getroot().iter():
            rid = el.get("{http://schemas.android.com/apk/res/android}id", "")
            m = re.search(r"id/(\w+)", rid)
            if not m:
                continue
            name = m.group(1)
            if el.tag.endswith("fragment") or el.tag.endswith("activity") or el.tag.endswith("dialog"):
                nav_dest_ids.add(name)
                resources["id"].add(name)  # destinations are R.id targets
            elif el.tag.endswith("action") or el.tag.endswith("navigation"):
                resources["id"].add(name)  # actions & the graph itself

# manifest well-formed
manifest = ROOT / "app/src/main/AndroidManifest.xml"
try:
    ET.parse(manifest)
except ET.ParseError as e:
    fail(f"XML parse error in AndroidManifest.xml: {e}")

# ---------------------------------------------------------------- check XML references
ref_re = re.compile(r"@(string|color|dimen|drawable|mipmap|layout|menu|navigation)/([\w.]+)")
for xml_file in RES.rglob("*.xml"):
    content = xml_file.read_text()
    try:
        ET.fromstring(content)
    except ET.ParseError as e:
        fail(f"XML parse error in {xml_file}: {e}")
        continue
    for kind, name in ref_re.findall(content):
        if kind == "dimen" and name.startswith("?attr"):
            continue
        if name not in resources.get(kind, set()):
            fail(f"{xml_file.relative_to(ROOT)}: unresolved @{'@' if False else ''}{kind}/{name}")

# ---------------------------------------------------------------- check Kotlin references
kt_files = sorted(SRC.rglob("*.kt"))
r_ref_re = re.compile(r"\bR\.(string|color|dimen|drawable|mipmap|layout|menu|navigation|style|id)\.(\w+)")
android_r_ref_re = re.compile(r"\bandroid\.R\.[\w.]+")

layout_ids = {}  # layout file -> set of ids
for lf in (RES / "layout").glob("*.xml"):
    content = lf.read_text()
    layout_ids[lf.stem] = set(re.findall(r"\+id/(\w+)", content))

def snake_to_camel(s):
    parts = s.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])

binding_class_re = re.compile(r"Fragment(\w+)Binding")
binding_prop_re = re.compile(r"(?<!data)binding\.(\w+)\b")  # not "databinding."

kotlin_packages = {}  # package name -> exists
type_decl_re = re.compile(r"^(?:@\w+(?:\([^)]*\))?\s+)*(?:public\s+|internal\s+|private\s+)?(?:sealed\s+|abstract\s+|open\s+|data\s+|value\s+|enum\s+|fun\s+)*(?:class|interface|object)\s+(\w+)", re.M)

package_files = set()
for kt in kt_files:
    text = kt.read_text()
    m = re.search(r"^package\s+([\w.]+)", text, re.M)
    if not m:
        fail(f"{kt.relative_to(ROOT)}: missing package declaration")
        continue
    pkg = m.group(1)
    relative_dir = str(kt.parent.relative_to(SRC))
    expected_pkg = "com.studytrack.app" + (("." + relative_dir.replace("/", ".")) if relative_dir != "." else "")
    if pkg != expected_pkg:
        fail(f"{kt.relative_to(ROOT)}: package '{pkg}' does not match directory '{expected_pkg}'")
    # a Kotlin file may declare several top-level types
    package_files.add((pkg, kt.stem))
    for type_name in type_decl_re.findall(text):
        package_files.add((pkg, type_name))

declared_pkgs = {p for p, _ in package_files}

for kt in kt_files:
    text = kt.read_text()
    rel = kt.relative_to(ROOT)
    stripped = android_r_ref_re.sub("", text)  # android.R.* are framework resources
    for kind, name in r_ref_re.findall(stripped):
        if name not in resources.get(kind, set()):
            fail(f"{rel}: unresolved R.{kind}.{name}")
    for imp in re.findall(r"^import\s+(com\.studytrack\.app\.[\w.]+)", text, re.M):
        if imp in ("com.studytrack.app",):
            continue
        # generated sources: ViewBinding classes + safe-args Directions/Args
        if imp.startswith("com.studytrack.app.databinding") or imp in ("com.studytrack.app.R", "com.studytrack.app.BuildConfig"):
            continue
        if imp.endswith("Directions") or imp.endswith("Args"):
            continue
        pkg = imp.rsplit(".", 1)[0]
        leaf = imp.rsplit(".", 1)[1]
        if leaf.isupper() or leaf[0].isupper():  # class import
            if (pkg, leaf) not in package_files:
                fail(f"{rel}: import '{imp}' does not match any project file")
        else:  # wildcard-ish or function import: package must exist
            if pkg not in declared_pkgs and not any(p.startswith(imp + ".") for p in declared_pkgs):
                fail(f"{rel}: import '{imp}' package not found in project")

    # ViewBinding property check per fragment file
    bm = re.search(r"private var _binding:\s*Fragment(\w+)Binding", text)
    if bm:
        layout_name = "fragment_" + re.sub(r"(?<!^)(?=[A-Z])", "_", bm.group(1)).lower()
        if layout_name not in layout_ids:
            fail(f"{rel}: no layout '{layout_name}' for binding Fragment{bm.group(1)}Binding")
        else:
            valid_props = layout_ids[layout_name] | {"root"}
            for prop in binding_prop_re.findall(text):
                if prop not in {snake_to_camel(i) for i in valid_props}:
                    fail(f"{rel}: binding.{prop} has no matching id in {layout_name}.xml")

# ---------------------------------------------------------------- nav graph attribute lint
APP = "{http://schemas.android.com/apk/res-auto}"
ANDROID = "{http://schemas.android.com/apk/res/android}"
VALID_ACTION_ATTRS = {
    ANDROID + "id",
    APP + "destination", APP + "popUpTo", APP + "popUpToInclusive", APP + "popUpToSaveState",
    APP + "launchSingleTop", APP + "enterAnim", APP + "exitAnim", APP + "popEnterAnim", APP + "popExitAnim",
}
VALID_FRAGMENT_ATTRS = {
    ANDROID + "id", ANDROID + "name", ANDROID + "label",
    APP + "layout", APP + "toolbarColor",
}
VALID_ARG_ATTRS = {
    ANDROID + "name", ANDROID + "defaultValue",
    APP + "argType", APP + "nullable",
}
if (RES / "navigation").exists():
    for nf in (RES / "navigation").glob("*.xml"):
        try:
            tree = ET.parse(nf)
        except ET.ParseError:
            continue
        for el in tree.getroot().iter():
            tag = el.tag.split("}")[-1]
            allowed = {"action": VALID_ACTION_ATTRS, "fragment": VALID_FRAGMENT_ATTRS,
                       "argument": VALID_ARG_ATTRS}.get(tag)
            if not allowed:
                continue
            for attr in el.keys():
                if attr not in allowed:
                    fail(f"{nf.name}: unexpected attribute '{attr}' on <{tag}> "
                         f"(typo? e.g. popUpToInclusive, not inclusive)")

# ---------------------------------------------------------------- bottom nav vs nav graph
menu = RES / "menu/menu_bottom_nav.xml"
if menu.exists() and nav_dest_ids:
    content = menu.read_text()
    for mid in re.findall(r"\+id/(\w+)", content):
        if mid not in nav_dest_ids:
            fail(f"menu_bottom_nav.xml: id '{mid}' is not a navigation destination")

# ---------------------------------------------------------------- hardcoded colour lint
# Dark mode is implemented with theme attributes and values/values-night colour
# resources, so a literal colour in a layout or a menu is always a bug.
LITERAL_COLOUR = re.compile(r'"(#[0-9A-Fa-f]{3,8})"')
for folder in ("layout", "menu", "drawable"):
    if not (RES / folder).exists():
        continue
    for xf in sorted((RES / folder).rglob("*.xml")):
        text = xf.read_text()
        # Vendor logo vectors opt out with a comment: their colours are fixed
        # brand values and must not follow the theme.
        if re.search(r"brand colour|brand color|fixed vendor palette|fixed .* glyph", text, re.I):
            continue
        for lineno, line in enumerate(text.splitlines(), start=1):
            m = LITERAL_COLOUR.search(line)
            if m:
                fail(f"{xf.relative_to(RES)}:{lineno}: hardcoded colour {m.group(1)} "
                     f"— use a @color resource (add a values-night variant if it differs)")

# ------------------------------------------------- per-account (ownerUid) isolation
# Every row of study data belongs to exactly one Firebase UID. A Room query that
# forgets its `ownerUid` predicate is a data leak between accounts, and it is
# invisible at runtime until two people share a device. Gradle cannot catch it
# (the SQL is valid either way), so it is enforced here.
DAO_DIR = SRC / "data/local/dao"
ENTITY_DIR = SRC / "data/local/entity"
SCOPED_TABLES = {"tasks", "subjects", "progress", "study_sessions"}


def strip_kotlin_comments(text):
    """Removes /* ... */ and // comments so docs cannot be mistaken for code.

    The DAO KDoc deliberately quotes a forbidden query as an example
    (`@Query("SELECT * FROM tasks")`); without this the checker would flag its
    own documentation.
    """
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    out = []
    for line in text.splitlines():
        # Only treat // as a comment start when it is not inside a string.
        in_string = False
        cut = len(line)
        i = 0
        while i < len(line) - 1:
            ch = line[i]
            if ch == '"' and (i == 0 or line[i - 1] != "\\"):
                in_string = not in_string
            elif not in_string and line[i:i + 2] == "//":
                cut = i
                break
            i += 1
        out.append(line[:cut])
    return "\n".join(out)


def extract_annotation_bodies(text, annotation):
    """Yields (annotation_args, text_after_annotation) for each @annotation(...)."""
    results = []
    for m in re.finditer(re.escape(annotation) + r"\s*\(", text):
        i = m.end()
        depth = 1
        while i < len(text) and depth:
            if text[i] == "(":
                depth += 1
            elif text[i] == ")":
                depth -= 1
            i += 1
        results.append((text[m.end():i - 1], text[i:]))
    return results


def join_string_literals(body):
    """Concatenates the literals of a multi-line Kotlin string expression."""
    return "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', body))


def method_params(rest):
    """The parameter list of the function that follows an annotation."""
    m = re.search(r"\b(?:suspend\s+)?fun\s+\w+\s*\(", rest)
    if not m:
        return ""
    i = m.end()
    depth = 1
    while i < len(rest) and depth:
        if rest[i] == "(":
            depth += 1
        elif rest[i] == ")":
            depth -= 1
        i += 1
    return rest[m.end():i - 1]


OWNER_PREDICATE = re.compile(r"\bownerUid\s*(?:=|!=|<>|\bIN\b)", re.IGNORECASE)
TABLE_REF = re.compile(
    r"\b(?:FROM|UPDATE|INTO|JOIN)\s+([A-Za-z_][A-Za-z0-9_]*)", re.IGNORECASE
)

if DAO_DIR.exists():
    for dao_file in sorted(DAO_DIR.glob("*.kt")):
        code = strip_kotlin_comments(dao_file.read_text())
        for body, rest in extract_annotation_bodies(code, "@Query"):
            sql = join_string_literals(body)
            if not sql.strip():
                continue
            scoped = {t.lower() for t in TABLE_REF.findall(sql)} & SCOPED_TABLES
            if not scoped:
                continue
            if not OWNER_PREDICATE.search(sql):
                fail(f"{dao_file.name}: query on {sorted(scoped)} has no ownerUid "
                     f"predicate — it would read or write every account's rows: "
                     f"{' '.join(sql.split())[:90]}")
            elif "ownerUid" not in method_params(rest):
                fail(f"{dao_file.name}: query on {sorted(scoped)} binds :ownerUid but "
                     f"the method takes no ownerUid parameter: "
                     f"{' '.join(sql.split())[:90]}")

# Every entity holding study data must be keyed by ownerUid, so two accounts can
# never collide on the same row id. account_records is exempt: it is the device's
# account registry and holds no study data.
PRIMARY_KEYS_LIST = re.compile(r"primaryKeys\s*=\s*\[([^\]]*)\]")
PRIMARY_KEY_ANNO = re.compile(r"((?:@\w+(?:\([^)]*\))?\s*)+)val\s+ownerUid\b")

if ENTITY_DIR.exists():
    for entity_file in sorted(ENTITY_DIR.glob("*.kt")):
        code = strip_kotlin_comments(entity_file.read_text())
        if "@Entity" not in code:
            continue

        # (a) Room rejects any @Entity with no primary key at KSP time. This
        # applies to every entity, including the account registry.
        has_pk = bool(PRIMARY_KEYS_LIST.search(code)) or "@PrimaryKey" in code
        if not has_pk:
            fail(f"{entity_file.name}: @Entity has no primary key — Room fails to "
                 f"compile it (\"An entity must have at least 1 field annotated "
                 f"with @PrimaryKey\")")
            continue

        # (b) Isolation: entities holding study data must be keyed by ownerUid.
        # account_records is exempt from this one only — it is the device's
        # account registry and holds no study data.
        if entity_file.stem == "AccountRecordEntity":
            continue
        if 'name = "ownerUid"' not in code:
            fail(f"{entity_file.name}: @Entity is missing an ownerUid column — every "
                 f"row of study data must be scoped to a Firebase UID")
            continue
        listed = PRIMARY_KEYS_LIST.search(code)
        keyed_by_list = bool(listed) and '"ownerUid"' in listed.group(1)
        anno = PRIMARY_KEY_ANNO.search(code)
        keyed_by_anno = bool(anno) and "@PrimaryKey" in anno.group(1)
        if not (keyed_by_list or keyed_by_anno):
            fail(f"{entity_file.name}: ownerUid must be part of the primary key "
                 f"(primaryKeys = [..., \"ownerUid\"] or @PrimaryKey), so two accounts "
                 f"can never collide on the same row id")

# ---------------------------------------------------------------- report
if failures:
    print(f"FAILED: {len(failures)} problem(s)")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print(f"OK: {len(kt_files)} Kotlin files, {len(layout_ids)} layouts, "
      f"{len(resources['string'])} strings, {len(resources['drawable'])} drawables — all references resolve")
