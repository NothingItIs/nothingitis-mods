package dev.nothingitis.bredcapexempt.neoforge;

import dev.nothingitis.bredcapexempt.BredExempt;
import dev.nothingitis.bredcapexempt.command.BredExemptCommands;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod("bredcapexempt")
public class BredExemptNeoForge {
    public BredExemptNeoForge() {
        BredExempt.init(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> BredExemptCommands.register(event.getDispatcher()));
    }
}
