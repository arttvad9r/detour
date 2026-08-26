{ pkgs ? import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  } }:

let
  android = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "36" ];
    buildToolsVersions = [ "36.0.0" ];
    includeNDK = true;
    ndkVersions = [ "28.0.13004108" ];
  };
  androidHome = "${android.androidsdk}/libexec/android-sdk";
in
pkgs.mkShell {
  name = "triplet-dev";

  nativeBuildInputs = [
    pkgs.jdk17
    pkgs.go
    pkgs.gomobile
    pkgs.govulncheck
    pkgs.git
    pkgs.python3
    pkgs.cacert
    pkgs.which
    pkgs.unzip
    pkgs.zip
    pkgs.coreutils
    pkgs.patchelf
    pkgs.binutils
    pkgs.android-tools
    pkgs.curl
  ];

  ANDROID_HOME = androidHome;
  ANDROID_SDK_ROOT = androidHome;
  ANDROID_NDK_HOME = "${androidHome}/ndk/28.0.13004108";
  ANDROID_NDK_ROOT = "${androidHome}/ndk/28.0.13004108";
  JAVA_HOME = "${pkgs.jdk17}";

  shellHook = ''
    export PATH="$JAVA_HOME/bin:$PATH"
    export PATH="$(go env GOPATH)/bin:$PATH"
    export GRADLE_USER_HOME="$PWD/.gradle"

    # sdk.dir -> Nix SDK (compileSdk 36 присутствует в nixpkgs, оверлей не нужен)
    if [ ! -f local.properties ]; then
      echo "sdk.dir=$ANDROID_HOME" > local.properties
    fi

    # Use the Nix-provided, already-patched SDK aapt2 instead of mutating
    # immutable Gradle transform caches. AGP reads this flag only from
    # gradle.properties, so the machine-specific path is appended locally and
    # deliberately kept out of git.
    if ! grep -q '^android.aapt2FromMavenOverride=' gradle.properties 2>/dev/null; then
      printf 'android.aapt2FromMavenOverride=%s/build-tools/36.0.0/aapt2\n' "$ANDROID_HOME" >> gradle.properties
    fi
    echo "Triplet dev shell  ANDROID_HOME=$ANDROID_HOME"
  '';
}
