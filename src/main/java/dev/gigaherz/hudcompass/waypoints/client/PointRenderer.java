package dev.gigaherz.hudcompass.waypoints.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gigaherz.hudcompass.client.HudOverlay;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class PointRenderer
{
    public static void renderIcon(PointInfo<?> info, Player player, TextureManager textureManager, PoseStack matrixStack, int x, int y, int alpha)
    {
        IconRendererRegistry.renderIcon(info.getIconData(), player, textureManager, matrixStack, x, y, alpha);
    }

    public static void renderLabel(PointInfo<?> info, Font font, PoseStack matrixStack, int x, int y, int alpha)
    {
        Component label = info.getLabel();
        if (label != null && label.getString().length() > 0)
        {
            HudOverlay.drawCenteredBoxedString(matrixStack, font, label, x, y, (alpha << 24) | 0xFFFFFF);
        }
    }
}
