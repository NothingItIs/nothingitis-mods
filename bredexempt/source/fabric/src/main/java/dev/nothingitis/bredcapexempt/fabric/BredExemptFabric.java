package dev.nothingitis.bredcapexempt.fabric;

import dev.nothingitis.bredcapexempt.BredExempt;
import dev.nothingitis.bredcapexempt.command.BredExemptCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

public class BredExemptFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BredExempt.init(FabricLoader.getInstance().getConfigDir());
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext, selection) -> BredExemptCommands.register(dispatcher));
    }
}
