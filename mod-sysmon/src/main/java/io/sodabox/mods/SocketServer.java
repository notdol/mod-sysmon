package io.sodabox.mods;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class SocketServer {
	final List<SockJSSocket> sockList = new ArrayList<>();
	private JedisPool jedisP;
	
	public SocketServer(Vertx vertx,String host,int port){
		//this.eb = eb;
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
		SockJSServer sockJSServer = vertx.createSockJSServer(server);
		JsonObject sockConfig = new JsonObject().putString("prefix", "/echo");
		
		sockJSServer.installApp(sockConfig, new Handler<SockJSSocket>() {
			
			@Override
			public void handle(final SockJSSocket sock) {
				// TODO Auto-generated method stub
				System.out.println("SOCKET OPENED");
				
				
				sockList.add(sock);
				
				sock.dataHandler(new Handler<Buffer>() {
					
					@Override
					public void handle(Buffer buffer) {
						// TODO Auto-generated method stub
						
						JsonObject sentMe = new JsonObject(buffer.toString());
						
						String action = sentMe.getString("action");
						System.out.println(sentMe);
						switch(action){
						case "AS":
							Jedis jedis = jedisP.getResource();
							System.out.println(jedis.llen("serverId"));
							List<String> servers = jedis.lrange("serverId", 0, jedis.llen("serverId"));
							
							JsonObject serverObj = new JsonObject();
							JsonArray serverArr = new JsonArray();
							
							
							Iterator<String> serverIter = servers.iterator();
							
							while(serverIter.hasNext()){
								serverArr.addString(serverIter.next());
							}
							serverObj.putArray("serverList", serverArr);
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
						sockList.remove(sock);
					}
				});
				
			}
		});
		server.listen(9090);
		
	}
	public void publishMessage(String str){
		if(str == null) return;
		Iterator<SockJSSocket> sockIter = sockList.iterator();
		SockJSSocket sock;
		Buffer bf=new Buffer(str);
		System.out.println(str);
		while(sockIter.hasNext()){
			sock = sockIter.next();
			
			sock.writeBuffer(bf);
		}
		
	}
}
