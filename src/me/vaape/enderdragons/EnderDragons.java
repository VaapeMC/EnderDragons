package me.vaape.enderdragons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragon.Phase;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent;
import com.destroystokyo.paper.event.entity.EnderDragonFlameEvent;
import com.destroystokyo.paper.event.entity.EnderDragonShootFireballEvent;

import net.md_5.bungee.api.ChatColor;
import net.minecraft.server.level.EntityPlayer;

public class EnderDragons extends JavaPlugin implements Listener{
	
	public static EnderDragons plugin;
	private FileConfiguration config = this.getConfig();
	
	public boolean allowSpawn = false;
	
	private BukkitTask repeatingSpawnTimer;
	
	Calendar calendar = Calendar.getInstance();
	int day = calendar.get(Calendar.DAY_OF_WEEK);
	
	//NOTES
	//How the plugin works:
	//If a player spawns the enderdragon with crystals, if allowSpawn = false, the dragon will be .removed() 1 tick after spawn
	//This will create a timer that loops every 5 minutes that will attempt to respawn the dragon
	//When the dragon spawns, this timer is cancelled()
	//When the server restarts, the respawn timer is stopped, but a player can activate it again by trying to spawn the enderdragon with crystals
	//When dragon is killed, allowSpawn = false until a timer sets it to true
	//This timer is active even if the server is restarted, as the time is logged in the config
	
	public void onEnable() {
		plugin = this;
		loadConfiguration();
		getLogger().info(ChatColor.GREEN + "EnderDragons has been enabled!");
		getServer().getPluginManager().registerEvents(this,  this);
		saveConfig();

		if (config.get("time to allow spawning") == null) { //True if dragon is alive
			return;
		}
		
		Date timeToAllowSpawning = (Date) config.get("time to allow spawning");
		long timeToAllowSpawningInMillis = timeToAllowSpawning.getTime();
		Date now = new Date();
		long nowInMillis = now.getTime();
		
		if (timeToAllowSpawningInMillis <= nowInMillis) { //True if time is in the past
			allowSpawn = true;
			getLogger().info(ChatColor.YELLOW + "A dragon can be summoned from the void...");
		}
		else {
			//Create the timer to allow spawning saved by config			
			long timeUntilSpawnInMillis = timeToAllowSpawningInMillis - nowInMillis; //Get the difference in milliseconds from now until when it should spawn
			long timeUntilSpawnInSeconds = timeUntilSpawnInMillis/1000;
			
			setSpawnableTrue((int) timeUntilSpawnInSeconds * 20);
		}
	}
	
