package doorman.listener;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.io.File;

import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.SubscriptionEventListener;

public class Doorman {

	public static void main(String[] args) throws Exception {
		Properties props = new Properties();
		File propFile = new File(args[0]);
		System.out.println("Using " + propFile);
		if (!propFile.exists()) {
		    System.out.println("Properties don't exist");
		} else {
		    props.load(new FileInputStream(propFile));
		    props.list(System.out);
		}
	    
		String appId = props.getProperty("app_id");
		if (appId == null) {
			System.err.println("No secret provided");
		} else {
			System.out.println("Using App Id: " + appId);
		}
		String key = props.getProperty("key");
		String secret = props.getProperty("secret");
		if (secret == null) {
			System.err.println("No secret provided");
		}
		String cluster = props.getProperty("cluster", "us2");
		String channelName = props.getProperty("channel", "my-channel");
		String eventName = props.getProperty("event", "my-event");
		final String command = props.getProperty("command");

		PusherOptions options = new PusherOptions();
		options.setCluster(cluster);
		Pusher pusher = new Pusher(key, options);
		Channel channel = pusher.subscribe(channelName);

		channel.bind(eventName, new SubscriptionEventListener() {
			public void onEvent(String channelName, String eventName, final String data) {
				System.out.println(data);
				try {
					System.out.println("Executing:" + command);
					Runtime.getRuntime().exec(command);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		pusher.connect();

		while (true) {
			Thread.sleep(1000);
			System.out.println("sleeping");

		}
	}
}
