package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.ResourceBuffer;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** All ports of a controller participate in the same transaction, including nested rollbacks. */
final class MachineTransaction extends SnapshotParticipant<MachineTransaction.State> {
    private static final Map<MachineBlockEntity, WeakReference<MachineTransaction>> STATES = new WeakHashMap<>();
    private final MachineBlockEntity machine;
    private final Map<Output, Long> outputs = new HashMap<>();

    static MachineTransaction of(MachineBlockEntity machine) {
        var reference = STATES.get(machine);
        var state = reference == null ? null : reference.get();
        if (state == null) {
            state = new MachineTransaction(machine);
            STATES.put(machine, new WeakReference<>(state));
        }
        return state;
    }

    private MachineTransaction(MachineBlockEntity machine) { this.machine = machine; }
    void beforeChange(TransactionContext transaction) { updateSnapshots(transaction); }
    void recordOutput(MachineBlockEntity module, Direction side, long amount) {
        outputs.merge(new Output(module, side), amount, Long::sum);
    }

    private record Output(MachineBlockEntity module, Direction side) {}
    private record BufferState(boolean item, ResourceLocation id, DataComponentPatch components, double amount) {
        BufferState(ResourceBuffer buffer) { this(buffer.item, buffer.id, buffer.components, buffer.amount); }
        void restore(ResourceBuffer buffer) {
            buffer.item = item; buffer.id = id; buffer.components = components; buffer.amount = amount;
        }
    }
    record State(long energy, BufferState[] buffers, Map<Output, Long> outputs) {}

    @Override protected State createSnapshot() {
        var buffers = new BufferState[machine.buffers.length];
        for (int i = 0; i < buffers.length; i++) buffers[i] = new BufferState(machine.buffers[i]);
        return new State(machine.energy, buffers, new HashMap<>(outputs));
    }
    @Override protected void readSnapshot(State state) {
        machine.energy = state.energy();
        for (int i = 0; i < state.buffers().length; i++) state.buffers()[i].restore(machine.buffers[i]);
        outputs.clear(); outputs.putAll(state.outputs());
    }
    @Override protected void onFinalCommit() {
        outputs.forEach((output, amount) -> machine.recordOutput(output.module(), output.side(), amount));
        outputs.clear();
        machine.setChanged();
    }
}
