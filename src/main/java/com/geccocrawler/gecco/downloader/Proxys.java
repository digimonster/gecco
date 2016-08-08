package com.geccocrawler.gecco.downloader;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

/**
 * 多代理支持，classpath根目录下放置proxys文件，文件格式如下
 * 127.0.0.1:8888
 * 127.0.0.1:8889
 * 支持记录代理成功率，自动发现无效代理
 * 支持在线添加代理
 * 
 * @author huchengyi
 *
 */
public class Proxys {
	
	private static Log log = LogFactory.getLog(Proxys.class);
	
	private static ConcurrentLinkedQueue<Proxy> proxyQueue;
	
	private static Map<String, Proxy> proxys = null;
	static{
		try {
			proxys = new ConcurrentHashMap<String, Proxy>();
			proxyQueue = new ConcurrentLinkedQueue<Proxy>();
			URL url = Resources.getResource("proxys");
			File file = new File(url.getPath());
			List<String> lines = Files.readLines(file, Charsets.UTF_8);
			if(lines.size() > 0) {
				for(String line : lines) {
					line = line.trim();
					if(line.startsWith("#")) {
						continue;
					}
					String[] hostPort = line.split(":");
					if(hostPort.length == 2) {
						String host = hostPort[0];
						int port = NumberUtils.toInt(hostPort[1], 80);
						addProxy(host, port);
					}
				}
			}
		} catch(Exception ex) {
			log.info("proxys not load");
		}
	}
	
	public static HttpHost getProxy() {
		if(proxys == null || proxys.size() == 0) {
			return null;
		}
		Proxy proxy = proxyQueue.poll();
		if(log.isDebugEnabled()) {
			log.debug("use proxy : " + proxy);
		}
		if(proxy == null) {
			return null;
		}
		return proxy.getHttpHost();
	}
	
	public static boolean addProxy(String host, int port) {
		Proxy proxy = new Proxy(host, port);
		if(proxys.containsKey(proxy.toHostString())) {
			return false;
		} else {
			proxys.put(proxy.toHostString(), proxy);
			proxyQueue.offer(proxy);
			if(log.isDebugEnabled()) {
				log.debug("add proxy : " + host + ":" + port);
			}
			return true;
		}
	}
	
	/**
	 * 代理失败
	 */
	public static void failure(String host) {
		Proxy proxy = proxys.get(host);
		if(proxy != null) {
			long failure = proxy.getFailureCount().incrementAndGet();
			long success = proxy.getSuccessCount().get();
			reProxy(proxy, success, failure);
		}
	}
	
	/**
	 * 代理成功
	 */
	public static void success(String host) {
		Proxy proxy = proxys.get(host);
		if(proxy != null) {
			long success = proxy.getSuccessCount().incrementAndGet();
			long failure = proxy.getFailureCount().get();
			reProxy(proxy, success, failure);
		}
	}
	
	private static void reProxy(Proxy proxy, long success, long failure) {
		long sum = failure + success;
		if(sum < 20) {
			proxyQueue.offer(proxy);
		} else {
			if((success / (float)sum) >= 0.5f) {
				proxyQueue.offer(proxy);
			}
		}
	}
	
    public static List<Map<String, Object>> export() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for(Proxy proxy : proxys.values()) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("ip", proxy.getHttpHost().getHostName());
            map.put("port", proxy.getHttpHost().getPort());
            map.put("failure", proxy.getFailureCount());
            map.put("success", proxy.getSuccessCount());
            list.add(map);
        }
        return list;
    }
}
