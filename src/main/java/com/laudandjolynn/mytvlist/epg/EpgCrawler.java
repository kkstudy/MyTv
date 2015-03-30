package com.laudandjolynn.mytvlist.epg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.laudandjolynn.mytvlist.Crawler;
import com.laudandjolynn.mytvlist.Init;
import com.laudandjolynn.mytvlist.exception.MyTvListException;
import com.laudandjolynn.mytvlist.model.ProgramTable;
import com.laudandjolynn.mytvlist.model.TvStation;
import com.laudandjolynn.mytvlist.utils.Constant;
import com.laudandjolynn.mytvlist.utils.DateUtils;
import com.laudandjolynn.mytvlist.utils.MyTvUtils;

/**
 * @author: Laud
 * @email: htd0324@gmail.com
 * @date: 2015年3月28日 上午12:00:44
 * @copyright: www.laudandjolynn.com
 */
public class EpgCrawler {
	private final static Logger logger = LoggerFactory
			.getLogger(EpgCrawler.class);

	/**
	 * 获取所有电视台
	 * 
	 * @param htmlPage
	 * @return
	 */
	public static List<TvStation> crawlAllTvStationByPage(HtmlPage htmlPage) {
		return EpgParser.parseTvStation(htmlPage.asXml());
	}

	/**
	 * 获取所有电视台
	 * 
	 * @return
	 */
	public static List<TvStation> crawlAllTvStation() {
		return EpgParser.parseTvStation(Crawler.crawlAsXml(Constant.EPG_URL));
	}

	private interface CrawProgramTableImpl {
		public List<ProgramTable> invoke(String stationName, String date);
	}

	/**
	 * 获取指定日期的所有电视台节目表
	 * 
	 * @param htmlPage
	 *            已获取的html页面对象
	 * @param date
	 *            日期，yyyy-MM-dd
	 * @return
	 */
	public static List<ProgramTable> crawlAllProgramTableByPage(
			final HtmlPage htmlPage, final String date) {
		Collection<TvStation> stations = Init.getIntance()
				.getAllCacheTvStation();
		CrawProgramTableImpl impl = new CrawProgramTableImpl() {

			@Override
			public List<ProgramTable> invoke(String stationName, String date) {
				return crawlProgramTableByPage(htmlPage, stationName, date);
			}
		};
		return crawlAllProgramTable(new ArrayList<TvStation>(stations), date,
				impl);
	}

	/**
	 * 获取指定日期的所有电视台节目表
	 * 
	 * @param date
	 *            日期，yyyy-MM-dd
	 * @return
	 */
	public static List<ProgramTable> crawlAllProgramTable(String date) {
		Collection<TvStation> stations = Init.getIntance()
				.getAllCacheTvStation();
		CrawProgramTableImpl impl = new CrawProgramTableImpl() {

			@Override
			public List<ProgramTable> invoke(String stationName, String date) {
				return crawlProgramTable(stationName, date);
			}
		};
		return crawlAllProgramTable(new ArrayList<TvStation>(stations), date,
				impl);
	}

	/**
	 * 抓取所有电视台指定日志的电视节目表，多线程
	 * 
	 * @param stations
	 * @param date
	 * @param impl
	 * @return
	 */
	private static List<ProgramTable> crawlAllProgramTable(
			List<TvStation> stations, final String date,
			final CrawProgramTableImpl impl) {
		List<ProgramTable> resultList = new ArrayList<ProgramTable>();
		int threadCount = EpgDao.getTvStationClassify().size();
		ExecutorService executorService = Executors
				.newFixedThreadPool(threadCount);
		CompletionService<List<ProgramTable>> completionService = new ExecutorCompletionService<List<ProgramTable>>(
				executorService);
		for (final TvStation station : stations) {
			Callable<List<ProgramTable>> task = new Callable<List<ProgramTable>>() {
				@Override
				public List<ProgramTable> call() throws Exception {
					return impl.invoke(station.getName(), date);
				}
			};
			completionService.submit(task);
		}
		int size = stations == null ? 0 : stations.size();
		int count = 0;
		while (count < size) {
			Future<List<ProgramTable>> future;
			try {
				future = completionService.poll(10, TimeUnit.MINUTES);
				resultList.addAll(future.get());
			} catch (InterruptedException e) {
				logger.error("craw program table of all station at " + date
						+ " was interrupted.", e);
			} catch (ExecutionException e) {
				logger.error(
						"error occur while craw program table of all station at "
								+ date, e);
			}
			count++;
		}
		executorService.shutdown();

		return resultList;
	}

