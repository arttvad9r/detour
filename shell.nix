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
    # nixpkgs' gomobile wrapper appends its read-only $out to GOPATH so the
    # packaged golang.org/x/mobile sources stay visible. Put a writable path
    # first so Go's module/build cache never targets /nix/store.
    export GOPATH="$PWD/.cache/go"
    export GOMODCACHE="$GOPATH/pkg/mod"
    export GOTOOLCHAIN=local
    mkdir -p "$GOMODCACHE"
    export PATH="$JAVA_HOME/bin:$GOPATH/bin:$PATH"
    export GRADLE_USER_HOME="$PWD/.gradle"

    # sdk.dir -> Nix SDK. local.properties is ignored by git.
    if [ ! -f local.properties ]; then
      echo "sdk.dir=$ANDROID_HOME" > local.properties
    fi

    # AGP reads Gradle properties from GRADLE_USER_HOME as well as the project.
    # Keep the machine-specific Nix aapt2 path in the ignored user-home file so
    # entering the dev shell never dirties the tracked gradle.properties.
    mkdir -p "$GRADLE_USER_HOME"
    user_gradle_props="$GRADLE_USER_HOME/gradle.properties"
    if ! grep -q '^android.aapt2FromMavenOverride=' "$user_gradle_props" 2>/dev/null; then
      printf 'android.aapt2FromMavenOverride=%s/build-tools/36.0.0/aapt2\n' "$ANDROID_HOME" >> "$user_gradle_props"
    fi
    echo "Triplet dev shell  ANDROID_HOME=$ANDROID_HOME"
  '';
}
