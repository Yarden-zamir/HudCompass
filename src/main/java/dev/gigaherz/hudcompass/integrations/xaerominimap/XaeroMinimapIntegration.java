package dev.gigaherz.hudcompass.integrations.xaerominimap;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.icons.client.IIconRenderer;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import xaero.common.IXaeroMinimap;
import xaero.common.XaeroMinimapSession;
import xaero.common.gui.GuiWaypoints;
import xaero.common.interfaces.render.InterfaceRenderer;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.WaypointSet;
import xaero.common.minimap.waypoints.WaypointsManager;
import xaero.common.misc.Misc;
import xaero.common.settings.ModSettings;
import xaero.minimap.XaeroMinimap;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class XaeroMinimapIntegration
{
    public static void init()
    {
        if (FMLEnvironment.dist == Dist.CLIENT)
            XMWaypoints.init();
    }

    public static class XMWaypoints
    {
        public static final XMWaypoints INSTANCE = new XMWaypoints();

        private final Map<Waypoint, XMWaypoint> waypoints = Maps.newHashMap();

        private static final DeferredRegister<PointInfoType<?>> PIT = HudCompass.makeDeferredPOI();
        private static final DeferredRegister<IconDataSerializer<?>> IDS = HudCompass.makeDeferredIDS();
        public static final RegistryObject<PointInfoType<XMWaypoint>> TYPE = PIT.register("xmwaypoints", () -> new PointInfoType<>(XMWaypoint::new));
        public static final RegistryObject<XMIconData.Serializer> ICON_DATA = IDS.register("xmwaypoints", XMIconData.Serializer::new);

        public static void init()
        {
            IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

            modEventBus.addListener(INSTANCE::clientSetup);

            PIT.register(modEventBus);
            IDS.register(modEventBus);

            MinecraftForge.EVENT_BUS.addListener(INSTANCE::clientTick);
        }

        private void clientSetup(FMLClientSetupEvent event)
        {
            IconRendererRegistry.registerRenderer(ICON_DATA.get(), new XMWaypointRenderer());
        }

        private int counter = 0;
        private void clientTick(TickEvent.ClientTickEvent event)
        {
            if ((++counter) > 20)
            {
                counter = 0;
                updateWaypoints();
            }
        }

        public void updateWaypoints()
        {
            final ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player == null)
                return;

            XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
            if (session == null)
                return;

            WaypointsManager waypointsManager = session.getWaypointsManager();
            if (waypointsManager == null)
                return;

            WaypointSet wps = waypointsManager.getWaypoints();
            if (wps == null)
                return;

            Set<Waypoint> wpsList = wps.getList().stream().filter(w -> !w.isDisabled()).collect(Collectors.toSet());

            Set<Waypoint> toAdd = new HashSet<>();
            for(Waypoint wp : wpsList)
            {
                if (!waypoints.containsKey(wp))
                    toAdd.add(wp);
            }

            Set<Waypoint> toRemove = new HashSet<>();
            for(Waypoint wp : waypoints.keySet())
            {
                if (!wpsList.contains(wp))
                    toRemove.add(wp);
            }

            player.getCapability(PointsOfInterest.INSTANCE).ifPresent((pois) -> {
                for(Waypoint wp : toAdd)
                {
                    XMWaypoint way = new XMWaypoint(wp);
                    waypoints.put(wp, way);
                    pois.get(player.world).addPoint(way);
                }
                for(Waypoint wp : toRemove)
                {
                    XMWaypoint way = waypoints.get(wp);
                    waypoints.remove(wp);
                    pois.get(player.world).remove(way);
                }
            });
        }

        private static class XMIconData implements IIconData<XMIconData>
        {
            public final Waypoint parent;

            public XMIconData(Waypoint way)
            {
                parent = way;
            }

            @Override
            public IconDataSerializer<XMIconData> getSerializer()
            {
                return ICON_DATA.get();
            }

            public static class Serializer extends IconDataSerializer<XMIconData>
            {

                @Override
                public CompoundNBT write(XMIconData data, CompoundNBT tag)
                {
                    throw new IllegalStateException("Serialization not supported.");
                }

                @Override
                public XMIconData read(CompoundNBT tag)
                {
                    throw new IllegalStateException("Serialization not supported.");
                }

                @Override
                public void write(XMIconData data, PacketBuffer buffer)
                {
                    throw new IllegalStateException("Serialization not supported.");
                }

                @Override
                public XMIconData read(PacketBuffer buffer)
                {
                    throw new IllegalStateException("Serialization not supported.");
                }
            }
        }

        private static class XMWaypoint extends PointInfo<XMWaypoint>
        {
            private Waypoint parent;

            public XMWaypoint()
            {
                super(TYPE.get(), true);
            }

            public XMWaypoint(Waypoint way)
            {
                super(TYPE.get(), true, way.getLocalizedName(), new XMIconData(way));
                this.parent = way;
            }

            @Override
            public Vector3d getPosition()
            {
                return new Vector3d(parent.getX() + 0.5, parent.getY() + 0.5, parent.getZ() + 0.5);
            }

            @Override
            public String getLabel()
            {
                return parent.getLocalizedName();
            }

            @Override
            protected void serializeAdditional(CompoundNBT tag)
            {
                throw new IllegalStateException("Serialization not supported.");
            }

            @Override
            protected void deserializeAdditional(CompoundNBT tag)
            {
                throw new IllegalStateException("Serialization not supported.");
            }

            @Override
            protected void serializeAdditional(PacketBuffer tag)
            {
                throw new IllegalStateException("Serialization not supported.");
            }

            @Override
            protected void deserializeAdditional(PacketBuffer tag)
            {
                throw new IllegalStateException("Serialization not supported.");
            }
        }

        private static class XMWaypointRenderer implements IIconRenderer<XMIconData>
        {
            @Override
            public void renderIcon(XMIconData data, PlayerEntity player, TextureManager textureManager, MatrixStack matrixStack, int x, int y)
            {
                matrixStack.push();
                matrixStack.translate(0, 2.8f,0);
                matrixStack.scale(7/9f, 7/9f, 7/9f);
                XaeroMinimap.instance.getInterfaces().getMinimapInterface().getWaypointsGuiRenderer()
                        .drawIconOnGUI(matrixStack, data.parent, XaeroMinimap.instance.getSettings(), x, y);
                matrixStack.pop();
            }
        }
    }
}