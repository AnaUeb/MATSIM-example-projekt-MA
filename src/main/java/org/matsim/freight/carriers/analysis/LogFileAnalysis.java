// based on contribs/application/src/main/java/org/matsim/application/analysis/LogFileAnalysis.java
//

package org.matsim.freight.carriers.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.matsim.application.options.CsvOptions;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogFileAnalysis {

	private static final Logger log = LogManager.getLogger(LogFileAnalysis.class);

	Logger oldLog;
	String input;
	String output;

	private CsvOptions csv = new CsvOptions(CSVFormat.Predefined.Default);


	public LogFileAnalysis(Logger oldLog, String input, String output){
		this.oldLog = oldLog;
		this.input = input;
		this.output = output;
		csv.getFormat().builder().setDelimiter(RunFreightAnalysisEventBased.delimiter);
	}

	public void runLogFileAnalysis() throws Exception {
		log.info("Writing out log analysis ...");
		//Load per vehicle
		call();

	}
	private static LocalDateTime parseDate(String line) {
		// Ignore milliseconds part
		int idx = line.indexOf(',');
		return LocalDateTime.parse(line.substring(0, idx));
	}

	public Integer call() throws Exception {

		Pattern jSpritIteration = Pattern.compile("iterations end at (\\d+) iterations");
		Pattern durationTourPlanning = Pattern.compile("for carrier (\\w+_\\w+) took ([\\d.]+) seconds");


		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

		Map<String, String> info = new LinkedHashMap<>();

		List<Iteration> iterations = new ArrayList<>();

		String first = null;
		String last = null;

		LocalDateTime itBegin = null;

		try (BufferedReader reader = IOUtils.getBufferedReader(input+"logfile.log")) {
			String line;
			while ((line = reader.readLine()) != null) {

				try {

					if (line.contains("### ITERATION")) {
						if (line.contains("BEGINS")) {
							itBegin = parseDate(line);
						} else if (line.contains("ENDS")) {
							iterations.add(new Iteration(itBegin, parseDate(line)));
						}
					}

					Matcher m = jSpritIteration.matcher(line);
					if (m.find()) {
						info.put("jSprit Iterationen", String.valueOf(Integer.parseInt(m.group(1))));
					}

					m = durationTourPlanning.matcher(line);
					if (m.find()) {
						String carrier = m.group(1);
						double seconds = Double.parseDouble(m.group(2));
						info.put("Tour planning for "+carrier+":", String.valueOf(seconds));
					}

				} catch (Exception e) {
					//log.warn("Error processing line {}", line, e);
					continue;
				}

				if (first == null)
					first = line;

				last = line;
			}
		}


		if (first != null) {
			LocalDateTime start = parseDate(first);
			LocalDateTime end = parseDate(last);

			info.put("Start", formatter.format(start));
			info.put("End", formatter.format(end));
			info.put("Duration", DurationFormatUtils.formatDurationWords(Duration.between(start, end).toMillis(), true, true));
		}


		try (CSVPrinter printer = csv.createPrinter(Path.of(output + "run_info"+RunFreightAnalysisEventBased.fileExtension))) {
			printer.printRecord("info", "value");
			for (Map.Entry<String, String> e : info.entrySet()) {
				printer.printRecord(e.getKey(), e.getValue());
			}
			printer.printRecord("MATSim iterations",iterations.size()-1);
		}

		return 0;
	}


	private record Iteration(LocalDateTime begin, LocalDateTime end) {
	}

	private record Warning(String module, String msg) {

	}


}
