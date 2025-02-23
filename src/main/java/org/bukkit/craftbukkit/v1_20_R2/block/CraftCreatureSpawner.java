package org.bukkit.craftbukkit.v1_20_R2.block;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftEntitySnapshot;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftEntityType;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;

public class CraftCreatureSpawner extends CraftBlockEntityState<SpawnerBlockEntity> implements CreatureSpawner {

    public CraftCreatureSpawner(World world, SpawnerBlockEntity te) {
        super(world, te);
    }

    protected CraftCreatureSpawner(CraftCreatureSpawner state) {
        super(state);
    }

    @Override
    public EntityType getSpawnedType() {
        SpawnData spawnData = this.getSnapshot().getSpawner().nextSpawnData;
        if (spawnData == null) {
            return null;
        }

        Optional<net.minecraft.world.entity.EntityType<?>> type = net.minecraft.world.entity.EntityType.by(spawnData.getEntityToSpawn());
        return type.map(CraftEntityType::minecraftToBukkit).orElse(null);
    }

    @Override
    public void setSpawnedType(EntityType entityType) {
        if (entityType == null) {
            this.getSnapshot().getSpawner().spawnPotentials = SimpleWeightedRandomList.empty(); // need clear the spawnPotentials to avoid nextSpawnData being replaced later
            this.getSnapshot().getSpawner().nextSpawnData = new SpawnData();
            return;
        }
        Preconditions.checkArgument(entityType != EntityType.UNKNOWN, "Can't spawn EntityType %s from mob spawners!", entityType);

        RandomSource rand = (this.isPlaced()) ? this.getWorldHandle().getRandom() : RandomSource.create();
        this.getSnapshot().setEntityId(CraftEntityType.bukkitToMinecraft(entityType), rand);
    }


    @Override
    public EntitySnapshot getSpawnedEntity() {
        SpawnData spawnData = this.getSnapshot().getSpawner().nextSpawnData;
        if (spawnData == null) {
            return null;
        }
        return CraftEntitySnapshot.create(spawnData.getEntityToSpawn());
    }
    @Override
    public void setSpawnedEntity(EntitySnapshot snapshot) {
        CompoundTag compoundTag = ((CraftEntitySnapshot) snapshot).getData();
        this.getSnapshot().getSpawner().spawnPotentials = SimpleWeightedRandomList.empty();
        this.getSnapshot().getSpawner().nextSpawnData = new SpawnData(compoundTag, Optional.empty());
    }
    @Override
    public void addPotentialSpawn(EntitySnapshot snapshot, int weight, SpawnRule spawnRule) {
        CompoundTag compoundTag = ((CraftEntitySnapshot) snapshot).getData();
        SimpleWeightedRandomList.Builder<SpawnData> builder = SimpleWeightedRandomList.builder(); // PAIL rename Builder
        this.getSnapshot().getSpawner().spawnPotentials.unwrap().forEach(entry -> builder.add(entry.getData(), entry.getWeight().asInt()));
        builder.add(new SpawnData(compoundTag, Optional.ofNullable(toMinecraftRule(spawnRule))), weight);
        this.getSnapshot().getSpawner().spawnPotentials = builder.build();
    }
    @Override
    public void addPotentialSpawn(SpawnerEntry spawnerEntry) {
        addPotentialSpawn(spawnerEntry.getSnapshot(), spawnerEntry.getSpawnWeight(), spawnerEntry.getSpawnRule());
    }
    @Override
    public void setPotentialSpawns(Collection<SpawnerEntry> entries) {
        SimpleWeightedRandomList.Builder<SpawnData> builder = SimpleWeightedRandomList.builder();
        for (SpawnerEntry spawnerEntry : entries) {
            CompoundTag compoundTag = ((CraftEntitySnapshot) spawnerEntry.getSnapshot()).getData();
            builder.add(new SpawnData(compoundTag, Optional.ofNullable(toMinecraftRule(spawnerEntry.getSpawnRule()))), spawnerEntry.getSpawnWeight());
        }
        this.getSnapshot().getSpawner().spawnPotentials = builder.build();
    }
    @Override
    public List<SpawnerEntry> getPotentialSpawns() {
        List<SpawnerEntry> entries = new ArrayList<>();
        for (WeightedEntry.Wrapper<SpawnData> entry : this.getSnapshot().getSpawner().spawnPotentials.unwrap()) { // PAIL rename Wrapper
            CraftEntitySnapshot snapshot = CraftEntitySnapshot.create(entry.getData().getEntityToSpawn());
            if (snapshot != null) {
                SpawnRule rule = entry.getData().customSpawnRules().map(this::fromMinecraftRule).orElse(null);
                entries.add(new SpawnerEntry(snapshot, entry.getWeight().asInt(), rule));
            }
        }
        return entries;
    }
    private SpawnData.CustomSpawnRules toMinecraftRule(SpawnRule rule) { // PAIL rename CustomSpawnRules
        if (rule == null) {
            return null;
        }
        return new SpawnData.CustomSpawnRules(new InclusiveRange<>(rule.getMinBlockLight(), rule.getMaxBlockLight()), new InclusiveRange<>(rule.getMinSkyLight(), rule.getMaxSkyLight()));
    }
    private SpawnRule fromMinecraftRule(SpawnData.CustomSpawnRules rule) {
        InclusiveRange<Integer> blockLight = rule.blockLightLimit();
        InclusiveRange<Integer> skyLight = rule.skyLightLimit();
        return new SpawnRule(blockLight.maxInclusive(), blockLight.maxInclusive(), skyLight.minInclusive(), skyLight.maxInclusive());
    }

