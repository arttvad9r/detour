# SnakeYAML resolves its logger names via Class.getPackage(). R8 repackaging moves
# its classes into the unnamed package, where getPackage() returns null and
# TypeDescription's static initializer crashes WARP/AmneziaWG YAML import.
-keepnames class org.yaml.snakeyaml.** { *; }
