package com.gmail.goosius.siegewar.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.jetbrains.annotations.NotNull;

import com.gmail.goosius.siegewar.SiegeController;
import com.gmail.goosius.siegewar.SiegeWarTimerTaskController;
import com.gmail.goosius.siegewar.enums.SiegeSide;
import com.gmail.goosius.siegewar.enums.SiegeStatus;
import com.gmail.goosius.siegewar.enums.SiegeWarPermissionNodes;
import com.gmail.goosius.siegewar.metadata.TownMetaDataController;
import com.gmail.goosius.siegewar.objects.Siege;
import com.gmail.goosius.siegewar.settings.SiegeWarSettings;
import com.gmail.goosius.siegewar.utils.SiegeWarBlockUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarDistanceUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarMoneyUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarPermissionUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarTimeUtil;
import com.gmail.goosius.siegewar.utils.TownPeacefulnessUtil;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyFormatter;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.DeleteNationEvent;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NationPreAddTownEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.PreDeleteNationEvent;
import com.palmergames.bukkit.towny.event.PreNewDayEvent;
import com.palmergames.bukkit.towny.event.RenameNationEvent;
import com.palmergames.bukkit.towny.event.RenameTownEvent;
import com.palmergames.bukkit.towny.event.SpawnEvent;
import com.palmergames.bukkit.towny.event.TownPreAddResidentEvent;
import com.palmergames.bukkit.towny.event.TownPreClaimEvent;
import com.palmergames.bukkit.towny.event.TownyLoadedDatabaseEvent;
import com.palmergames.bukkit.towny.event.nation.NationPreTownLeaveEvent;
import com.palmergames.bukkit.towny.event.nation.NationRankAddEvent;
import com.palmergames.bukkit.towny.event.nation.NationTownLeaveEvent;
import com.palmergames.bukkit.towny.event.nation.PreNewNationEvent;
import com.palmergames.bukkit.towny.event.statusscreen.NationStatusScreenEvent;
import com.palmergames.bukkit.towny.event.statusscreen.TownStatusScreenEvent;
import com.palmergames.bukkit.towny.event.time.NewHourEvent;
import com.palmergames.bukkit.towny.event.time.NewShortTimeEvent;
import com.palmergames.bukkit.towny.event.time.dailytaxes.PreTownPaysNationTaxEvent;
import com.palmergames.bukkit.towny.event.town.TownPreUnclaimCmdEvent;
import com.palmergames.bukkit.towny.event.town.TownRuinedEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownToggleExplosionEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownToggleNeutralEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownToggleOpenEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownTogglePVPEvent;
import com.palmergames.bukkit.towny.event.townblockstatus.NationZoneTownBlockStatusEvent;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Translation;
import com.palmergames.bukkit.util.ChatTools;
import com.palmergames.util.TimeMgmt;

/**
 * 
 * @author LlmDl
 *
 */
public class SiegeWarEventListener implements Listener {

	private final Towny plugin;
	
	public SiegeWarEventListener(Towny instance) {

		plugin = instance;
	}

	/*
	 * SW will prevent someone in a banner area from curing their poisoning with milk.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerConsume(PlayerItemConsumeEvent event) {
		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		if(SiegeWarSettings.getWarSiegeEnabled()) {
			try {
				//Prevent milk bucket usage while attempting to gain banner control
				if(event.getItem().getType() == Material.MILK_BUCKET) {
					for(Siege siege: SiegeController.getSieges()) {
						if(siege.getBannerControlSessions().containsKey(event.getPlayer())) {
							event.setCancelled(true);
							TownyMessaging.sendErrorMsg(event.getPlayer(), Translation.of("msg_war_siege_zone_milk_bucket_forbidden_while_attempting_banner_control"));
						}
					}
				}
		
			} catch (Exception e) {
				System.out.println("Problem evaluating siege player consume event");
				e.printStackTrace();
			}
		}
	}
	
	
	/*
	 * SW limits which Towns can join or be added to a nation.
	 */
	@EventHandler
	public void onNationAddTownEvent(NationPreAddTownEvent event) {
		if(SiegeWarSettings.getWarSiegeEnabled() && SiegeWarSettings.getWarCommonPeacefulTownsEnabled() && event.getTown().isNeutral()) {
			Set<Nation> validGuardianNations = TownPeacefulnessUtil.getValidGuardianNations(event.getTown());
			if(!validGuardianNations.contains(event.getNation())) {
				event.setCancelMessage(Translation.of("msg_war_siege_peaceful_town_cannot_join_nation", 
						event.getTown().getName(),
						event.getNation().getName(),
						SiegeWarSettings.getWarSiegePeacefulTownsGuardianTownMinDistanceRequirement(),
						SiegeWarSettings.getWarSiegePeacefulTownsGuardianTownPlotsRequirement()));
				event.setCancelled(true);
			}
		}
	}
	
