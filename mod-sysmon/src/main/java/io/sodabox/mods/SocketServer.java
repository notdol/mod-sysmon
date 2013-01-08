package io.sodabox.mods;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;

public class SocketServer {
	final List<SockJSSocket> sockList = new ArrayList<>();
	public SocketServer(Vertx vertx){
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
					public void handle(Buffer buffer){
						if(!sock.writeQueueFull()){
							sock.writeBuffer(buffer);
						}else {
							sock.pause();
							sock.drainHandler(new SimpleHandler() {
								
								@Override
								protected void handle() {
									// TODO Auto-generated method stub
									sock.resume();
								}
							});
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
