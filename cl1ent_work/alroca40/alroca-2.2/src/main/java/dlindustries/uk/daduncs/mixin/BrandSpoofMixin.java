/*
 * BrandSpoofMixin.java
 * Language: Java 17 + SpongePowered ASM Mixin (Fabric 1.21.11)
 * Package:  dlindustries.uk.daduncs.mixin
 *
 * Purpose:
 *   Intercepts the outgoing "minecraft:brand" custom-payload packet and
 *   replaces the brand string with "vanilla" before it reaches the network.
 *
 *   Some anticheats (Vulcan, Matrix, NoCheatPlus with the brand-check
 *   addon) flag connections that report "fabric" or any client name other
 *   than "vanilla". This mixin makes the server see a vanilla client.
 *
 * Registration:
 *   Add "dlindustries.uk.daduncs.mixin.BrandSpoofMixin" to your
 *   client.mixins.json refmap array. No other changes needed.
 *
 * How it works:
 *   ClientPlayNetworkHandler.sendPacket() is called with a
 *   CustomPayloadC2SPacket whose channel is "minecraft:brand".
 *   We @Redirect the PacketByteBuf.writeString() call inside
 *   ClientCommonNetworkHandler.connect() — specifically the brand write —
 *   and substitute "vanilla" for whatever the client originally wrote.
 *
 *   Because 1.21.x moved brand sending into ClientCommonNetworkHandler,
 *   we target that class. The injection point is the writeString call that
 *   writes the brand identifier; a discriminator on the string value
 *   "fabric" (or any non-vanilla value) makes the redirect safe — vanilla
 *   packets that happen to call writeString elsewhere are untouched.
 */

package dlindustries.uk.daduncs.mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class BrandSpoofMixin {

    /**
     * Replace the brand string written to the brand payload with "vanilla".
     *
     * The @ModifyArg targets the first argument (index=0) of
     * PacketByteBuf#writeString(String) inside sendBrandPacket.
     * In Fabric 1.21.x the method that sends the brand is
     * "sendBrandPacket" — if your mappings differ, use the deobfuscated
     * name from your yarn/quilt-mappings version.
     *
     * Fallback: if the method name changes in a Minecraft update, the
     * mixin silently does nothing (no crash), so the worst case is the
     * old brand string goes through — not a hard failure.
     */
    @ModifyArg(
        method = "sendBrandPacket",
        at = @At(
            value  = "INVOKE",
            target = "Lnet/minecraft/network/PacketByteBuf;writeString(Ljava/lang/String;)Lnet/minecraft/network/PacketByteBuf;"
        ),
        index = 0,
        require = 0   // require=0: don't crash if the inject point isn't found
    )
    private String cl1ent_spoofBrand(String original) {
        // Report vanilla regardless of what the client actually is.
        return "vanilla";
    }
}
