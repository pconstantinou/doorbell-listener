package doorman.listener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Properties;

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

	/**
	 * Pusher Event Handler invoked when an event is triggered on the channel
	 */
	public void onEvent(String channelName, String eventName, final String data) {

		reloadPasswordFile();
		String passcode = getPasscodeFromData(data);
		String user = passwords.getProperty(passcode);
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
					Runtime.getRuntime().exec(command);
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