	public void loadConfiguration() {
		final FileConfiguration config = this.getConfig();
		config.set(("time of server start"), new Date());
		config.options().copyDefaults(true);
		saveConfig();
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		
		if (sender instanceof Player) {
			Player player = (Player) sender;
			
			if (cmd.getName().equalsIgnoreCase("allowdragonspawn")) {
				
				if (player.hasPermission("dragons.allowdragonspawn")) {
					
					setSpawnableTrue(1);
				}
				else {
					player.sendMessage("You do not have permission to do that.");
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	@EventHandler
	public void onSpawn (EntitySpawnEvent event) {
		Entity entity = event.getEntity();
		if (entity instanceof EnderDragon) {
			if (allowSpawn) {
				Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
					
					@Override
					public void run() {
						config.set("time to allow spawning", null);
						List<EnderDragon> eDrags = getDragons();
						List<String> dragNames = new ArrayList<>(Arrays.asList(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Twilight Dragon", ChatColor.GOLD + "" + ChatColor.BOLD + "Inferno Dragon", ChatColor.AQUA + "" + ChatColor.BOLD + "Glacial Dragon", ChatColor.GREEN + "" + ChatColor.BOLD + "Serpent Dragon"));
						Random randomizer = new Random();
						String name = dragNames.get(randomizer.nextInt(dragNames.size()));
						for (EnderDragon drag : eDrags) {
							drag.setCustomName(name);
							drag.setMaxHealth(drag.getMaxHealth() * 3);
							drag.setHealth(drag.getHealth() * 3);
						}
						allowSpawn = false;
						if (repeatingSpawnTimer != null) {
							repeatingSpawnTimer.cancel();
						}
					}
				},1);
			}
			else {
				
				new BukkitRunnable() {
					
					@Override
					public void run() {
						for (EnderDragon drag : getDragons()) {
							drag.remove();
						}
					}
				}.runTaskLater(plugin, 1); //1 tick after spawn
				
				for (Player player : Bukkit.getServer().getOnlinePlayers()) {
					if (player.getWorld() == Bukkit.getServer().getWorld("world_the_end")) {
						player.sendMessage(ChatColor.RED + "No dragons can be summoned right now right now...");
					}
				}
				
				repeatingSpawnTimer = new BukkitRunnable() {
					
					@Override
					public void run() {
						spawnDragon();
					}
				}.runTaskLater(plugin, 20 * 30); //Every 30 seconds this will loop through
			}
		}
	}
	
	@EventHandler
	public void onDeath (EntityDeathEvent event) {
		if (event.getEntity() instanceof EnderDragon) {
			Location location = event.getEntity().getLocation().add(0, 5, 0);
			Block block = location.getBlock();
			block.setType(Material.DRAGON_EGG);
			double random = Math.random();
			Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.COAL, (int) (1*random) + 1));
			Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.CHARCOAL,  (int) (2*random) + 1));
			Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.FLINT,  (int) (2*random) + 1));
			Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.BLACK_DYE,  (int) (3*random) + 1));
			Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.GUNPOWDER,  (int) (1*random) + 1));
			
			if (event.getEntity().getKiller() != null) {
				if (event.getEntity().getKiller() instanceof Arrow) {
					Arrow arrow = (Arrow) event.getEntity().getKiller();
					if (arrow.getShooter() instanceof Player) {
						Player shooter = (Player) arrow.getShooter();
						if (shooter.hasPermission("enderdragons.dropdiamonds")) {
							Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.DIAMOND, (int) (Math.round(random * 12)) + 1));
						}
						if (shooter.hasPermission("enderdragons.dropnetherite")) {
							Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.NETHERITE_INGOT, (int) (Math.round(random * 1)) + 1));
						}
						if (shooter.hasPermission("enderdragons.dropessence")) {
							ItemStack essence = new ItemStack(Material.FIRE_CHARGE, (int) (Math.round(random * 2)) + 1);
							essence.addUnsafeEnchantment(Enchantment.ARROW_FIRE, 1);
							ItemMeta meta = essence.getItemMeta();
							meta.setDisplayName(ChatColor.GOLD + "Dragon Essence");
							essence.setItemMeta(meta);
							Bukkit.getWorld("world_the_end").dropItemNaturally(location, essence);
							Bukkit.getWorld("world_the_end").playSound(location, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 5f, 0.1f);
						}
					}
				}
				else if (event.getEntity().getKiller() instanceof Player) {
					Player killer = event.getEntity().getKiller();
					if (killer.hasPermission("enderdragons.dropdiamonds")) {
						Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.DIAMOND, (int) (Math.round(random * 12)) + 1));
					}
					if (killer.hasPermission("enderdragons.dropnetherite")) {
						Bukkit.getWorld("world_the_end").dropItemNaturally(location, new ItemStack(Material.NETHERITE_INGOT, (int) (Math.round(random * 1)) + 1));
					}
					if (killer.hasPermission("enderdragons.dropessence")) {
						ItemStack essence = new ItemStack(Material.FIRE_CHARGE, (int) (Math.round(random * 2)) + 1);
						essence.addUnsafeEnchantment(Enchantment.ARROW_FIRE, 1);
						ItemMeta meta = essence.getItemMeta();
						meta.setDisplayName(ChatColor.GOLD + "Dragon Essence");
						essence.setItemMeta(meta);
						Bukkit.getWorld("world_the_end").dropItemNaturally(location, essence);
						Bukkit.getWorld("world_the_end").playSound(location, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 5f, 0.1f);
					}
				}
			}
			
			Random randomizer = new Random();
			
			if (day == Calendar.SUNDAY || day == Calendar.SATURDAY) {
				
				int hours = 6 + randomizer.nextInt(4); //Between 6 and 10 hours
				
				setSpawnableTrue(20 * 60 * 60 * hours);
				
				Date timeToAllowSpawning = addHoursToJavaUtilDate(new Date(), hours);
				config.set("time to allow spawning", timeToAllowSpawning);
				saveConfig();
			}
			else {
				int hours = 6 + randomizer.nextInt(4); //Between 6 and 10 hours
				
				setSpawnableTrue(20 * 60 * 60 * hours);
				
				Date timeToAllowSpawning = addHoursToJavaUtilDate(new Date(), hours);
				config.set("time to allow spawning", timeToAllowSpawning);
				saveConfig();
			}
		}
	}
	
	@EventHandler
	public void onEnderDragonFireballHit (EnderDragonFireballHitEvent event) {
		DragonFireball fireball = event.getEntity();
		AreaEffectCloud cloud = event.getAreaEffectCloud();
		if (fireball.hasMetadata("twilight")) {
			cloud.setParticle(Particle.SPELL_WITCH);
			cloud.addCustomEffect(new PotionEffect(PotionEffectType.WITHER, 300, 2, false, false), true);
		}
		else if (fireball.hasMetadata("inferno")) {
			cloud.setParticle(Particle.FLAME);
		}
		else if (fireball.hasMetadata("glacial")) {
			cloud.setParticle(Particle.SPELL_INSTANT);
			cloud.addCustomEffect(new PotionEffect(PotionEffectType.SLOW, 300, 3, false, false), true);
		}
		else if (fireball.hasMetadata("serpent")) {
			cloud.setParticle(Particle.TOTEM);
			cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 300, 2, false, false), true);
		}
	}
	
	@EventHandler
	public void onEnderDragonFireballShoot (EnderDragonShootFireballEvent event) {
		if (event.getEntity().getCustomName().contains("Twilight Dragon")) {
			DragonFireball fireball = event.getFireball();
			fireball.setMetadata("twilight", new FixedMetadataValue(plugin, "dragon"));
		}
		else if (event.getEntity().getCustomName().contains("Inferno Dragon")) {
			DragonFireball fireball = event.getFireball();
			fireball.setMetadata("inferno", new FixedMetadataValue(plugin, "dragon"));
		}
		else if (event.getEntity().getCustomName().contains("Glacial Dragon")) {
			DragonFireball fireball = event.getFireball();
			fireball.setMetadata("glacial", new FixedMetadataValue(plugin, "dragon"));
		}
		else if (event.getEntity().getCustomName().contains("Serpent Dragon")) {
			DragonFireball fireball = event.getFireball();
			fireball.setMetadata("serpent", new FixedMetadataValue(plugin, "dragon"));
		}
	}
	
	@EventHandler
	public void onDragonFlame (EnderDragonFlameEvent event) {
		String name = event.getEntity().getCustomName();
		AreaEffectCloud cloud = event.getAreaEffectCloud();
		if (event.getEntity().getCustomName().contains("Twilight Dragon")) {
			cloud.setParticle(Particle.SPELL_WITCH);
			cloud.addCustomEffect(new PotionEffect(PotionEffectType.WITHER, 300, 2, false, false), true);
		}
		else if (event.getEntity().getCustomName().contains("Inferno Dragon")) {
			cloud.setParticle(Particle.FLAME);
		}
		else if (name.contains("Glacial Dragon")) {
			cloud.setParticle(Particle.SPELL_INSTANT);
			cloud.addCustomEffect(new PotionEffect(PotionEffectType.SLOW, 300, 3, false, false), true);
		}
		else if (name.contains("Serpent Dragon")) {
			cloud.setParticle(Particle.TOTEM);
			cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 300, 2, false, false), true);
		}
 	}
	
	@EventHandler
	public void onDamage (EntityDamageByEntityEvent event) { //Admin does more damage to dragons

		Entity entity = event.getEntity();
		if (event.getDamager() instanceof Player) {
			Player player = (Player) event.getDamager();
			if (player.getGameMode() == GameMode.CREATIVE) {
				if (entity instanceof EnderDragon) {
					event.setDamage(event.getDamage() * 10);
				}
			}
		}
		
		if (event.getDamager() instanceof AreaEffectCloud) {			
			AreaEffectCloud cloud = (AreaEffectCloud) event.getDamager();
			Particle particle = cloud.getParticle();
			if (particle == Particle.SPELL_WITCH) { //Twilight dragon
				event.setDamage(event.getDamage()*4);
			}
			else if (particle == Particle.FLAME) { //Inferno Dragon
				entity.setFireTicks(300);
				event.setDamage(event.getDamage()*2);
			}
			else if (particle == Particle.SPELL_INSTANT) { //Glacial
				entity.setFreezeTicks(600);
				event.setDamage(event.getDamage()*2);
			}
			else if (particle == Particle.TOTEM) { //Serpent dragon
				event.setDamage(event.getDamage()*2);
			}
		}
	}
	
	private List<EnderDragon> getDragons() {
		World end = Bukkit.getWorld("world_the_end");
		List<LivingEntity> mobs = end.getLivingEntities();
		List<EnderDragon> eDrags = new ArrayList<>();
		for (LivingEntity mob : mobs) {
			if (mob instanceof EnderDragon) {
				eDrags.add((EnderDragon) mob);
			}
		}
		return eDrags;
	}
	
	private void spawnDragon() {
		World end = Bukkit.getServer().getWorld("world_the_end");
		Location spawnPoint = new Location(end, 0, 100, 0);
		Bukkit.getServer().getWorld("world_the_end").spawnEntity(spawnPoint, EntityType.ENDER_DRAGON);
		for (EnderDragon drag : getDragons()) {
			drag.setPhase(Phase.CHARGE_PLAYER);
		}
	}
	
	private void setSpawnableTrue(int ticks) {
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			
			@Override
			public void run() {				
				allowSpawn = true;
				saveConfig();
				for (Player player : Bukkit.getServer().getOnlinePlayers()) {
					player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 2f, 0.1f);
					player.sendMessage(ChatColor.DARK_PURPLE + "A dragon can be summoned from the void...");
				}
			}
		}, ticks);
	}
	
	public Date addHoursToJavaUtilDate(Date date, int hours) {
	    Calendar calendar = Calendar.getInstance();
	    calendar.setTime(date);
	    calendar.add(Calendar.HOUR_OF_DAY, hours);
	    return calendar.getTime();
	}
}
