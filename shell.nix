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
    pkgs.gradle_9
    pkgs.go
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

    # NixOS: пропатчить кешированный AGP aapt2 на системный загрузчик
    LD_LINKER="${pkgs.glibc.out}/lib/ld-linux-x86-64.so.2"
    find "$GRADLE_USER_HOME/caches" -path "*transforms*" -type f -name "aapt2" 2>/dev/null | while read -r f; do
      if grep -aq "/lib64/ld-linux" "$f" 2>/dev/null; then
        patchelf --set-interpreter "$LD_LINKER" "$f" 2>/dev/null || true
      fi
    done

    echo "Triplet dev shell  ANDROID_HOME=$ANDROID_HOME"
  '';
}
