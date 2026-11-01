#!/usr/bin/env python3
"""Fill missing Android string resources with Azure Translator (development only)."""
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / 'app/src/main/res/values/strings.xml'
TARGETS = {'af': 'af', 'ja': 'ja', 'nso': 'b+nso', 'tn': 'tn', 'es': 'es', 'fr': 'fr'}
# Preserve Android format arguments, escaped newlines and apostrophes exactly.
PROTECTED = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|\\[ntr\\\'\"]')
TOKEN = re.compile(r'STPH\d{4}X')


def protect(text):
    parts = []
    def replace(match):
        token = f'STPH{len(parts):04d}X'
        parts.append((token, match.group()))
        return token
    protected = PROTECTED.sub(replace, text)
    if TOKEN.search(text):
        raise ValueError('Source text contains a reserved placeholder token')
    return protected, parts


def android_text(translated, parts):
    expected = sorted(x for x, _ in parts)
    if sorted(TOKEN.findall(translated)) != expected:
        raise ValueError('Translation changed a protected formatting placeholder')
    # Azure may return apostrophes or quotes that Android's resource parser requires escaped.
    result = translated.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    for token, value in parts:
        result = result.replace(token, value)
    return escape(result)


def translate_batch(texts, language, key, region):
    query = urllib.parse.urlencode({'api-version': '3.0', 'from': 'en', 'to': language})
    request = urllib.request.Request(
        'https://api.cognitive.microsofttranslator.com/translate?' + query,
        data=json.dumps([{'Text': text} for text in texts], ensure_ascii=False).encode('utf-8'),
        headers={'Content-Type': 'application/json', 'Ocp-Apim-Subscription-Key': key,
                 'Ocp-Apim-Subscription-Region': region}, method='POST')
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.load(response)
    except urllib.error.HTTPError as error:
        # Do not log request headers, keys, or provider response bodies.
        raise RuntimeError(f'Azure Translator returned HTTP {error.code} for {language}') from None
    if len(data) != len(texts):
        raise ValueError(f'Azure Translator returned an unexpected response count for {language}')
    return [item['translations'][0]['text'] for item in data]


def run(key, region, sender=translate_batch):
    source_root = ET.parse(BASE).getroot()
    source = {}
    for item in source_root:
        if item.tag != 'string' or item.get('translatable') == 'false':
            continue
        if len(item):
            raise ValueError(f'Nested markup needs manual translation: {item.get("name")}')
        source[item.attrib['name']] = item.text or ''
    total = 0
    for language, qualifier in TARGETS.items():
        path = ROOT / f'app/src/main/res/values-{qualifier}/strings.xml'
        document = ET.parse(path)
        existing = {item.get('name') for item in document.getroot() if item.tag == 'string'}
        missing = [(name, text) for name, text in source.items() if name not in existing]
        print(f'{language}: {len(missing)} missing strings', flush=True)
        if not missing:
            continue
        generated = []
        for index in range(0, len(missing), 50):
            batch = missing[index:index + 50]
            protected = [protect(text) for _, text in batch]
            result = sender([text for text, _ in protected], language, key, region)
            for (name, _), (_, parts), text in zip(batch, protected, result, strict=True):
                try:
                    safe_text = android_text(text, parts)
                except ValueError:
                    # Retry separately; never insert a translation with damaged placeholders.
                    retry = sender([dict(batch)[name] if not parts else protect(dict(batch)[name])[0]], language, key, region)
                    try:
                        safe_text = android_text(retry[0], parts)
                    except ValueError:
                        print(f'{language}: skipped {name}; formatting needs manual review', flush=True)
                        continue
                generated.append(f'    <string name="{name}">{safe_text}</string>')
        # Append text without rewriting existing translations or their Android escapes.
        original = path.read_text(encoding='utf-8')
        if original.count('</resources>') != 1:
            raise ValueError(f'Unexpected resources document in {path}')
        path.write_text(original.replace('</resources>', '\n'.join(generated) + '\n</resources>'), encoding='utf-8')
        total += len(generated)
    print(f'Generated {total} missing strings. Review translations before release.')


if __name__ == '__main__':
    key = os.environ.get('AZURE_TRANSLATOR_KEY', '').strip()
    region = os.environ.get('AZURE_TRANSLATOR_REGION', 'uaenorth').strip()
    if not key:
        sys.exit('AZURE_TRANSLATOR_KEY is missing. Add it to GitHub Actions repository secrets.')
    try:
        run(key, region)
    except (RuntimeError, ValueError, OSError, KeyError) as error:
        sys.exit(str(error))
