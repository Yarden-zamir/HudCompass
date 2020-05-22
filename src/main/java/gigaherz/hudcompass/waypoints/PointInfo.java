package gigaherz.hudcompass.waypoints;

import gigaherz.hudcompass.icons.IIconData;
import gigaherz.hudcompass.icons.IconDataRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public abstract class PointInfo<T extends PointInfo<T>>
{
    public static Vec3d toVec3d(BlockPos pos)
    {
        return new Vec3d(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
    }

    private final PointInfoType<? extends T> type;
    private PointsOfInterest owner;
    private UUID internalId;
    private String label;
    private IIconData<?> iconData;
    private boolean displayVerticalDistance = true;
    private boolean isServerProvided = true; // not used in the server
    private boolean isDynamic = false; // will not be saved to disk

    public PointInfo(PointInfoType<? extends T> type)
    {
        this.type = type;
        this.internalId = UUID.randomUUID();
    }

    public PointInfo(PointInfoType<? extends T> type, String label, IIconData<?> iconData)
    {
        this(type);
        this.label = label;
        this.iconData = iconData;
    }

    public PointInfoType<? extends T> getType()
    {
        return type;
    }

    public UUID getInternalId()
    {
        return internalId;
    }
    public void setInternalId(UUID uuid)
    {
        internalId = uuid;
    }

    public abstract Vec3d getPosition();

    public String getLabel()
    {
        return this.label;
    }

    public IIconData<?> getIconData()
    {
        return iconData;
    }

    @SuppressWarnings("unchecked")
    public final T noVerticalDistance()
    {
        this.displayVerticalDistance = false;
        return (T) this;
    }

    public boolean displayVerticalDistance(PlayerEntity player)
    {
        return displayVerticalDistance;
    }

    public void makeClientPoint()
    {
        isServerProvided = false;
    }

    public boolean isServerManaged()
    {
        return isServerProvided;
    }

    public boolean isDynamic()
    {
        return isDynamic;
    }

    public void tick(PlayerEntity player)
    {
    }

    void setOwner(PointsOfInterest owner)
    {
        this.owner = owner;
    }

    public void markDirty()
    {
        if(owner != null)
        {
            owner.markDirty(this);
        }
    }

    public void remove()
    {
        if (owner != null)
        {
            owner.remove(this);
        }
    }

    public final CompoundNBT write(CompoundNBT tag)
    {
        tag.putString("ID", internalId.toString());
        tag.putString("Label", label);
        tag.put("Icon", IconDataRegistry.serializeIcon(iconData));
        tag.putBoolean("DisplayVerticalDistance", displayVerticalDistance);
        serializeAdditional(tag);
        return tag;
    }

    public final void read(CompoundNBT tag)
    {
        internalId = UUID.fromString(tag.getString("ID"));
        label = tag.getString("Label");
        iconData = IconDataRegistry.deserializeIcon(tag.getCompound("Icon"));
        displayVerticalDistance = tag.getBoolean("DisplayVerticalDistance");
        deserializeAdditional(tag);
    }

    public final void writeToPacket(PacketBuffer buffer)
    {
        buffer.writeUniqueId(internalId);
        buffer.writeString(label);
        IconDataRegistry.serializeIcon(iconData, buffer);
        buffer.writeBoolean(displayVerticalDistance);
        buffer.writeBoolean(isDynamic);
        serializeAdditional(buffer);
    }

    public final void readFromPacket(PacketBuffer buffer)
    {
        internalId = buffer.readUniqueId();
        label = buffer.readString();
        iconData = IconDataRegistry.deserializeIcon(buffer);
        displayVerticalDistance = buffer.readBoolean();
        isDynamic = buffer.readBoolean();
        deserializeAdditional(buffer);
    }

    protected abstract void serializeAdditional(CompoundNBT tag);
    protected abstract void deserializeAdditional(CompoundNBT tag);
    protected abstract void serializeAdditional(PacketBuffer tag);
    protected abstract void deserializeAdditional(PacketBuffer tag);
}
