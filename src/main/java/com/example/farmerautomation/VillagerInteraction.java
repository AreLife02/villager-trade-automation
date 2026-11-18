package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Köylü etkileşimini vanilla istemci sağ-tık sırasıyla gönderir.
 * interactAt sonucu eylemi tüketmezse standart entity interact çağrılır.
 */
public final class VillagerInteraction {
    private VillagerInteraction() { }

    public record Attempt(InteractionResult interactAt, InteractionResult interact) {
        public String describe() {
            return "MAIN_HAND / interactAt=" + interactAt
                    + " / interact=" + (interact == null ? "AT sonucu eylemi tüketti" : interact);
        }
    }

    public static Attempt interact(Minecraft client, Villager villager, EntityHitResult hit) {
        if (client == null || client.player == null || client.gameMode == null
                || villager == null || hit == null || hit.getEntity() != villager) {
            throw new IllegalArgumentException("Köylü etkileşimi için geçerli oyuncu, hedef ve nişangâh gerekli.");
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        InteractionResult atResult = client.gameMode.interactAt(client.player, villager, hit, hand);
        InteractionResult entityResult = atResult.consumesAction()
                ? null
                : client.gameMode.interact(client.player, villager, hand);
        return new Attempt(atResult, entityResult);
    }
}