	/**
	 * 根据电视台、日期获取电视节目表
	 * 
	 * @param stationName
	 *            电视台名称
	 * @param date
	 *            日期，yyyy-MM-dd
	 * @return
	 */
	public static List<ProgramTable> crawlProgramTable(String stationName,
			String date) {
		if (stationName == null || date == null) {
			logger.debug("station name or date is null.");
			return null;
		}
		TvStation station = Init.getIntance().getStation(stationName);
		if (station == null) {
			station = EpgDao.getStation(stationName);
		}
		return crawlProgramTable(station, date);
	}

	/**
	 * 根据电视台、日期获取电视节目表
	 * 
	 * @param htmlPage
	 *            已获取的html页面对象
	 * @param stationName
	 *            电视台名称
	 * @param date
	 *            日期，yyyy-MM-dd
	 * @return
	 */
	public static List<ProgramTable> crawlProgramTableByPage(HtmlPage htmlPage,
			String stationName, String date) {
		TvStation station = Init.getIntance().getStation(stationName);
		if (station == null) {
			station = EpgDao.getStation(stationName);
		}
		return crawlProgramTableByPage(htmlPage, station, date);
	}

	private static List<ProgramTable> crawlProgramTableByPage(
			HtmlPage htmlPage, TvStation station, String date) {
		if (station == null || htmlPage == null) {
			logger.debug("station and html page must not null.");
			return null;
		}
		Date dateObj = DateUtils.string2Date(date, "yyyy-MM-dd");
		if (dateObj == null) {
			logger.debug("date must not null.");
			return null;
		}
		String queryDate = DateFormatUtils.format(dateObj, "yyyy-MM-dd");
		logger.info("crawl program table of " + queryDate);
		String stationName = station.getName();
		if (EpgDao.isProgramTableExists(stationName, queryDate)) {
			logger.debug("the TV station's program table of " + stationName
					+ " have been saved in db.");
			return null;
		}

		List<?> classifyElements = htmlPage
				.getByXPath("//ul[@class='weishi']/li/a");
		for (Object element : classifyElements) {
			HtmlAnchor anchor = (HtmlAnchor) element;
			if (station.getClassify().equals(anchor.getTextContent())) {
				try {
					htmlPage = anchor.click();
				} catch (IOException e) {
					throw new MyTvListException(
							"error occur while search program table of "
									+ stationName + " at spec date: " + date, e);
				}
				break;
			}
		}

		if (queryDate != DateUtils.today()) {
			DomElement element = htmlPage.getElementById("date");
			element.setNodeValue(queryDate);
			element.setTextContent(queryDate);
			List<?> list = htmlPage.getByXPath("//div[@id='search_1']/a");
			HtmlAnchor anchor = (HtmlAnchor) list.get(0);
			try {
				htmlPage = anchor.click();
			} catch (IOException e) {
				throw new MyTvListException(
						"error occur while search program table of "
								+ stationName + " at spec date: " + date, e);
			}
		}
		String html = htmlPage.asXml();
		List<ProgramTable> ptList = EpgParser.parseProgramTable(html);
		ProgramTable[] ptArray = new ProgramTable[ptList.size()];
		EpgDao.save(ptList.toArray(ptArray));
		MyTvUtils.outputCrawlData(queryDate, html);
		return ptList;
	}

	/**
	 * 根据电视台、日期获取电视节目表
	 * 
	 * @param station
	 *            电视台对象
	 * @param date
	 *            日期，yyyy-MM-dd
	 * @return
	 */
	private static List<ProgramTable> crawlProgramTable(TvStation station,
			String date) {
		if (station == null) {
			logger.debug("the station must be not null.");
			return null;
		}
		Page page = Crawler.crawl(Constant.EPG_URL);
		if (!page.isHtmlPage()) {
			logger.debug("the page isn't html page at url " + Constant.EPG_URL);
			return null;
		}
		return crawlProgramTableByPage((HtmlPage) page, station, date);
	}
}