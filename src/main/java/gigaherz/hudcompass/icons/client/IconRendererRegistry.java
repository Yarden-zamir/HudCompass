package gigaherz.hudcompass.icons.client;

import com.google.common.collect.Maps;
import gigaherz.hudcompass.HudOverlay;
import gigaherz.hudcompass.icons.BasicIconData;
import gigaherz.hudcompass.icons.IIconData;
import gigaherz.hudcompass.icons.IconDataSerializer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class IconRendererRegistry
{
    private static final Logger LOGGER = LogManager.getLogger();

    public static final Map<IconDataSerializer<?>, IIconRenderer<?>> REGISTRY = Maps.newHashMap();

    private static final BasicIconRenderer POI_RENDERER = registerRenderer(BasicIconData.POI_SERIALIZER, new BasicIconRenderer(HudOverlay.LOCATION_POI_ICONS, 128, 128, 8, 8));
    private static final BasicIconRenderer MAP_RENDERER = registerRenderer(BasicIconData.MAP_SERIALIZER, new BasicIconRenderer(HudOverlay.LOCATION_MAP_ICONS, 128, 128, 8, 8));

    private static final IIconRenderer MISSING_ICON_RENDERER = (data, player, textureManager, x, y) ->
            POI_RENDERER.renderIcon(BasicIconData.MISSING_ICON, player, textureManager, x, y);

    private static <T extends IIconData<T>, R extends IIconRenderer<T>> R registerRenderer(IconDataSerializer<T> serializer, R renderer)
    {
        REGISTRY.put(serializer, renderer);
        return renderer;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void renderIcon(IIconData<?> data, PlayerEntity player, TextureManager textureManager, int x, int y)
    {
        IIconRenderer renderer = REGISTRY.computeIfAbsent(data.getSerializer(), (key) -> {
            LOGGER.warn("Missing icon renderer for {}", data.getSerializer().getRegistryName());
            return MISSING_ICON_RENDERER;
        });

        renderer.renderIcon(data, player, textureManager, x, y);
    }
}
