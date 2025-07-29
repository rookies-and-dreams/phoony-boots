package net.meteox.phoony_boots.player;

import net.meteox.phoony_boots.PhoonyBoots;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = PhoonyBoots.MOD_ID)
public class PlayerAirStrafeHandler {

    private static final double AIR_STRAFE_SPEED_FACTOR_PER_LEVEL = 0.0005f;
    private static final double AIR_STRAFE_SPEED_CAP_PER_LEVEL = 0.005f;

    private static final double AIR_STRAFE_SPEED_FACTOR = 0.002f;
    private static final double AIR_STRAFE_SPEED_CAP = 0.015f;
    private static final double AIR_TURN_SCALE = 0.8f;

    private static final double VANILLA_AIR_DRAG = 0.89725f;
    private static final double VANILLA_FRICTION = 0.89725f;

    private static final Logger LOGGER = LogManager.getLogger();

    private static float lastYaw = Float.NaN;

    // Server and client common tick
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Player player = event.player;
        Minecraft mc = Minecraft.getInstance();

        // Cancel out the mod if sprint is held
        boolean sprintHeld = mc.options.keySprint.isDown();
        if (sprintHeld)
            return;

        // Store Yaw values
        float currentYaw = player.getYRot(); // live camera yaw
        float yawDelta = Float.isNaN(lastYaw) ? 0 : currentYaw - lastYaw;
        lastYaw = currentYaw;

        // Invert friction while space is held
        boolean jumpHeld = mc.options.keyJump.isDown();
        boolean wHeld = mc.options.keyUp.isDown();

        if (jumpHeld && player.onGround()) {
            double dx = player.getDeltaMovement().x;
            double dz = player.getDeltaMovement().z;
            invertFriction(player, dx, dz);
        }

        if (!player.onGround() && canUsePhoony(player)) {
            removeVanillaAirDrag(player);

            if (!wHeld) {
                killVanillaMovement(player);

                boolean leftPressed = mc.options.keyLeft.isDown();
                boolean rightPressed = mc.options.keyRight.isDown();

                double speedFactor = getSpeedBoostForLevel(player);
                double speedCap = getSpeedBoostCapForLevel(player);

                LOGGER.info("Speed Cap:" + speedCap);
                LOGGER.info("Speed Factor:" + speedFactor);

                double speedBoost = Math.min(speedCap, Math.abs(yawDelta * speedFactor));

                if (leftPressed && yawDelta < 0) {
                    addForwardMomentum(player, speedBoost, AIR_TURN_SCALE);
                }
                // If D is held and player turned camera right
                else if (rightPressed && yawDelta > 0) {
                    addForwardMomentum(player, speedBoost, AIR_TURN_SCALE);
                }
            }
        }
    }

    private static boolean canUsePhoony(Player player) {
        // No enchantment, no bitches
        if (EnchantmentHelper.getItemEnchantmentLevel(PhoonyBoots.PHOONY.get(),
                player.getItemBySlot(EquipmentSlot.FEET)) <= 0)
            return false;

        // Is flying, no bitches
        if (player.getAbilities().flying)
            return false;

        // If you're wearing some fancy chest piece that could be an elytra, a jetpack, a backpack? No bitches
        // This is to ensure compatibility with mods. If you want a backpack and bhopping, just use curios or something.
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chestStack.isEmpty()) {
            Item chestItem = chestStack.getItem();
            if (!(chestItem instanceof ArmorItem)) {
                return false;
            }
        }

        // If you get here, you have phoony, you're not flying, and you're either topless
        // or you have some kind of armor piece on your chest. Good to go!
        return true;
    }

    private static void killVanillaMovement(Player pPlayer) {
        // Kill vanilla air strafing input
        pPlayer.xxa = 0;

        // Remove sideways velocity (velocity perpendicular to facing)
        double dx = pPlayer.getDeltaMovement().x;
        double dz = pPlayer.getDeltaMovement().z;

        float yawRad = (float) Math.toRadians(pPlayer.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        // Project velocity onto forward vector
        double forwardSpeed = dx * forwardX + dz * forwardZ;

        // New velocity = only forward component (remove sideways)
        double newVelX = forwardX * forwardSpeed;
        double newVelZ = forwardZ * forwardSpeed;

        pPlayer.setDeltaMovement(newVelX, pPlayer.getDeltaMovement().y, newVelZ);
    }

    public static void removeVanillaAirDrag(Player pPlayer) {
        // Get current motion vector
        double dx = pPlayer.getDeltaMovement().x;
        double dz = pPlayer.getDeltaMovement().z;

        // Compute current horizontal speed
        double currentSpeed = Math.sqrt(dx * dx + dz * dz);

        // Undo the drag by scaling speed back up
        if (currentSpeed > 0) {
            double restoredSpeed = currentSpeed / VANILLA_AIR_DRAG;

            // Scale motion vector to restored speed
            double scale = restoredSpeed / currentSpeed;
            double newX = dx * scale;
            double newZ = dz * scale;

            pPlayer.setDeltaMovement(newX, pPlayer.getDeltaMovement().y, newZ);
        }
    }

    public static void invertFriction(Player pPlayer, double pVelocityX, double pVelocityZ) {
        BlockPos posBelow = pPlayer.blockPosition().below();
        BlockState blockBelow = pPlayer.level().getBlockState(posBelow);

        double friction = blockBelow.getBlock().getFriction();
        double frictionCompensation = VANILLA_FRICTION / friction;

        pPlayer.setDeltaMovement(
                pVelocityX * frictionCompensation,
                pPlayer.getDeltaMovement().y,
                pVelocityZ * frictionCompensation
        );
    }

    private static void addForwardMomentum(Player player, double speedBoost, double sideScale) {
        Vec3 vel = player.getDeltaMovement();
        double vx = vel.x;
        double vz = vel.z;

        double yawRad = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        // Project velocity onto forward direction
        double forwardSpeed = vx * forwardX + vz * forwardZ;

        // Compute sideways component
        double sideX = vx - forwardSpeed * forwardX;
        double sideZ = vz - forwardSpeed * forwardZ;

        // Reduce sideways drift
        sideX *= sideScale;
        sideZ *= sideScale;

        // Add boosted forward speed
        double newVx = forwardX * (forwardSpeed + speedBoost) + sideX;
        double newVz = forwardZ * (forwardSpeed + speedBoost) + sideZ;

        player.setDeltaMovement(
                newVx,
                vel.y,
                newVz
        );
    }

    private static double getSpeedBoostForLevel(Player pPlayer) {
        int boostLevel = EnchantmentHelper.getItemEnchantmentLevel(PhoonyBoots.PHOONY.get(), pPlayer.getItemBySlot(EquipmentSlot.FEET)) - 1;
        return AIR_STRAFE_SPEED_FACTOR + (AIR_STRAFE_SPEED_FACTOR_PER_LEVEL * boostLevel);
    }

    private static double getSpeedBoostCapForLevel(Player pPlayer) {
        int boostLevel = EnchantmentHelper.getItemEnchantmentLevel(PhoonyBoots.PHOONY.get(), pPlayer.getItemBySlot(EquipmentSlot.FEET)) - 1;
        return AIR_STRAFE_SPEED_CAP + (AIR_STRAFE_SPEED_CAP_PER_LEVEL * boostLevel);
    }
}