	/*
	 * SW warns peaceful towns who make nations their decision may be a poor one, but does not stop them.
	 */
	@EventHandler
	public void onNewNationEvent(PreNewNationEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled() && SiegeWarSettings.getWarCommonPeacefulTownsEnabled()
				&& event.getTown().isNeutral()) {
			if (!SiegeWarSettings.getWarCommonPeacefulTownsAllowedToMakeNation()) {
				event.setCancelled(true);
				event.setCancelMessage(Translation.of("msg_war_siege_peaceful_towns_cannot_make_nations"));
			} else 
				TownyMessaging.sendMsg(event.getTown().getMayor(), Translation.of("msg_war_siege_warning_peaceful_town_should_not_create_nation"));
		}
	}
	
	/*
	 * SW will warn a nation about to delete itself that it can claim a refund after the fact.
	 */
	@EventHandler
	public void onNationDeleteEvent(PreDeleteNationEvent event) {
		//If nation refund is enabled, warn the player that they will get a refund (and indicate how to claim it).
		if (SiegeWarSettings.getWarSiegeEnabled() && TownySettings.isUsingEconomy()
				&& SiegeWarSettings.getWarSiegeRefundInitialNationCostOnDelete()) {
			int amountToRefund = (int)(TownySettings.getNewNationPrice() * 0.01 * SiegeWarSettings.getWarSiegeNationCostRefundPercentageOnDelete());
			TownyMessaging.sendMsg(event.getNation().getKing(), Translation.of("msg_err_siege_war_delete_nation_warning", TownyEconomyHandler.getFormattedBalance(amountToRefund)));
		}

	}
	
	/*
	 * Duplicates what exists in the TownyBlockListener but on a higher priority.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPistonRetract(BlockPistonRetractEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		if (testBlockMove(event.getBlock(), event.isSticky() ? event.getDirection().getOppositeFace() : event.getDirection()))
			event.setCancelled(true);

		List<Block> blocks = event.getBlocks();
		
		if (!blocks.isEmpty()) {
			//check each block to see if it's going to pass a plot boundary
			for (Block block : blocks) {
				if (testBlockMove(block, event.getDirection()))
					event.setCancelled(true);
			}
		}
	}

	/*
	 * Duplicates what exists in the TownyBlockListener but on a higher priority.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPistonExtend(BlockPistonExtendEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}
		
		if (testBlockMove(event.getBlock(), event.getDirection()))
			event.setCancelled(true);
		
		List<Block> blocks = event.getBlocks();

		if (!blocks.isEmpty()) {
			//check each block to see if it's going to pass a plot boundary
			for (Block block : blocks) {
				if (testBlockMove(block, event.getDirection()))
					event.setCancelled(true);
			}
		}
	}

	/**
	 * Decides whether blocks moved by pistons follow the rules.
	 * 
	 * @param block - block that is being moved.
	 * @param direction - direction the piston is facing.
	 * 
	 * @return true if block is able to be moved according to siege war rules. 
	 */
	private boolean testBlockMove(Block block, BlockFace direction) {

		Block blockTo = block.getRelative(direction);

		if(SiegeWarSettings.getWarSiegeEnabled()) {
			if(SiegeWarBlockUtil.isBlockNearAnActiveSiegeBanner(block) || SiegeWarBlockUtil.isBlockNearAnActiveSiegeBanner(blockTo)) {
				return true;
			}
		}

		return false;
	}
	
	/*
	 * SW will prevent towns leaving their nations.
	 */
	@EventHandler
	public void onTownTriesToLeaveNation(NationPreTownLeaveEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled()) {

			Town town = event.getTown();
			//If a peaceful town has no options, we don't let it revolt
			if(SiegeWarSettings.getWarCommonPeacefulTownsEnabled() && town.isNeutral()) {
				Set<Nation> validGuardianNations = TownPeacefulnessUtil.getValidGuardianNations(town);
				if(validGuardianNations.size() == 0) {
					event.setCancelMessage(Translation.of("msg_war_siege_peaceful_town_cannot_revolt_nearby_guardian_towns_zero", 
						SiegeWarSettings.getWarSiegePeacefulTownsGuardianTownMinDistanceRequirement(), 
						SiegeWarSettings.getWarSiegePeacefulTownsGuardianTownPlotsRequirement()));
					event.setCancelled(true);
				} else if(validGuardianNations.size() == 1) {
					event.setCancelMessage(Translation.of("msg_war_siege_peaceful_town_cannot_revolt_nearby_guardian_towns_one", 
						SiegeWarSettings.getWarSiegePeacefulTownsGuardianTownMinDistanceRequirement(), 
						SiegeWarSettings.getWarSiegePeacefulTownsGuardianTownPlotsRequirement()));
					event.setCancelled(true);
				}
			}

			if (SiegeWarSettings.getWarSiegeTownLeaveDisabled()) {

				if (!SiegeWarSettings.getWarSiegeRevoltEnabled()) {
					event.setCancelMessage(Translation.of("msg_err_siege_war_town_voluntary_leave_impossible"));
					event.setCancelled(true);
				}
				if (town.isConquered() && System.currentTimeMillis() < TownMetaDataController.getRevoltImmunityEndTime(town)) {
					event.setCancelMessage(Translation.of("msg_err_siege_war_revolt_immunity_active"));
					event.setCancelled(true);
				} else {
					// Towny will cancel the leaving on lowest priority if the town is conquered.
					// We want to un-cancel it.
					if (event.isCancelled())
						event.setCancelled(false);
				}
			}
		}
	}
	
	@EventHandler
	public void onTownLeavesNation(NationTownLeaveEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled() && event.getTown().isConquered()) {
			//Activate revolt immunity
			SiegeWarTimeUtil.activateRevoltImmunityTimer(event.getTown());
			event.getTown().setConquered(false);
			event.getTown().setConqueredDays(0);
			TownyUniverse.getInstance().getDataSource().saveTown(event.getTown());

			TownyMessaging.sendGlobalMessage(
				String.format(Translation.of("msg_siege_war_revolt"),
					event.getTown().getFormattedName(),
					event.getTown().getMayor().getFormattedName(),
					event.getNation().getFormattedName()));
		}	
	}
	
	@EventHandler
	public void onTownGoesToRuin(TownRuinedEvent event) {
		if (SiegeController.hasSiege(event.getTown()))
			SiegeController.removeSiege(SiegeController.getSiege(event.getTown()), SiegeSide.ATTACKERS);
	}
	
	@EventHandler
	public void onNationRankGivenToPlayer(NationRankAddEvent event) throws NotRegisteredException {
		//In Siegewar, if target town is peaceful, can't add military rank
		if(SiegeWarSettings.getWarSiegeEnabled()
			&& SiegeWarSettings.getWarCommonPeacefulTownsEnabled()
			&& SiegeWarPermissionUtil.doesNationRankAllowPermissionNode(event.getRank(), SiegeWarPermissionNodes.SIEGEWAR_NATION_SIEGE_POINTS)
			&& event.getResident().getTown().isNeutral()) { // We know that the resident's town will not be null based on the tests already done.
			event.setCancelled(true);
			event.setCancelMessage(Translation.of("msg_war_siege_cannot_add_nation_military_rank_to_peaceful_resident"));
		}
		
	}

	/*
	 * If town is under siege, town cannot recruit new members
	 */
	@EventHandler
	public void onTownAddResident(TownPreAddResidentEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled()
				&& SiegeWarSettings.getWarSiegeBesiegedTownRecruitmentDisabled()
				&& SiegeController.hasActiveSiege(event.getTown())) {
			event.setCancelled(true);
			event.setCancelMessage(Translation.of("msg_err_siege_besieged_town_cannot_recruit"));
		}
	}

	/*
	 * Upon creation of a town, towns can be set to neutral.
	 */
	@EventHandler
	public void onCreateNewTown(NewTownEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled()) {
			Town town = event.getTown();
			TownMetaDataController.setSiegeImmunityEndTime(town, System.currentTimeMillis() + (long)(SiegeWarSettings.getWarSiegeSiegeImmunityTimeNewTownsHours() * TimeMgmt.ONE_HOUR_IN_MILLIS));
			TownMetaDataController.setDesiredPeacefullnessSetting(town, TownySettings.getTownDefaultNeutral());
			TownyUniverse.getInstance().getDataSource().saveTown(town);
		}
	}
	
	/*
	 * On toggle explosions, SW will stop a town toggling explosions.
	 */
	@EventHandler
	public void onTownToggleExplosion(TownToggleExplosionEvent event) {
		if(SiegeWarSettings.getWarSiegeEnabled()
				&& SiegeWarSettings.getWarSiegeExplosionsAlwaysOnInBesiegedTowns()
				&& SiegeController.hasActiveSiege(event.getTown()))  {
			event.setCancellationMsg(Translation.of("msg_err_siege_besieged_town_cannot_toggle_explosions"));
			event.setCancelled(true);
		}
	}
	
	/*
	 * On toggle pvp, SW will stop a town toggling pvp.
	 */
	@EventHandler
	public void onTownTogglePVP(TownTogglePVPEvent event) {
		if(SiegeWarSettings.getWarSiegeEnabled()
				&& SiegeWarSettings.getWarSiegePvpAlwaysOnInBesiegedTowns()
				&& SiegeController.hasActiveSiege(event.getTown()))  {
			event.setCancellationMsg(Translation.of("msg_err_siege_besieged_town_cannot_toggle_pvp"));
			event.setCancelled(true);
		}
	}
	
	/*
	 * On toggle open, SW will stop a town toggling open.
	 */
	@EventHandler
	public void onTownToggleOpen(TownToggleOpenEvent event) {
		if(SiegeWarSettings.getWarSiegeEnabled()
				&& SiegeWarSettings.getWarSiegeBesiegedTownRecruitmentDisabled()
				&& SiegeController.hasActiveSiege(event.getTown())) {
			event.setCancellationMsg(Translation.of("msg_err_siege_besieged_town_cannot_toggle_open_off"));
			event.setCancelled(true);
		}
	}
	
	/*
	 * On toggle neutral, SW will evaluate a number of things.
	 */
	@EventHandler
	public void onTownToggleNeutral(TownToggleNeutralEvent event) {
		if (!SiegeWarSettings.getWarSiegeEnabled())
			return;
		
		if(!SiegeWarSettings.getWarCommonPeacefulTownsEnabled()) {
			event.setCancellationMsg(Translation.of("msg_err_command_disable"));
			event.setCancelled(true);
			return;
		}
		
		Town town = event.getTown();
		
		if (event.isAdminAction()) {
			return;
		} else {
			int days;
			if(System.currentTimeMillis() < (town.getRegistered() + (TimeMgmt.ONE_DAY_IN_MILLIS * 7))) {
				days = SiegeWarSettings.getWarCommonPeacefulTownsNewTownConfirmationRequirementDays();
			} else {
				days = SiegeWarSettings.getWarCommonPeacefulTownsConfirmationRequirementDays();
			}
			
			if (TownMetaDataController.getPeacefulnessChangeConfirmationCounterDays(town) == 0) {
				
				//Here, no countdown is in progress, and the town wishes to change peacefulness status
				TownMetaDataController.setDesiredPeacefullnessSetting(town, !town.isNeutral());
				TownMetaDataController.setPeacefulnessChangeDays(town, days);
				
				//Send message to town
				if (TownMetaDataController.getDesiredPeacefulnessSetting(town))
					TownyMessaging.sendPrefixedTownMessage(town, String.format(Translation.of("msg_war_common_town_declared_peaceful"), days));
				else
					TownyMessaging.sendPrefixedTownMessage(town, String.format(Translation.of("msg_war_common_town_declared_non_peaceful"), days));
				
				//Remove any military nation ranks of residents
				for(Resident peacefulTownResident: town.getResidents()) {
					for (String nationRank : new ArrayList<>(peacefulTownResident.getNationRanks())) {
						if (SiegeWarPermissionUtil.doesNationRankAllowPermissionNode(nationRank, SiegeWarPermissionNodes.SIEGEWAR_NATION_SIEGE_POINTS)) {
							try {
								peacefulTownResident.removeNationRank(nationRank);
							} catch (NotRegisteredException ignored) {}
						}
					}
				}
				event.setCancellationMsg(Translation.of("status_town_peacefulness_status_change_timer", days));
				event.setCancelled(true);
				
			} else {
				//Here, a countdown is in progress, and the town wishes to cancel the countdown,
				TownMetaDataController.setDesiredPeacefullnessSetting(town, town.isNeutral());
				TownMetaDataController.setPeacefulnessChangeDays(town, 0);
				//Send message to town
				TownyMessaging.sendPrefixedTownMessage(town, String.format(Translation.of("msg_war_common_town_peacefulness_countdown_cancelled")));				
				event.setCancellationMsg(Translation.of("msg_war_common_town_peacefulness_countdown_cancelled"));
				event.setCancelled(true);
			}
		}
	}
	
	/*
	 * Siegewar has to be conscious of when Towny has loaded the Towny database.
	 */
	@EventHandler
	public void onTownyDatabaseLoad(TownyLoadedDatabaseEvent event) {
		loadSieges();
	}
	
	@EventHandler
	public void onTownyEnable(PluginEnableEvent event) {
		if (event.getPlugin().getName().equals("Towny")) {
			loadSieges();
		}
	}
	
	private void loadSieges() {
		SiegeController.clearSieges();
		SiegeController.loadSiegeList();
		SiegeController.loadSieges();
		System.out.println("SiegeWar: " + SiegeController.getSieges().size() + " siege(s) loaded.");
		
	}
	
	/*
	 * Update town peacefulness counters.
	 */
	@EventHandler
	public void onNewDay(PreNewDayEvent event) {
		if (SiegeWarSettings.getWarCommonPeacefulTownsEnabled()) {
			TownPeacefulnessUtil.updateTownPeacefulnessCounters();
			if(SiegeWarSettings.getWarSiegeEnabled())
				TownPeacefulnessUtil.evaluatePeacefulTownNationAssignments();
		}
	}
	
	/*
	 * On NewHours SW makes some calculations.
	 */
	@EventHandler
	public void onNewHour(NewHourEvent event) {
		if(SiegeWarSettings.getWarSiegeEnabled()) {
			SiegeWarTimerTaskController.updatePopulationBasedSiegePointModifiers();
		}
	}

	/*
	 * On each ShortTime period, SW makes some calcuations.
	 */
	@EventHandler
	public void onShortTime(NewShortTimeEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled()) {
			SiegeWarTimerTaskController.punishPeacefulPlayersInActiveSiegeZones();
			SiegeWarTimerTaskController.evaluateBattleSessions();
			SiegeWarTimerTaskController.evaluateBannerControl();
			SiegeWarTimerTaskController.evaluateTacticalVisibility();
			SiegeWarTimerTaskController.evaluateTimedSiegeOutcomes();
		}

	}
	
	/*
	 * Upon attempting to claim land, SW will stop it under some conditions.
	 */
	@EventHandler
	public void onTownClaim(TownPreClaimEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled()) {
			//If the claimer's town is under siege, they cannot claim any land
			if (SiegeWarSettings.getWarSiegeBesiegedTownClaimingDisabled()
				&& SiegeController.hasActiveSiege(event.getTown())) {
				event.setCancelled(true);
				event.setCancelMessage(Translation.of("msg_err_siege_besieged_town_cannot_claim"));
				return;
			}

			//If the land is too near any active siege zone, it cannot be claimed.
			if(SiegeWarSettings.getWarSiegeClaimingDisabledNearSiegeZones()) {
				for(Siege siege: SiegeController.getSieges()) {
					try {
						if (siege.getStatus().isActive()
							&& SiegeWarDistanceUtil.isInSiegeZone(event.getPlayer(), siege)) {
							event.setCancelled(true);
							event.setCancelMessage(Translation.of("msg_err_siege_claim_too_near_siege_zone"));
							break;
						}
					} catch (Exception e) {
						//Problem with this particular siegezone. Ignore siegezone
						try {
							System.out.println("Problem with verifying claim against the following siege zone" + siege.getName() + ". Claim allowed.");
						} catch (Exception e2) {
							System.out.println("Problem with verifying claim against a siege zone (name could not be read). Claim allowed");
						}
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/*
	 * Siege War will prevent unclaiming land in some situations.
	 */
	@EventHandler
	public void onTownUnclaim(TownPreUnclaimCmdEvent event) {
		if (SiegeWarSettings.getWarCommonOccupiedTownUnClaimingDisabled() && event.getTown().isConquered()) {
			event.setCancelled(true);
			event.setCancelMessage(Translation.of("msg_err_war_common_occupied_town_cannot_unclaim"));
			return;
		}
			
		if(SiegeWarSettings.getWarSiegeEnabled()
			&& SiegeWarSettings.getWarSiegeBesiegedTownUnClaimingDisabled()
			&& SiegeController.hasSiege(event.getTown())
			&& (
				SiegeController.getSiege(event.getTown()).getStatus().isActive()
				|| SiegeController.getSiege(event.getTown()).getStatus() == SiegeStatus.ATTACKER_WIN
				|| SiegeController.getSiege(event.getTown()).getStatus() == SiegeStatus.DEFENDER_SURRENDER
				)
			)
		{
			event.setCancelled(true);
			event.setCancelMessage(Translation.of("msg_err_siege_besieged_town_cannot_unclaim"));
		}
	}
	
	/*
	 * Simply saving the siege will set the name of the siege.
	 */
	@EventHandler
	public void onTownRename(RenameTownEvent event) {
		if (SiegeController.hasSiege(event.getTown())) {
			SiegeController.saveSiege(SiegeController.getSiege(event.getTown()));
		}
	}

	/*
	 * Simply saving the siege will set the name of the siege.
	 */
	@EventHandler
	public void onNationRename(RenameNationEvent event) {
		if (SiegeController.hasSieges(event.getNation())) {
			for (Siege siege : SiegeController.getSieges(event.getNation()))
				SiegeController.saveSiege(siege);
		}
	}

	/*
	 * A town being deleted with a siege means the siege ends.
	 */
	@EventHandler
	public void onDeleteTown(DeleteTownEvent event) {
		if (SiegeController.hasSiege(event.getTownUUID()))
			SiegeController.removeSiege(SiegeController.getSiege(event.getTownUUID()), SiegeSide.ATTACKERS);
	}

	/*
	 * A nation being deleted with a siege means the siege ends.
	 */
	@EventHandler
	public void onDeleteNation(DeleteNationEvent event) {
		Resident king = TownyUniverse.getInstance().getResident(event.getNationKing());
		if (king != null)
			SiegeWarMoneyUtil.makeNationRefundAvailable(king);
		
		for (Siege siege : SiegeController.getSiegesByNationUUID(event.getNationUUID())) {
			SiegeController.removeSiege(siege, SiegeSide.DEFENDERS);
		}
	}

	/*
	 * In SiegeWar neutral/peaceful towns do not pay their Nation tax. 
	 */
	@EventHandler
	public void onTownPayNationTax(PreTownPaysNationTaxEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled() && SiegeWarSettings.getWarCommonPeacefulTownsEnabled() && event.getTown().isNeutral()) {
			event.setCancelled(true);
		}
	}
	
	/*
	 * SiegeWar will disable nation-zones if the town has a siege.
	 */
	@EventHandler
	public void onNationZoneStatus(NationZoneTownBlockStatusEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled() 
			&& SiegeController.hasActiveSiege(event.getTown()))	{
			event.setCancelled(true);
		}
	}
	
	/*
	 * SiegeWar prevents people from spawning to siege areas if they are not peaceful and do not belong to the town in question.
	 */
	@EventHandler
	public void onPlayerUsesTownySpawnCommand(SpawnEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled() && SiegeWarSettings.getWarSiegeNonResidentSpawnIntoSiegeZonesOrBesiegedTownsDisabled()) {
			Town destinationTown = TownyAPI.getInstance().getTown(event.getTo());
			Resident res = TownyUniverse.getInstance().getResident(event.getPlayer().getUniqueId());
			if (destinationTown == null || res == null)
				return;
			
			// Don't block spawning for residents which belong to the Town, or to neutral towns.
			if (destinationTown.hasResident(res) || destinationTown.isNeutral())
				return;

			//Block TP if the target town is besieged
			if (SiegeController.hasActiveSiege(destinationTown)) {
				event.setCancelled(true);
				event.setCancelMessage(Translation.of("msg_err_siege_war_cannot_spawn_into_siegezone_or_besieged_town"));
				return;
			}

			//Block TP if the target spawn point is in a siege zone
			if (SiegeWarDistanceUtil.isLocationInActiveSiegeZone(event.getTo())) {
				event.setCancelled(true);
				event.setCancelMessage(Translation.of("msg_err_siege_war_cannot_spawn_into_siegezone_or_besieged_town"));

			}
		}		
	}
	
	/*
	 * SiegeWar will add lines to towns which have a siege
	 */
	@EventHandler
	public void onTownStatusScreen(TownStatusScreenEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled()) {
			List<String> out = new ArrayList<>();
			Town town = event.getTown();
			
	        //Revolt Immunity Timer: 71.8 hours
	        if (SiegeWarSettings.getWarSiegeRevoltEnabled() && System.currentTimeMillis() < TownMetaDataController.getRevoltImmunityEndTime(town)) {        	
	        	String time = TimeMgmt.getFormattedTimeValue(TownMetaDataController.getRevoltImmunityEndTime(town)- System.currentTimeMillis());        	
	            out.add(Translation.of("status_town_revolt_immunity_timer", time));
	        }

	        if (SiegeController.hasSiege(town)) {
	            Siege siege = SiegeController.getSiege(town);
	            String time = TimeMgmt.getFormattedTimeValue(TownMetaDataController.getRevoltImmunityEndTime(town)- System.currentTimeMillis());
	            switch (siege.getStatus()) {
	                case IN_PROGRESS:
	                    //Siege:
	                    String siegeStatus = Translation.of("status_town_siege_status", getStatusTownSiegeSummary(siege));
	                    out.add(siegeStatus);

	                    // > Banner XYZ: {2223,82,9877}
	                    out.add(
	                            Translation.of("status_town_siege_status_banner_xyz",
	                            siege.getFlagLocation().getBlockX(),
	                            siege.getFlagLocation().getBlockY(),
	                            siege.getFlagLocation().getBlockZ())
	                    );

	                    // > Attacker: Land of Empire (Nation) {+30}
	                    int pointsInt = siege.getSiegePoints();
	                    String pointsString = pointsInt > 0 ? "+" + pointsInt : "" + pointsInt;
	                    out.add(Translation.of("status_town_siege_status_besieger", siege.getAttackingNation().getFormattedName(), pointsString));

	                    // >  Victory Timer: 5.3 hours
	                    String victoryTimer = Translation.of("status_town_siege_victory_timer", siege.getFormattedHoursUntilScheduledCompletion());
	                    out.add(victoryTimer);

	                    // > Banner Control: Attackers [4] Killbot401x, NerfeyMcNerferson, WarCriminal80372
	                    if (siege.getBannerControllingSide() == SiegeSide.NOBODY) {
	                        out.add(Translation.of("status_town_banner_control_nobody", siege.getBannerControllingSide().getFormattedName()));
	                    } else {
	                        String[] bannerControllingResidents = TownyFormatter.getFormattedNames(siege.getBannerControllingResidents());
	                        if (bannerControllingResidents.length > 34) {
	                            String[] entire = bannerControllingResidents;
	                            bannerControllingResidents = new String[36];
	                            System.arraycopy(entire, 0, bannerControllingResidents, 0, 35);
	                            bannerControllingResidents[35] = Translation.of("status_town_reslist_overlength");
	                        }
	                        out.addAll(ChatTools.listArr(bannerControllingResidents, Translation.of("status_town_banner_control", siege.getBannerControllingSide().getFormattedName(), siege.getBannerControllingResidents().size())));
	                    }
	                    break;

	                    
	                case ATTACKER_WIN:
	                case DEFENDER_SURRENDER:
	                    siegeStatus = Translation.of("status_town_siege_status", getStatusTownSiegeSummary(siege));
	                    String invadedYesNo = siege.isTownInvaded() ? Translation.of("status_yes") : Translation.of("status_no_green");
	                    String plunderedYesNo = siege.isTownPlundered() ? Translation.of("status_yes") : Translation.of("status_no_green");
	                    String invadedPlunderedStatus = Translation.of("status_town_siege_invaded_plundered_status", invadedYesNo, plunderedYesNo);
	                    String siegeImmunityTimer = Translation.of("status_town_siege_immunity_timer", time);
	                    out.add(siegeStatus);
	                    out.add(invadedPlunderedStatus);
	                    out.add(siegeImmunityTimer);
	                    break;

	                case DEFENDER_WIN:
	                case ATTACKER_ABANDON:
	                    siegeStatus = Translation.of("status_town_siege_status", getStatusTownSiegeSummary(siege));
	                    siegeImmunityTimer = Translation.of("status_town_siege_immunity_timer", time);
	                    out.add(siegeStatus);
	                    out.add(siegeImmunityTimer);
	                    break;

	                case PENDING_DEFENDER_SURRENDER:
	                case PENDING_ATTACKER_ABANDON:
	                    siegeStatus = Translation.of("status_town_siege_status", getStatusTownSiegeSummary(siege));
	                    out.add(siegeStatus);
	                    break;
	            }
	        } else {
	            if (SiegeWarSettings.getWarSiegeAttackEnabled() 
	            	&& !(SiegeController.hasActiveSiege(town))
	            	&& System.currentTimeMillis() < TownMetaDataController.getSiegeImmunityEndTime(town)) {
	                //Siege:
	                // > Immunity Timer: 40.8 hours
	                out.add(Translation.of("status_town_siege_status", ""));
	                String time = TimeMgmt.getFormattedTimeValue(TownMetaDataController.getSiegeImmunityEndTime(town)- System.currentTimeMillis()); 
	                out.add(Translation.of("status_town_siege_immunity_timer", time));
	            }
	        }
	        event.addLines(out);
		}
	}

    private static String getStatusTownSiegeSummary(@NotNull Siege siege) {
        switch (siege.getStatus()) {
            case IN_PROGRESS:
                return Translation.of("status_town_siege_status_in_progress");
            case ATTACKER_WIN:
                return Translation.of("status_town_siege_status_attacker_win", siege.getAttackingNation().getFormattedName());
            case DEFENDER_SURRENDER:
                return Translation.of("status_town_siege_status_defender_surrender", siege.getAttackingNation().getFormattedName());
            case DEFENDER_WIN:
                return Translation.of("status_town_siege_status_defender_win");
            case ATTACKER_ABANDON:
                return Translation.of("status_town_siege_status_attacker_abandon");
            case PENDING_DEFENDER_SURRENDER:
                return Translation.of("status_town_siege_status_pending_defender_surrender", siege.getFormattedTimeUntilDefenderSurrender());
            case PENDING_ATTACKER_ABANDON:
                return Translation.of("status_town_siege_status_pending_attacker_abandon", siege.getFormattedTimeUntilAttackerAbandon());
            default:
                return "???";
        }
    }
    
	/*
	 * SiegeWar will add lines to Nation which have a siege
	 */
    @EventHandler
	public void onNationStatusScreen(NationStatusScreenEvent event) {
		if (SiegeWarSettings.getWarSiegeEnabled()) {
			Nation nation = event.getNation();
			
	        // Siege Attacks [3]: TownA, TownB, TownC
	        List<Town> siegeAttacks = getTownsUnderSiegeAttack(nation);
	        String[] formattedSiegeAttacks = TownyFormatter.getFormattedNames(siegeAttacks.toArray(new Town[0]));
	        List<String> out = new ArrayList<>(ChatTools.listArr(formattedSiegeAttacks, Translation.of("status_nation_siege_attacks", siegeAttacks.size())));

	        // Siege Defences [3]: TownX, TownY, TownZ
	        List<Town> siegeDefences = getTownsUnderSiegeDefence(nation);
	        String[] formattedSiegeDefences = TownyFormatter.getFormattedNames(siegeDefences.toArray(new Town[0]));
	        out.addAll(ChatTools.listArr(formattedSiegeDefences, Translation.of("status_nation_siege_defences", siegeDefences.size())));
	        
	        event.addLines(out);
		}
	}
    
	public static List<Town> getTownsUnderSiegeAttack(Nation nation) {
		List<Town> result = new ArrayList<>();
		for(Siege siege : SiegeController.getSieges()) {
			if(siege.getAttackingNation().equals(nation)) {				
				result.add(siege.getDefendingTown());
			}
		}
		return result;
	}

	public static List<Town> getTownsUnderSiegeDefence(Nation nation) {
		List<Town> result = new ArrayList<Town>();
		for(Town town: nation.getTowns()) {
			if(SiegeController.hasActiveSiege(town))
				result.add(town);
		}
		return result;
	}
	
}
