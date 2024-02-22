package tk.estecka.selfcarehive;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

public class BeehiveUtil
{
	static public BlockState	SetHoneyLevel(int honey, World world, BlockState hiveState, BlockPos hivePos){
		hiveState = hiveState.with(BeehiveBlock.HONEY_LEVEL, honey);
		world.setBlockState(hivePos, hiveState);
		return hiveState;
	}

	static public BlockState TryHeal(BeeEntity bee, World world, BlockState hiveState, BlockPos hivePos){
		GameRules rules = world.getGameRules();
		boolean canHeal = rules.getBoolean(SelfCareHive.CAN_HEAL);
		int cost = rules.getInt(SelfCareHive.HEALING_COST);
		float potency = (float)rules.get(SelfCareHive.HEALING_AMOUNT).get();
		
		int honey = BeehiveBlockEntity.getHoneyLevel(hiveState);
		boolean isHurt = bee.getHealth() < bee.getMaxHealth();
		boolean willOverheal = (bee.getHealth() + potency) >= bee.getMaxHealth();
		boolean willOverflow = bee.hasNectar() && honey >= BeehiveBlock.FULL_HONEY_LEVEL;

		if (canHeal && isHurt && honey>=cost && (willOverflow || !willOverheal)){
			bee.heal(potency);
			return SetHoneyLevel(honey-cost, world, hiveState, hivePos);
		}
		else
			return hiveState;
	}

	static public Pair<@Nullable BeeEntity, BlockState>	TryCreateBaby(BeeEntity parent, IBeeColonyTracker colony, ServerWorld world, BlockState hiveState, BlockPos hivePos){
		GameRules rules = world.getGameRules();
		boolean canBreed = rules.getBoolean(SelfCareHive.CAN_BREED);
		int cost = rules.getInt(SelfCareHive.BREEDING_COST);

		int honey = BeehiveBlockEntity.getHoneyLevel(hiveState);
		
		// colony.selfcarehive$LogColony();
		if (canBreed
		&&  honey >= cost
		&&  parent.getBreedingAge() == 0 // Checks both adulthood and breeding cooldown.
		&&  !colony.selfcarehive$isColonyFull()
		){
			BeeEntity baby = parent.createChild(world, parent);
			baby.setBaby(true);
			baby.setPosition(parent.getPos());
			parent.resetLoveTicks();
			parent.setBreedingAge(6000);
			hiveState = SetHoneyLevel(honey-cost, world, hiveState, hivePos);
			colony.selfcarehive$RememberBee(baby.getUuid());
			return Pair.of(baby, hiveState);
		}
		else
			return Pair.of(null, hiveState);
	}
}
