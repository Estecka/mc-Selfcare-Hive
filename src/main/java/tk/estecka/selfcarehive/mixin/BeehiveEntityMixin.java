package tk.estecka.selfcarehive.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import tk.estecka.selfcarehive.BeehiveUtil;
import tk.estecka.selfcarehive.IBeeColonyTracker;
import tk.estecka.selfcarehive.SelfCareHive;

import static net.minecraft.block.entity.BeehiveBlockEntity.MAX_BEE_COUNT;


@Unique
@Mixin(BeehiveBlockEntity.class)
public class BeehiveEntityMixin
extends BlockEntity
implements IBeeColonyTracker
{
	static private final String KNOWNBEES_KEY = "selfcare-hive:KnownBees";
	private final Map<UUID,Long> knownBees = new HashMap<>(MAX_BEE_COUNT + 1);

	private BeehiveEntityMixin(){ super(null, null, null); }
	@Shadow public int	getBeeCount(){ throw new AssertionError(); }


	private void GarbageCollectBees(){
		long minLastSeen = this.getWorld().getTime() - this.getWorld().getGameRules().getInt(SelfCareHive.TRACKING_DURATION);
		knownBees.entrySet().removeIf(entry -> {
			boolean r = entry.getValue() < minLastSeen;
			if (r && FabricLoader.getInstance().isDevelopmentEnvironment())
				SelfCareHive.LOGGER.warn("A bee has gone missing: {}", entry.getKey());
			return r;
		});

		int maxKnownBees = MAX_BEE_COUNT - this.getBeeCount();
		if (maxKnownBees < 0)
			maxKnownBees = 0;

		if (knownBees.size() > maxKnownBees) {
			// Sort from newest (largest) to oldest (smallest)
			final var sortedEntries = new ArrayList<>(knownBees.entrySet());
			sortedEntries.sort( (a, b) -> -Long.compare(a.getValue(), b.getValue()) );
	
			for (int i=maxKnownBees; i<sortedEntries.size(); ++i){
				if (FabricLoader.getInstance().isDevelopmentEnvironment())
					SelfCareHive.LOGGER.warn("Superfluous bee was pruned: {}", sortedEntries.get(i).getKey());
				this.knownBees.remove(sortedEntries.get(i).getKey());
			}
		}
	}

	public void selfcarehive$LogColony(){
		StringBuilder string = new StringBuilder();
		string.append("Inside: ").append(this.getBeeCount())
		      .append(", Outside: ").append(this.knownBees.size())
		      ;

		for (var entry : this.knownBees.entrySet())
			string.append("\n - ").append(entry.getKey()).append(' ').append(entry.getValue());

		SelfCareHive.LOGGER.info(string.toString());
	}

	public boolean selfcarehive$isColonyFull(){
		GarbageCollectBees();
		return (this.getBeeCount() + this.knownBees.size()) >= MAX_BEE_COUNT;
	}

	public void selfcarehive$RememberBee(UUID uuid){
		knownBees.put(uuid, this.getWorld().getTime());
	}

	@Inject( method="writeNbt", at=@At("TAIL") )
	private void WriteCustomNBT(NbtCompound nbt, CallbackInfo ci){
		if (!knownBees.isEmpty()){
			NbtCompound list = new NbtCompound();
			for (var entry : knownBees.entrySet())
				list.putLong(entry.getKey().toString(), entry.getValue());
			nbt.put(KNOWNBEES_KEY, list);
		}
	}
	@Inject( method="readNbt", at=@At("TAIL") )
	private void ReadCustomNBT(NbtCompound nbt, CallbackInfo ci){
		if (nbt.contains(KNOWNBEES_KEY, NbtElement.COMPOUND_TYPE)){
			NbtCompound list = nbt.getCompound(KNOWNBEES_KEY);
			for (String key : list.getKeys()){
				UUID uuid;
				long time;
				try {
					uuid = UUID.fromString(key);
					time = list.getLong(key);
				} catch (IllegalArgumentException|ClassCastException e){
					SelfCareHive.LOGGER.error("Invalid last-seen data in behive at {}:\nKey: {}, Value:\n{}", this.pos, key, nbt.get(key).asString());
					continue;
				}
				knownBees.put(uuid, time);
			}
		}
	}


	@Inject(method="tryEnterHive(Lnet/minecraft/entity/Entity;ZI)V", at=@At("TAIL"))
	private void OnBeeEntrance(Entity bee, boolean hasNectar, int ticksInHive, CallbackInfo ci){
		UUID uuid = bee.getUuid();
		if (FabricLoader.getInstance().isDevelopmentEnvironment() && !this.knownBees.containsKey(uuid))
			SelfCareHive.LOGGER.warn("An unknown bee joined the hive: {}", uuid);
		// Bees loose their UUID when returning to the nest.
		this.knownBees.remove(uuid);
	}

	/**
	 * @implNote At this point, the BeeEntity that is being released has not yet
	 * been removed  from  the hive's  own internal counter.  (This is never
	 * done inside `releaseBee`, only after the method has ended.)
	 * For this reason `rememberBee` must be called  AFTER  we evaluate the size
	 * of the colony, otherwise it will count one extra member.
	 * 
	 * @implNote This handler is intentionally injected  before the released bee
	 * has deposited its nectar. However, the bee's position in the world is not
	 * yet properly set, so babies need to have their position updated at a later
	 * time.
	 */
	@ModifyExpressionValue( method="releaseBee", expect=1, at=@At(value="INVOKE", target="net/minecraft/entity/EntityType.loadEntityWithPassengers (Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/world/World;Ljava/util/function/Function;)Lnet/minecraft/entity/Entity;") )
	static private Entity OnBeeEntityCreated(Entity original, World world, BlockPos pos, @Local(argsOnly=true) LocalRef<BlockState> stateRef, @Share("baby") LocalRef<BeeEntity> babyRef)
	{
		if (original instanceof BeeEntity bee && !world.isClient() && world.getBlockEntity(pos) instanceof BeehiveBlockEntity hive){
			IBeeColonyTracker colony = IBeeColonyTracker.Of(hive);
			BlockState hiveState = stateRef.get();
			BeeEntity baby = null;

			var result = BeehiveUtil.TryCreateBaby(bee, colony, (ServerWorld)world, hiveState, pos);
			baby = result.getLeft();
			hiveState = result.getRight();
			
			hiveState = BeehiveUtil.TryHeal(bee, world, hiveState, pos);

			colony.selfcarehive$RememberBee(bee.getUuid());

			babyRef.set(baby);
			stateRef.set(hiveState);
		}
		
		return original;
	}

	@WrapOperation( method="releaseBee", at=@At(value="INVOKE", target="net/minecraft/entity/Entity.refreshPositionAndAngles (DDDFF)V") )
	static private void	OnBeePositionUpdated(Entity bee, double x, double y, double z, float yaw, float pitch, Operation<Void> original, @Share("baby") LocalRef<BeeEntity> baby){
		BeeEntity babyEntity = baby.get();
		if (babyEntity != null){
			babyEntity.refreshPositionAndAngles(x, y, z, yaw, pitch);
			babyEntity.getWorld().spawnEntity(babyEntity);
		}

		original.call(bee, x, y, z, yaw, pitch);
	}

	/**
	 * Makes bees leave the nest instantly for testing purposes
	 */
	@ModifyArg( method="tryEnterHive(Lnet/minecraft/entity/Entity;ZI)V", at=@At(value="INVOKE", target="net/minecraft/block/entity/BeehiveBlockEntity.addBee (Lnet/minecraft/nbt/NbtCompound;IZ)V") )
	private int IncreaseTicksInHive(int original){
		return FabricLoader.getInstance().isDevelopmentEnvironment() ? 2380 : original;
	}
}
