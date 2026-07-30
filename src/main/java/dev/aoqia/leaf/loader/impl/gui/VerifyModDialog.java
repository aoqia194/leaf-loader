package dev.aoqia.leaf.loader.impl.gui;

import javax.swing.*;

import dev.aoqia.leaf.loader.impl.discovery.ModCandidateImpl;

public class VerifyModDialog {
    public static boolean show(ModCandidateImpl mod) {
        int result = JOptionPane.showConfirmDialog(
            null,
            "A leaf Java mod was discovered and is about to be loaded."
                + "\nDo you want to load the mod?"
                + "\nIf you do not recognise or trust this mod, you should click no."
                + "\n"
                + "\nWorkshop ID: " + mod.getWorkshopId()
                + "\nGame Mod ID: " + mod.getGameId()
                + "\nLeaf Mod ID: " + mod.getId(),
            "Mod Verification",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        return result == JOptionPane.YES_OPTION;
    }
}
