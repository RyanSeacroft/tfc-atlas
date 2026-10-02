package dev.ryan.tfcatlas.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("displayPos")
    int tfcatlas$displayPos();

    @Accessor("displayPos")
    void tfcatlas$displayPos(int value);
}
