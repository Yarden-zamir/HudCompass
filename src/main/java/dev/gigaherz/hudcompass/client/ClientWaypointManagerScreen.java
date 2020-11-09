package dev.gigaherz.hudcompass.client;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FocusableGui;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.IRenderable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.gui.ScrollPanel;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ClientWaypointManagerScreen extends Screen
{
    private static final ITextComponent TITLE = new TranslationTextComponent("text.hudcompass.waypoint_manager.title");

    private static final Pattern COORD_VALIDATOR = Pattern.compile("^[0-9]*\\.?[0-9]*$");
    private static final Pattern COORD_FORMAT = Pattern.compile("^[0-9]*\\.?[0-9]*$");
    private static final Predicate<String> COORD_FORMAT_MATCHER = COORD_FORMAT.asPredicate();

    private static final int MARGIN_LEFT = 8;
    private static final int MARGIN_TOP = 56;
    private static final int MARGIN_RIGHT = 8;
    private static final int MARGIN_BOTTOM = 34;

    private final List<WaypointListItem> toAdd = new ArrayList<>();
    private final List<WaypointListItem> toRemove = new ArrayList<>();
    private final PointsOfInterest pois;
    private final List<RegistryKey<World>> worlds;

    private ItemsScrollPanel scrollPanel;
    private Button createWaypoint;
    private Button changeWorld;
    private Button saveButton;
    private Button cancelButton;

    private RegistryKey<World> currentWorld;
    private Map<Integer, List<WaypointListItem>> itemsPerPage = Maps.newHashMap();
    private int currentWorldIndex;

    protected ClientWaypointManagerScreen(PointsOfInterest pois)
    {
        super(TITLE);
        this.pois = pois;
        this.worlds = minecraft.world.getServer().func_244267_aX()
                .func_230520_a_().keySet().stream()
                .map(key -> RegistryKey.getOrCreateKey(Registry.WORLD_KEY, key))
                .collect(Collectors.toList());
    }

    @Override
    protected void init()
    {
        super.init();

        currentWorldIndex = worlds.size();

        scrollPanel = new ItemsScrollPanel(minecraft, width - MARGIN_RIGHT - MARGIN_LEFT, height - MARGIN_TOP - MARGIN_BOTTOM, MARGIN_TOP, MARGIN_LEFT);
        addListener(scrollPanel);

        int center = width/2;
        addButton(createWaypoint = new Button(center-124,20,120,20, new StringTextComponent("NEW WAYPOINT"), (button) -> {
            if (currentWorld != null)
                createNewPoint();
        }));
        addButton(changeWorld = new Button(center+4,20,120,20, new StringTextComponent("WORLD: ALL"), (button) -> {
            currentWorldIndex = (currentWorldIndex+1) % (worlds.size() + 1);
            currentWorld = currentWorldIndex < worlds.size() ? worlds.get(currentWorldIndex) : null;
            createWaypoint.active = currentWorld != null;
        }));

        addButton(saveButton = new Button(width-128,height-28,120,20, new StringTextComponent("SAVE"), (button) -> {
            scrollPanel.saveAll();
            toRemove.forEach(point -> {
                pois.get(point.worldKey).remove(point.pointInfo);
            });
            toAdd.forEach(point -> {
                pois.get(point.worldKey).addPoint(point.pointInfo);
            });
            closeScreen();
        }));
        addButton(cancelButton = new Button(8,height-28,120,20, new StringTextComponent("CANCEL"), (button) -> {
            closeScreen();
        }));

        createWaypoint.active = currentWorld != null;

        for (PointsOfInterest.WorldPoints world : pois.getAllWorlds())
        {
            int index = worlds.indexOf(world.getWorldKey());
            for (PointInfo<?> point : world.getPoints())
            {
                if (point instanceof BasicWaypoint)
                {
                    BasicWaypoint wp = (BasicWaypoint) point;
                    addPoint(world.getWorldKey(), wp, index);
                }
            }
        }
    }

    private void createNewPoint()
    {
        BasicWaypoint wp = new BasicWaypoint(minecraft.player.getPositionVec(), "", BasicIconData.mapMarker(7));
        toAdd.add(addPoint(currentWorld, wp, currentWorldIndex));
    }

    private WaypointListItem addPoint(RegistryKey<World> worldKey, BasicWaypoint wp, int index)
    {
        WaypointListItem item = new WaypointListItem(minecraft, wp, worldKey);
        scrollPanel.addItem(item);
        scrollPanel.scrollToItem(item);
        itemsPerPage.computeIfAbsent(index, key -> Lists.newArrayList()).add(item);
        itemsPerPage.computeIfAbsent(worlds.size(), key -> Lists.newArrayList()).add(item);
        return item;
    }

    private void deletePoint(WaypointListItem item)
    {
        if (!toAdd.remove(item))
            toRemove.add(item);
        scrollPanel.removeItem(item);
        int index = worlds.indexOf(item.worldKey);
        itemsPerPage.computeIfAbsent(index, key -> Lists.newArrayList()).remove(item);
        itemsPerPage.computeIfAbsent(worlds.size(), key -> Lists.newArrayList()).remove(item);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
    {
        renderBackground(matrixStack);

        scrollPanel.setPartialTicks(partialTicks);
        scrollPanel.render(matrixStack, mouseX, mouseY, partialTicks);

        super.render(matrixStack, mouseX, mouseY, partialTicks);

        drawCenteredString(matrixStack, minecraft.fontRenderer, title, width/2, 7, 0xFFFFFFFF);

        int nameWidth = Math.max(scrollPanel.getContentWidth() - (61*3+41+3), 50);
        int x = scrollPanel.getLeft() + 6;
        int y = scrollPanel.getTop() - 10;
        drawString(matrixStack, minecraft.fontRenderer,  new StringTextComponent("LABEL"), x, y, 0xFFFFFFFF);
        x+=nameWidth+3;
        drawString(matrixStack, minecraft.fontRenderer,  new StringTextComponent("X"), x, y, 0xFFFFFFFF);
        x+=61;
        drawString(matrixStack, minecraft.fontRenderer,  new StringTextComponent("Y"), x, y, 0xFFFFFFFF);
        x+=41;
        drawString(matrixStack, minecraft.fontRenderer,  new StringTextComponent("Z"), x, y, 0xFFFFFFFF);
    }

    private class WaypointListItem extends CompositeListItem
    {
        private final BasicWaypoint pointInfo;
        private final RegistryKey<World> worldKey;
        private TextFieldWidget label;
        private TextFieldWidget xCoord;
        private TextFieldWidget yCoord;
        private TextFieldWidget zCoord;
        private Button delete;

        public WaypointListItem(Minecraft minecraft, BasicWaypoint pointInfo, RegistryKey<World> worldKey)
        {
            super(minecraft, 24);

            this.pointInfo = pointInfo;
            this.worldKey = worldKey;
        }

        @Override
        public void init()
        {
            Vector3d pos = pointInfo.getPosition();

            int nameWidth = Math.max(getWidth() - (61*3+41+3), 50);

            int x = 2;
            addWidget(label = new TextFieldWidget(minecraft.fontRenderer, x+1, 2, nameWidth, 16, new StringTextComponent("LABEL")));
            x+=nameWidth+3;
            addWidget(xCoord = new TextFieldWidget(minecraft.fontRenderer, x+1, 2, 60, 16, new StringTextComponent("X COORD")));
            x+=61;
            addWidget(yCoord = new TextFieldWidget(minecraft.fontRenderer, x+1, 2, 40, 16, new StringTextComponent("Y COORD")));
            x+=41;
            addWidget(zCoord = new TextFieldWidget(minecraft.fontRenderer, x+1, 2, 60, 16, new StringTextComponent("Z COORD")));
            x+=63;
            addWidget(delete = new Button(x, 0, 50, 20, new StringTextComponent("DELETE"), (button) -> {
                deletePoint(this);
            }));

            label.setMaxStringLength(1024);
            label.setText(pointInfo.getLabelText());
            xCoord.setText(String.format("%1.2f", pos.x));
            yCoord.setText(String.format("%1.2f", pos.y));
            zCoord.setText(String.format("%1.2f", pos.z));
            xCoord.setValidator(COORD_VALIDATOR.asPredicate());
            yCoord.setValidator(COORD_VALIDATOR.asPredicate());
            zCoord.setValidator(COORD_VALIDATOR.asPredicate());
        }

        public void save()
        {
            pointInfo.setLabelText(label.getText());

            String strX = xCoord.getText();
            String strY = yCoord.getText();
            String strZ = zCoord.getText();
            if(COORD_FORMAT_MATCHER.test(strX) && COORD_FORMAT_MATCHER.test(strY) && COORD_FORMAT_MATCHER.test(strZ))
                pointInfo.setPosition(new Vector3d(Double.parseDouble(strX), Double.parseDouble(strY), Double.parseDouble(strZ)));
        }
    }

    private static class CompositeListItem extends ListItem
    {
        private final List<IRenderable> renderables = Lists.newArrayList();
        private final List<IGuiEventListener> listeners = Lists.newArrayList();

        public CompositeListItem(Minecraft minecraft, int height)
        {
            super(minecraft, height);
        }

        public CompositeListItem addWidget(IRenderable widget)
        {
            if (widget instanceof IGuiEventListener)
                addListener((IGuiEventListener) widget);
            renderables.add(widget);
            return this;
        }

        public CompositeListItem addListener(IGuiEventListener listener)
        {
            listeners.add(listener);
            return this;
        }

        @Override
        public List<? extends IGuiEventListener> getEventListeners()
        {
            return listeners;
        }


        private int getActualX(int mouseX)
        {
            int x = mouseX;
            if (getParent() != null) x -= getParent().getLeft();
            return x;
        }

        private double getActualX(double mouseX)
        {
            double x = mouseX;
            if (getParent() != null) x -= getParent().getLeft();
            return x;
        }

        private int getActualY(int mouseY)
        {
            int y = mouseY - getTop();
            if (getParent() != null) y -= getParent().getContentTop();
            return y;
        }

        private double getActualY(double mouseY)
        {
            double y = mouseY - getTop();
            if (getParent() != null) y -= getParent().getContentTop();
            return y;
        }

        @Override
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
        {
            int actualMouseX = getActualX(mouseX);
            int actualMouseY = getActualY(mouseY);
            for (IRenderable i : renderables)
            {
                i.render(matrixStack, actualMouseX, actualMouseY, partialTicks);
            }
        }

        @Override
        public Optional<IGuiEventListener> getEventListenerForPos(double mouseX, double mouseY) {
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            for(IGuiEventListener iguieventlistener : this.getEventListeners()) {
                if (iguieventlistener.isMouseOver(actualMouseX, actualMouseY)) {
                    return Optional.of(iguieventlistener);
                }
            }

            return Optional.empty();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            for(IGuiEventListener iguieventlistener : this.getEventListeners()) {
                if (iguieventlistener.mouseClicked(actualMouseX, actualMouseY, button)) {
                    this.setListener(iguieventlistener);
                    if (button == 0) {
                        this.setDragging(true);
                    }

                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            this.setDragging(false);
            return this.getEventListenerForPos(actualMouseX, actualMouseY).filter((listener) -> {
                return listener.mouseReleased(actualMouseX, actualMouseY, button);
            }).isPresent();
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            return this.getListener() != null && this.isDragging() && button == 0 ? this.getListener().mouseDragged(actualMouseX, actualMouseY, button, dragX, dragY) : false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            return this.getEventListenerForPos(actualMouseX, actualMouseY).filter((listener) -> {
                return listener.mouseScrolled(actualMouseX, actualMouseY, delta);
            }).isPresent();
        }
    }

    private static abstract class ListItem extends FocusableGui implements IRenderable
    {
        protected final Minecraft minecraft;

        private ItemsScrollPanel parent;
        private int height;
        private int width;
        private int top;

        public ListItem(Minecraft minecraft, int height)
        {
            this.minecraft = minecraft;
            this.height = height;
        }

        public int getTop()
        {
            return top;
        }

        public void setTop(int top)
        {
            this.top = top;
        }

        public int getHeight()
        {
            return height;
        }

        public void setHeight(int height)
        {
            this.height = height;
            if (parent != null)
                parent.recalculateHeight();
        }

        public int getWidth()
        {
            return width;
        }

        public void setWidth(int width)
        {
            this.width = width;
        }

        @Nullable
        protected ItemsScrollPanel getParent()
        {
            return parent;
        }

        public void setParent(ItemsScrollPanel parent)
        {
            this.parent = parent;
        }

        public void init()
        {
        }

        @Override
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
        {

        }

        /*
        @Override
        public boolean changeFocus(boolean focus) {
            value = changeFocusSuper(focus);

            this.isFocused = !this.isFocused;
            return this.isFocused;
        }
         */

        private boolean changeFocusSuper(boolean focus)
        {
            IGuiEventListener iguieventlistener = this.getListener();
            boolean flag = iguieventlistener != null;
            if (flag && iguieventlistener.changeFocus(focus)) {
                return true;
            } else {
                List<? extends IGuiEventListener> list = this.getEventListeners();
                int j = list.indexOf(iguieventlistener);
                int i;
                if (flag && j >= 0) {
                    i = j + (focus ? 1 : 0);
                } else if (focus) {
                    i = 0;
                } else {
                    i = list.size();
                }

                ListIterator<? extends IGuiEventListener> listiterator = list.listIterator(i);
                BooleanSupplier booleansupplier = focus ? listiterator::hasNext : listiterator::hasPrevious;
                Supplier<? extends IGuiEventListener> supplier = focus ? listiterator::next : listiterator::previous;

                while(booleansupplier.getAsBoolean()) {
                    IGuiEventListener iguieventlistener1 = supplier.get();
                    if (iguieventlistener1.changeFocus(focus)) {
                        this.setListener(iguieventlistener1);
                        return true;
                    }
                }

                this.setListener((IGuiEventListener)null);
                return false;
            }
        }

        public void save()
        {
        }
    }

    private static class ItemsScrollPanel extends ScrollPanel
    {
        private final List<ListItem> items = Lists.newArrayList();
        private final Minecraft minecraft;

        private int contentHeight;
        private float partialTicks;

        public ItemsScrollPanel(Minecraft client, int width, int height, int top, int left)
        {
            super(client, width, height, top, left);
            this.minecraft = client;
        }

        public void addItem(ListItem item)
        {
            items.add(item);
            item.setParent(this);
            recalculateHeight();
            item.setWidth(getContentWidth());
            item.init();
        }

        @Override
        protected int getContentHeight()
        {
            return contentHeight;
        }

        public void setPartialTicks(float partialTicks)
        {
            this.partialTicks = partialTicks;
        }

        @Override
        protected void drawPanel(MatrixStack mStack, int entryRight, int relativeY, Tessellator tess, int mouseX, int mouseY)
        {
            mStack.push();
            mStack.translate(left, relativeY, 0);
            for(ListItem item : items)
            {
                item.render(mStack, mouseX, mouseY, partialTicks);
                mStack.translate(0, item.getHeight(), 0);
            }
            mStack.pop();
        }

        @Override
        public List<? extends IGuiEventListener> getEventListeners()
        {
            return items;
        }

        public void recalculateHeight()
        {
            int totalHeight = 0;
            for(ListItem item : items)
            {
                item.setTop(totalHeight);
                totalHeight += item.getHeight();
            }
            contentHeight = totalHeight;
        }

        public int getLeft()
        {
            return left;
        }

        public int getTop()
        {
            return top;
        }
        public int getContentTop()
        {
            int baseY = this.top + border - (int)this.scrollDistance;
            return baseY;
        }

        public void saveAll()
        {
            items.forEach(ListItem::save);
        }

        public int getContentWidth()
        {
            return right-left-6;
        }

        public void removeItem(WaypointListItem item)
        {
            item.setParent(null);
            items.remove(item);
            recalculateHeight();
        }

        public void scrollToItem(WaypointListItem item)
        {
            int scrollOffset = item.getTop() - height/2 - item.getHeight();
            this.scrollDistance = MathHelper.clamp(scrollOffset, 0, Math.max(0, getContentHeight() - height));
        }
    }
}
