package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
    private final Clock clock;
    private final Duration timeout;
    private final int popularWordCount;
    private final int threadCount;
    PageParserFactory parserFactory;
    private final int maxDepth;
    private final List<Pattern> ignoredUrls;


    @Inject
    ParallelWebCrawler(
            Clock clock,
            PageParserFactory parserFactory,
            @Timeout Duration timeout,
            @PopularWordCount int popularWordCount,
            @MaxDepth int maxDepth,
            @IgnoredUrls List<Pattern> ignoredUrls,
            @TargetParallelism int threadCount
    ) {
        this.clock = clock;
        this.timeout = timeout;
        this.maxDepth = maxDepth;
        this.ignoredUrls = ignoredUrls;
        this.threadCount = threadCount;
        this.popularWordCount = popularWordCount;
        this.parserFactory = parserFactory;

    }

    @Override
    public CrawlResult crawl(List<String> startingUrls) {

        ForkJoinPool pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
        ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();
        ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
        Instant timeLimit = clock.instant().plus(timeout);

        // Submit starting urls to pool and wait for them all to finish (join)
        List<ForkJoinTask<Void>> tasks = startingUrls.stream().map(url ->
                pool.submit(new CrawlTask(url, maxDepth, visitedUrls, counts, timeLimit))).toList();
        tasks.forEach(ForkJoinTask::join);

        //  shutdown() method will gracefully shut down the pool waiting for job completion.
        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.out.println("Exception shutting down pool");
            e.printStackTrace();
        }

        //  convert to Map<String, Integer> from Map<String, LongAdder>
        Map<String, Integer> integersWordCountMap = new HashMap<>();
        counts.forEach((key, value) -> integersWordCountMap.put(key, value.intValue()));


        if (counts.isEmpty()) {
            return new CrawlResult.Builder()
                    .setWordCounts(integersWordCountMap)
                    .setUrlsVisited(visitedUrls.size())
                    .build();
        }

        return new CrawlResult.Builder()
                .setWordCounts(WordCounts.sort(integersWordCountMap, popularWordCount))
                .setUrlsVisited(visitedUrls.size())
                .build();
    }

    private class CrawlTask extends RecursiveAction {
        private final String url;
        final int depth;
        final ConcurrentSkipListSet<String> visitedUrls;
        final ConcurrentHashMap<String, LongAdder> counts;
        final Instant timeLimit;

        private CrawlTask(String url,
                          int depth,
                          ConcurrentSkipListSet<String> visitedUrls,
                          ConcurrentHashMap<String, LongAdder> counts,
                          Instant timeLimit) {
            this.url = url;
            this.depth = depth;
            this.visitedUrls = visitedUrls;
            this.counts = counts;
            this.timeLimit = timeLimit;
        }

        @Override
        protected void compute() {

            // #Filter crawl depth reached
            if (depth <= 0) return;

            // #Filter ignored patterns
            for (Pattern pattern : ignoredUrls) {
                if (pattern.matcher(url).matches()) return;
            }

            /*
            Once time has been reached, the crawler will finish processing any HTML it has already downloaded,
            but it is not allowed to download any more HTML or follow any more hyperlinks.
            */
            if (clock.instant().isAfter(timeLimit)) return;

            PageParser.Result result = parserFactory.get(url).parse();

            // #Filter on visited
            if (!visitedUrls.add(url)) {
                return;
            }

            // add word counts to a thread-safe concurrent hashmap.
            result.getWordCounts().forEach((key, value) -> counts.computeIfAbsent(key, k -> new LongAdder()).add(value));


            // #Recursion: Process found url updating new maxDepth.
            // The ForkJoinPool is not specified as it is inherited  from the parent.
            invokeAll(
                    result.getLinks().stream()
                            .distinct()
                            .map(link -> new CrawlTask(link, depth - 1, visitedUrls, counts, timeLimit))
                            .collect(Collectors.toList())
            );
        }
    }

    @Override
    public int getMaxParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }
}
