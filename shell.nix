{ pkgs ? import <nixpkgs> {} }:

let
    libs = [
        pkgs.wayland
        pkgs.libxkbcommon
        pkgs.libx11
        pkgs.glfw
        pkgs.libGL
    ];
in
pkgs.mkShell {
    buildInputs = libs;

    LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath (libs ++ [ pkgs.stdenv.cc.cc ]);
}
