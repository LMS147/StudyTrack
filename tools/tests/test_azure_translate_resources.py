import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('translate', Path(__file__).resolve().parents[1] / 'azure_translate_resources.py')
translate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(translate)

class TranslationTests(unittest.TestCase):
    def test_placeholders_are_preserved_and_new_apostrophes_are_escaped(self):
        protected, placeholders = translate.protect(r"Today\'s %1$d of %2$s%%\n")
        result = translate.android_text("L'utilisateur " + protected, placeholders)
        self.assertEqual(result, r"L\'utilisateur Today\'s %1$d of %2$s%%\n")

    def test_dropped_or_duplicated_placeholder_is_rejected(self):
        protected, placeholders = translate.protect('Due %1$s')
        with self.assertRaises(ValueError):
            translate.android_text('missing', placeholders)
        with self.assertRaises(ValueError):
            translate.android_text(protected + protected, placeholders)

    def test_only_missing_resources_are_sent_and_existing_entries_survive(self):
        old_root, old_base, old_targets = translate.ROOT, translate.BASE, translate.TARGETS
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / 'app/src/main/res/values/strings.xml'
            base.parent.mkdir(parents=True)
            base.write_text('<resources><string name="keep">Keep</string><string name="new">Count %1$d</string></resources>')
            target = root / 'app/src/main/res/values-fr/strings.xml'
            target.parent.mkdir(parents=True)
            target.write_text('<resources>\n    <string name="keep">Garder</string>\n</resources>')
            translate.ROOT, translate.BASE, translate.TARGETS = root, base, {'fr': 'fr'}
            sent = []
            def sender(texts, language, key, region):
                sent.extend(texts)
                return ['Nombre ' + texts[0]]
            try:
                translate.run('private', 'uaenorth', sender)
                self.assertEqual(len(sent), 1)
                result = target.read_text()
                self.assertIn('<string name="keep">Garder</string>', result)
                self.assertIn('<string name="new">Nombre Count %1$d</string>', result)
            finally:
                translate.ROOT, translate.BASE, translate.TARGETS = old_root, old_base, old_targets

if __name__ == '__main__':
    unittest.main()
