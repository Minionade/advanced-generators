package net.bdew.generators.machine;

/**
 * Which recipe type each heat exchanger input accepts.
 *
 * The recipe names describe what happens to the <em>fluid</em>, not to the machine, so they read
 * backwards from the machine's point of view. The original mod's registry swapped them
 * ({@code heaters = exchangerCooling}, {@code coolers = exchangerHeating}); this keeps that mapping in one place.
 */
public final class ExchangerRoles {
    private ExchangerRoles() {}

    /** Hot input (buffer 0): a fluid that is cooled, e.g. lava to obsidian. It releases heat into the machine. */
    public static final String HOT_INPUT = "exchanger_cooling";
    /** Cold input (buffer 1): a fluid that is heated, e.g. water to steam. It absorbs heat from the machine. */
    public static final String COLD_INPUT = "exchanger_heating";

    public static String inputType(int slot) { return slot == 0 ? HOT_INPUT : COLD_INPUT; }
}
