package doorman.listener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.SubscriptionEventListener;

/**
 * Doorman is an executable that listens for messages from the pusher service
 * and if the passcode matches one of the provided passwords, it will execute a
 * system command that that should open the door.
 * 
 * The password properties file is of the format <code>passcode = name</code>.
 * The pusher file can should follow the format in doorman.properties.default
 * 
 * @author phil
 *
 */
public class Doorman implements SubscriptionEventListener {

	final private Properties passwords = new Properties();
	final private File passwordsFile;
	final private PrintWriter accessLog;
	private String command;
	private Pusher pusher;
	private boolean handlingEvent = false;
	private Map<String, FailureScore> failureScoreboard = new HashMap<String, FailureScore>();
	final static long EXPIRE_FAILURE_AGE = TimeUnit.HOURS.toMillis(8);
	final static long BLOCK_FAILURE_AGE = TimeUnit.HOURS.toMillis(1);
	final static long BLOCK_AFTER_FAILURE_COUNT = 10;

	static class FailureScore {
		long count = 0;
		long leastRecentFailure = System.currentTimeMillis();
		long mostRecentFailure = System.currentTimeMillis();

		long getAge() {
			return (System.currentTimeMillis() - mostRecentFailure);
		}

		boolean isExpired() {
			return getAge() > EXPIRE_FAILURE_AGE && !isBlocked();
		}

		boolean isBlocked() {
			return (mostRecentFailure > System.currentTimeMillis() - BLOCK_FAILURE_AGE)
					&& count > BLOCK_AFTER_FAILURE_COUNT;
		}

		void incrementFailure() {
			mostRecentFailure = System.currentTimeMillis();
			count = Math.min(count + 1, Integer.MAX_VALUE);
		}

		@Override
		public String toString() {
			return "FailureScore [count=" + count + ", leastRecentFailure=" + new Date(leastRecentFailure)
					+ ", mostRecentFailure=" + new Date(mostRecentFailure) + "]";
		}

	}

	public Doorman(File pusherPropertiesFile, File passwordsFile, File accessLogFile) throws IOException {

		accessLog = new PrintWriter(new FileWriter(accessLogFile));
		// Load Passwords
		this.passwordsFile = passwordsFile;
		if (!reloadPasswordFile()) {
			System.err.println("Bad password file: " + passwordsFile);
			System.exit(-1);
		}
		// Set up pusher
		Properties props = getPusherProperties(pusherPropertiesFile);
		pusher = getPusherFromProperties(props);
		String channelName = props.getProperty("channel", "my-channel");
		String eventName = props.getProperty("event", "my-event");
		Channel channel = pusher.subscribe(channelName);
		channel.bind(eventName, this);

		command = props.getProperty("command");
	}

	public void start() {
		pusher.connect();
	}

	/***
	 * 
	 * @param args
	 *            <pusher properties file> <password file> [<access log file>]
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println(
					"Usage: java Doorman <pusher properties file> <password properties file> [<access log file>]");
			System.exit(-1);
		}
		File accessLogFile;
		if (args.length >= 3) {
			accessLogFile = new File(args[2]);
		} else {
			accessLogFile = new File("access." + System.currentTimeMillis() + ".log");
		}
		Doorman doorman = new Doorman(new File(args[0]), new File(args[1]), accessLogFile);
		doorman.start();

		for (int i = 0; true; i++) {
			Thread.sleep(1000);
			System.out.print(".");
			if (i % 60 == 0 && i != 0) {
				System.out.println();
			}
		}
	}

	public boolean shouldBlockDueToTooManyFailures(boolean isValidLogin, String ipString) {
		FailureScore failureScore = failureScoreboard.get(ipString);
		System.out.println("Checking IP " + ipString);
		if (failureScore != null) {
		    System.out.println("Existing failure found for IP " + failureScore);
			if (!isValidLogin) {
				failureScore.incrementFailure();
				return true;
			} else if (failureScore.isExpired()) {
				failureScoreboard.remove(ipString);
			}
			return failureScore.isBlocked();
		} else if (failureScore == null && !isValidLogin) {
		        failureScore = new FailureScore();
			failureScore.incrementFailure();
			failureScoreboard.put(ipString, failureScore);
			System.out.println("Adding a new failure score for IP: " + ipString);
			return true;
		}
		// Don't block valid logs without a failure
		return false;
	}

	/**
	 * Pusher Event Handler invoked when an event is triggered on the channel
	 */
	public void onEvent(String channelName, String eventName, final String data) {
		reloadPasswordFile();
		String passcode = getPasscodeFromData(data);
		String ipString = getIPFromData(data);
		String user = passwords.getProperty(passcode);
		if (ipString != null) {
			if (shouldBlockDueToTooManyFailures(user != null, ipString)) {
				accessLog.printf("Blocked user=%s %s with %s\n", user, ipString,
						failureScoreboard.get(ipString).toString());
				accessLog.flush();
				return;
			}
		}

		synchronized (this) {
			if (handlingEvent) {
				return;
			}
			handlingEvent = true;
		}
		try {
			if (user != null) {
				try {
					accessLog.println(new Date() + ";" + "Success;" + user);
					System.out.println(new Date() + ": Executing:" + command + " for " + user);
					Process p = Runtime.getRuntime().exec(command);
					Thread.sleep(1000);
					p.waitFor();
					p.exitValue();
					Thread.sleep(1000);
				} catch (Throwable e) {
					e.printStackTrace();
				}
			} else {
				accessLog.println(new Date() + ";" + "Failed;" + passcode);
			}
			accessLog.flush();
		} finally {
			synchronized (this) {
				handlingEvent = false;
			}
			accessLog.flush();
		}
	}

	/**
	 * Parse JSON from Pusher
	 * 
	 * @param data
	 * @return the message attribute of the data
	 */
	public String getPasscodeFromData(final String data) {
		System.out.println("Data:" + data);
		JsonElement element = new JsonParser().parse(data);
		String message = element.getAsJsonObject().get("message").getAsString();
		return message;
	}

	public String getIPFromData(final String data) {
		try {
			JsonElement element = new JsonParser().parse(data);
			String ipString = element.getAsJsonObject().get("ip").getAsString();
			return ipString;
		} catch (Exception e) {
			return null;
		}
	}

	public boolean reloadPasswordFile() {
		try {
			passwords.load(new FileInputStream(passwordsFile));
		} catch (IOException e1) {
			System.err.println("Can't reload the password file:" + passwordsFile);
			return false;
		}
		return true;
	}

	protected Properties getPusherProperties(File propFile) throws IOException, FileNotFoundException {
		Properties props = new Properties();
		System.out.println("Using " + propFile);
		if (!propFile.exists()) {
			System.out.println("Properties " + propFile + "don't exist");
			System.exit(-1);
		} else {
			props.load(new FileInputStream(propFile));
			props.list(System.out);
		}
		return props;
	}

	public Pusher getPusherFromProperties(Properties pusherProperties) {
		String appId = pusherProperties.getProperty("app_id");
		if (appId == null) {
			System.err.println("No app id provided");
		} else {
			System.out.println("Using App Id: " + appId);
		}
		String key = pusherProperties.getProperty("key");
		if (key == null) {
			System.err.println("No pusher key provided");
			System.exit(-1);
		}
		String secret = pusherProperties.getProperty("secret");
		if (secret == null) {
			System.err.println("No secret provided");
		}
		String cluster = pusherProperties.getProperty("cluster", "us2");

		PusherOptions options = new PusherOptions();
		options.setCluster(cluster);
		Pusher pusher = new Pusher(key, options);
		return pusher;
	}
}
