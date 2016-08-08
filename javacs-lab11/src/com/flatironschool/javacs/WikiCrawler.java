package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
        if(queue.isEmpty()){
        	return null;
        }

		//get first element in the queue
        String currentURL = queue.poll();

        //don't refresh index if testing is false
        if(testing==false && index.isIndexed(currentURL)){
        	return null;
        }

        Elements paragraphs;
        if(testing==true){
        	paragraphs = wf.readWikipedia(currentURL);
        }
        else{
        	paragraphs = wf.fetchWikipedia(currentURL);
        }
        index.indexPage(currentURL,paragraphs);

        //add current pages links to queue
        queueInternalLinks(paragraphs);
		return currentURL;
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
        for(Element paragraph: paragraphs){
        	queueParagraphInternalLinks(paragraph);
        }
	}

	/**
	* add internal links in a paragraph to the queue
	* @param paragraph
	*/
	private void queueParagraphInternalLinks(Element paragraph){
		Elements links = paragraph.select("a[href]");
		for(Element link: links){
			String linkURL = link.attr("href");

			//only queue wikipedia links
			if (linkURL.startsWith("/wiki/")) {
				String fullURL = "https://en.wikipedia.org" + linkURL;
				queue.offer(fullURL);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            break;
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
