package io.sodabox.mods;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class SocketServer {
	final List<SockJSSocket> sockList = new ArrayList<>();
	private Map<SockJSSocket, JsonObject> socketList;
	private Vertx vertx;
	private long timerId = -1;
	private JedisPool jedisP;
	private final int DEFAULT_INTERVAL = 2000;

	
	public SocketServer(final Vertx vertx,String host,int port){
		//this.eb = eb;
		this.vertx = vertx;
		
		socketList = new HashMap<SockJSSocket, JsonObject>();
		JedisPoolConfig config = new JedisPoolConfig();
		config.testOnBorrow = true;
		
		if( host == null || host.length() == 0 ){
			jedisP = new JedisPool(config, "localhost");
		}else{
			jedisP = new JedisPool(config, host, port);
		}
		System.out.println("Socket Server started " );
		//Vertx vertx = Vertx.newVertx();
		HttpServer server = vertx.createHttpServer();
		
	    server.requestHandler(new Handler<HttpServerRequest>() {
	    	public void handle(HttpServerRequest req) {
	    		if (req.path.equals("/")) req.response.sendFile("/index.html"); // Serve the html
    		}    
    	});
	    	
		SockJSServer sockJSServer = vertx.createSockJSServer(server);
		JsonObject sockConfig = new JsonObject().putString("prefix", SL.CONNECTION_PREFIX);
		
		sockJSServer.installApp(sockConfig, new Handler<SockJSSocket>() {
			
			@Override
			public void handle(final SockJSSocket sock) {
				// TODO Auto-generated method stub
				System.out.println("SOCKET OPENED");
				// socket 
				JsonObject initData = new JsonObject();
				initData.putNumber(SL.SOCKET_CN_TIME, Calendar.getInstance().getTimeInMillis());
				initData.putArray("events",new JsonArray());
				
				socketList.put(sock, initData);
				
				sock.dataHandler(new Handler<Buffer>() {
					
					@Override
					public void handle(Buffer buffer) {
						// TODO Auto-generated method stub
						
						JsonObject sentMe = new JsonObject(buffer.toString());
						System.out.println(" Message Received=====================");
						System.out.println(sentMe);
						String action = sentMe.getString("action");
						Jedis jedis;
						switch(action){
							case SL.SOCKET_AC_SEND_STAT:
								Boolean isEventExist = false;
								
								Iterator<Object> events = socketList.get(sock).getArray("events").iterator();
								while(events.hasNext()){
									if( SL.SOCKET_AC_SEND_STAT.equals( events.next() ) ) {
										isEventExist = true; break;
									}
								}
								
								if(isEventExist == false){
									JsonArray serverList = sentMe.getArray(SL.SERVER_LIST);
									JsonArray paramList = sentMe.getArray(SL.SERVER_PARAM_LIST);
									
									socketList.get(sock).putArray(SL.SERVER_LIST, serverList);
									socketList.get(sock).putArray(SL.SERVER_PARAM_LIST, paramList);
									
									timerId = vertx.setPeriodic(DEFAULT_INTERVAL, new Handler<Long>() {
										public void handle(Long event) {
											sendStats(sock);
										}
									});
									socketList.get(sock).getArray("events").addString(SL.SOCKET_AC_SEND_STAT);
									
								}else {
									
								}
								
							break;
				
							case "svrList":
								jedis = jedisP.getResource();
								System.out.println(jedis.llen(SL.SERVER_ID));
								List<String> servers = jedis.lrange(SL.SERVER_ID, 0, jedis.llen(SL.SERVER_ID));
								
								JsonObject serverObj = new JsonObject();
								JsonArray serverArr = new JsonArray();
								
								Iterator<String> serverIter = servers.iterator();
								
								while(serverIter.hasNext()){
									serverArr.addString(serverIter.next());
								}
								serverObj.putArray(SL.SERVER_ID, serverArr);
								publishMessage(serverObj.encode());
								jedisP.returnResource(jedis);
							break;
						
							 default:
							
							break;
						
						}
						
						
					}
				});
				
				sock.endHandler(new Handler<Void>() {
					public void handle(Void v){
						System.out.println("SOCKET CLOSED");
						socketList.remove(sock);
						
					}
				});
				
			}
		});
		server.listen(9090);
		
	}
	
	public void sendStats(SockJSSocket sock){
		System.out.println("============================== sendStat");
		Jedis jedis = jedisP.getResource();
		JsonObject sendData = new JsonObject();
		List<String> svrList = jedis.lrange(SL.SERVER_LIST, 0, jedis.llen(SL.SERVER_LIST));
		System.out.println(svrList);
		Iterator<String> svrListIt = svrList.iterator();
		String serverId;
		while(svrListIt.hasNext()){
			serverId = svrListIt.next();
			Map<String, String> map = jedis.hgetAll(serverId);
			Iterator<String> it = map.keySet().iterator();
			JsonObject params = new JsonObject();
			while(it.hasNext()){
				String value = it.next();
				params.putString(value , map.get(value));
			}
			sendData.putObject(serverId, params);
		}
		
		jedisP.returnResource(jedis);
		publishMessage(sendData.encode());
	}
	
	public void publishMessage(String str){
		if(str == null) return;
		Iterator<SockJSSocket> sockIter = socketList.keySet().iterator();
		SockJSSocket sock;
		Buffer bf=new Buffer(str);
		System.out.println(str);
		while(sockIter.hasNext()){
			sock = sockIter.next();
			sock.writeBuffer(bf);
		}
		
	}
}
