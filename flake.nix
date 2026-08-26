# Reproducible dev/build environment. nixpkgs pinned to an exact revision;
# bump deliberately (update rev here AND run `nix flake lock`).
{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/56c02bc00adc";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in
    {
      devShells.${system}.default = pkgs.callPackage ./shell.nix { };
    };
}
