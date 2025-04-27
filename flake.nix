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
          demoDatabase = pkgs.stdenvNoCC.mkDerivation rec {
            pname = "TriliumDroid-demoDatabase";
            version = "0";

            src = null;
            dontUnpack = true;

            nativeBuildInputs = with pkgs; [
              trilium-next-server
              curl
              psmisc
              writableTmpDirAsHomeHook
            ];

            buildPhase = ''
              export TRILIUM_PORT=14385
              trilium-server &
              sleep 10
              curl "http://127.0.0.1:$TRILIUM_PORT/api/setup/new-document" -X POST
              curl "http://127.0.0.1:$TRILIUM_PORT/set-password" -X POST --data-raw "password1=1234&password2=1234"
              sleep 5
              killall node
              sleep 5
            '';

            installPhase = ''
              mkdir $out
              mv $HOME/trilium-data/document.db $out/
            '';
          };

          docs = pkgs.stdenv.mkDerivation rec {
            pname = "TriliumDroid-docs";
            version = "0";

            src = null;
            dontUnpack = true;

            nativeBuildInputs = with pkgs; [
              asar
              _7zz
            ];

            buildInputs = with pkgs; [ ];

            buildPhase = ''
              asar_out=$(mktemp -d)
              asar extract ${pkgs.trilium-next-desktop}/share/trilium/resources/app.asar $asar_out
              mv $asar_out/src/public/app-dist/doc_notes/* .
              mv $asar_out/src/public/stylesheets .
              rm -r $asar_out

              mkdir doc_notes

              shopt -s globstar nullglob
              rm en/**/*.png en/**/*.jpg en/**/*.gif
              mv en cn doc_notes/
              cd doc_notes
              7zz a -mm=Deflate -mfb=258 -mpass=15 -- ../doc_notes.zip
              cd ..

              cd stylesheets
              7zz a -mm=Deflate -mfb=258 -mpass=15 -- ../stylesheets.zip
              cd ..
            '';

            installPhase = ''
              mkdir $out
              mv doc_notes.zip $out/
              mv stylesheets.zip $out/
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
