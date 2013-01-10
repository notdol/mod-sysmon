package io.sodabox.mods;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;

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
		sockServer = new SocketServer(vertx, host,port);

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
		System.out.println(address+"핸들러 등록");
		eb.registerHandler(address, this);
			
	}

	public void stop() {
		//subThread.stop();
	}

	public void handle(Message<JsonObject> message) {
		System.out.println(message);
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
				
				
				JsonObject getMessage = new JsonObject(message.body.getString("messageTxt"));
				String serverId = getMessage.getString("serverId");
				
				String eServerId ;
				Boolean isExistServer = false;
				Jedis jedis = jedisPool.getResource();
				
				for(int i=0;i < jedis.llen("serverId"); i++ ){
					eServerId = jedis.lindex("serverId", i);
					if(serverId.equalsIgnoreCase(eServerId)){
						isExistServer = true; break;
					}
				}
				if(isExistServer == false) {jedis.lpush("serverId", serverId);
					System.out.println(isExistServer+":"+serverId);
				}
				jedisPool.returnResource(jedis);

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
		.putString("type", "stat")
		.putNumber("time", Calendar.getInstance().getTimeInMillis())
		.putNumber("uptime", uptime)
		.putNumber("heapUsage", usedMem)
		.putNumber("heapPercentageUsage", heapPercentageUsage);

		InetAddress Address;
		try {
			Address = InetAddress.getLocalHost();
			stats.putString("serverId",Address.getHostName());
			stats.putString("hostName", Address.getHostName());
			stats.putString("hostAddress", Address.getHostAddress());
			System.out.println("로컬 컴퓨터의 이름 : "+Address.getHostName());

			System.out.println("로컬 컴퓨터의 IP 주소 : "+Address.getHostAddress());

		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		Jedis jedis = jedisPool.getResource();
		jedis.publish(channel, stats.encode());
		jedisPool.returnResource(jedis);

	}

}

