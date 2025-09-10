/*
 * Teragrep Data Processing Language (DPL) translator for Apache Spark (pth_10)
 * Copyright (C) 2019-2025 Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.pth_10.executor;

import com.teragrep.pth_10.ast.DPLParserCatalystContext;
import com.typesafe.config.Config;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

public class DPLStreamingQueryListener extends StreamingQueryListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DPLStreamingQueryListener.class);

    private final String queryName;
    private final StreamingQuery streamingQuery;
    private final Config config;

    private final DPLParserCatalystContext catalystContext;

    public DPLStreamingQueryListener(
            StreamingQuery streamingQuery,
            Config config,
            DPLParserCatalystContext catalystContext
    ) {
        this.queryName = streamingQuery.name();
        this.streamingQuery = streamingQuery;
        this.config = config;
        this.catalystContext = catalystContext;
    }

    @Override
    public void onQueryStarted(QueryStartedEvent queryStarted) {
        LOGGER.info("Query started: {}", queryStarted.id());
    }

    @Override
    public void onQueryTerminated(QueryTerminatedEvent queryTerminated) {
        LOGGER.info("Query terminated: {}", queryTerminated.id());
        streamingQuery.sparkSession().streams().removeListener(this);
    }

    @Override
    public void onQueryProgress(QueryProgressEvent queryProgress) {
        LOGGER.debug("onQueryProgress() called");
        String nameOfStream = queryProgress.progress().name();
        LOGGER.debug("Name of stream: {}", nameOfStream);
        LOGGER.debug("Query name: {}", nameOfStream);

        if (queryName.equals(nameOfStream)) {
            LOGGER.debug("Name of stream equals query name");

            LOGGER.debug("Checking for completion");
            if (checkCompletion(streamingQuery)) {
                LOGGER.debug("Flushing context");
                // a flush call for post query actions to finish
                catalystContext.flush();
                try {
                    LOGGER.info("Stopping streaming query");
                    streamingQuery.stop();
                }
                catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private boolean checkCompletion(StreamingQuery streamingQuery) {
        LOGGER.debug("Checking for checkCompletion");
        if (!config.getBoolean("dpl.pth_07.checkCompletion")) {
            LOGGER.debug("CheckCompletion was not enabled");
            return false;
        }

        LOGGER.debug("Checking last progress || initializing sources");
        if (streamingQuery.lastProgress() == null || streamingQuery.status().message().equals("Initializing sources")) {
            // query has not started
            LOGGER.debug("Query has not started");
            return false;
        }

        boolean shouldStop = false;
        LOGGER.debug("Checking if sources exist");
        if (streamingQuery.lastProgress().sources().length != 0) {
            // sources have started
            LOGGER.debug("Sources exist, checking if query is done");
            shouldStop = SourceStatus.isQueryDone(config, streamingQuery);
        }

        /*
        if (streamingQuery.status().isTriggerActive()) {
            // if trigger is still processing
            shouldStop = false;
        }
         */
        LOGGER.debug("Returning shouldstop: {}", shouldStop);
        return shouldStop;
    }

}
