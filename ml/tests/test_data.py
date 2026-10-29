"""Tests for the dataset tooling. Run from the repo root: python -m unittest discover -s ml/tests -t ."""
import unittest

from ml.data import leakcheck, privacy_guard, schema

RUBRIC = schema.load_rubric()
CATALOGUE = schema.load_catalogue()


def item(**over):
    base = {
        "id": "en-dev-00001", "split": "dev", "lang": "en", "region": "US", "package": "com.ubercab",
        "app_name": "Uber", "app_category": "ride_maps", "usecase": "ride_arriving", "sender_type": "service",
        "title": "Your driver is arriving", "text": "Maya is 2 minutes away in a white Toyota",
        "urgentIn": ["Commute"], "difficulty": "easy", "template_family": "t-ride-1", "provenance": "test",
    }
    base.update(over)
    return base


class PrivacyGuardTests(unittest.TestCase):
    def test_clean_text_passes(self):
        self.assertEqual(privacy_guard.check_text("Maya is 2 minutes away. Fare 4.50"), [])

    def test_reserved_phone_and_masks_pass(self):
        self.assertEqual(privacy_guard.check_text("Call +1 555-0142 or +91 9XXXXXXXXX"), [])
        self.assertEqual(privacy_guard.check_text("Missed call from 07700 900123"), [])

    def test_real_looking_phone_fails(self):
        self.assertTrue(privacy_guard.check_text("Call 9876543210 now"))

    def test_card_number_fails(self):
        self.assertTrue(privacy_guard.check_text("Card 4539 1488 0343 6467 charged"))

    def test_email_and_links(self):
        self.assertEqual(privacy_guard.check_text("Write to help@example.com"), [])
        self.assertTrue(privacy_guard.check_text("Write to someone@gmail.com"))
        self.assertEqual(privacy_guard.check_text("Claim at http://prize.example.invalid/x"), [])
        self.assertTrue(privacy_guard.check_text("Claim at http://bit.ly/abc"))

    def test_otp_out_of_scope(self):
        self.assertTrue(privacy_guard.check_text("Your OTP is 123456"))


class SchemaTests(unittest.TestCase):
    def test_valid_item(self):
        self.assertEqual(schema.validate_item(item(), RUBRIC, CATALOGUE), [])

    def test_label_must_match_rubric(self):
        errs = schema.validate_item(item(urgentIn=[]), RUBRIC, CATALOGUE)
        self.assertTrue(any("does not match rubric" in e for e in errs))

    def test_unknown_package_and_category(self):
        self.assertTrue(schema.validate_item(item(package="com.nope.app"), RUBRIC, CATALOGUE))
        errs = schema.validate_item(item(app_category="shopping"), RUBRIC, CATALOGUE)
        self.assertTrue(any("app_category" in e for e in errs))

    def test_app_category_must_fit_usecase(self):
        errs = schema.validate_item(item(package="com.spotify.music", app_name="Spotify", app_category="media_music"),
                                    RUBRIC, CATALOGUE)
        self.assertTrue(any("not allowed for usecase" in e for e in errs))

    def test_language_script_checked(self):
        errs = schema.validate_item(item(lang="hi"), RUBRIC, CATALOGUE)
        self.assertTrue(any("language hi" in e for e in errs))
        ok = item(lang="hi", title="आपका ड्राइवर आ रहा है", text="माया 2 मिनट में पहुंच रही है")
        self.assertEqual(schema.validate_item(ok, RUBRIC, CATALOGUE), [])

    def test_hinglish_must_be_romanized(self):
        errs = schema.validate_item(item(lang="hi-Latn", text="Maya 2 मिनट में"), RUBRIC, CATALOGUE)
        self.assertTrue(errs)

    def test_every_catalogue_category_is_used_by_the_rubric(self):
        used = {c for u in RUBRIC["usecases"] for c in u["apps"]}
        self.assertEqual(used - {c["category"] for c in CATALOGUE.values()}, set())

    def test_rubric_labels_use_known_modes(self):
        for u in RUBRIC["usecases"]:
            self.assertTrue(set(u["urgentIn"]) <= set(RUBRIC["modes"]), u["id"])


class LeakTests(unittest.TestCase):
    def test_exact_cross_split(self):
        a = item(id="en-train-1", split="train")
        b = item(id="en-test-1", split="test", text=a["text"].upper())
        self.assertEqual(len(leakcheck.cross_split_exact([a, b])), 1)

    def test_template_leak(self):
        a = item(id="a", split="train", template_family="fam")
        b = item(id="b", split="test", template_family="fam")
        self.assertEqual(leakcheck.template_leaks([a, b]), ["fam"])

    def test_near_duplicate(self):
        a = item(id="a", split="train", text="Maya is 2 minutes away in a white Toyota Corolla today")
        b = item(id="b", split="test", text="Maya is 3 minutes away in a white Toyota Corolla today")
        self.assertTrue(leakcheck.near_duplicates([a], [b], threshold=0.7))


if __name__ == "__main__":
    unittest.main()
