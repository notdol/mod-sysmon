package io.sodabox.mods;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

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

		address = getOptionalStringConfig("address", "sodabox.sysmon");
		host = getOptionalStringConfig("host", "www.notdol.com");
		port = getOptionalIntConfig("port", 6379);
		channel = getOptionalStringConfig("channel", "SYSMON");
		sockServer = new SocketServer(vertx, host,port);

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
			case SL.ACTION_SUBSCRIBE:
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
				String serverId = getMessage.getString(SL.SERVER_ID);
				
				Jedis jedis = jedisPool.getResource();

				if(jedis.hexists(serverId, SL.SERVER_ID) == false){
					System.out.println("=================== add server "+serverId);
					jedis.hset(serverId, SL.SERVER_ID, serverId );
					jedis.lpush(SL.SERVER_LIST , serverId);
				}
				jedis.hset(serverId, "time", String.valueOf(Calendar.getInstance().getTimeInMillis()));

				JsonObject svrStat = getMessage.getObject(SL.SERVER_STAT);
				Iterator<String> svrStatKeys = svrStat.getFieldNames().iterator();
				
				String statKey = null;
				while(svrStatKeys.hasNext()){
					statKey = svrStatKeys.next();
					jedis.hset(serverId, statKey, svrStat.getString(statKey)) ;
				}
				
				System.out.println("==========================");
				System.out.println(jedis.hgetAll(serverId));
				/*
				for(int i=0;i < jedis.llen(SL.SERVER_ID); i++ ){
					eServerId = jedis.lindex(SL.SERVER_ID, i);
					if(serverId.equalsIgnoreCase(eServerId)){
						isExistServer = true; break;
					}
				}
				if(isExistServer == false) {
					jedis.lpush(SL.SERVER_ID, serverId);
				}
				*/
				jedisPool.returnResource(jedis);

				//sockServer.publishMessage(message.body.getString("messageTxt"));
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

		JsonObject stat;
		JsonObject stats = new JsonObject()
		.putString("type", "stat")
		.putString("time", String.valueOf(Calendar.getInstance().getTimeInMillis()) )
		.putString("uptime", String.valueOf(uptime) )
		.putObject(SL.SERVER_STAT, 
				stat = new JsonObject()
			.putString("heapUsage", String.valueOf(usedMem) )
			.putString("heapPercentageUsage", String.valueOf(heapPercentageUsage) )
				);

		InetAddress Address;
		try {
			Address = InetAddress.getLocalHost();
			stats.putString(SL.SERVER_ID,Address.getHostName());
			stat.putString("hostName", Address.getHostName());
			stat.putString("hostAddress", Address.getHostAddress());
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Jedis jedis = jedisPool.getResource();
		jedis.publish(channel, stats.encode());
		jedisPool.returnResource(jedis);

	}

}

