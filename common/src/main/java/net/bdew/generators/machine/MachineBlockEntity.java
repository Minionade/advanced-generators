package net.bdew.generators.machine;

import net.bdew.generators.ModContent;
import net.bdew.generators.config.GeneratorConfig;
import net.bdew.generators.menu.MachineMenu;
import net.bdew.generators.platform.TransferPlatform;
import net.bdew.generators.recipe.MachineRecipe;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

public final class MachineBlockEntity extends BlockEntity implements MenuProvider {
    public final ResourceBuffer[] buffers = {new ResourceBuffer(32000), new ResourceBuffer(32000), new ResourceBuffer(32000), new ResourceBuffer(32000)};
    public final SimpleContainer inventory = new SimpleContainer(4) { @Override public boolean canPlaceItem(int slot, ItemStack stack) { return kind()==MachineKind.SYNGAS_CONTROLLER && carbonValue(stack)>0; } };
    public long energy, energyCapacity;
    public double maxOutput, heat, carbon, burnTime, rpm, energyFraction;
    public double generationRate, consumptionRate, processRate, heatLossRate;
    public int turbines, heatingChambers, mixingChambers, exchangers;
    public double inertia = 1, efficiency = 1.1;
    public int controlAction, sensorMetric, sensorCondition;
    public boolean controlHigh = true;
    public final int[] outputMode = new int[6], outputSelection = new int[6];
    public final long[] outputLimit = new long[6];
    private final double[] steamHistory = new double[20];
    private int steamIndex;
    private MultiblockNetwork cachedNetwork;
    private long networkRevision = -1, networkTick = -1;
    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MACHINE_ENTITY.get(), pos, state);
        Arrays.fill(outputLimit, Integer.MAX_VALUE);
        inventory.addListener(c -> setChanged());
    }
    public MachineKind kind() { return ((MachineBlock)getBlockState().getBlock()).kind; }
    public void cacheNetwork(MultiblockNetwork network) { cachedNetwork = network; networkRevision = MultiblockNetwork.revision(level); networkTick = level.getGameTime(); }
    public MultiblockNetwork network() {
        if (cachedNetwork == null || networkRevision != MultiblockNetwork.revision(level) || level.getGameTime() - networkTick >= 20) return MultiblockNetwork.discover(this);
        return cachedNetwork;
    }
    public MachineBlockEntity controller() {
        if (level == null) return null;
        var network = network();
        if (network.error() != 0 || network.controller() == null || !level.hasChunkAt(network.controller())) return null;
        return level.getBlockEntity(network.controller()) instanceof MachineBlockEntity m ? m : null;
    }
    public List<MachineBlockEntity> members() {
        var result = new ArrayList<MachineBlockEntity>();
        for (var p : network().members()) if (level.hasChunkAt(p) && level.getBlockEntity(p) instanceof MachineBlockEntity m) result.add(m);
        return result;
    }
    public BlockPos findUpgradeTarget(ResourceLocation from) {
        if (BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).equals(from)) return worldPosition;
        for (var machine : members()) if (BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock()).equals(from)) return machine.getBlockPos();
        return null;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        var network = machine.network();
        if (machine.kind().controller()) {
            machine.recalculate();
            machine.generationRate = machine.consumptionRate = machine.processRate = machine.heatLossRate = 0;
            if (network.error() == 0) {
                switch (machine.kind()) {
                    case FUEL_TURBINE_CONTROLLER -> machine.tickFuel();
                    case STEAM_TURBINE_CONTROLLER -> machine.tickSteam();
                    case SYNGAS_CONTROLLER -> machine.tickSyngas();
                    case EXCHANGER_CONTROLLER -> machine.tickExchanger();
                    default -> {}
                }
                machine.setChanged();
            }
        } else if (machine.kind().output() && network.error() == 0) TransferPlatform.push(machine);
        if (machine.kind() == MachineKind.SENSOR || machine.kind() == MachineKind.CONTROL) {
            boolean powered = machine.kind() == MachineKind.CONTROL ? level.hasNeighborSignal(pos) : machine.sensor();
            if (state.getValue(BlockStateProperties.POWERED) != powered) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), 3);
                level.updateNeighborsAt(pos, state.getBlock());
            }
        }
    }
    public void recalculate() {
        var c = GeneratorConfig.INSTANCE;
        energyCapacity = c.energyCapacity; maxOutput = 0; turbines = heatingChambers = mixingChambers = exchangers = 0;
        int tanks = 0; boolean tier1 = false, tier2 = false; double totalInertia = 0;
        for (var m : members()) {
            var kind = m.kind();
            switch (kind.category) {
                case "turbine" -> { turbines++; maxOutput += c.turbineOutput[kind.tier-1]; totalInertia += c.turbineInertia[kind.tier-1]; }
                case "powerCapacitor" -> energyCapacity += c.capacitorCapacity[kind.tier-1];
                case "fuelTank" -> tanks++;
                case "heatingChamber" -> heatingChambers++;
                case "mixingChamber" -> mixingChambers++;
                case "heatExchanger" -> exchangers++;
                case "efficiencyUpgradeTier1" -> tier1 = true;
                case "efficiencyUpgradeTier2" -> tier2 = true;
            }
        }
        inertia = turbines == 0 ? 1 : totalInertia / turbines;
        efficiency = c.fuelEfficiency[tier1 ? (tier2 ? 2 : 1) : 0];
        double capacity = switch (kind()) {
            case FUEL_TURBINE_CONTROLLER -> c.fuelCapacity + (double)tanks * c.fuelTankCapacity;
            case STEAM_TURBINE_CONTROLLER -> c.steamCapacity;
            case SYNGAS_CONTROLLER -> c.syngasCapacity;
            default -> c.exchangerCapacity;
        };
        for (var buffer : buffers) buffer.capacity = capacity;
        // Do not erase stored resources when a module or chunk disappears. Over-capacity storage refuses insertions.
    }
    private List<RecipeHolder<MachineRecipe>> recipes(String type) { return level.getRecipeManager().getAllRecipesFor(ModContent.RECIPE_TYPES.get(type).get()); }
    public double carbonValue(ItemStack stack) {
        return recipes("carbon_source").stream().map(RecipeHolder::value).filter(r -> r.ingredient.test(stack)).mapToDouble(r -> r.value("carbonValue")).findFirst().orElse(0);
    }
    private double fuelValue() {
        if (buffers[0].empty()) return 0;
        return recipes("liquid_fuel").stream().map(RecipeHolder::value).filter(r -> r.matchesFluid(buffers[0].fluid())).mapToDouble(r -> r.value("fePerMb")).findFirst().orElse(0);
    }
    private void addEnergy(double quantity) {
        double accepted = Math.min(quantity, Math.max(0, energyCapacity - energy));
        energyFraction += accepted; long whole = (long)Math.floor(energyFraction); energy += whole; energyFraction -= whole; generationRate = accepted;
    }
    private void tickFuel() {
        if (maxOutput <= 0 || !control(1, true)) return;
        double fuelPerTick = fuelValue() > 0 ? maxOutput / fuelValue() / efficiency : 0;
        if (burnTime < 5 && fuelPerTick > 0) {
            double consumed = buffers[0].extract(Math.ceil(10 * fuelPerTick), false);
            burnTime += consumed / fuelPerTick; consumptionRate = consumed;
        }
        if (burnTime >= 1 && energyCapacity - energy >= maxOutput) { burnTime -= 1; addEnergy(maxOutput); }
    }
    private void tickSteam() {
        var c = GeneratorConfig.INSTANCE;
        if (maxOutput <= 0) { rpm = 0; return; }
        double delta = EngineMath.rpmDelta(rpm, c.maxRpm, c.inertiaMultiplier, c.inertiaExponent, inertia);
        if (control(3, energy < energyCapacity) && rpm > 1) { addEnergy(rpm / c.maxRpm * maxOutput); rpm -= delta * c.coilDrag; }
        rpm = Math.max(0, rpm - delta * c.baseDrag);
        double maxSteam = maxOutput / c.steamEnergyDensity;
        double used = control(2, true) ? buffers[0].extract(Math.ceil(maxSteam), false) : 0;
        steamHistory[steamIndex++ % 20] = used; consumptionRate = used;
        double average = Arrays.stream(steamHistory).sum() / 20;
        if (average > 0) rpm = Math.min(rpm + delta * c.spinUp, Math.max(rpm, Math.min(c.maxRpm, average / maxSteam * c.maxRpm)));
        if (rpm < 1) rpm = 0;
    }
    private void tickSyngas() {
        var c = GeneratorConfig.INSTANCE;
        if (heat < c.syngasMaxHeat && carbon > 0 && heatingChambers > 0 && control(5, buffers[0].amount > 0)) {
            double add = EngineMath.min(c.syngasMaxHeat-heat, carbon/c.carbonPerHeat, heatingChambers*c.chamberHeating);
            heat += add; carbon -= add*c.carbonPerHeat; consumptionRate += add*c.carbonPerHeat;
        }
        if (heat > c.syngasWorkHeat && heatingChambers > 0) {
            double steam = EngineMath.min(buffers[0].amount*c.waterSteamRatio, c.syngasCapacity-buffers[1].amount, heatingChambers*c.heatingThroughput*heat/c.syngasMaxHeat);
            buffers[0].extract(Math.ceil(steam/c.waterSteamRatio), false);
            buffers[1].insert(false, net.bdew.generators.Generators.id("steam"), DataComponentPatch.EMPTY, steam, false);
        }
        if (control(6, true)) {
            double gas = Math.floor(EngineMath.min(carbon/c.carbonPerSyngas, buffers[1].amount/c.steamPerSyngas, buffers[2].capacity-buffers[2].amount, c.mixingThroughput*mixingChambers));
            carbon -= gas*c.carbonPerSyngas; buffers[1].extract(gas*c.steamPerSyngas, false);
            buffers[2].insert(false, net.bdew.generators.Generators.id("syngas"), DataComponentPatch.EMPTY, gas, false);
            processRate = gas; consumptionRate += gas*c.carbonPerSyngas;
        }
        heat -= Math.min(heat, heatingChambers*c.chamberLoss);
        for (int slot=0;slot<4;slot++) {
            double value = carbonValue(inventory.getItem(slot));
            if (value > 0 && c.syngasCapacity-carbon >= value) { inventory.removeItem(slot,1); carbon += value; }
        }
    }
    private MachineRecipe exchangerRecipe(String type, boolean item, ResourceLocation id, DataComponentPatch patch) {
        if (id == null) return null;
        return recipes(type).stream().map(RecipeHolder::value).filter(r -> r.matchesInput(item,id,patch)).findFirst().orElse(null);
    }
    private double exchange(ResourceBuffer input, ResourceBuffer output, String type, double budget) {
        if (input.empty()) return 0;
        var r = exchangerRecipe(type,input.item,input.id,input.components); if (r == null) return 0;
        double space = output.insert(r.outputItem(),r.outputId(),DataComponentPatch.EMPTY,Math.max(0,budget*r.outPerHeat()),true);
        double hu = EngineMath.heatTransfer(budget,input.amount,space,r.inPerHeat(),r.outPerHeat());
        if (hu > 0) { input.extract(hu*r.inPerHeat(),false); output.insert(r.outputItem(),r.outputId(),DataComponentPatch.EMPTY,hu*r.outPerHeat(),false); }
        return hu;
    }
    private void tickExchanger() {
        var c = GeneratorConfig.INSTANCE; boolean enabled = control(4,true);
        if (enabled && heat > c.exchangerStartHeat) {
            double hu = exchange(buffers[1],buffers[2],"exchanger_cooling",Math.min(heat-c.exchangerStartHeat,exchangers*c.exchangerTransfer));
            heat -= hu; processRate = hu;
        }
        double beforeLoss=heat;
        heat *= 1-c.exchangerDecay;
        heatLossRate=beforeLoss-heat;
        if (enabled && heat < c.exchangerMaxHeat) {
            double hu = exchange(buffers[0],buffers[3],"exchanger_heating",Math.min(c.exchangerMaxHeat-heat,exchangers*c.exchangerTransfer));
            heat += hu; consumptionRate = hu;
        }
    }
    /** Actions: disabled, fuel, steam, generation, exchange, heating, mixing. Enable wins over disable. */
    public boolean control(int action, boolean fallback) {
        boolean disabled = false;
        for (var m : members()) if (m.kind() == MachineKind.CONTROL && m.controlAction == action) {
            if (level.hasNeighborSignal(m.worldPosition) == m.controlHigh) return true;
            disabled = true;
        }
        return disabled ? false : fallback;
    }
    public boolean outputEnabled(Direction side) {
        if (!kind().output() || level==null) return false;
        var config=controller(); if(config==null)return false;
        int mode = config.outputMode[side.get3DDataValue()]; boolean signal = level.hasNeighborSignal(worldPosition);
        return mode == 0 || mode == 1 && signal || mode == 2 && !signal;
    }
    public int[] inputSlots() { return kind() == MachineKind.SYNGAS_CONTROLLER || kind() == MachineKind.EXCHANGER_CONTROLLER ? new int[]{0,1} : new int[]{0}; }
    public int[] outputSlots(int selection) {
        if (kind() == MachineKind.SYNGAS_CONTROLLER) return new int[]{2};
        if (kind() == MachineKind.EXCHANGER_CONTROLLER) return selection == 1 ? new int[]{2} : selection == 2 ? new int[]{3} : new int[]{2,3};
        return new int[0];
    }
    private boolean steam(ResourceLocation id) {
        var fluid = BuiltInRegistries.FLUID.get(id);
        return id.equals(net.bdew.generators.Generators.id("steam")) || fluid.is(TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c","steam")));
    }
    public boolean accepts(int slot, boolean item, ResourceLocation id, DataComponentPatch patch) {
        if (item && kind() != MachineKind.EXCHANGER_CONTROLLER) return false;
        return switch (kind()) {
            case FUEL_TURBINE_CONTROLLER -> !item && slot == 0 && recipes("liquid_fuel").stream().anyMatch(r -> r.value().matchesFluid(BuiltInRegistries.FLUID.get(id)));
            case STEAM_TURBINE_CONTROLLER -> !item && slot == 0 && steam(id);
            case SYNGAS_CONTROLLER -> !item && (slot == 0 && BuiltInRegistries.FLUID.get(id) == Fluids.WATER || slot == 1 && steam(id));
            case EXCHANGER_CONTROLLER -> slot < 2 && exchangerRecipe(slot == 0 ? "exchanger_heating" : "exchanger_cooling",item,id,patch) != null;
            default -> false;
        };
    }
    public double insertResource(int slot, boolean item, ResourceLocation id, DataComponentPatch patch, double amount, boolean simulate) {
        if (!accepts(slot,item,id,patch)) return 0;
        if (!item && (kind() == MachineKind.STEAM_TURBINE_CONTROLLER || kind() == MachineKind.SYNGAS_CONTROLLER && slot == 1)) id = net.bdew.generators.Generators.id("steam");
        double added = buffers[slot].insert(item,id,patch,amount,simulate); if (!simulate && added > 0) setChanged(); return added;
    }
    public long extractEnergy(long amount, boolean simulate) {
        long moved = Math.max(0,Math.min(amount,energy)); if (!simulate && moved > 0) { energy -= moved; setChanged(); } return moved;
    }
    public double metric(int metric) {
        return switch(metric) {
            case 1 -> energyCapacity == 0 ? 0 : (double)energy/energyCapacity;
            case 2,3,4,5 -> buffers[metric-2].capacity == 0 ? 0 : buffers[metric-2].amount/buffers[metric-2].capacity;
            case 6 -> rpm/GeneratorConfig.INSTANCE.maxRpm;
            case 7 -> heat/(kind() == MachineKind.SYNGAS_CONTROLLER ? GeneratorConfig.INSTANCE.syngasMaxHeat : GeneratorConfig.INSTANCE.exchangerMaxHeat);
            case 8 -> carbon/GeneratorConfig.INSTANCE.syngasCapacity;
            case 9 -> maxOutput == 0 ? 0 : generationRate/maxOutput;
            default -> 0;
        };
    }
    private boolean sensor() {
        var c = controller(); if(c == null || sensorMetric == 0) return false;
        double x = c.metric(sensorMetric);
        return switch(sensorCondition) { case 0 -> x <= 0; case 1 -> x > 0; case 2 -> x < .25; case 3 -> x < .5; case 4 -> x < .75; case 5 -> x >= .25; case 6 -> x >= .5; case 7 -> x >= .75; case 8 -> x >= 1; default -> false; };
    }
    public void dropItems() {
        if (level == null || level.isClientSide) return;
        Containers.dropContents(level,worldPosition,inventory);
        for (var b : buffers) while (b.item && b.amount >= 1) {
            ItemStack stack = b.stack(); if (stack.isEmpty()) break;
            Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack); b.extract(stack.getCount(),false);
        }
    }
    @Override public Component getDisplayName() { return Component.translatable("block.advgenerators."+kind().id()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new MachineMenu(id,inv,this); }
    public CompoundTag snapshot() { var tag = new CompoundTag(); saveAdditional(tag,level.registryAccess()); return tag; }
    public void restore(CompoundTag tag) { loadAdditional(tag,level.registryAccess()); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.putLong("energy",energy); tag.putDouble("heat",heat); tag.putDouble("carbon",carbon); tag.putDouble("burnTime",burnTime); tag.putDouble("rpm",rpm); tag.putDouble("fraction",energyFraction);
        for(int i=0;i<4;i++) tag.put("buffer"+i,buffers[i].save(registries));
        tag.put("inventory",inventory.createTag(registries));
        tag.putInt("action",controlAction); tag.putBoolean("high",controlHigh); tag.putInt("metric",sensorMetric); tag.putInt("condition",sensorCondition);
        tag.putIntArray("outputMode",outputMode); tag.putIntArray("outputSelection",outputSelection); tag.putLongArray("outputLimit",outputLimit);
    }
    private static double finite(CompoundTag tag,String key) { double v = tag.getDouble(key); return Double.isFinite(v) ? Math.max(0,v) : 0; }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        energy = Math.max(0,tag.getLong("energy")); heat=finite(tag,"heat"); carbon=finite(tag,"carbon"); burnTime=finite(tag,"burnTime"); rpm=finite(tag,"rpm"); energyFraction=Math.min(.999999,finite(tag,"fraction"));
        for(int i=0;i<4;i++) { buffers[i].capacity = Double.MAX_VALUE; buffers[i].load(tag.getCompound("buffer"+i),registries); }
        inventory.clearContent(); inventory.fromTag(tag.getList("inventory",Tag.TAG_COMPOUND),registries);
        controlAction=Math.clamp(tag.getInt("action"),0,6); controlHigh=!tag.contains("high") || tag.getBoolean("high");
        sensorMetric=Math.clamp(tag.getInt("metric"),0,9); sensorCondition=Math.clamp(tag.getInt("condition"),0,8);
        int[] modes=tag.getIntArray("outputMode"), selections=tag.getIntArray("outputSelection"); long[] limits=tag.getLongArray("outputLimit");
        for(int i=0;i<6;i++) { outputMode[i]=i<modes.length?Math.clamp(modes[i],0,3):0; outputSelection[i]=i<selections.length?Math.clamp(selections[i],0,2):0; outputLimit[i]=i<limits.length?Math.clamp(limits[i],1,Integer.MAX_VALUE):Integer.MAX_VALUE; }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { var tag=new CompoundTag();saveAdditional(tag,registries);return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
