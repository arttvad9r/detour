from pathlib import Path

for path in [
    "app/src/main/java/dev/detour/app/ui/ChoiceRow.kt",
    "app/src/main/java/dev/detour/app/ui/VlessKeyScreen.kt",
]:
    p = Path(path)
    text = p.read_text()
    old = "idleColor = if (selected) c.surfaceSelected else Color.Transparent,"
    if old not in text:
        raise SystemExit(f"missing expected selection color in {path}")
    p.write_text(text.replace(old, "idleColor = if (selected) c.surfaceSoft else Color.Transparent,", 1))

p = Path("app/src/main/java/dev/detour/app/ui/NavigationRow.kt")
text = p.read_text()
old1 = "idleColor = if (selectedBackground) c.surfaceSelected else Color.Transparent,"
old2 = "base.background(if (selectedBackground) c.surfaceSelected else Color.Transparent)"
if old1 not in text or old2 not in text:
    raise SystemExit("missing expected NavigationRow colors")
text = text.replace(old1, "idleColor = if (selectedBackground) c.surfaceSoft else Color.Transparent,", 1)
text = text.replace(old2, "base.background(if (selectedBackground) c.surfaceSoft else Color.Transparent)", 1)
p.write_text(text)
