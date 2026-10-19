import tempfile
import unittest
from pathlib import Path

from check_r8_mapping import check_mapping, find_keep_classes


CONTRACT = {
    "classes": ["sample.UrlOption"],
    "fields": [{"class": "sample.UrlOption", "name": "headers"}],
}


class CheckR8MappingTest(unittest.TestCase):
    def test_accepts_preserved_reflection_class_and_field(self) -> None:
        mapping = """sample.UrlOption -> sample.UrlOption:
    java.lang.Object headers -> headers
"""
        self.assertEqual([], check_mapping(mapping, CONTRACT))

    def test_rejects_renamed_reflection_field(self) -> None:
        mapping = """sample.UrlOption -> sample.UrlOption:
    java.lang.Object headers -> c
"""
        errors = check_mapping(mapping, CONTRACT)
        self.assertEqual(
            ["Required field was renamed: sample.UrlOption.headers -> c"], errors
        )

    def test_rejects_renamed_reflection_class(self) -> None:
        mapping = """sample.UrlOption -> a.b:
    java.lang.Object headers -> headers
"""
        errors = check_mapping(mapping, CONTRACT)
        self.assertEqual(
            ["Required class was renamed: sample.UrlOption -> a.b"], errors
        )

    def test_checks_source_keep_inventory_completeness(self) -> None:
        errors = check_mapping(
            "sample.Kept -> sample.Kept:\n",
            {"kept_classes": ["sample.Kept"]},
            {"sample.Kept", "sample.NewKeep"},
        )
        self.assertEqual(
            ["@Keep declaration missing from contract: sample.NewKeep"], errors
        )

    def test_discovers_top_level_and_nested_keep_declarations(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "Example.kt"
            source.write_text(
                """package sample
import androidx.annotation.Keep
@Keep
object Outer {
    @Keep
    data class Nested(val value: String)
}
""",
                encoding="utf-8",
            )
            self.assertEqual(
                {"sample.Outer", "sample.Outer$Nested"},
                find_keep_classes(Path(directory)),
            )


if __name__ == "__main__":
    unittest.main()
