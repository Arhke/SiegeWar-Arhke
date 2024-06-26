package com.gmail.goosius.siegewar.objects;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.gmail.goosius.siegewar.Messaging;
import com.gmail.goosius.siegewar.SiegeController;
import com.gmail.goosius.siegewar.enums.SiegeType;
import com.gmail.goosius.siegewar.events.PreSiegeWarStartEvent;
import com.gmail.goosius.siegewar.settings.SiegeWarSettings;
import com.gmail.goosius.siegewar.utils.SiegeWarMoneyUtil;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.Translatable;
import com.palmergames.util.TimeTools;

public class SiegeCamp {
	private final Player player;
	private final Block bannerBlock;
	private final SiegeType siegeType;
	private final Town targetTown;
	private final Government attacker;
	private final Government defender;
	private final Town townOfSiegeStarter;
	private final TownBlock townBlock;
	private int attackerPoints = 0;
	private final long endTime;

	public SiegeCamp(Player player, Block bannerBlock, SiegeType siegeType, Town targetTown, Government attacker,
			Government defender, Town townOfSiegeStarter,TownBlock townBlock) {
		this.player = player;
		this.bannerBlock = bannerBlock;
		this.siegeType = siegeType;
		this.targetTown = targetTown;
		this.attacker = attacker;
		this.defender = defender;
		this.townOfSiegeStarter = townOfSiegeStarter;
		this.townBlock = townBlock;
		this.endTime = System.currentTimeMillis() + TimeTools.getMillis(SiegeWarSettings.getSiegeCampDurationInMinutes() + "m");
	}

	/**
	 * @return the player
	 */
	public Player getPlayer() {
		return player;
	}

	/**
	 * @return the bannerBlock
	 */
	public Block getBannerBlock() {
		try{
			return targetTown.getSpawn().getBlock();
		}catch(Exception e){
			Bukkit.broadcastMessage(targetTown.getName() + " doesn't have a homeblock, please report this to admins. ");
		}
		return bannerBlock;
	}

	/**
	 * @return the siegeType
	 */
	public SiegeType getSiegeType() {
		return siegeType;
	}

	/**
	 * @return the targetTown
	 */
	public Town getTargetTown() {
		return targetTown;
	}

	/**
	 * @return the attacker
	 */
	public Government getAttacker() {
		return attacker;
	}

	/**
	 * @return the defender
	 */
	public Government getDefender() {
		return defender;
	}

	/**
	 * @return the townOfSiegeStarter
	 */
	public Town getTownOfSiegeStarter() {
		return townOfSiegeStarter;
	}

	/**
	 * @return the townBlock
	 */
	public TownBlock getTownBlock() {
		return townBlock;
	}

	/**
	 * @return the endTime
	 */
	public long getEndTime() {
		return endTime;
	}

	/**
	 * @return the attackerPoints
	 */
	public int getAttackerPoints() {
		return attackerPoints;
	}

	/**
	 * @param attackerPoints the attackerPoints to set
	 */
	public void setAttackerPoints(int attackerPoints) {
		this.attackerPoints = attackerPoints;
	}

	/**
	 * Starts the Siege after the success of the SiegeCamp.
	 */
	public void startSiege() {
		//Test that the siege starter can pay the siege start cost
		if (TownyEconomyHandler.isActive()) {
			double siegeStartCost = SiegeWarMoneyUtil.calculateTotalSiegeStartCost(targetTown);
			if (siegeType.equals(SiegeType.CONQUEST) && !attacker.getAccount().canPayFromHoldings(siegeStartCost)) {
				TownyMessaging.sendPrefixedNationMessage((Nation)attacker, Translatable.of("msg_err_your_nation_cannot_afford_to_siege_for_x", TownyEconomyHandler.getFormattedBalance(siegeStartCost)));
				return;
			} else if (siegeType.equals(SiegeType.REVOLT) && !targetTown.getAccount().canPayFromHoldings(siegeStartCost)) {
				TownyMessaging.sendPrefixedTownMessage(targetTown, Translatable.of("msg_err_your_town_cannot_afford_to_siege_for_x", TownyEconomyHandler.getFormattedBalance(siegeStartCost)));
				return;
			}
		}

		// Call event
		PreSiegeWarStartEvent preSiegeWarStartEvent = new PreSiegeWarStartEvent(siegeType, targetTown, (Nation)attacker, townOfSiegeStarter, bannerBlock, townBlock);
		Bukkit.getPluginManager().callEvent(preSiegeWarStartEvent);

		// Setup attack
		if (!preSiegeWarStartEvent.isCancelled()) {
			SiegeController.startSiege(
					bannerBlock, 
					siegeType, 
					targetTown, 
					attacker, 
					defender,
					townOfSiegeStarter, 
					!siegeType.equals(SiegeType.REVOLT));
		} else {
			Messaging.sendErrorMsg(player, preSiegeWarStartEvent.getCancellationMsg());
		}
	}

}
