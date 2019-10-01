package hu.montlikadani.AutoMessager.bungee;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

public class Announce {

	private final AutoMessager plugin;

	private ScheduledTask task = null;

	private boolean isRandom;

	private int messageCounter;
	private int lastMessage;
	private int lastRandom;
	private int warningCounter;

	public Announce(AutoMessager plugin) {
		this.plugin = plugin;
	}

	public void load() {
		// We need to start from -1, due to first line reading
		messageCounter = -1;
		warningCounter = 0;
		isRandom = false;

		int cm = plugin.getMessages().size();
		if (plugin.config.getBoolean("random") && cm > 2) {
			isRandom = true;
		}

		lastMessage = cm;
	}

	public void schedule(final ProxiedPlayer p) {
		if (task == null) {
			task = plugin.getProxy().getScheduler().schedule(plugin, () -> {
				if (warningCounter <= 4) {
					if (plugin.getEnabledMessages() != null && (plugin.getEnabledMessages().contains(p.getUniqueId()))) {
						return;
					}

					if (plugin.getMessages().size() < 1) {
						plugin.getLogger().log(Level.WARNING,
								"There is no message in '" + plugin.config.getString("message-file") + "' file!");

						warningCounter++;

						if (warningCounter == 5) {
							plugin.getLogger().log(Level.WARNING,
									"Will stop outputing warnings now. Please write a message to the '"
											+ plugin.config.getString("message-file") + "' file.");
						}
					} else {
						if (isRandom) {
							onRandom(p);
						} else {
							onInOrder(p);
						}
					}
				}
			}, 0L, plugin.config.getInt("time", 1 * 1000), TimeUnit.valueOf(plugin.config.getString("time-setup").toUpperCase()));
		}
	}

	public void cancelTask() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	public ScheduledTask getTask() {
		return task;
	}

	private void onRandom(ProxiedPlayer p) {
		int nm = getNextMessage();
		String message = plugin.getMessages().get(nm < 0 ? 0 : nm);
		lastRandom = nm;
		send(p, message);
	}

	private void onInOrder(ProxiedPlayer p) {
		int nm = getNextMessage();
		String message = plugin.getMessages().get(nm < 0 ? 0 : nm);
		send(p, message);
	}

	int getNextMessage() {
		if (isRandom) {
			int r = plugin.getRandomInt(lastMessage - 1);
			while (r == lastRandom) {
				r = plugin.getRandomInt(lastMessage - 1);
			}

			return r;
		}

		int nm = (messageCounter + 1);
		if (nm == lastMessage) {
			messageCounter = 0;
			return 0;
		}

		messageCounter++;
		return nm;
	}

	private void send(ProxiedPlayer p, String message) {
		String msg = message;

		String path = "placeholder-format.time.";
		if (!plugin.config.getString(path + "title", "").equals("")) {
			msg = msg.replace("%title%", plugin.config.getString(path + "title").replace("%newline%", "\n"));
		}

		if (!plugin.config.getString(path + "suffix", "").equals("")) {
			msg = msg.replace("%suffix%", plugin.config.getString(path + "suffix"));
		}

		msg = plugin.replaceVariables(msg, p);

		if (!plugin.config.getStringList("disabled-servers").contains(p.getServer().getInfo().getName())) {
			p.sendMessage(new ComponentBuilder(msg).create());
		}

		if (plugin.config.getBoolean("broadcast-to-console")) {
			plugin.getProxy().getConsole().sendMessage(new ComponentBuilder(msg).create());
		}
	}
}