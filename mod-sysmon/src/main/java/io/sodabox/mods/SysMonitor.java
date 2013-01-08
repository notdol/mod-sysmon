package io.sodabox.mods;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class SysMonitor extends BusModBase implements Handler<Message<JsonObject>> {

	private Logger log;

	private String address;

	private String host;
	private int port;
	private String channel;

	private long timerId = -1;

	private Thread subThread;

	private final int DEFAULT_INTERVAL = 2000;
	RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
	MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

	private SocketServer sockServer;
	
	private JedisPool jedisPool;

	public void start() {
		super.start();
		sockServer = new SocketServer(vertx);

		address = getOptionalStringConfig("address", "sodabox.sysmon");
		host = getOptionalStringConfig("host", "www.notdol.com");
		port = getOptionalIntConfig("port", 6379);
		channel = getOptionalStringConfig("channel", "SYSMON");

		new SubscribeThread(log, eb, address, host, port, channel);


		subThread = new Thread(
				new SubscribeThread(log, eb, address, host, port, channel)
				);

		subThread.start();

		JedisPoolConfig config = new JedisPoolConfig();
		config.testOnBorrow = true;


		if( host == null || host.length() == 0 ){
			jedisPool = new JedisPool(config, "localhost");
		}else{
			jedisPool = new JedisPool(config, host, port);
		}

		eb.registerHandler(address, this);
			
	}

	public void stop() {
		//subThread.stop();
	}

	public void handle(Message<JsonObject> message) {

		String action = message.body.getString("action");

		if (action == null) {
			sendError(message, "action must be specified");
			return;
		}
		sendError(message,action+" is sent!!");

		switch (action) {
		case "subscribe":
			timerId = vertx.setPeriodic(DEFAULT_INTERVAL, new Handler<Long>() {
				public void handle(Long event) {
					sendStats();
				}
			});
			break;
		case "stop":
			vertx.cancelTimer(timerId);
			break;
			
		case "message" : 
			String messageType = message.body.getString("messageType");
			String messageTxt = message.body.getString("messageTxt");
			
			System.out.println("vertx interval");
			sockServer.publishMessage(messageTxt);
			break;
			
		default:
			sendError(message, "Invalid action: " + action);
			return;
		}
	}

	private void sendStats() {

		MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
		long maxMem = usage.getMax();
		long usedMem = usage.getUsed();
		float heapPercentageUsage = 100 * ((float) usedMem/maxMem);
		long uptime = runtimeMXBean.getUptime();

		JsonObject stats = new JsonObject()
		.putNumber("uptime", uptime)
		.putNumber("heapUsage", usedMem)
		.putNumber("heapPercentageUsage", heapPercentageUsage);

		Jedis jedis = jedisPool.getResource();
		jedis.publish(channel, stats.encode());
		jedisPool.returnResource(jedis);

	}

}

