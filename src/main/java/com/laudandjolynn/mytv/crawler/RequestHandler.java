/*******************************************************************************
 * Copyright 2015 htd0324@gmail.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.laudandjolynn.mytv.crawler;

import java.util.Date;
import java.util.List;

import org.eclipse.jetty.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.laudandjolynn.mytv.exception.MyTvException;
import com.laudandjolynn.mytv.model.CrawlerTask;
import com.laudandjolynn.mytv.model.ProgramTable;
import com.laudandjolynn.mytv.model.TvStation;
import com.laudandjolynn.mytv.service.TvService;
import com.laudandjolynn.mytv.service.TvServiceImpl;
import com.laudandjolynn.mytv.utils.DateUtils;

/**
 * @author: Laud
 * @email: htd0324@gmail.com
 * @date: 2015年3月27日 下午11:42:27
 * @copyright: www.laudandjolynn.com
 */
public class RequestHandler {
	private final static Logger logger = LoggerFactory
			.getLogger(RequestHandler.class);
	private final ConcurrentHashSet<CrawlerTask> CURRENT_EPG_TASK = new ConcurrentHashSet<CrawlerTask>();
	private TvService tvService = new TvServiceImpl();

	private RequestHandler() {
	}

	public static RequestHandler getIntance() {
		return EpgTaskManagerSingletonHolder.MANAGER;
	}

	private final static class EpgTaskManagerSingletonHolder {
		private final static RequestHandler MANAGER = new RequestHandler();
	}

	/**
	 * <pre>
	 * 所有请求入口
	 * 查询指定日期、电视台的节目表
	 * </pre>
	 * 
	 * @param stationOrDisplayName
	 *            电视台显示名
	 * @param classify
	 *            电视台分类，可以为null。为空时，将查找stationName与displayName相同的电视台
	 * @param date
	 *            日期，yyyy-MM-dd
	 * @return
	 */
	public List<ProgramTable> queryProgramTable(String stationOrDisplayName,
			String classify, String date) {
		TvStation tvStation = tvService.getStation(stationOrDisplayName);
		if (tvStation == null) {
			tvStation = tvService.getStationByDisplayName(stationOrDisplayName,
					classify);
		}
		if (tvStation == null) {
			logger.error(stationOrDisplayName + " isn't exists.");
			return null;
		}
		return queryProgramTable(tvStation, date);
	}

	/**
	 * 查询指定日期、电视台的电视节目表
	 * 
	 * @param tvStation
	 *            电视台对象
	 * @param date
	 *            日期，yyyy-MM-dd
	 * @return
	 */
	private List<ProgramTable> queryProgramTable(TvStation tvStation,
			final String date) {
		String[] weeks = DateUtils.getWeek(new Date(), "yyyy-MM-dd");
		// 只能查询一周内的节目表
		if (date.compareTo(weeks[0]) < 0 || date.compareTo(weeks[6]) > 0) {
			return null;
		}

		final String stationName = tvStation.getName();
		logger.info("query program table of " + stationName + " at " + date);
		if (tvService.isProgramTableExists(stationName, date)) {
			return tvService.getProgramTable(stationName, date);
		}
		CrawlerTask crawlerTask = new CrawlerTask(stationName, date);
		if (CURRENT_EPG_TASK.contains(crawlerTask)) {
			synchronized (this) {
				try {
					logger.debug(crawlerTask
							+ " is wait for the other same task's notification.");
					wait();
				} catch (InterruptedException e) {
					throw new MyTvException(
							"thread interrupted while query program table of "
									+ stationName + " at " + date, e);
				}

				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// do nothing
				}

				logger.debug(crawlerTask
						+ " has receive notification and try to get program table from db.");
				return tvService.getProgramTable(stationName, date);
			}
		}

		logger.debug(crawlerTask
				+ " is try to query program table from network.");
		CURRENT_EPG_TASK.add(crawlerTask);
		try {
			return tvService.crawlProgramTable(stationName, date);
		} catch (Exception e) {
			logger.error("crawl program table of " + stationName + " at "
					+ date + " is fail.", e);
			throw new MyTvException(e);
		} finally {
			synchronized (this) {
				CURRENT_EPG_TASK.remove(crawlerTask);
				logger.debug(crawlerTask
						+ " have finished to get program table data and send notification.");
				notifyAll();
			}
		}
	}

}
