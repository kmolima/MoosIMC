package pt.lsts.moosimc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.TimeZone;

import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.IMCOutputStream;
import pt.lsts.imc.LogBookEntry;
import pt.lsts.imc.LogBookEntry.TYPE;
import pt.lsts.imc.LoggingControl;
import pt.lsts.imc.LoggingControl.OP;
import pt.lsts.neptus.messages.listener.Periodic;

import java.util.zip.GZIPInputStream;

public class LogManagment {

	private Path currentLogName = null;
	private final String logName = "Data.lsf.gz";
	private final String basedir;
	private Calendar calendar;
	private LogBookEntry logbook;
	private final String context = "LogManagment";
	private Map<Path, OutputStream> toFlush;
	private final String systemid;

	public LogManagment(String moos_dir, String sys) {
		basedir = moos_dir;
		systemid = sys;
		calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		toFlush = new HashMap<>();

	}

	/**
	 * 
	 */
	private String[] getDatetimePaths() {
		calendar.setTime(new Date(System.currentTimeMillis()));
		int year = calendar.get(Calendar.YEAR);
		int month = calendar.get(Calendar.MONTH);
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int min = calendar.get(Calendar.MINUTE);
		int sec = calendar.get(Calendar.SECOND);
		String s1 = ("" + year + month + day), s2 = ("" + hour + min + sec);
		String[] s = new String[2];
		s[0] = s1;
		s[1] = s2;
		return s;

	}

	public LoggingControl startNewLog(String planid) {
		String[] datetime = getDatetimePaths();
		StringJoiner sj = new StringJoiner("_");
		sj.add(datetime[1]);
		sj.add(planid);
		currentLogName = Paths.get(basedir, systemid, datetime[0], sj.toString(), logName);
		LoggingControl lc = new LoggingControl(OP.STARTED, planid);
		return lc;
	}

	public LogBookEntry logBookEntry(TYPE type, String text) {
		ZonedDateTime zdt = ZonedDateTime.now(ZoneOffset.UTC);
		LogBookEntry le = new LogBookEntry(type, zdt.getSecond(), context, text);
		logMessage(le);
		return le;
	}

	public boolean logMessage(IMCMessage msg) {
		// TODO serialize
		// TODO add to ifs
//	     IMC::Packet::serialize(msg, m_buffer);
//	        m_lsf->write(m_buffer.getBufferSigned(), m_buffer.getSize());
		// msg.serialize(destination, offset)
		//msg.serialize(toFlush.get(currentLogName));

		return false;
	}

	public IMCMessage stopLog() {
		currentLogName = null;
		if (currentLogName != null) {
			String planid = currentLogName.getParent().getFileName().toString().split("_")[1];
			LoggingControl lc = new LoggingControl(OP.STOPPED, planid);
			// std::ifstream ifs(file.c_str(), std::ios::binary);
			// ifs.read(bfr, sizeof(bfr));
			// m_lsf->write(bfr, ifs.gcount());

			// TODO add os
			toFlush.put(currentLogName, null);
			return lc;
		}
		logbook = logBookEntry(TYPE.WARNING, "Unable to stop plan: no plan is running.");
		return logbook;
	}

	public void shutdown() {
		logBookEntry(TYPE.INFO, "Shutting down");
		writeLogToFile();
	}

	@Periodic(600_000)
	private boolean writeLogToFile() {
		if (!toFlush.isEmpty()) {
			while (toFlush.entrySet().iterator().hasNext()) {
				Entry<Path, OutputStream> entry = toFlush.entrySet().iterator().next();
				OutputStream os = entry.getValue();
				File logFile = entry.getKey().toFile();
				try {
					if(logFile.createNewFile()) {
						// TODO write to file - return result of write
						//create parent directory

					}
				} catch (IOException e) {
					logBookEntry(TYPE.ERROR, "Error creating log file.");
					e.printStackTrace();
				}
			}
		}
		return false;
	}

}
