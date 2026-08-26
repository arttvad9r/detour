# Reproducible dev/build environment. nixpkgs pinned to an exact revision;
# bump deliberately (update rev here AND run `nix flake lock`).
{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/56c02bc00adc";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
    in
    {
      devShells.${system}.default = pkgs.callPackage ./shell.nix { };
    };
}
