package tk.estecka.selfcarehive;

import java.util.UUID;

import net.minecraft.block.entity.BeehiveBlockEntity;

public interface IBeeColonyTracker
{
	static public IBeeColonyTracker Of(BeehiveBlockEntity hive){
		return (IBeeColonyTracker)hive;
	}

	boolean selfcarehive$isColonyFull();
	void selfcarehive$RememberBee(UUID uuid);
	void selfcarehive$LogColony();
}
