package hu.montlikadani.AutoMessager.bungee;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

import hu.montlikadani.AutoMessager.Global;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class AutoMessager extends Plugin {

	private List<UUID> msgEnable = new ArrayList<>();
	private List<String> texts = new ArrayList<>();

	private static AutoMessager instance;

	public Configuration config;
	private Announce announce;
	private File file;
	private boolean isYaml = false;

	private int cver = 4;

	@Override
	public void onEnable() {
		instance = this;
		createFile();

		loadFile();
		loadMessages();
		registerCommand();

		announce = new Announce(this);
		announce.load();
		announce.schedule();
	}

	@Override
	public void onDisable() {
		announce.cancelTask();
		getProxy().getPluginManager().unregisterCommands(this);
		announce = null;
	}

	private void createFile() {
		try {
			File folder = getDataFolder();
			if (!folder.exists()) {
				folder.mkdir();
			}

			File file = new File(folder, "bungeeconfig.yml");
			if (!file.exists()) {
				try (InputStream in = getResourceAsStream("bungeeconfig.yml")) {
					Files.copy(in, file.toPath());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

			if (!config.get("config-version").equals(cver)) {
				getLogger().log(Level.WARNING, "Found outdated configuration (bungeeconfig.yml)! (Your version: "
						+ config.getInt("config-version") + " | Newest version: " + cver + ")");
			}
		} catch (Exception e) {
			e.printStackTrace();
			getLogger().log(Level.WARNING,
					"There was an error. Please report it here:\nhttps://github.com/montlikadani/AutoMessager/issues");
		}
	}

	void loadFile() {
		String fName = config.getString("message-file", "");
		if (fName.isEmpty()) {
			getLogger().log(Level.WARNING, "The message-file string is empty or not found. Defaulting to messages.txt");
			fName = "messages.txt";
		}

		if (fName.equals("messages.yml")) {
			getLogger().log(Level.WARNING,
					"The message file cannot be an existing message file! Defaulting to messages.txt");
			fName = "messages.txt";
		}

		file = new File(getDataFolder(), fName);
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	void loadMessages() {
		texts.clear();

		if (file == null) {
			loadFile();
		}

		if (file.getName().endsWith(".yml")) {
			ConfigurationProvider msgC = ConfigurationProvider.getProvider(YamlConfiguration.class);
			Configuration c = null;
			try {
				c = msgC.load(file);
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			if (c == null) {
				getLogger().log(Level.SEVERE, "Error has occured while loading the file for you!");
				return;
			}

			if (!c.contains("messages")) {
				c.set("messages", Arrays.asList("&aYes, this is an&b Auto&6Message&a.",
						"&cThis plugin is now in BungeeCord software."));
			}

			try {
				msgC.save(c, file);
			} catch (IOException e) {
				e.printStackTrace();
			}

			isYaml = true;

			c.getStringList("messages").forEach(texts::add);
		} else {
			isYaml = false;

			try (BufferedReader read = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = read.readLine()) != null) {
					if (line.startsWith("#")) {
						continue;
					}

					texts.add(line);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void registerCommand() {
		getProxy().getPluginManager().registerCommand(this, new Command("automessager") {
			@Override
			public void execute(CommandSender s, String[] args) {
				if (args.length == 0) {
					if (!s.hasPermission("automessager.help")) {
						sendMessage(s, "messages.no-permission");
						return;
					}

					config.getStringList("messages.chat-messages")
							.forEach(msg -> s.sendMessage(new ComponentBuilder(colorMsg(msg)).create()));
					return;
				}

				if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("rl")) {
					if (!s.hasPermission("automessager.reload")) {
						sendMessage(s, "messages.no-permission");
						return;
					}

					if (announce != null) {
						announce.cancelTask();
					} else {
						announce = new Announce(instance);
					}

					announce.load();
					announce.schedule();

					createFile();
					sendMessage(s, "messages.reload-config");
				} else if (args[0].equalsIgnoreCase("toggle")) {
					if (!s.hasPermission("automessager.toggle")) {
						sendMessage(s, "messages.no-permission");
						return;
					}

					if (getProxy().getPlayers().isEmpty()) {
						sendMessage(s, "messages.toggle.no-player");
						return;
					}

					for (ProxiedPlayer pl : getProxy().getPlayers()) {
						if (!msgEnable.contains(pl.getUniqueId())) {
							msgEnable.add(pl.getUniqueId());
							sendMessage(s, "messages.toggle.disabled");
						} else {
							msgEnable.remove(pl.getUniqueId());
							sendMessage(s, "messages.toggle.enabled");
						}
					}
				} else if (args[0].equalsIgnoreCase("broadcast") || args[0].equalsIgnoreCase("bc")) {
					if (!s.hasPermission("automessager.broadcast")) {
						sendMessage(s, "messages.no-permission");
						return;
					}

					if (args.length < 2) {
						sendMessage(s, "messages.broadcast-usage");
						return;
					}

					StringBuilder builder = new StringBuilder();
					for (int i = 1; i < args.length; i++) {
						builder.append(args[i] + " ");
					}

					String msg = builder.toString();
					msg = colorMsg(msg);
					msg = Global.setSymbols(msg);
					getProxy().broadcast(new ComponentBuilder(
							colorMsg(config.getString("messages.broadcast-message").replace("%message%", msg)))
									.create());
				} else if (args[0].equalsIgnoreCase("list")) {
					if (!s.hasPermission("automessager.list")) {
						sendMessage(s, "messages.no-permission");
						return;
					}

					if (texts.size() < 1) {
						sendMessage(s, "messages.no-message-to-list");
						return;
					}

					for (String m : texts) {
						if (m.isEmpty()) {
							continue;
						}

						s.sendMessage(new ComponentBuilder(m).create());
					}
				}
				// TODO: implement remove/add command to modify messages
			}
		});
	}

	@SuppressWarnings("deprecation")
	public String replaceVariables(String str, ProxiedPlayer p) {
		int online = p.getServer().getInfo().getPlayers().size();

		Runtime r = Runtime.getRuntime();
		Long fram = Long.valueOf(r.freeMemory() / 1048576L);
		Long mram = Long.valueOf(r.maxMemory() / 1048576L);
		Long uram = Long.valueOf((r.totalMemory() - r.freeMemory()) / 1048576L);

		String t = null;
		String dt = null;
		if (str.contains("%server-time%") || str.contains("%date%")) {
			String tPath = "placeholder-format.time.";
			DateTimeFormatter form = !config.getString(tPath + "time-format.format", "").isEmpty()
					? DateTimeFormatter.ofPattern(config.getString(tPath + "time-format.format"))
					: null;

			DateTimeFormatter form2 = !config.getString(tPath + "date-format.format", "").isEmpty()
					? DateTimeFormatter.ofPattern(config.getString(tPath + "date-format.format"))
					: null;

			TimeZone zone = config.getBoolean(tPath + "use-system-zone", false)
					? TimeZone.getTimeZone(java.time.ZoneId.systemDefault())
					: TimeZone.getTimeZone(config.getString(tPath + "time-zone", "GMT0"));
			LocalDateTime now = zone == null ? LocalDateTime.now() : LocalDateTime.now(zone.toZoneId());

			Calendar cal = Calendar.getInstance();

			t = form != null ? now.format(form) : cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE);
			dt = form2 != null ? now.format(form2) : cal.get(Calendar.YEAR) + "/" + cal.get(Calendar.DATE);
		}

		str = Global.setSymbols(str);
		if (t != null)
			str = str.replace("%time%", t);

		if (dt != null)
			str = str.replace("%date%", dt);

		if (str.contains("%server%"))
			str = str.replace("%server%", p.getServer().getInfo().getName());

		if (str.contains("%server-online%"))
			str = str.replace("%server-online%", Integer.toString(online));

		if (str.contains("%max-players%"))
			str = str.replace("%max-players%", Integer
					.toString(getProxy().getConfigurationAdapter().getListeners().iterator().next().getMaxPlayers()));

		if (str.contains("%ip%"))
			str = str.replace("%ip%", p.getAddress().getAddress().getHostAddress());

		if (str.contains("%player-language%"))
			str = str.replace("%player-language%", String.valueOf(p.getLocale()));

		if (str.contains("%player-name%"))
			str = str.replace("%player-name%", p.getName());

		if (str.contains("%display-name%"))
			str = str.replace("%display-name%", p.getDisplayName());

		if (str.contains("%ping%"))
			str = str.replace("%ping%", Integer.toString(p.getPing()));

		if (str.contains("%ram-used%"))
			str = str.replace("%ram-used%", Long.toString(uram.longValue()));

		if (str.contains("%ram-max%"))
			str = str.replace("%ram-max%", Long.toString(mram.longValue()));

		if (str.contains("%ram-free%"))
			str = str.replace("%ram-free%", Long.toString(fram.longValue()));

		if (str.contains("%player-uuid%"))
			str = str.replace("%player-uuid%", p.getUniqueId().toString());

		if (str.contains("%game-version%"))
			str = str.replace("%game-version%", getProxy().getGameVersion());

		if (str.contains("%bungee-online%"))
			str = str.replace("%bungee-online%", Integer.toString(getProxy().getOnlineCount()));

		if (str.contains("%bungee-motd%"))
			str = str.replace("%bungee-motd%", p.getServer().getInfo().getMotd());

		if (str.contains("%player-country%"))
			str = str.replace("%player-country%", p.getLocale() == null ? "unknown" : p.getLocale().getCountry());

		str = str.replace("\\n", "\n");
		return colorMsg(str);
	}

	public boolean checkOnlinePlayers() {
		int min = config.getInt("min-players", 1);
		if (min < 1) {
			return false;
		}

		int online = getProxy().getPlayers().size();
		return online >= min;
	}

	// TODO: Complete this
	public void removeText(int index) {
		texts.remove(index);
	}

	private void sendMessage(CommandSender s, String path) {
		if (!config.getString(path, "").isEmpty()) {
			s.sendMessage(new ComponentBuilder(colorMsg(config.getString(path))).create());
		}
	}

	public String colorMsg(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}

	public boolean isYaml() {
		return isYaml;
	}

	public List<String> getTexts() {
		return texts;
	}

	public List<UUID> getEnabledMessages() {
		return msgEnable;
	}

	public File getMsgFile() {
		return file;
	}

	public AutoMessager getInstance() {
		return instance;
	}
}