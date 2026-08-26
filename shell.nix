{ pkgs ? import <nixpkgs> {} }:

let
    libs = [
        pkgs.wayland
        pkgs.libxkbcommon
        pkgs.libx11
        pkgs.libxrandr
        pkgs.glfw
        pkgs.libGL
        pkgs.libpulseaudio
        pkgs.libusb1
        pkgs.openal
        pkgs.udev
    ];
in
pkgs.mkShell {
    buildInputs = libs;

    LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath (libs ++ [ pkgs.stdenv.cc.cc ]);
}