    @Override
    public String getCreatureTypeName() {
        SpawnData spawnData = this.getSnapshot().getSpawner().nextSpawnData;
        if (spawnData == null) {
            return null;
        }

        Optional<net.minecraft.world.entity.EntityType<?>> type = net.minecraft.world.entity.EntityType.by(spawnData.getEntityToSpawn());
        return type.map(entityTypes -> net.minecraft.world.entity.EntityType.getKey(entityTypes).getPath()).orElse(null);
    }

    @Override
    public void setCreatureTypeByName(String creatureType) {
        // Verify input
        EntityType type = EntityType.fromName(creatureType);
        if (type == null) {
            setSpawnedType(null);
            return;
        }
        setSpawnedType(type);
    }

    @Override
    public int getDelay() {
        return this.getSnapshot().getSpawner().spawnDelay;
    }

    @Override
    public void setDelay(int delay) {
        this.getSnapshot().getSpawner().spawnDelay = delay;
    }

    @Override
    public int getMinSpawnDelay() {
        return this.getSnapshot().getSpawner().minSpawnDelay;
    }

    @Override
    public void setMinSpawnDelay(int spawnDelay) {
        Preconditions.checkArgument(spawnDelay <= getMaxSpawnDelay(), "Minimum Spawn Delay must be less than or equal to Maximum Spawn Delay");
        this.getSnapshot().getSpawner().minSpawnDelay = spawnDelay;
    }

    @Override
    public int getMaxSpawnDelay() {
        return this.getSnapshot().getSpawner().maxSpawnDelay;
    }

    @Override
    public void setMaxSpawnDelay(int spawnDelay) {
        Preconditions.checkArgument(spawnDelay > 0, "Maximum Spawn Delay must be greater than 0.");
        Preconditions.checkArgument(spawnDelay >= getMinSpawnDelay(), "Maximum Spawn Delay must be greater than or equal to Minimum Spawn Delay");
        this.getSnapshot().getSpawner().maxSpawnDelay = spawnDelay;
    }

    @Override
    public int getMaxNearbyEntities() {
        return this.getSnapshot().getSpawner().maxNearbyEntities;
    }

    @Override
    public void setMaxNearbyEntities(int maxNearbyEntities) {
        this.getSnapshot().getSpawner().maxNearbyEntities = maxNearbyEntities;
    }

    @Override
    public int getSpawnCount() {
        return this.getSnapshot().getSpawner().spawnCount;
    }

    @Override
    public void setSpawnCount(int count) {
        this.getSnapshot().getSpawner().spawnCount = count;
    }

    @Override
    public int getRequiredPlayerRange() {
        return this.getSnapshot().getSpawner().requiredPlayerRange;
    }

    @Override
    public void setRequiredPlayerRange(int requiredPlayerRange) {
        this.getSnapshot().getSpawner().requiredPlayerRange = requiredPlayerRange;
    }

    @Override
    public int getSpawnRange() {
        return this.getSnapshot().getSpawner().spawnRange;
    }

    @Override
    public void setSpawnRange(int spawnRange) {
        this.getSnapshot().getSpawner().spawnRange = spawnRange;
    }

    @Override
    public CraftCreatureSpawner copy() {
        return new CraftCreatureSpawner(this);
    }
}
