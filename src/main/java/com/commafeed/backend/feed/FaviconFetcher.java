package com.commafeed.backend.feed;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.commafeed.backend.HttpGetter;
import com.commafeed.backend.HttpGetter.HttpResult;

/**
 * Inspired/Ported from https://github.com/potatolondon/getfavicon
 * 
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
public class FaviconFetcher {

	private static List<String> ICON_MIMETYPE_BLACKLIST = Arrays.asList("application/xml", "text/html");
	private static long MIN_ICON_LENGTH = 100;
	private static long MAX_ICON_LENGTH = 100000;
	private static int TIMEOUT = 4000;

	private final HttpGetter getter;

	public byte[] fetch(String url) {

		if (url == null) {
			log.debug("url is null");
			return null;
		}

		int doubleSlash = url.indexOf("//");
		if (doubleSlash == -1) {
			doubleSlash = 0;
		} else {
			doubleSlash += 2;
		}
		int firstSlash = url.indexOf('/', doubleSlash);
		if (firstSlash != -1) {
			url = url.substring(0, firstSlash);
		}

		byte[] icon = getIconAtRoot(url);

		if (icon == null) {
			icon = getIconInPage(url);
		}

		return icon;
	}

	private byte[] getIconAtRoot(String url) {
		byte[] bytes = null;
		String contentType = null;

		try {
			url = FeedUtils.removeTrailingSlash(url) + "/favicon.ico";
			log.debug("getting root icon at {}", url);
			HttpResult result = getter.getBinary(url, TIMEOUT);
			bytes = result.getContent();
			contentType = result.getContentType();
		} catch (Exception e) {
			log.debug("Failed to retrieve iconAtRoot: " + e.getMessage(), e);
		}

		if (!isValidIconResponse(bytes, contentType)) {
			bytes = null;
		}
		return bytes;
	}

	private boolean isValidIconResponse(byte[] content, String contentType) {
		if (content == null) {
			return false;
		}

		long length = content.length;

		if (StringUtils.isNotBlank(contentType)) {
			contentType = contentType.split(";")[0];
		}

		if (ICON_MIMETYPE_BLACKLIST.contains(contentType)) {
			log.debug("Content-Type {} is blacklisted", contentType);
			return false;
		}

		if (length < MIN_ICON_LENGTH) {
			log.debug("Length {} below MIN_ICON_LENGTH {}", length, MIN_ICON_LENGTH);
			return false;
		}

		if (length > MAX_ICON_LENGTH) {
			log.debug("Length {} greater than MAX_ICON_LENGTH {}", length, MAX_ICON_LENGTH);
			return false;
		}

		return true;
	}

	private byte[] getIconInPage(String url) {

		Document doc = null;
		try {
			HttpResult result = getter.getBinary(url, TIMEOUT);
			doc = Jsoup.parse(new String(result.getContent()), url);
		} catch (Exception e) {
			log.debug("Failed to retrieve page to find icon");
			return null;
		}

		Elements icons = doc.select("link[rel~=(?i)^(shortcut|icon|shortcut icon)$]");

		if (icons.isEmpty()) {
			log.debug("No icon found in page {}", url);
			return null;
		}

		String href = icons.get(0).attr("abs:href");
		if (StringUtils.isBlank(href)) {
			log.debug("No icon found in page");
			return null;
		}

		log.debug("Found unconfirmed iconInPage at {}", href);

		byte[] bytes = null;
		String contentType = null;
		try {
			HttpResult result = getter.getBinary(href, TIMEOUT);
			bytes = result.getContent();
			contentType = result.getContentType();
		} catch (Exception e) {
			log.debug("Failed to retrieve icon found in page {}", href);
			return null;
		}

		if (!isValidIconResponse(bytes, contentType)) {
			log.debug("Invalid icon found for {}", href);
			return null;
		}

		return bytes;
	}

}
