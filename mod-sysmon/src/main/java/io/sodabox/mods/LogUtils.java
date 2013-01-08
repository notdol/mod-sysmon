package io.sodabox.mods;

import org.vertx.java.core.logging.Logger;

public class LogUtils {

	public static void DEBUG(Logger log, String message, Object... args ){
		if(log != null) log.debug("[MOD::SUBSCRIBE] "+String.format(message, args));
	}
	public static void ERROR(Logger log, String message, Object... args ){
		if(log != null) log.error("[MOD::SUBSCRIBE] "+String.format(message, args));
	}

	public static void INFO(Logger log, String message, Object... args){
		if(log != null) log.info("[MOD::INFO] "+String.format(message,args));
	}
}
