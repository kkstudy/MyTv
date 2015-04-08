package com.laudandjolynn.mytv;

import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.laudandjolynn.mytv.exception.MyTvException;
import com.laudandjolynn.mytv.service.JolynnTv;
import com.laudandjolynn.mytv.service.JolynnTvImpl;
import com.laudandjolynn.mytv.service.JolynnTvRmiImpl;
import com.laudandjolynn.mytv.utils.Config;

/**
 * @author: Laud
 * @email: htd0324@gmail.com
 * @date: 2015年4月8日 上午10:00:03
 * @copyright: www.laudandjolynn.com
 */
public class RmiServer implements Server {
	private final static Logger logger = LoggerFactory
			.getLogger(RmiServer.class);
	private final static String url = "rmi://" + Config.NET_CONFIG.getIp()
			+ ":" + Config.NET_CONFIG.getRmiPort() + "/epg";

	@Override
	public void start() {
		logger.info("epg bind to: " + url);
		try {
			JolynnTv jolynnTv = new JolynnTvRmiImpl(new JolynnTvImpl());
			LocateRegistry.createRegistry(Config.NET_CONFIG.getRmiPort());
			Naming.bind(url, jolynnTv);
		} catch (RemoteException e) {
			throw new MyTvException("error occur while start RMI server.", e);
		} catch (MalformedURLException e) {
			throw new MyTvException("invalid url: " + url, e);
		} catch (AlreadyBoundException e) {
			throw new MyTvException(url + " is already bind.", e);
		}
		logger.info("RMI server started. " + url);
	}

}
