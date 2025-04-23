{
  description = "TriliumDroid - build tools";

  # Nixpkgs / NixOS version to use.
  inputs.nixpkgs.url = "nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let

      lib = nixpkgs.lib;

      # System types to support.
      supportedSystems = [ "x86_64-linux" "x86_64-darwin" "aarch64-linux" "aarch64-darwin" ];

      # Helper function to generate an attrset '{ x86_64-linux = f "x86_64-linux"; ... }'.
      forAllSystems = lib.genAttrs supportedSystems;

      # Nixpkgs instantiated for supported system types.
      nixpkgsFor = forAllSystems (system: import nixpkgs { inherit system; });

    in
    {

      # Provide some binary packages for selected system types.
      packages = forAllSystems (system:
        let
          pkgs = nixpkgsFor.${system};
        in
        {
          docs = pkgs.stdenv.mkDerivation rec {
            pname = "TriliumDroid-docs";
            version = "0";

            src = null;
            dontUnpack = true;

            nativeBuildInputs = with pkgs; [
              asar
              p7zip
            ];

            buildInputs = with pkgs; [ ];

            buildPhase = ''
              asar_out=$(mktemp -d)
              asar extract ${pkgs.trilium-next-desktop}/share/trilium/resources/app.asar $asar_out
              mv $asar_out/src/public/app-dist/doc_notes/* .
              rm -r $asar_out
              shopt -s globstar nullglob
              rm en/**/*.png en/**/*.jpg en/**/*.gif
              7z a -mm=Deflate -mfb=258 -mpass=15 -r doc_notes.zip en cn
            '';

            installPhase = ''
              mkdir $out
              mv doc_notes.zip $out/
            '';

            meta = with lib; {
              description = "Docs copied from TriliumNext notes";
              homepage = "https://github.com/TriliumNext/Notes";
              license = licenses.agpl3Plus;
              maintainers = with maintainers; [ fliegendewurst ];
            };
          };
        });

      defaultPackage = forAllSystems (system: self.packages.${system}.docs);
    };
}
